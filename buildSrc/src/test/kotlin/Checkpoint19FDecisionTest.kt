import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint19FDecisionTest {
    @Test
    fun `19F records candidate tooling without claiming later destructive states`() {
        val source = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19FDecision.kt",
        ).readText()
        assertTrue(source.contains("ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT = true"))
        assertTrue(source.contains("DISPOSABLE_DEVICE_CONTRACT_TEMPLATE_PRESENT = true"))
        assertTrue(source.contains("CANDIDATE_ARTIFACT_ELIGIBLE = false"))
        assertTrue(source.contains("REAL_DEVICE_IDENTITY_RECORDED = false"))
        assertTrue(source.contains("HARDWARE_VALIDATION_PREPARATION_READY = false"))
        assertTrue(source.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(source.contains("PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false"))
        assertTrue(source.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(source.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(source.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(source.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(source.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(source.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = \"NO\""))
        assertTrue(source.contains("CHECKPOINT_19F_USED_AS_RUNTIME_AUTHORIZATION = false"))
        assertTrue(source.contains("\"19F_ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT\" to \"true\""))
        assertTrue(source.contains("\"19F_CANDIDATE_ARTIFACT_ELIGIBLE\" to \"false\""))
        assertTrue(source.contains("\"19F_REAL_DEVICE_IDENTITY_RECORDED\" to \"false\""))
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
            verifier.contains("com/example/devicemanagement/destructive/Checkpoint19FDecision"),
        )
        assertTrue(
            verifier.contains("com/example/devicemanagement/destructive/Checkpoint19GDecision"),
        )
        assertTrue(
            verifier.contains("com/example/devicemanagement/destructive/Checkpoint19HDecision"),
        )
        assertTrue(
            verifier.contains("com/example/devicemanagement/destructive/Checkpoint19JDecision") &&
                verifier.contains(
                    "com/example/devicemanagement/destructive/Checkpoint19PGovernanceObservation",
                ),
        )
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
