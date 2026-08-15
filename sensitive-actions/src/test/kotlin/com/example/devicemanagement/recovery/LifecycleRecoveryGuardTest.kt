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
