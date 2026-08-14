import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseArtifactSecurityVerifierTest {
    private val arbitraryNonDebugCert = """
        Signer #1 certificate DN: CN=Random Developer, OU=Engineering, O=Example Corp, C=US
        Signer #1 certificate SHA-256 digest: deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef
    """.trimIndent()

    private val arbitraryFingerprint =
        "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
    private val otherFingerprint =
        "0000000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun `missing required class after R8 fails closed`() {
        val violations = ReleaseArtifactSecurityVerifier.verifyPackagedDex(
            strings = emptySet(),
            sourceName = "app-release.apk",
        )
        assertTrue(violations.any { "SentinelDeviceAdminReceiver" in it })
        assertTrue(violations.any { "DeviceManagementImplementation" in it })
        assertTrue(violations.any { "RecoveryInspection" in it })
    }

    @Test
    fun `forbidden destructive tokens fail closed`() {
        val violations = ReleaseArtifactSecurityVerifier.verifyPackagedDex(
            strings = setOf("wipeData", "lockNow"),
            sourceName = "app-release.apk",
        )
        assertTrue(violations.any { "wipeData" in it })
        assertTrue(violations.any { "lockNow" in it })
    }

    @Test
    fun `debug certificate is classified as test-signed`() {
        val classification = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = "Signer #1 certificate DN: CN=Android Debug, O=Android, C=US",
            signed = true,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.TEST_SIGNED,
            classification,
        )
        val productionViolations = ReleaseArtifactSecurityVerifier.verifySigningBoundary(
            classification = classification,
            productionDistributionRequested = true,
        )
        assertTrue(productionViolations.any { "debug" in it })
        val localViolations = ReleaseArtifactSecurityVerifier.verifySigningBoundary(
            classification = classification,
            productionDistributionRequested = false,
        )
        assertTrue(localViolations.isEmpty())
    }

    @Test
    fun `unsigned artifacts are not production distributions`() {
        val classification = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = "DOES NOT VERIFY",
            signed = false,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.UNSIGNED,
            classification,
        )
        val productionViolations = ReleaseArtifactSecurityVerifier.verifySigningBoundary(
            classification = classification,
            productionDistributionRequested = true,
        )
        assertTrue(productionViolations.any { "unsigned" in it })
    }

    @Test
    fun `arbitrary non-debug certificate is never production without expected fingerprint`() {
        val classification = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = arbitraryNonDebugCert,
            signed = true,
            expectedProductionFingerprint = null,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.UNKNOWN,
            classification,
        )
        assertNotEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.PRODUCTION_SIGNED,
            classification,
        )
        val productionViolations = ReleaseArtifactSecurityVerifier.verifySigningBoundary(
            classification = classification,
            productionDistributionRequested = true,
        )
        assertTrue(productionViolations.isNotEmpty())
    }

    @Test
    fun `arbitrary non-debug certificate is never production when fingerprint mismatches`() {
        val classification = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = arbitraryNonDebugCert,
            signed = true,
            expectedProductionFingerprint = otherFingerprint,
        )
        assertNotEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.PRODUCTION_SIGNED,
            classification,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.UNKNOWN,
            classification,
        )
    }

    @Test
    fun `only exact configured production fingerprint is production signed`() {
        val classification = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = arbitraryNonDebugCert,
            signed = true,
            expectedProductionFingerprint = arbitraryFingerprint,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.PRODUCTION_SIGNED,
            classification,
        )
        assertTrue(
            ReleaseArtifactSecurityVerifier.verifySigningBoundary(
                classification = classification,
                productionDistributionRequested = true,
            ).isEmpty(),
        )
    }

    @Test
    fun `fingerprint normalization accepts colon space and mixed case`() {
        val formatted = "DE:AD:BE:EF:DE:AD:BE:EF:DE:AD:BE:EF:DE:AD:BE:EF:" +
            "DE:AD:BE:EF:DE:AD:BE:EF:DE:AD:BE:EF:DE:AD:BE:EF"
        assertEquals(
            arbitraryFingerprint,
            ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(formatted),
        )
        assertEquals(
            arbitraryFingerprint,
            ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(
                "DEAD BEEF DEAD-BEEF DEAD_BEEF DEADBEEF DEADBEEF DEADBEEF DEADBEEF DEADBEEF",
            ),
        )
        val classified = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = arbitraryNonDebugCert,
            signed = true,
            expectedProductionFingerprint = formatted,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.PRODUCTION_SIGNED,
            classified,
        )
    }

    @Test
    fun `invalid fingerprint formatting never becomes a production identity`() {
        assertNull(ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint("not-a-hash"))
        assertNull(ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint("deadbeef"))
        assertNull(
            ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(
                "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz",
            ),
        )
        val classification = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = arbitraryNonDebugCert,
            signed = true,
            expectedProductionFingerprint = "not-a-hash",
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.UNKNOWN,
            classification,
        )
        val productionConfig =
            ReleaseArtifactSecurityVerifier.verifyExpectedProductionFingerprint(
                productionDistributionRequested = true,
                expectedProductionFingerprint = "not-a-hash",
            )
        assertTrue(productionConfig.any { "valid SHA-256" in it })
    }

    @Test
    fun `production distribution fails unless classification is exactly production signed`() {
        val classifications = enumValues<ReleaseArtifactSecurityVerifier.SigningClassification>()
        classifications.forEach { classification ->
            val violations = ReleaseArtifactSecurityVerifier.verifySigningBoundary(
                classification = classification,
                productionDistributionRequested = true,
            )
            if (classification ==
                ReleaseArtifactSecurityVerifier.SigningClassification.PRODUCTION_SIGNED
            ) {
                assertTrue(violations.isEmpty(), classification.name)
            } else {
                assertTrue(violations.isNotEmpty(), classification.name)
            }
        }
    }

    @Test
    fun `missing expected production fingerprint fails closed only for production`() {
        val production = ReleaseArtifactSecurityVerifier.verifyExpectedProductionFingerprint(
            productionDistributionRequested = true,
            expectedProductionFingerprint = null,
        )
        assertTrue(production.any { "SENTINEL_RELEASE_CERT_SHA256" in it })
        val local = ReleaseArtifactSecurityVerifier.verifyExpectedProductionFingerprint(
            productionDistributionRequested = false,
            expectedProductionFingerprint = null,
        )
        assertTrue(local.isEmpty())
    }

    @Test
    fun `missing apksigner fails closed only for production distribution`() {
        val production = ReleaseArtifactSecurityVerifier.verifyApksignerAvailability(
            productionDistributionRequested = true,
            apksignerAvailable = false,
        )
        assertTrue(production.any { "apksigner.bat" in it })
        val local = ReleaseArtifactSecurityVerifier.verifyApksignerAvailability(
            productionDistributionRequested = false,
            apksignerAvailable = false,
        )
        assertTrue(local.isEmpty())
        assertTrue(
            ReleaseArtifactSecurityVerifier.verifyApksignerAvailability(
                productionDistributionRequested = true,
                apksignerAvailable = true,
            ).isEmpty(),
        )
    }

    @Test
    fun `unsigned archive without signature files is classified unsigned`() {
        val archive = unsignedZip()
        val evidence = ReleaseArtifactSecurityVerifier.inspectSignedArchive(archive)
        assertTrue(!evidence.signed)
        val classification = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = evidence.certificateOutput,
            signed = evidence.signed,
            expectedProductionFingerprint = arbitraryFingerprint,
            observedFingerprints = evidence.fingerprints,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.UNSIGNED,
            classification,
        )
    }

    @Test
    fun `generated non-debug keystore is never production without expected fingerprint`() {
        val signed = SignedArchiveFixtures.signedJar()
        val evidence = ReleaseArtifactSecurityVerifier.inspectSignedArchive(signed)
        assertTrue(evidence.signed, "signed archive should have a certificate")
        assertTrue(evidence.fingerprints.isNotEmpty())
        assertTrue(evidence.certificateOutput.contains("Arbitrary Developer"))
        val withoutExpected = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = evidence.certificateOutput,
            signed = true,
            expectedProductionFingerprint = null,
            observedFingerprints = evidence.fingerprints,
        )
        assertNotEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.PRODUCTION_SIGNED,
            withoutExpected,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.UNKNOWN,
            withoutExpected,
        )
        val mismatched = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = evidence.certificateOutput,
            signed = true,
            expectedProductionFingerprint = otherFingerprint,
            observedFingerprints = evidence.fingerprints,
        )
        assertNotEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.PRODUCTION_SIGNED,
            mismatched,
        )
        val matched = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = evidence.certificateOutput,
            signed = true,
            expectedProductionFingerprint = evidence.fingerprints.single(),
            observedFingerprints = evidence.fingerprints,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.PRODUCTION_SIGNED,
            matched,
        )
    }

    @Test
    fun `debug certificate stays test-signed even if a fingerprint is configured`() {
        val debugOutput = """
            Signer #1 certificate DN: CN=Android Debug, O=Android, C=US
            Signer #1 certificate SHA-256 digest: $arbitraryFingerprint
        """.trimIndent()
        val classification = ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = debugOutput,
            signed = true,
            expectedProductionFingerprint = arbitraryFingerprint,
        )
        assertEquals(
            ReleaseArtifactSecurityVerifier.SigningClassification.TEST_SIGNED,
            classification,
        )
    }

    private fun unsignedZip(): File {
        val file = File.createTempFile("unsigned", ".apk")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write("Manifest-Version: 1.0\n".toByteArray())
            zip.closeEntry()
        }
        return file
    }
}
