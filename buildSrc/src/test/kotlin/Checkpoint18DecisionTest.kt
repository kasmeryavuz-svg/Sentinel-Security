import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint18DecisionTest {
    @Test
    fun `bytecode verifier still hard-blocks destructive DPM method names`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val block = source
            .substringAfter("checkpoint17BForbiddenDpmMethodNames = setOf(")
            .substringBefore(")")
        assertTrue(block.contains("\"wipeData\""))
        assertTrue(!block.contains("\"wipeDevice\""))
        assertTrue(source.contains("FutureDestructiveRealChainBoundary"))
        assertTrue(source.contains("assembleAndHandoff"))
        assertTrue(source.contains("RuntimeDurablePreExecutionCommitProof"))
        assertTrue(source.contains("mintFinalLiveValidationPermit"))
        assertTrue(source.contains("assembleBundleFromPermit"))
        assertTrue(source.contains("commitAfterConsumedAuthorization"))
        assertTrue(source.contains("onAuthorizedHandoff"))
        assertTrue(source.contains("RealChainHandoffRegistry"))
        assertTrue(source.contains("IssuedRealChainFinalLiveValidationPermit"))
        assertTrue(source.contains("IssuedFutureDestructiveExecutionBundle"))
        assertTrue(source.contains("registerIssuedPermit"))
        assertTrue(source.contains("consumeIssuedBundle"))
    }

    @Test
    fun `production DPM allowlist still excludes destructive methods`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(!allowlistBlock.contains("wipeData"))
        assertTrue(allowlistBlock.contains("wipeDevice(I)V"))
    }
}
