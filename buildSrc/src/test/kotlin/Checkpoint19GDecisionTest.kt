import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint19GDecisionTest {
    @Test
    fun `19G records observable purpose without claiming later destructive states`() {
        val source = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19GDecision.kt",
        ).readText()
        assertTrue(source.contains("DISPOSABLE_VALIDATION_VARIANT_PRESENT = true"))
        assertTrue(source.contains("BUILD_PURPOSE_OBSERVABLE = true"))
        assertTrue(source.contains("CANDIDATE_ARTIFACT_ELIGIBLE = false"))
        assertTrue(source.contains("REAL_DEVICE_IDENTITY_RECORDED = false"))
        assertTrue(source.contains("HARDWARE_VALIDATION_PREPARATION_READY = false"))
        assertTrue(source.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(source.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(source.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(source.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = \"NO\""))
        assertTrue(source.contains("CHECKPOINT_19G_USED_AS_RUNTIME_AUTHORIZATION = false"))
        assertTrue(source.contains("\"19G_DISPOSABLE_VALIDATION_VARIANT_PRESENT\" to \"true\""))
        assertTrue(source.contains("\"19G_CANDIDATE_ARTIFACT_ELIGIBLE\" to \"false\""))
        assertTrue(!source.contains("wipeDevice"))
        assertTrue(!source.contains("wipeData"))
        assertTrue(!HEX_SHA256.containsMatchIn(source))
        val verifier = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        assertTrue(
            verifier.contains("com/example/devicemanagement/destructive/Checkpoint19GDecision"),
        )
        assertTrue(
            verifier.contains("com/example/devicemanagement/destructive/Checkpoint19HDecision"),
        )
        assertTrue(
            verifier.contains("com/example/devicemanagement/destructive/Checkpoint19JDecision"),
        )
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
