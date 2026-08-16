import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndependentWitnessAuthorityEnrollmentTest {
    @Test
    fun `valid enrollment schema round-trips without becoming repository state`() {
        val record = validRecord()
        val parsed = IndependentWitnessAuthorityEnrollment.parse(
            IndependentWitnessAuthorityEnrollment.render(record),
        )
        assertEquals(record, parsed)
        assertTrue(IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isEmpty())
        assertTrue(
            IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty(),
        )
    }

    @Test
    fun `parser accepts supported independence classifications`() {
        IndependentWitnessAuthorityEnrollment.IndependenceBasis.values().forEach { basis ->
            val parsed = IndependentWitnessAuthorityEnrollment.parse(
                IndependentWitnessAuthorityEnrollment.render(
                    validRecord().copy(independenceBasis = basis),
                ),
            )
            assertEquals(basis, parsed.independenceBasis)
        }
    }

    @Test
    fun `operator and witness identifiers must differ`() {
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                IndependentWitnessAuthorityEnrollment.render(
                    validRecord().copy(operatorIdentifier = "external-reviewer"),
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                IndependentWitnessAuthorityEnrollment.render(
                    validRecord().copy(operatorIdentifier = "EXTERNAL-REVIEWER"),
                ),
            )
        }
    }

    @Test
    fun `public-key fingerprint hashes stable bytes and rejects private-key names`() {
        val root = Files.createTempDirectory("19u-fingerprint").toFile()
        try {
            val publicKey = File(root, "witness.pub").apply { writeText("abc") }
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223" +
                    "b00361a396177a9cb410ff61f20015ad",
                IndependentWitnessAuthorityEnrollment.verificationKeySha256(publicKey),
            )
            val privateNamed = File(root, "witness-private.pub").apply { writeText("abc") }
            assertFailsWith<IllegalStateException> {
                IndependentWitnessAuthorityEnrollment.verificationKeySha256(privateNamed)
            }
            val pk8 = File(root, "witness.pk8").apply { writeText("abc") }
            assertFailsWith<IllegalStateException> {
                IndependentWitnessAuthorityEnrollment.verificationKeySha256(pk8)
            }
            val linked = File(root, "linked.pub")
            Files.createSymbolicLink(linked.toPath(), publicKey.toPath())
            assertFailsWith<IllegalStateException> {
                IndependentWitnessAuthorityEnrollment.verificationKeySha256(linked)
            }
            assertTrue(publicKey.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `malformed and ambiguous enrollment records fail closed`() {
        val valid = IndependentWitnessAuthorityEnrollment.render(validRecord())
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace("enrollment_version=1", "enrollment_version=2"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace(FINGERPRINT, "not-a-digest"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace(REVISION, "not-a-commit"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace(TIMESTAMP, "2026-08-16 19:00:00Z"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace("independence_basis=SEPARATE_NATURAL_PERSON", "independence_basis=SELF"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace("\nreview_identifier=review-desk\n", "\n"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace("witness_identifier=external-reviewer", "witness_identifier="),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace(
                    "witness_identifier=external-reviewer\n",
                    "witness_identifier=external-reviewer\nwitness_identifier=other\n",
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(valid + "extra_critical=1\n")
        }
        assertFailsWith<IllegalStateException> {
            FailClosedStatusLines.parseUnique("witness_identifier=a\nwitness_identifier=b\n")
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace("independence_basis=SEPARATE_NATURAL_PERSON\n", ""),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace("witness_identifier=external-reviewer", "witness_identifier=ci"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessAuthorityEnrollment.parse(
                valid.replace(
                    "witness_identifier=external-reviewer",
                    "witness_identifier=19t-report",
                ),
            )
        }
    }

    @Test
    fun `signature validity and key fingerprint never imply independence`() {
        assertFalse(
            IndependentWitnessAuthorityEnrollment.independenceFromSignatureValidity(true),
        )
        assertFalse(
            IndependentWitnessAuthorityEnrollment.independenceFromKeyFingerprint(FINGERPRINT),
        )
        assertFalse(
            IndependentWitnessAuthorityContract.independenceEstablished("external-reviewer"),
        )
        assertFalse(
            IndependentWitnessAuthorityContract.approval(
                statementPresent = true,
                signatureVerified = true,
                evidenceMatches = true,
                independenceEstablished = false,
            ),
        )
    }

    private fun validRecord(): IndependentWitnessAuthorityEnrollment.Record {
        return IndependentWitnessAuthorityEnrollment.Record(
            witnessIdentifier = "external-reviewer",
            witnessDisplayName = "External Reviewer",
            witnessVerificationKeySha256 = FINGERPRINT,
            witnessRole = IndependentWitnessAuthorityEnrollment.WITNESS_ROLE,
            independenceBasis =
                IndependentWitnessAuthorityEnrollment.IndependenceBasis.SEPARATE_NATURAL_PERSON,
            enrollmentTimestampUtc = TIMESTAMP,
            enrollmentRepositoryRevision = REVISION,
            operatorIdentifier = "local-operator",
            reviewIdentifier = "review-desk",
        )
    }

    private companion object {
        const val FINGERPRINT =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val REVISION = "12".repeat(20)
        const val TIMESTAMP = "2026-08-16T19:00:00Z"
    }
}
