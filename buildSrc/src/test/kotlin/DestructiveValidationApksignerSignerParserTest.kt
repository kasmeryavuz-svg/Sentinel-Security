import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestructiveValidationApksignerSignerParserTest {
    @Test
    fun `one current signer and one certificate is reliable`() {
        val parsed = DestructiveValidationApksignerSignerParser.parse(ONE_SIGNER_ONE_CERT)
        assertTrue(parsed.reliable)
        assertEquals(1, parsed.currentSignerCount)
        assertEquals(setOf(1), parsed.currentSignerIndexes)
        assertEquals(CERT_A, parsed.currentCertificateSha256)
        assertFalse(parsed.lineagePresent)
        assertEquals(
            DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED,
            DestructiveValidationCandidateInspectors.classifyOfficialApksignerOutput(
                exitCode = 0,
                output = ONE_SIGNER_ONE_CERT,
            ),
        )
    }

    @Test
    fun `two current signers with different certificates are multiple signers`() {
        val parsed = DestructiveValidationApksignerSignerParser.parse(TWO_SIGNERS_DIFFERENT_CERTS)
        assertTrue(parsed.reliable)
        assertEquals(2, parsed.currentSignerCount)
        assertEquals(setOf(1, 2), parsed.currentSignerIndexes)
        assertNull(parsed.currentCertificateSha256)
        assertEquals(
            DestructiveValidationCandidateEvidence.Signing.MULTIPLE_SIGNERS,
            DestructiveValidationCandidateInspectors.classifyOfficialApksignerOutput(
                exitCode = 0,
                output = TWO_SIGNERS_DIFFERENT_CERTS,
            ),
        )
    }

    @Test
    fun `two current signers with the same certificate digest are still multiple signers`() {
        val parsed = DestructiveValidationApksignerSignerParser.parse(TWO_SIGNERS_SAME_CERT)
        assertTrue(parsed.reliable)
        assertEquals(2, parsed.currentSignerCount)
        assertEquals(1, parsed.currentCertificateSha256BySigner.values.distinct().size)
        assertEquals(
            DestructiveValidationCandidateEvidence.Signing.MULTIPLE_SIGNERS,
            DestructiveValidationCandidateInspectors.classifyOfficialApksignerOutput(
                exitCode = 0,
                output = TWO_SIGNERS_SAME_CERT,
            ),
        )
    }

    @Test
    fun `one current signer with certificate lineage is not multiple signers`() {
        val parsed = DestructiveValidationApksignerSignerParser.parse(ONE_SIGNER_WITH_LINEAGE)
        assertTrue(parsed.reliable)
        assertEquals(1, parsed.currentSignerCount)
        assertTrue(parsed.lineagePresent)
        assertEquals(CERT_A, parsed.currentCertificateSha256)
        assertEquals(
            DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED,
            DestructiveValidationCandidateInspectors.classifyOfficialApksignerOutput(
                exitCode = 0,
                output = ONE_SIGNER_WITH_LINEAGE,
            ),
        )
    }

    @Test
    fun `malformed number of signers is unreliable and unverifiable`() {
        val parsed = DestructiveValidationApksignerSignerParser.parse(MALFORMED_NUMBER_OF_SIGNERS)
        assertFalse(parsed.reliable)
        assertNull(parsed.currentSignerCount)
        assertEquals("malformed_number_of_signers", parsed.unreliabilityReason)
        assertEquals(
            DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE,
            DestructiveValidationCandidateInspectors.classifyOfficialApksignerOutput(
                exitCode = 0,
                output = MALFORMED_NUMBER_OF_SIGNERS,
            ),
        )
    }

    @Test
    fun `missing signer indexes with a loose digest is unreliable`() {
        val parsed = DestructiveValidationApksignerSignerParser.parse(MISSING_SIGNER_INDEXES)
        assertFalse(parsed.reliable)
        assertEquals("missing_signer_indexes", parsed.unreliabilityReason)
        assertEquals(
            DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE,
            DestructiveValidationCandidateInspectors.classifyOfficialApksignerOutput(
                exitCode = 0,
                output = MISSING_SIGNER_INDEXES,
            ),
        )
    }

    @Test
    fun `contradictory signer count and fingerprint output is unverifiable`() {
        val parsed = DestructiveValidationApksignerSignerParser.parse(CONTRADICTORY_COUNT)
        assertFalse(parsed.reliable)
        assertTrue(
            parsed.unreliabilityReason == "contradictory_signer_count_and_indexes" ||
                parsed.unreliabilityReason == "contradictory_signer_count_and_fingerprint_output",
        )
        assertEquals(
            DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE,
            DestructiveValidationCandidateInspectors.classifyOfficialApksignerOutput(
                exitCode = 0,
                output = CONTRADICTORY_COUNT,
            ),
        )
    }

    private companion object {
        const val CERT_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val CERT_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

        val ONE_SIGNER_ONE_CERT = """
            Verifies
            Verified using v2 scheme (APK Signature Scheme v2): true
            Verified using v3 scheme (APK Signature Scheme v3): true
            Number of signers: 1
            Signer #1 certificate DN: CN=Example, O=Sentinel
            Signer #1 certificate SHA-256 digest: $CERT_A
            Signer #1 key algorithm: RSA
            Signer #1 key size (bits): 2048
        """.trimIndent()

        val TWO_SIGNERS_DIFFERENT_CERTS = """
            Verifies
            Number of signers: 2
            Signer #1 certificate DN: CN=First
            Signer #1 certificate SHA-256 digest: $CERT_A
            Signer #2 certificate DN: CN=Second
            Signer #2 certificate SHA-256 digest: $CERT_B
        """.trimIndent()

        val TWO_SIGNERS_SAME_CERT = """
            Verifies
            Number of signers: 2
            Signer #1 certificate DN: CN=Shared
            Signer #1 certificate SHA-256 digest: $CERT_A
            Signer #2 certificate DN: CN=Shared
            Signer #2 certificate SHA-256 digest: $CERT_A
        """.trimIndent()

        val ONE_SIGNER_WITH_LINEAGE = """
            Verifies
            Verified using v3 scheme (APK Signature Scheme v3): true
            Number of signers: 1
            Signer #1 certificate DN: CN=Current
            Signer #1 certificate SHA-256 digest: $CERT_A
            Signer #1 signing certificate lineage (in oldest-to-newest order):
            Signer #1, certificate #1 (oldest):
                DN: CN=Oldest
                SHA-256 digest: $CERT_B
            Signer #1, certificate #2 (newest):
                DN: CN=Current
                SHA-256 digest: $CERT_A
        """.trimIndent()

        val MALFORMED_NUMBER_OF_SIGNERS = """
            Verifies
            Number of signers: potato
            Signer #1 certificate SHA-256 digest: $CERT_A
        """.trimIndent()

        val MISSING_SIGNER_INDEXES = """
            Verifies
            Verified using v2 scheme (APK Signature Scheme v2): true
            certificate SHA-256 digest: $CERT_A
        """.trimIndent()

        val CONTRADICTORY_COUNT = """
            Verifies
            Number of signers: 1
            Signer #1 certificate DN: CN=First
            Signer #1 certificate SHA-256 digest: $CERT_A
            Signer #2 certificate DN: CN=Second
            Signer #2 certificate SHA-256 digest: $CERT_B
        """.trimIndent()
    }
}
