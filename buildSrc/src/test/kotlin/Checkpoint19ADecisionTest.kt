import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint19ADecisionTest {
    @Test
    fun `bytecode verifier still hard-blocks destructive DPM method names`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val block = source
            .substringAfter("checkpoint17BForbiddenDpmMethodNames = setOf(")
            .substringBefore(")")
        assertTrue(block.contains("\"wipeData\""))
        assertTrue(block.contains("\"wipeDevice\""))
        assertTrue(source.contains("if (name in checkpoint17BForbiddenDpmMethodNames)"))
        assertTrue(source.contains("Checkpoint 17B-blocked"))
        assertTrue(source.contains("com/example/devicemanagement/destructive/Checkpoint19ADecision"))
    }

    @Test
    fun `production DPM allowlist still excludes destructive methods`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(!allowlistBlock.contains("wipeData"))
        assertTrue(!allowlistBlock.contains("wipeDevice"))
        val writeKeys = Regex("\"(set[^\"]+\\([^\"]*\\)[^\"]*)\"\\s+to\\s+origins")
            .findAll(allowlistBlock)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(
            writeKeys == setOf(
                "setScreenCaptureDisabled(Landroid/content/ComponentName;Z)V",
                "setCameraDisabled(Landroid/content/ComponentName;Z)V",
                "setStatusBarDisabled(Landroid/content/ComponentName;Z)Z",
            ),
        )
    }

    @Test
    fun `release DEX denylist still rejects both destructive tokens`() {
        val source = File("src/main/kotlin/ReleaseArtifactSecurityVerifier.kt").readText()
        val denylist = source
            .substringAfter("private val forbiddenDexTokens = listOf(")
            .substringBefore("private val debugCertMarkers")
        assertTrue(denylist.contains("\"wipeData\""))
        assertTrue(denylist.contains("\"wipeDevice\""))
        assertTrue(!source.contains("AndroidFutureDestructiveExecutor"))
        assertTrue(!source.contains("AndroidDevicePolicyFactoryResetService"))
    }
}
