package com.example.devicemanagement.action

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AuditAuthorizationIsolationGuardTest {
    @Test
    fun `authorization path does not use audit wall-clock or audit health`() {
        val root = File("src/main/kotlin/com/example/devicemanagement")
        val approval = File(root, "action/ApprovalAuthority.kt").readText()
        val executor = File(root, "action/ActionExecutor.kt").readText()
        val decision = File(root, "decision/DecisionEngine.kt").readText()
        val controller = File(root, "action/SensitiveActionController.kt").readText()

        listOf(approval, executor, decision).forEach { source ->
            assertFalse(source.contains("presentationWallClockMillis"))
            assertFalse(source.contains("AuditStorageHealth"))
            assertFalse(source.contains("AuditHistory"))
            assertFalse(source.contains("SensitiveActionAuditWriter"))
        }
        assertTrue(controller.contains("AuditEventPhase.REQUESTED"))
        assertTrue(
            controller.indexOf("auditWriter.append") <
                controller.indexOf("decisionEngine.decide"),
        )
        assertTrue(
            controller.indexOf("decisionEngine.decide") <
                controller.lastIndexOf("auditWriter.append"),
        )
        assertFalse(controller.contains("setCameraDisabled"))
        assertFalse(controller.contains("DevicePolicyManager"))
    }
}
