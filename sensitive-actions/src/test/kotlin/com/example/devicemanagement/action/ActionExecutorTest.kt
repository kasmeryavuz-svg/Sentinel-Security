package com.example.devicemanagement.action

import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.decision.DecisionReason
import com.example.devicemanagement.decision.FailSafeDecisionEngine
import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.ManagementState
import com.example.devicemanagement.persistence.StateRepository
import com.example.devicemanagement.trigger.DefaultTriggerEvaluator
import com.example.devicemanagement.trigger.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class ActionExecutorTest {
    private val logger = RecordingLogger()

    @Test
    fun `denied decision never invokes an action`() {
        val authority = ApprovalAuthority()
        val action = RecordingAction()
        val executor = executor(setOf(action), authority)

        val result = executor.execute(
            ActionDecision.Denied(DecisionReason.INVALID_TRIGGER),
        )

        assertTrue(result is ActionResult.Rejected)
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `invalid trigger cannot reach an action through the public entry point`() {
        val action = RecordingAction()
        val controller = enabledController(action)

        val result = controller.submit(
            Trigger(
                command = "malformed",
                requestId = "request-1",
                expiresAtEpochMillis = 2_000,
            ),
        )

        assertTrue(result is ActionResult.Rejected)
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `public entry point invokes decision engine before executing an action`() {
        val action = RecordingAction()
        val controller = enabledController(action)

        val result = controller.submit(
            Trigger(
                command = "mock_wipe",
                requestId = "request-1",
                expiresAtEpochMillis = 2_000,
            ),
        )

        assertTrue(result is ActionResult.Simulated)
        assertEquals(1, action.executionCount)
    }

    @Test
    fun `production controller remains disabled by default`() {
        val controller = SensitiveActionController.createFailSafe(logger)

        val result = controller.submit(
            Trigger(
                command = "mock_wipe",
                requestId = "request-1",
                expiresAtEpochMillis = Long.MAX_VALUE,
            ),
        )

        assertTrue(result is ActionResult.Rejected)
        assertFalse(logger.events.contains(SafeMockWipeAction.WIPE_LOG_MESSAGE))
    }

    @Test
    fun `forged approval not issued by the paired decision engine is rejected`() {
        val authority = ApprovalAuthority()
        val action = RecordingAction()
        val executor = executor(setOf(action), authority)
        val forgedDecision = ActionDecision.Approved(Approval.create())

        val result = executor.execute(forgedDecision)

        assertEquals(
            ActionResult.Rejected("approval_not_issued_or_already_consumed"),
            result,
        )
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `approval issued by a different authority is rejected`() {
        val executorAuthority = ApprovalAuthority()
        val foreignAuthority = ApprovalAuthority()
        val action = RecordingAction()
        val executor = executor(setOf(action), executorAuthority)
        val foreignApproval = foreignAuthority.issue(
            request(),
            issuedAtMonotonicMillis = 100L,
        )

        val result = executor.execute(ActionDecision.Approved(foreignApproval))

        assertTrue(result is ActionResult.Rejected)
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `approved capability is single use and cannot be replayed`() {
        val authority = ApprovalAuthority()
        val action = RecordingAction()
        val executor = executor(setOf(action), authority)
        val decision = ActionDecision.Approved(
            authority.issue(request(), issuedAtMonotonicMillis = 100L),
        )

        val firstResult = executor.execute(decision)
        val replayResult = executor.execute(decision)

        assertTrue(firstResult is ActionResult.Simulated)
        assertTrue(replayResult is ActionResult.Rejected)
        assertEquals(1, action.executionCount)
    }

    @Test
    fun `expired approval request cannot reach action`() {
        val authority = ApprovalAuthority()
        val action = RecordingAction()
        val decision = ActionDecision.Approved(
            authority.issue(request(), issuedAtMonotonicMillis = 100L),
        )
        val executor = ActionExecutor(
            registry = registry(setOf(action)),
            approvalAuthority = authority,
            logger = logger,
            nowEpochMillis = { 2_000L },
            monotonicTimeSource = MonotonicTimeSource { 100L },
        )

        val result = executor.execute(decision)

        assertEquals(
            ActionResult.Rejected(
                "request_expired_before_execution",
                "authoritative-correlation",
            ),
            result,
        )
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `stale monotonic approval cannot reach action`() {
        val authority = ApprovalAuthority()
        val action = RecordingAction()
        val decision = ActionDecision.Approved(
            authority.issue(request(), issuedAtMonotonicMillis = 100L),
        )
        val executor = ActionExecutor(
            registry = registry(setOf(action)),
            approvalAuthority = authority,
            logger = logger,
            nowEpochMillis = { 1_000L },
            monotonicTimeSource = MonotonicTimeSource { 5_101L },
        )

        val result = executor.execute(decision)

        assertEquals(
            ActionResult.Rejected("approval_stale", "authoritative-correlation"),
            result,
        )
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `unregistered action is rejected after a genuine approval`() {
        val authority = ApprovalAuthority()
        val executor = executor(setOf(RecordingAction()), authority)
        val decision = ActionDecision.Approved(
            authority.issue(
                request(type = DeviceActionType.DISABLE_CAMERA),
                issuedAtMonotonicMillis = 100L,
            ),
        )

        val result = executor.execute(decision)

        assertEquals(
            ActionResult.Rejected("action_not_registered", "authoritative-correlation"),
            result,
        )
    }

    @Test
    fun `safe mock wipe only logs simulation and returns simulated result`() {
        val action = SafeMockWipeAction(logger)
        val request = request()

        val result = action.execute(request)

        assertEquals(
            ActionResult.Simulated(
                SafeMockWipeAction.WIPE_LOG_MESSAGE,
                "authoritative-correlation",
            ),
            result,
        )
        assertTrue(logger.events.contains(SafeMockWipeAction.WIPE_LOG_MESSAGE))
    }

    @Test
    fun `action exception is contained as a failed result`() {
        val authority = ApprovalAuthority()
        val executor = executor(setOf(ThrowingAction()), authority)
        val decision = ActionDecision.Approved(
            authority.issue(request(), issuedAtMonotonicMillis = 100L),
        )

        val result = executor.execute(decision)

        assertTrue(result is ActionResult.Failed)
    }

    @Test
    fun `public controller API accepts triggers and exposes no decision entry point`() {
        val publicMethods = SensitiveActionController::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }

        assertTrue(
            publicMethods.any {
                it.name == "submit" &&
                    it.parameterTypes.contentEquals(arrayOf(Trigger::class.java))
            },
        )
        assertFalse(
            publicMethods.any { method ->
                method.parameterTypes.any { it.name.contains("ActionDecision") }
            },
        )
    }

    private fun enabledController(action: DeviceAction): SensitiveActionController {
        val authority = ApprovalAuthority()
        val registry = registry(setOf(action))
        return SensitiveActionController(
            decisionEngine = FailSafeDecisionEngine(
                triggerEvaluator = DefaultTriggerEvaluator(registry),
                stateRepository = StateRepository {
                    ManagementState(
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
            correlationIdGenerator = { "authoritative-correlation" },
        )
    }

    private fun request(type: DeviceActionType = DeviceActionType.MOCK_WIPE) =
        ActionRequest(
            type = type,
            correlationId = "authoritative-correlation",
            callerRequestId = "request-1",
            expiresAtEpochMillis = 2_000L,
        )

    private fun executor(
        actions: Set<DeviceAction>,
        authority: ApprovalAuthority,
    ) = ActionExecutor(
        registry = registry(actions),
        approvalAuthority = authority,
        logger = logger,
        nowEpochMillis = { 1_000L },
        monotonicTimeSource = MonotonicTimeSource { 100L },
    )

    private fun registry(actions: Set<DeviceAction>): SensitiveActionRegistry {
        return SensitiveActionRegistry(
            actions.mapIndexed { index, action ->
                SensitiveActionRegistration("test-command-$index", action)
            },
        )
    }

    private class RecordingAction : DeviceAction {
        override val type = DeviceActionType.MOCK_WIPE
        var executionCount = 0

        override fun execute(request: ActionRequest): ActionResult {
            executionCount += 1
            return ActionResult.Simulated("recorded", request.correlationId)
        }
    }

    private class ThrowingAction : DeviceAction {
        override val type = DeviceActionType.MOCK_WIPE

        override fun execute(request: ActionRequest): ActionResult {
            error("unexpected action error")
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
}
