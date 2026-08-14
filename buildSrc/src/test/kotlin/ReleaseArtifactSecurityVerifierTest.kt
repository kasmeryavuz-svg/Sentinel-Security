import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseArtifactSecurityVerifierTest {
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
        assertTrue(productionViolations.any { "debug key" in it })
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
    }
}
