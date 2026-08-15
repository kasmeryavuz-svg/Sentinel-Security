import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Checkpoint16DpmAllowlistFreezeTest {
    @Test
    fun `production DPM invocation allowlist is the exact complete key set`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        val allowedKeys = Regex("\"([^\"]+\\([^\"]*\\)[^\"]*)\"\\s+to\\s+origins")
            .findAll(allowlistBlock)
            .map { it.groupValues[1] }
            .toSet()

        val expectedKeys = setOf(
            "isDeviceOwnerApp(Ljava/lang/String;)Z",
            "isProfileOwnerApp(Ljava/lang/String;)Z",
            "isAdminActive(Landroid/content/ComponentName;)Z",
            "isProvisioningAllowed(Ljava/lang/String;)Z",
            "getActiveAdmins()Ljava/util/List;",
            "getScreenCaptureDisabled(Landroid/content/ComponentName;)Z",
            "getCameraDisabled(Landroid/content/ComponentName;)Z",
            "isStatusBarDisabled()Z",
            "setScreenCaptureDisabled(Landroid/content/ComponentName;Z)V",
            "setCameraDisabled(Landroid/content/ComponentName;Z)V",
            "setStatusBarDisabled(Landroid/content/ComponentName;Z)Z",
            "wipeDevice(I)V",
        )

        assertEquals(expectedKeys, allowedKeys)

        val writeKeys = allowedKeys.filter { it.startsWith("set") }.toSet()
        assertEquals(
            setOf(
                "setScreenCaptureDisabled(Landroid/content/ComponentName;Z)V",
                "setCameraDisabled(Landroid/content/ComponentName;Z)V",
                "setStatusBarDisabled(Landroid/content/ComponentName;Z)Z",
            ),
            writeKeys,
        )
    }

    @Test
    fun `release DEX denylist still forbids destructive DPM tokens`() {
        val source = File("src/main/kotlin/ReleaseArtifactSecurityVerifier.kt").readText()
        val denylist = source
            .substringAfter("private val forbiddenDexTokens = listOf(")
            .substringBefore("private val debugCertMarkers")

        assertTrue(denylist.contains("\"wipeData\""))
        assertTrue(!denylist.contains("\"wipeDevice\""))
        assertTrue(denylist.contains("\"lockNow\""))
        assertTrue(denylist.contains("\"resetPassword\""))
        assertTrue(denylist.contains("\"clearDeviceOwnerApp\""))
    }
}
