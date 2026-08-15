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
        assertTrue(block.contains("\"wipeDevice\""))
        assertTrue(source.contains("FutureDestructiveRealChainBoundary"))
        assertTrue(source.contains("assembleAndHandoff"))
        assertTrue(source.contains("RuntimeDurablePreExecutionCommitProof"))
    }

    @Test
    fun `production DPM allowlist still excludes destructive methods`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(!allowlistBlock.contains("wipeData"))
        assertTrue(!allowlistBlock.contains("wipeDevice"))
    }
}
