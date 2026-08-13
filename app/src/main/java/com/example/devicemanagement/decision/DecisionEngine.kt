package com.example.devicemanagement.decision

import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.StateRepository
import com.example.devicemanagement.trigger.Trigger
import com.example.devicemanagement.trigger.TriggerEvaluation
import com.example.devicemanagement.trigger.TriggerEvaluator

interface DecisionEngine {
    fun decide(trigger: Trigger?): ActionDecision
}

class FailSafeDecisionEngine(
    private val triggerEvaluator: TriggerEvaluator,
    private val stateRepository: StateRepository,
    private val logger: StructuredLogger,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : DecisionEngine {
    override fun decide(trigger: Trigger?): ActionDecision {
        return try {
            when (val evaluation = triggerEvaluator.evaluate(trigger, nowEpochMillis())) {
                is TriggerEvaluation.Invalid -> deny(
                    reason = DecisionReason.INVALID_TRIGGER,
                    detail = evaluation.reason,
                )
                is TriggerEvaluation.Valid -> decideForValidTrigger(evaluation)
            }
        } catch (error: Throwable) {
            logger.error(
                event = "action_decision_error",
                fields = mapOf("outcome" to "denied"),
                throwable = error,
            )
            ActionDecision.Denied(
                reason = DecisionReason.EVALUATION_ERROR,
                detail = error::class.simpleName,
            )
        }
    }

    private fun decideForValidTrigger(evaluation: TriggerEvaluation.Valid): ActionDecision {
        val state = stateRepository.load()
            ?: return deny(DecisionReason.MISSING_STATE)
        if (!state.serviceAvailable) {
            return deny(DecisionReason.SERVICE_UNAVAILABLE)
        }
        if (!state.sensitiveActionsEnabled) {
            return deny(DecisionReason.SENSITIVE_ACTIONS_DISABLED)
        }

        logger.info(
            event = "action_decision",
            fields = mapOf(
                "action" to evaluation.request.type.name,
                "outcome" to "approved",
                "reason" to DecisionReason.APPROVED_BY_POLICY.name,
                "request_id" to evaluation.request.requestId,
            ),
        )
        return ActionDecision.Approved(evaluation.request)
    }

    private fun deny(reason: DecisionReason, detail: String? = null): ActionDecision.Denied {
        logger.warn(
            event = "action_decision",
            fields = mapOf(
                "detail" to detail,
                "outcome" to "denied",
                "reason" to reason.name,
            ),
        )
        return ActionDecision.Denied(reason = reason, detail = detail)
    }
}
