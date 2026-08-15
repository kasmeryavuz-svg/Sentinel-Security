import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint19EDecisionTest {
    @Test
    fun `19E records independent CI without claiming later destructive states`() {
        val source = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19EDecision.kt",
        ).readText()
        assertTrue(source.contains("INDEPENDENT_CI_WORKFLOW_PRESENT = true"))
        assertTrue(source.contains("GITHUB_CI_RUN_OBSERVED = false"))
        assertTrue(source.contains("BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED = false"))
        assertTrue(source.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(source.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(source.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(source.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(source.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(source.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = \"NO\""))
        assertTrue(source.contains("CHECKPOINT_19E_USED_AS_RUNTIME_AUTHORIZATION = false"))
        assertTrue(source.contains("\"19E_INDEPENDENT_CI_WORKFLOW_PRESENT\" to \"true\""))
        assertTrue(source.contains("\"19E_GITHUB_CI_RUN_OBSERVED\" to \"false\""))
        assertTrue(source.contains("\"19E_BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED\" to \"false\""))
        assertTrue(!source.contains("wipeDevice"))
        assertTrue(!source.contains("wipeData"))
        assertTrue(!HEX_SHA256.containsMatchIn(source))
        val later = source
            .substringAfter("val laterStatesThatMustStayClosed = listOf(")
            .substringBefore(")")
        assertTrue(later.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED"))
        assertTrue(later.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED"))
        assertTrue(later.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED"))
        assertTrue(later.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED"))
        val verifier = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        assertTrue(
            verifier.contains("com/example/devicemanagement/destructive/Checkpoint19EDecision"),
        )
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
