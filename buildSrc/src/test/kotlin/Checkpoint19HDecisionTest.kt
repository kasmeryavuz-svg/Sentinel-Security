import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint19HDecisionTest {
    @Test
    fun `19H records ceremony contract without claiming later destructive states`() {
        val source = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19HDecision.kt",
        ).readText()
        assertTrue(source.contains("SIGNING_CEREMONY_CONTRACT_PRESENT = true"))
        assertTrue(source.contains("SIGNING_CEREMONY_READY = false"))
        assertTrue(source.contains("OFFLINE_KEY_GENERATED = false"))
        assertTrue(source.contains("PUBLIC_CERTIFICATE_SUPPLIED = false"))
        assertTrue(source.contains("EXPECTED_CERTIFICATE_RECORDED = false"))
        assertTrue(source.contains("OPERATOR_APPROVAL_AVAILABLE = false"))
        assertTrue(source.contains("WITNESS_APPROVAL_AVAILABLE = false"))
        assertTrue(source.contains("KEY_CUSTODY_APPROVED = false"))
        assertTrue(source.contains("RECOVERY_BACKUP_VERIFIED = false"))
        assertTrue(source.contains("BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED = false"))
        assertTrue(source.contains("PRODUCTION_ARTIFACT_SIGNED = false"))
        assertTrue(source.contains("SIGNED_VALIDATION_CANDIDATE_PRODUCED = false"))
        assertTrue(source.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(source.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(source.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(source.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = \"NO\""))
        assertTrue(source.contains("CHECKPOINT_19H_USED_AS_RUNTIME_AUTHORIZATION = false"))
        assertTrue(source.contains("\"19H_SIGNING_CEREMONY_CONTRACT_PRESENT\" to \"true\""))
        assertTrue(source.contains("\"19H_SIGNING_CEREMONY_READY\" to \"false\""))
        assertTrue(!source.contains("wipeDevice"))
        assertTrue(!source.contains("wipeData"))
        assertTrue(!HEX_SHA256.containsMatchIn(source))
        val verifier = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
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
