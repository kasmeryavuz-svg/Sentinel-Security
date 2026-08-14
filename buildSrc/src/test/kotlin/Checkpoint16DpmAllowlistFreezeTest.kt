import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Checkpoint16DpmAllowlistFreezeTest {
    @Test
    fun `production DPM mutator allowlist remains exactly the three reversible setters`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        val allowedMethods = Regex("\"([a-zA-Z]+)\\(")
            .findAll(allowlistBlock)
            .map { it.groupValues[1] }
            .toSet()
        val allowedMutators = allowedMethods.filter { it.startsWith("set") }.toSet()

        assertEquals(
            setOf(
                "setScreenCaptureDisabled",
                "setCameraDisabled",
                "setStatusBarDisabled",
            ),
            allowedMutators,
        )
        assertFalse("wipeData" in allowedMethods)
        assertFalse("wipeDevice" in allowedMethods)
        assertFalse("lockNow" in allowedMethods)
        assertFalse("resetPassword" in allowedMethods)
        assertFalse(allowlistBlock.contains("wipeData"))
        assertFalse(allowlistBlock.contains("wipeDevice"))
    }

    @Test
    fun `release DEX denylist still forbids destructive DPM tokens`() {
        val source = File("src/main/kotlin/ReleaseArtifactSecurityVerifier.kt").readText()
        val denylist = source
            .substringAfter("private val forbiddenDexTokens = listOf(")
            .substringBefore("private val debugCertMarkers")

        assertTrue(denylist.contains("\"wipeData\""))
        assertTrue(denylist.contains("\"wipeDevice\""))
        assertTrue(denylist.contains("\"lockNow\""))
        assertTrue(denylist.contains("\"resetPassword\""))
        assertTrue(denylist.contains("\"clearDeviceOwnerApp\""))
    }
}
