package com.example.devicemanagement.action

import com.example.devicemanagement.audit.AuditActionNames
import com.example.devicemanagement.audit.AuditAppendResult
import com.example.devicemanagement.audit.AuditEventPhase
import com.example.devicemanagement.audit.AuditStorageHealth
import com.example.devicemanagement.audit.DurableAuditRepository
import com.example.devicemanagement.audit.InMemoryAuditRecordStore
import com.example.devicemanagement.audit.InMemoryAuditState
import com.example.devicemanagement.audit.SensitiveActionAuditWriter
import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.integration.PolicyMutationResult
import com.example.devicemanagement.integration.SensitiveActionAuthorization
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.trigger.SensitiveActionCommands
import com.example.devicemanagement.trigger.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveActionAuditPipelineTest {
    private val logger = RecordingLogger()

    @Test
    fun `REQUESTED persists before policy mutation and APPLIED only after Applied`() {
        val backend = RecordingBackend()
        val state = InMemoryAuditState()
        val audit = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        val controller = controller(backend, audit)

        val result = controller.submit(trigger(SensitiveActionCommands.DISABLE_CAMERA))

        assertTrue(result is ActionResult.Applied)
        assertEquals(1, backend.cameraWrites.size)
        val events = audit.latest(10).events.sortedBy { it.sequence }
        assertEquals(
            listOf(AuditEventPhase.REQUESTED, AuditEventPhase.APPLIED),
            events.map { it.phase },
        )
        assertEquals(setOf((result as ActionResult.Applied).correlationId), events.map { it.correlationId }.toSet())
        assertEquals(AuditActionNames.DISABLE_CAMERA, events.first().actionName)
        assertTrue(events.first().sequence < events.last().sequence)
    }

    @Test
    fun `pre-action audit write failure prevents DPM mutation`() {
        val backend = RecordingBackend()
        val state = InMemoryAuditState().apply { failWrites = true }
        val audit = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        val controller = controller(backend, audit)

        val result = controller.submit(trigger(SensitiveActionCommands.DISABLE_CAMERA))

        assertTrue(result is ActionResult.Rejected)
        assertEquals("audit_persistence_unavailable", (result as ActionResult.Rejected).reason)
        assertTrue(backend.cameraWrites.isEmpty())
        assertTrue(backend.screenCaptureWrites.isEmpty())
        assertTrue(backend.statusBarWrites.isEmpty())
        assertTrue(audit.latest(10).events.isEmpty())
    }

    @Test
    fun `REJECTED and FAILED are stored with correlation IDs`() {
        val deniedBackend = RecordingBackend(
            authorization = authorized.copy(verifiedDeviceOwner = false),
        )
        val failedBackend = RecordingBackend(
            mutationResult = PolicyMutationResult.Failed("post_write_read_back_mismatch"),
        )
        val deniedAudit = DurableAuditRepository(InMemoryAuditRecordStore(), logger)
        val failedAudit = DurableAuditRepository(InMemoryAuditRecordStore(), logger)

        val denied = controller(deniedBackend, deniedAudit)
            .submit(trigger(SensitiveActionCommands.ENABLE_CAMERA))
        val failed = controller(failedBackend, failedAudit)
            .submit(trigger(SensitiveActionCommands.DISABLE_SCREEN_CAPTURE))

        assertTrue(denied is ActionResult.Rejected)
        assertTrue(failed is ActionResult.Failed)
        val deniedPhases = deniedAudit.latest(10).events.map { it.phase }
        val failedPhases = failedAudit.latest(10).events.map { it.phase }
        assertEquals(
            listOf(AuditEventPhase.REJECTED, AuditEventPhase.REQUESTED),
            deniedPhases,
        )
        assertEquals(
            listOf(AuditEventPhase.FAILED, AuditEventPhase.REQUESTED),
            failedPhases,
        )
        assertEquals((denied as ActionResult.Rejected).correlationId, deniedAudit.latest(10).events.first().correlationId)
        assertEquals((failed as ActionResult.Failed).correlationId, failedAudit.latest(10).events.first().correlationId)
        assertTrue(deniedBackend.cameraWrites.isEmpty())
    }

    @Test
    fun `SIMULATED is stored distinctly and never becomes APPLIED`() {
        val action = RecordingSimulatedAction()
        val audit = DurableAuditRepository(InMemoryAuditRecordStore(), logger)
        val controller = enabledSimulatedController(action, audit)

        val result = controller.submit(
            Trigger(
                command = "mock_wipe",
                requestId = "request-1",
                expiresAtEpochMillis = 2_000,
            ),
        )

        assertTrue(result is ActionResult.Simulated)
        assertEquals(1, action.executionCount)
        val phases = audit.latest(10).events.map { it.phase }
        assertTrue(phases.contains(AuditEventPhase.REQUESTED))
        assertTrue(phases.contains(AuditEventPhase.SIMULATED))
        assertFalse(phases.contains(AuditEventPhase.APPLIED))
        assertEquals(AuditActionNames.MOCK_WIPE, audit.latest(10).events.first().actionName)
    }

    @Test
    fun `terminal audit write failure does not falsify successful mutation`() {
        val backend = RecordingBackend()
        val state = InMemoryAuditState().apply { remainingSuccessfulWrites = 1 }
        val audit = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        val controller = controller(backend, audit)

        val result = controller.submit(trigger(SensitiveActionCommands.DISABLE_CAMERA))

        assertTrue(result is ActionResult.Applied)
        assertEquals(1, backend.cameraWrites.size)
        assertEquals(AuditStorageHealth.DEGRADED, audit.currentStatus().health)
        val events = audit.latest(10).events
        assertEquals(1, events.size)
        assertEquals(AuditEventPhase.REQUESTED, events.single().phase)
        assertNotEquals(ActionResult.Failed::class.java, result.javaClass)
    }

    @Test
    fun `subsequent mutation fails closed when REQUESTED cannot be persisted`() {
        val backend = RecordingBackend()
        val state = InMemoryAuditState()
        val audit = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        val controller = controller(backend, audit)
        val first = controller.submit(trigger(SensitiveActionCommands.DISABLE_CAMERA))
        assertTrue(first is ActionResult.Applied)

        state.failWrites = true
        val second = controller.submit(trigger(SensitiveActionCommands.ENABLE_CAMERA))

        assertTrue(second is ActionResult.Rejected)
        assertEquals("audit_persistence_unavailable", (second as ActionResult.Rejected).reason)
        assertEquals(1, backend.cameraWrites.size)
    }

    @Test
    fun `presentation wall clock is never used for authorization`() {
        val backend = RecordingBackend()
        val audit = DurableAuditRepository(InMemoryAuditRecordStore(), logger)
        val controller = createControlledController(
            backend = backend,
            logger = logger,
            nowEpochMillis = { 1_000L },
            monotonicTimeSource = MonotonicTimeSource { 100L },
            auditWriter = audit,
            presentationWallClockMillis = { 50_000L },
        )

        val result = controller.submit(
            Trigger(
                command = SensitiveActionCommands.DISABLE_CAMERA,
                requestId = "caller",
                expiresAtEpochMillis = 2_000L,
            ),
        )

        assertTrue(result is ActionResult.Applied)
        assertEquals(1, backend.cameraWrites.size)
        assertTrue(audit.latest(10).events.all { it.presentationWallClockMillis == 50_000L })
    }

    @Test
    fun `untrusted payloads are not persisted`() {
        val backend = RecordingBackend()
        val audit = DurableAuditRepository(InMemoryAuditRecordStore(), logger)
        val controller = controller(backend, audit)

        controller.submit(
            Trigger(
                command = "password=secret stack: IllegalStateException",
                requestId = "caller",
                expiresAtEpochMillis = 2_000L,
            ),
        )

        val events = audit.latest(10).events
        val serialized = events.joinToString { "${it.actionName}:${it.reasonCode}" }
        assertFalse(serialized.contains("password"))
        assertFalse(serialized.contains("secret"))
        assertFalse(serialized.contains("IllegalStateException"))
        assertFalse(serialized.contains("stack"))
        assertTrue(events.any { it.actionName == AuditActionNames.UNRECOGNIZED })
    }

    private fun controller(
        backend: SensitiveActionPolicyBackend,
        audit: SensitiveActionAuditWriter,
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

    private fun enabledSimulatedController(
        action: DeviceAction,
        audit: SensitiveActionAuditWriter,
    ): SensitiveActionController {
        val authority = ApprovalAuthority()
        val registry = SensitiveActionRegistry(
            listOf(
                SensitiveActionRegistration(
                    command = MOCK_WIPE_SIMULATION_COMMAND,
                    action = action,
                ),
            ),
        )
        return DefaultSensitiveActionController(
            decisionEngine = com.example.devicemanagement.decision.FailSafeDecisionEngine(
                triggerEvaluator = com.example.devicemanagement.trigger.DefaultTriggerEvaluator(registry),
                stateRepository = com.example.devicemanagement.persistence.StateRepository {
                    com.example.devicemanagement.persistence.ManagementState(
                        policyServiceAvailable = true,
                        sensitiveActionsEnabled = true,
                        verifiedDeviceOwner = true,
                        profileOwner = false,
                        expectedAdminReceiverRegistered = true,
                        expectedAdminActive = true,
                        managementStateConsistent = true,
                    )
                },
                approvalAuthority = authority,
                logger = logger,
                nowEpochMillis = { 1_000 },
                monotonicTimeSource = MonotonicTimeSource { 100L },
            ),
            actionExecutor = ActionExecutor(
                registry = registry,
                approvalAuthority = authority,
                logger = logger,
                nowEpochMillis = { 1_000 },
                monotonicTimeSource = MonotonicTimeSource { 100L },
            ),
            auditWriter = audit,
            logger = logger,
            correlationIdGenerator = { "authoritative-correlation" },
        )
    }

    private fun trigger(command: String) = Trigger(
        command = command,
        requestId = "caller",
        expiresAtEpochMillis = 2_000L,
    )

    private class RecordingSimulatedAction : DeviceAction {
        override val type = DeviceActionType.MOCK_WIPE
        var executionCount = 0

        override fun execute(request: ActionRequest): ActionResult {
            executionCount += 1
            return ActionResult.Simulated("WIPE WOULD EXECUTE", request.correlationId)
        }
    }

    private class RecordingBackend(
        private val authorization: SensitiveActionAuthorization = authorized,
        private val mutationResult: PolicyMutationResult? = null,
    ) : SensitiveActionPolicyBackend {
        val cameraWrites = mutableListOf<Boolean>()
        val screenCaptureWrites = mutableListOf<Boolean>()
        val statusBarWrites = mutableListOf<Boolean>()

        override fun currentAuthorization(): SensitiveActionAuthorization = authorization

        override fun applyScreenCaptureDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult {
            screenCaptureWrites += disabled
            return mutationResult ?: PolicyMutationResult.Applied(disabled, disabled)
        }

        override fun applyCameraDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult {
            cameraWrites += disabled
            return mutationResult ?: PolicyMutationResult.Applied(disabled, disabled)
        }

        override fun applyStatusBarDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult {
            statusBarWrites += disabled
            return mutationResult ?: PolicyMutationResult.Applied(disabled, disabled)
        }
    }

    private class RecordingLogger : StructuredLogger {
        val events = mutableListOf<String>()
        override fun info(event: String, fields: Map<String, Any?>) {
            events += event
        }
        override fun warn(event: String, fields: Map<String, Any?>) {
            events += event
        }
        override fun error(
            event: String,
            fields: Map<String, Any?>,
            throwable: Throwable?,
        ) {
            events += event
        }
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
