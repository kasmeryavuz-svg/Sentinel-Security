import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint19JDecisionTest {
    @Test
    fun `19J records infrastructure repair without later destructive states`() {
        val source = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19JDecision.kt",
        ).readText()
        assertTrue(source.contains("AUDIT_FINDINGS_REPAIRED = \"YES\""))
        assertTrue(source.contains("CANDIDATE_TASK_SNAPSHOT_PATHS_ISOLATED = true"))
        assertTrue(source.contains("CANDIDATE_TASK_REPORT_PATHS_ISOLATED = true"))
        assertTrue(source.contains("SNAPSHOT_CLEANUP_ENFORCED = true"))
        assertTrue(source.contains("ORDINARY_RELEASE_REMAINS_UNSIGNED = true"))
        assertTrue(source.contains("PRODUCTION_SIGNING_REQUIRES_EXPLICIT_DISTRIBUTION_REQUEST = true"))
        assertTrue(source.contains("PRODUCTION_SIGNING_PERFORMED = false"))
        assertTrue(source.contains("SIGNED_VALIDATION_CANDIDATE_PRODUCED = false"))
        assertTrue(source.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(source.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(source.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(source.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = \"NO\""))
        assertTrue(source.contains("CHECKPOINT_19J_USED_AS_RUNTIME_AUTHORIZATION = false"))
        assertTrue(source.contains("\"19J_CANDIDATE_TASK_SNAPSHOT_PATHS_ISOLATED\" to \"true\""))
        assertTrue(source.contains("\"19J_PRODUCTION_SIGNING_PERFORMED\" to \"false\""))
        assertTrue(!source.contains("wipeDevice"))
        assertTrue(!source.contains("wipeData"))
        assertTrue(!HEX_SHA256.containsMatchIn(source))
        val verifier = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        assertTrue(
            verifier.contains("com/example/devicemanagement/destructive/Checkpoint19JDecision"),
        )
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
