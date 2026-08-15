import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Checkpoint19PDecisionTest {
    @Test
    fun `19P records cleanup and observation without later destructive states`() {
        val source = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19PGovernanceObservation.kt",
        ).readText()
        assertTrue(source.contains("MAINTAINABILITY_CLEANUP = \"YES\""))
        assertTrue(source.contains("CLONED_FREEZE_TESTS_REPAIRED = true"))
        assertTrue(source.contains("CURRENT_GOVERNANCE_RECORDED = true"))
        assertTrue(source.contains("OBSERVATION_MAY_DRIFT = true"))
        assertTrue(source.contains("RULESET_ID = \"20897672\""))
        assertTrue(source.contains("REQUIRED_CHECK_NAME = \"Independent safety verification\""))
        assertTrue(source.contains("USED_AS_RUNTIME_AUTHORIZATION = false"))
        assertTrue(source.contains("USED_AS_MERGE_AUTHORIZATION = false"))
        assertTrue(source.contains("NINETEEN_H_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED = false"))
        assertTrue(source.contains("HISTORICAL_19E_STATE_UNCHANGED = true"))
        assertTrue(source.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = \"NO\""))
        assertTrue(source.contains("BYTECODE_VERIFIER_REFACTOR_DEFERRED = true"))
        assertFalse(source.contains("wipeDevice"))
        assertFalse(source.contains("wipeData"))
        assertFalse(HEX_SHA256.containsMatchIn(source))
        val verifier = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        assertTrue(
            verifier.contains(
                "com/example/devicemanagement/destructive/Checkpoint19PGovernanceObservation",
            ),
        )
        val nineteenE = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19EDecision.kt",
        ).readText()
        assertTrue(nineteenE.contains("BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED = false"))
        assertTrue(nineteenE.contains("GITHUB_CI_RUN_OBSERVED = false"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
