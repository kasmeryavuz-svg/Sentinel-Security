package com.example.devicemanagement.action

import com.example.devicemanagement.audit.AuditEventPhase
import com.example.devicemanagement.audit.DurableAuditRepository
import com.example.devicemanagement.audit.InMemoryAuditRecordStore
import com.example.devicemanagement.audit.InMemoryAuditState
import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.integration.PolicyMutationResult
import com.example.devicemanagement.integration.SensitiveActionAuthorization
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.recovery.AuditRecoveryInspector
import com.example.devicemanagement.trigger.SensitiveActionCommands
import com.example.devicemanagement.trigger.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleRestartHardeningTest {
    private val logger = RecordingLogger()

    @Test
    fun `interrupted REQUESTED is not executed or replayed after restart`() {
        val backend = MutableBackend()
        val state = InMemoryAuditState()
        DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
            .append(
                com.example.devicemanagement.audit.AuditAppendRequest(
                    eventId = "pre-restart",
                    correlationId = "stale-correlation",
                    actionName = SensitiveActionCommands.DISABLE_CAMERA,
                    phase = AuditEventPhase.REQUESTED,
                    presentationWallClockMillis = 1_700L,
                    reasonCode = null,
                ),
            )

        val restartedAudit = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        val restarted = controller(backend, restartedAudit)
        val inspection = AuditRecoveryInspector(restartedAudit, logger).inspect()

        assertEquals(1, inspection.interruptedCount)
        assertEquals(listOf("stale-correlation"), inspection.interruptedCorrelationIds)
        assertEquals(0, backend.cameraWrites.size)
        assertEquals(0, backend.authorizationReads)
        assertEquals(AuditEventPhase.REQUESTED, restartedAudit.latest(10).events.single().phase)
        assertTrue(restarted is SensitiveActionController)
    }

    @Test
    fun `fresh process cannot consume a pre-restart approval`() {
        val preRestartAuthority = ApprovalAuthority()
        val approval = preRestartAuthority.issue(
            ActionRequest(
                type = DeviceActionType.DISABLE_CAMERA,
                correlationId = "stale-approval",
                callerRequestId = "caller",
                expiresAtEpochMillis = 2_000L,
            ),
            issuedAtMonotonicMillis = 100L,
        )
        val action = RecordingCameraAction()
        val restartedAuthority = ApprovalAuthority()
        val executor = ActionExecutor(
            registry = SensitiveActionRegistry(
                listOf(
                    SensitiveActionRegistration(
                        command = SensitiveActionCommands.DISABLE_CAMERA,
                        action = action,
                    ),
                ),
            ),
            approvalAuthority = restartedAuthority,
            logger = logger,
            nowEpochMillis = { 1_000L },
            monotonicTimeSource = MonotonicTimeSource { 100L },
        )

        val result = executor.execute(ActionDecision.Approved(approval))

        assertTrue(result is ActionResult.Rejected)
        assertEquals("approval_not_issued_or_already_consumed", (result as ActionResult.Rejected).reason)
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `new submission after restart uses a fresh decision path and correlation`() {
        val backend = MutableBackend()
        val state = InMemoryAuditState()
        DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
            .append(
                com.example.devicemanagement.audit.AuditAppendRequest(
                    eventId = "pre-restart",
                    correlationId = "stale-correlation",
                    actionName = SensitiveActionCommands.DISABLE_CAMERA,
                    phase = AuditEventPhase.REQUESTED,
                    presentationWallClockMillis = 1_700L,
                    reasonCode = null,
                ),
            )

        val restartedAudit = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        val restarted = controller(backend, restartedAudit)
        val result = restarted.submit(trigger(SensitiveActionCommands.DISABLE_CAMERA))

        assertTrue(result is ActionResult.Applied)
        val applied = result as ActionResult.Applied
        assertNotEquals("stale-correlation", applied.correlationId)
        assertEquals(1, backend.cameraWrites.size)
        assertTrue(backend.authorizationReads >= 1)
        val inspection = AuditRecoveryInspector(restartedAudit, logger).inspect()
        assertEquals(listOf("stale-correlation"), inspection.interruptedCorrelationIds)
        val phases = restartedAudit.latest(10).events
            .filter { it.correlationId == applied.correlationId }
            .map { it.phase }
            .toSet()
        assertTrue(phases.contains(AuditEventPhase.REQUESTED))
        assertTrue(phases.contains(AuditEventPhase.APPLIED))
    }

    @Test
    fun `backend Device Owner state is re-read between submissions`() {
        val backend = MutableBackend()
        val audit = DurableAuditRepository(InMemoryAuditRecordStore(), logger)
        val controller = controller(backend, audit)

        val first = controller.submit(trigger(SensitiveActionCommands.DISABLE_CAMERA))
        backend.authorization = authorized.copy(verifiedDeviceOwner = false)
        val second = controller.submit(trigger(SensitiveActionCommands.ENABLE_CAMERA))

        assertTrue(first is ActionResult.Applied)
        assertTrue(second is ActionResult.Rejected)
        assertEquals(1, backend.cameraWrites.size)
        assertTrue(backend.authorizationReads >= 2)
        assertEquals(
            "decision_denied:DEVICE_OWNER_NOT_VERIFIED",
            (second as ActionResult.Rejected).reason,
        )
    }

    @Test
    fun `createControlledController always constructs a fresh ApprovalAuthority`() {
        val source = java.io.File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        val factoryBody = source.substringAfter("internal fun createControlledController")
            .substringBefore("internal fun createFailSafeController")
            .ifEmpty {
                source.substringAfter("internal fun createControlledController")
            }
        assertTrue(factoryBody.contains("val approvalAuthority = ApprovalAuthority()"))
        assertTrue(source.contains("brand-new process-local"))
    }

    private fun controller(
        backend: SensitiveActionPolicyBackend,
        audit: DurableAuditRepository,
    ): SensitiveActionController {
        return createControlledController(
            backend = backend,
            logger = logger,
            nowEpochMillis = { 1_000L },
            monotonicTimeSource = MonotonicTimeSource { 100L },
            auditWriter = audit,
            presentationWallClockMillis = { 1_700L },
        )
    }

    private fun trigger(command: String) = Trigger(
        command = command,
        requestId = "caller",
        expiresAtEpochMillis = 2_000L,
    )

    private class RecordingCameraAction : DeviceAction {
        override val type = DeviceActionType.DISABLE_CAMERA
        var executionCount = 0

        override fun execute(request: ActionRequest): ActionResult {
            executionCount += 1
            return ActionResult.Applied(
                operation = SensitiveActionOperation.DISABLE_CAMERA,
                requestedDisabled = true,
                observedDisabled = true,
                correlationId = request.correlationId,
            )
        }
    }

    private class MutableBackend(
        var authorization: SensitiveActionAuthorization = authorized,
    ) : SensitiveActionPolicyBackend {
        val cameraWrites = mutableListOf<Boolean>()
        var authorizationReads = 0

        override fun currentAuthorization(): SensitiveActionAuthorization {
            authorizationReads += 1
            return authorization
        }

        override fun applyScreenCaptureDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult = PolicyMutationResult.Applied(disabled, disabled)

        override fun applyCameraDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult {
            cameraWrites += disabled
            return PolicyMutationResult.Applied(disabled, disabled)
        }

        override fun applyStatusBarDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult = PolicyMutationResult.Applied(disabled, disabled)
    }

    private class RecordingLogger : StructuredLogger {
        override fun info(event: String, fields: Map<String, Any?>) = Unit
        override fun warn(event: String, fields: Map<String, Any?>) = Unit
        override fun error(
            event: String,
            fields: Map<String, Any?>,
            throwable: Throwable?,
        ) = Unit
    }

    private companion object {
        val authorized = SensitiveActionAuthorization(
            policyServiceAvailable = true,
            sensitiveActionsEnabled = true,
            verifiedDeviceOwner = true,
            profileOwner = false,
            expectedAdminReceiverRegistered = true,
            expectedAdminActive = true,
            managementStateConsistent = true,
        )
    }
}
