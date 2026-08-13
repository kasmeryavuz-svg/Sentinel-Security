package com.example.devicemanagement.action

import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.decision.DecisionReason
import com.example.devicemanagement.decision.FailSafeDecisionEngine
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
        val executor = ActionExecutor(setOf(action), authority, logger)

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

        assertTrue(result is SensitiveActionResult.Denied)
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

        assertTrue(result is SensitiveActionResult.Approved)
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

        assertTrue(result is SensitiveActionResult.Denied)
        assertFalse(logger.events.contains(SafeMockWipeAction.WIPE_LOG_MESSAGE))
    }

    @Test
    fun `forged approval not issued by the paired decision engine is rejected`() {
        val authority = ApprovalAuthority()
        val action = RecordingAction()
        val executor = ActionExecutor(setOf(action), authority, logger)
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
        val executor = ActionExecutor(setOf(action), executorAuthority, logger)
        val foreignApproval = foreignAuthority.issue(
            ActionRequest(DeviceActionType.MOCK_WIPE, "request-1"),
        )

        val result = executor.execute(ActionDecision.Approved(foreignApproval))

        assertTrue(result is ActionResult.Rejected)
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `approved capability is single use and cannot be replayed`() {
        val authority = ApprovalAuthority()
        val action = RecordingAction()
        val executor = ActionExecutor(setOf(action), authority, logger)
        val decision = ActionDecision.Approved(
            authority.issue(ActionRequest(DeviceActionType.MOCK_WIPE, "request-1")),
        )

        val firstResult = executor.execute(decision)
        val replayResult = executor.execute(decision)

        assertTrue(firstResult is ActionResult.Simulated)
        assertTrue(replayResult is ActionResult.Rejected)
        assertEquals(1, action.executionCount)
    }

    @Test
    fun `unregistered action is rejected after a genuine approval`() {
        val authority = ApprovalAuthority()
        val executor = ActionExecutor(emptySet(), authority, logger)
        val decision = ActionDecision.Approved(
            authority.issue(ActionRequest(DeviceActionType.UNSUPPORTED, "request-1")),
        )

        val result = executor.execute(decision)

        assertEquals(ActionResult.Rejected("action_not_registered"), result)
    }

    @Test
    fun `safe mock wipe only logs simulation and returns simulated result`() {
        val action = SafeMockWipeAction(logger)
        val request = ActionRequest(DeviceActionType.MOCK_WIPE, "request-1")

        val result = action.execute(request)

        assertEquals(
            ActionResult.Simulated(SafeMockWipeAction.WIPE_LOG_MESSAGE),
            result,
        )
        assertTrue(logger.events.contains(SafeMockWipeAction.WIPE_LOG_MESSAGE))
    }

    @Test
    fun `action exception is contained as a failed result`() {
        val authority = ApprovalAuthority()
        val executor = ActionExecutor(setOf(ThrowingAction()), authority, logger)
        val decision = ActionDecision.Approved(
            authority.issue(ActionRequest(DeviceActionType.MOCK_WIPE, "request-1")),
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
        return SensitiveActionController(
            decisionEngine = FailSafeDecisionEngine(
                triggerEvaluator = DefaultTriggerEvaluator(),
                stateRepository = StateRepository {
                    ManagementState(
                        serviceAvailable = true,
                        sensitiveActionsEnabled = true,
                    )
                },
                approvalAuthority = authority,
                logger = logger,
                nowEpochMillis = { 1_000 },
            ),
            actionExecutor = ActionExecutor(setOf(action), authority, logger),
            logger = logger,
        )
    }

    private class RecordingAction : DeviceAction {
        override val type = DeviceActionType.MOCK_WIPE
        var executionCount = 0

        override fun execute(request: ActionRequest): ActionResult {
            executionCount += 1
            return ActionResult.Simulated("recorded")
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
