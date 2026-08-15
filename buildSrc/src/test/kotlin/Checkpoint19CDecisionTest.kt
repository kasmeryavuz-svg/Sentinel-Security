import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint19CDecisionTest {
    @Test
    fun `19B wipeDevice origin and wipeData hard-block stay unchanged`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(allowlistBlock.contains("wipeDevice(I)V"))
        assertTrue(allowlistBlock.contains("AndroidDevicePolicyFactoryResetService"))
        assertTrue(allowlistBlock.contains("performAuthorizedFactoryReset"))
        assertTrue(!allowlistBlock.contains("wipeData"))
        assertTrue(source.contains("exact integer constant 0"))
        assertTrue(source.contains("com/example/devicemanagement/destructive/Checkpoint19CDecision"))
        val forbidden = source
            .substringAfter("checkpoint17BForbiddenDpmMethodNames = setOf(")
            .substringBefore(")")
        assertTrue(forbidden.contains("\"wipeData\""))
        assertTrue(!forbidden.contains("\"wipeDevice\""))
        val dex = File("src/main/kotlin/DexWipeDeviceVerifier.kt").readText()
        assertTrue(dex.contains("control-flow proof of exact integer constant 0"))
        assertTrue(dex.contains("handlerEntries"))
        val release = File("src/main/kotlin/ReleaseArtifactSecurityVerifier.kt").readText()
        val denylist = release
            .substringAfter("private val forbiddenDexTokens = listOf(")
            .substringBefore("private val debugCertMarkers")
        assertTrue(denylist.contains("\"wipeData\""))
        assertTrue(!denylist.contains("\"wipeDevice\""))
    }
}
