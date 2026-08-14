package com.example.devicemanagement.decision

import com.example.devicemanagement.action.ApprovalAuthority
import com.example.devicemanagement.action.SensitiveActionRegistry
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.ManagementState
import com.example.devicemanagement.persistence.StateRepository
import com.example.devicemanagement.trigger.DefaultTriggerEvaluator
import com.example.devicemanagement.trigger.Trigger
import com.example.devicemanagement.trigger.TriggerEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {
    private val logger = RecordingLogger()

    @Test
    fun `null and malformed triggers are denied`() {
        val engine = engineWith(state = enabledState)
        val invalidTriggers = listOf(
            null,
            Trigger(command = null, requestId = "request-1", expiresAtEpochMillis = 2_000),
            Trigger(command = "", requestId = "request-1", expiresAtEpochMillis = 2_000),
            Trigger(command = "mock_wipe", requestId = null, expiresAtEpochMillis = 2_000),
            Trigger(command = "mock_wipe", requestId = "", expiresAtEpochMillis = 2_000),
            Trigger(command = "mock_wipe", requestId = "request-1", expiresAtEpochMillis = null),
            Trigger(command = "not-a-command", requestId = "request-1", expiresAtEpochMillis = 2_000),
        )

        invalidTriggers.forEach { trigger ->
            val decision = engine.decide(trigger)

            assertTrue(decision is ActionDecision.Denied)
            assertEquals(
                DecisionReason.INVALID_TRIGGER,
                (decision as ActionDecision.Denied).reason,
            )
        }
    }

    @Test
    fun `expired trigger is denied`() {
        val decision = engineWith(state = enabledState).decide(validTrigger(expiresAt = 1_000))

        assertEquals(
            DecisionReason.INVALID_TRIGGER,
            (decision as ActionDecision.Denied).reason,
        )
        assertEquals("expired_trigger", decision.detail)
    }

    @Test
    fun `missing state is denied`() {
        val decision = engineWith(state = null).decide(validTrigger())

        assertEquals(
            DecisionReason.MISSING_STATE,
            (decision as ActionDecision.Denied).reason,
        )
    }

    @Test
    fun `unavailable service is denied`() {
        val decision = engineWith(
            state = enabledState.copy(policyServiceAvailable = false),
        ).decide(validTrigger())

        assertEquals(
            DecisionReason.SERVICE_UNAVAILABLE,
            (decision as ActionDecision.Denied).reason,
        )
    }

    @Test
    fun `disabled sensitive actions are denied`() {
        val decision = engineWith(
            state = enabledState.copy(sensitiveActionsEnabled = false),
        ).decide(validTrigger())

        assertEquals(
            DecisionReason.SENSITIVE_ACTIONS_DISABLED,
            (decision as ActionDecision.Denied).reason,
        )
    }

    @Test
    fun `trigger evaluator exception fails closed`() {
        val throwingEvaluator = TriggerEvaluator { _, _, _ ->
            error("unexpected evaluator state")
        }
        val engine = FailSafeDecisionEngine(
            triggerEvaluator = throwingEvaluator,
            stateRepository = FixedStateRepository(enabledState),
            approvalAuthority = ApprovalAuthority(),
            logger = logger,
            nowEpochMillis = { 1_000 },
        )

        val decision = engine.decide(validTrigger())

        assertEquals(
            DecisionReason.EVALUATION_ERROR,
            (decision as ActionDecision.Denied).reason,
        )
    }

    @Test
    fun `repository exception fails closed`() {
        val throwingRepository = StateRepository { error("storage unavailable") }
        val engine = FailSafeDecisionEngine(
            triggerEvaluator = DefaultTriggerEvaluator(failSafeRegistry()),
            stateRepository = throwingRepository,
            approvalAuthority = ApprovalAuthority(),
            logger = logger,
            nowEpochMillis = { 1_000 },
        )

        val decision = engine.decide(validTrigger())

        assertEquals(
            DecisionReason.EVALUATION_ERROR,
            (decision as ActionDecision.Denied).reason,
        )
    }

    @Test
    fun `valid trigger and healthy explicitly enabled state are approved`() {
        val decision = engineWith(state = enabledState).decide(validTrigger())

        assertTrue(decision is ActionDecision.Approved)
    }

    private fun engineWith(state: ManagementState?): DecisionEngine =
        FailSafeDecisionEngine(
            triggerEvaluator = DefaultTriggerEvaluator(failSafeRegistry()),
            stateRepository = FixedStateRepository(state),
            approvalAuthority = ApprovalAuthority(),
            logger = logger,
            nowEpochMillis = { 1_000 },
        )

    private fun validTrigger(expiresAt: Long = 2_000) = Trigger(
        command = "mock_wipe",
        requestId = "request-1",
        expiresAtEpochMillis = expiresAt,
    )

    private fun DecisionEngine.decide(trigger: Trigger?): ActionDecision {
        return decide(trigger, CORRELATION_ID)
    }

    private fun failSafeRegistry(): SensitiveActionRegistry {
        return SensitiveActionRegistry.failSafe(logger)
    }

    private class FixedStateRepository(
        private val state: ManagementState?,
    ) : StateRepository {
        override fun load(): ManagementState? = state
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
        const val CORRELATION_ID = "authoritative-correlation"

        val enabledState = ManagementState(
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
