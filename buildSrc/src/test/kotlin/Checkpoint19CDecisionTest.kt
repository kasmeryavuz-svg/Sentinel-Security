import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Checkpoint19CDecisionTest {
    @Test
    fun `19C remaining preparation blockers exclude later approval execution and verification`() {
        val source = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19CDecision.kt",
        ).readText()
        val blockers = source
            .substringAfter("val remainingHardwarePreparationBlockers = listOf(")
            .substringBefore(")")
        val later = source
            .substringAfter("val laterHardwareValidationStates = listOf(")
            .substringBefore(")")
        assertTrue(source.contains("READINESS_MODEL_NON_CIRCULAR = \"YES\""))
        assertTrue(source.contains("HARDWARE_VALIDATION_PREPARATION_READY = \"NO\""))
        assertTrue(blockers.contains("REAL_DESTRUCTIVE_CHAIN_ASSEMBLED"))
        assertTrue(blockers.contains("PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED"))
        assertTrue(blockers.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED"))
        assertTrue(blockers.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED"))
        assertTrue(blockers.contains("DISPOSABLE_DEVICE_SERIAL_IDENTIFIED"))
        assertTrue(blockers.contains("EXPECTED_OS_BUILD_RECORDED"))
        assertTrue(blockers.contains("FACTORY_RESET_CONSEQUENCE_ACKNOWLEDGED"))
        assertTrue(blockers.contains("DESTRUCTIVE_RECOVERY_PROCEDURE_PREPARED"))
        assertTrue(blockers.contains("BATTERY_USB_ADB_STATE_RECORDED"))
        assertTrue(blockers.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED"))
        assertTrue(blockers.contains("REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED"))
        assertTrue(blockers.contains("REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED"))
        assertTrue(blockers.contains("REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED"))
        assertTrue(blockers.contains("REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED"))
        assertFalse(blockers.contains("HARDWARE_TEST_APPROVAL_GRANTED"))
        assertFalse(blockers.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED"))
        assertFalse(blockers.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED"))
        assertFalse(blockers.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED"))
        assertTrue(later.contains("HARDWARE_TEST_APPROVAL_GRANTED"))
        assertTrue(later.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED"))
        assertTrue(later.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED"))
        assertTrue(later.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED"))
    }

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
        assertTrue(source.contains("com/example/devicemanagement/destructive/Checkpoint19DDecision"))
        assertTrue(source.contains("com/example/devicemanagement/destructive/Checkpoint19EDecision"))
        assertTrue(source.contains("com/example/devicemanagement/destructive/Checkpoint19FDecision"))
        assertTrue(source.contains("com/example/devicemanagement/destructive/Checkpoint19GDecision"))
        assertTrue(source.contains("com/example/devicemanagement/destructive/Checkpoint19HDecision"))
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
