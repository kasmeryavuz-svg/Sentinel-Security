package com.example.devicemanagement.recovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LifecycleRecoveryGuardTest {
    @Test
    fun `recovery implementation has no execution or mutation capability`() {
        val sources = File("src/main/kotlin/com/example/devicemanagement/recovery")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertTrue(sources.contains("AuditHistoryProvider"))
        assertTrue(sources.contains("classifyInterrupted"))
        assertTrue(sources.contains("no execution capability"))
        assertFalse(sources.contains("SensitiveActionController"))
        assertFalse(sources.contains(".submit("))
        assertFalse(sources.contains("ApprovalAuthority"))
        assertFalse(sources.contains("ActionExecutor"))
        assertFalse(sources.contains("SensitiveActionPolicyBackend"))
        assertFalse(sources.contains("DevicePolicyManager"))
        assertFalse(sources.contains("setScreenCaptureDisabled"))
        assertFalse(sources.contains("setCameraDisabled"))
        assertFalse(sources.contains("setStatusBarDisabled"))
        assertFalse(sources.contains("SensitiveActionAuditWriter"))
        assertFalse(sources.contains("AuditRecordStore"))
        assertFalse(sources.contains(".append("))
        assertFalse(sources.contains("insert("))
        assertFalse(sources.contains("deleteOldest"))
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("ActionRequest"))
        assertFalse(sources.contains("SharedPreferences"))
        assertFalse(sources.contains("BOOT_COMPLETED"))
        assertFalse(sources.contains("DestructiveArmingAuthority"))
        assertFalse(sources.contains("DestructiveAuthorizationAuthority"))
        assertFalse(sources.contains("DestructiveAttemptAdmissionAuthority"))
        assertFalse(sources.contains("DestructiveFinalExecutionGate"))
        assertFalse(sources.contains("DestructiveCapability"))
        assertFalse(sources.contains("DestructiveAttemptLease"))
        assertFalse(sources.contains("FinalExecutionPermit"))
        assertFalse(sources.contains("SimulatedDestructiveExecutor"))
        assertFalse(sources.contains("Checkpoint17ASimulationSink"))
        assertFalse(sources.contains("TrustedRuntimeDenyOnlyCooldownMarkerStore"))
        assertFalse(sources.contains("DurableDestructivePreExecutionRepository"))
        assertFalse(sources.contains("DestructivePreExecutionDurableStore"))
        assertFalse(sources.contains("AndroidDestructiveSafetyPersistence"))
        assertFalse(sources.contains("RuntimeDenyOnlyCooldownStore"))
        assertFalse(sources.contains("RuntimeDestructivePreExecutionStore"))
        assertFalse(sources.contains("RuntimeDestructiveSafetyDurability"))
        assertFalse(sources.contains("issueFromTrustedAndroidStores"))
        assertFalse(sources.contains("issueRuntimeDurability"))
        assertFalse(sources.contains("DestructiveArtifactIdentity"))
        assertFalse(sources.contains("TrustedDestructiveArtifactExpectationFactory"))
        assertFalse(sources.contains("TrustedDestructiveArtifactExpectationMint"))
        assertFalse(sources.contains("RuntimeDestructiveSafetyDurabilityMint"))
        assertFalse(sources.contains("issueFromTrustedValidationSource"))
        assertFalse(sources.contains("DestructiveHumanApproval"))
        assertFalse(sources.contains("DestructiveHumanConfirmation"))
        assertFalse(sources.contains("DestructiveWipeOptionPolicy"))
        assertFalse(sources.contains("issueChallenge"))
        assertFalse(sources.contains("issueFromTrustedConfirmationSource"))
        assertFalse(sources.contains("FutureDestructiveExecutorContract"))
        assertFalse(sources.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(sources.contains("assembleAndHandoff"))
        assertFalse(sources.contains("mintFinalLiveValidationPermit"))
        assertFalse(sources.contains("assembleBundleFromPermit"))
        assertFalse(sources.contains("commitAfterConsumedAuthorization"))
        assertFalse(sources.contains("onAuthorizedHandoff"))
        assertFalse(sources.contains("RuntimeDurablePreExecutionCommitProof"))
        assertFalse(sources.contains("RealChainFinalLiveValidationPermit"))
        assertFalse(sources.contains("DestructiveWipeOptionPolicyProof"))
        assertFalse(sources.contains("Checkpoint18Decision"))
        assertFalse(sources.contains("Checkpoint19ADecision"))
        assertFalse(sources.contains("Checkpoint19BDecision"))
        assertFalse(sources.contains("Checkpoint19CDecision"))
        assertFalse(sources.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(sources.contains("AuthorizedFactoryResetPort"))
        assertFalse(sources.contains("ProductionDestructiveRealChain"))
        assertFalse(sources.contains("AndroidDevicePolicyFactoryResetService"))
        assertFalse(sources.contains("RealChainHandoffRegistry"))
        assertFalse(sources.contains("HandoffRegistry"))
        assertFalse(sources.contains("IssuedRealChainFinalLiveValidationPermit"))
        assertFalse(sources.contains("IssuedFutureDestructiveExecutionBundle"))
        assertFalse(sources.contains("registerIssuedPermit"))
        assertFalse(sources.contains("registerIssuedBundle"))
    }

    @Test
    fun `approvals and authorization are never persisted`() {
        val approval = File("src/main/kotlin/com/example/devicemanagement/action/ApprovalAuthority.kt")
            .readText()
        val repository = File(
            "src/main/kotlin/com/example/devicemanagement/persistence/PolicyBackendStateRepository.kt",
        ).readText()
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()

        assertTrue(approval.contains("process-local"))
        assertFalse(approval.contains("SharedPreferences"))
        assertFalse(approval.contains("SQLite"))
        assertFalse(approval.contains("FileOutputStream"))
        assertFalse(approval.contains("DestructiveAttemptLease"))
        assertFalse(approval.contains("Serializable"))
        assertTrue(repository.contains("does not cache or persist authorization"))
        assertTrue(repository.contains("backend.currentAuthorization()"))
        assertFalse(repository.contains("SharedPreferences"))
        assertTrue(controller.contains("brand-new process-local"))
        assertTrue(controller.contains("val approvalAuthority = ApprovalAuthority()"))
    }
}
