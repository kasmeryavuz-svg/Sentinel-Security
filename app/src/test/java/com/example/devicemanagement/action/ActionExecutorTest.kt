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
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutorTest {
    private val logger = RecordingLogger()

    @Test
    fun `denied decision never invokes an action`() {
        val action = RecordingAction()
        val executor = ActionExecutor(setOf(action), logger)

        val result = executor.execute(
            ActionDecision.Denied(DecisionReason.INVALID_TRIGGER),
        )

        assertTrue(result is ActionResult.Rejected)
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `invalid trigger cannot reach an action through the controlled flow`() {
        val action = RecordingAction()
        val engine = FailSafeDecisionEngine(
            triggerEvaluator = DefaultTriggerEvaluator(),
            stateRepository = StateRepository {
                ManagementState(
                    serviceAvailable = true,
                    sensitiveActionsEnabled = true,
                )
            },
            logger = logger,
            nowEpochMillis = { 1_000 },
        )
        val executor = ActionExecutor(setOf(action), logger)

        val decision = engine.decide(
            Trigger(
                command = "malformed",
                requestId = "request-1",
                expiresAtEpochMillis = 2_000,
            ),
        )
        val result = executor.execute(decision)

        assertTrue(decision is ActionDecision.Denied)
        assertTrue(result is ActionResult.Rejected)
        assertEquals(0, action.executionCount)
    }

    @Test
    fun `unregistered action is rejected even with fabricated approval`() {
        val action = RecordingAction()
        val executor = ActionExecutor(setOf(action), logger)
        val unsupportedDecision = ActionDecision.Approved(
            ActionRequest(
                type = DeviceActionType.UNSUPPORTED,
                requestId = "request-1",
            ),
        )

        val result = executor.execute(unsupportedDecision)

        assertEquals(ActionResult.Rejected("action_not_registered"), result)
        assertEquals(0, action.executionCount)
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
        val executor = ActionExecutor(setOf(ThrowingAction()), logger)
        val decision = ActionDecision.Approved(
            ActionRequest(DeviceActionType.MOCK_WIPE, "request-1"),
        )

        val result = executor.execute(decision)

        assertTrue(result is ActionResult.Failed)
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
