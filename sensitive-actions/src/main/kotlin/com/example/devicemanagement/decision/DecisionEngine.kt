package com.example.devicemanagement.decision

import com.example.devicemanagement.action.ApprovalAuthority
import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.integration.SystemMonotonicTimeSource
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.StateRepository
import com.example.devicemanagement.trigger.Trigger
import com.example.devicemanagement.trigger.TriggerEvaluation
import com.example.devicemanagement.trigger.TriggerEvaluator

internal fun interface DecisionEngine {
    fun decide(trigger: Trigger?, authoritativeCorrelationId: String): ActionDecision
}

internal class FailSafeDecisionEngine(
    private val triggerEvaluator: TriggerEvaluator,
    private val stateRepository: StateRepository,
    private val approvalAuthority: ApprovalAuthority,
    private val logger: StructuredLogger,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val monotonicTimeSource: MonotonicTimeSource = SystemMonotonicTimeSource,
) : DecisionEngine {
    override fun decide(
        trigger: Trigger?,
        authoritativeCorrelationId: String,
    ): ActionDecision {
        return try {
            when (
                val evaluation = triggerEvaluator.evaluate(
                    trigger = trigger,
                    nowEpochMillis = nowEpochMillis(),
                    authoritativeCorrelationId = authoritativeCorrelationId,
                )
            ) {
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
                detail = error.javaClass.simpleName,
            )
        }
    }

    private fun decideForValidTrigger(evaluation: TriggerEvaluation.Valid): ActionDecision {
        val state = stateRepository.load()
            ?: return deny(DecisionReason.MISSING_STATE)
        if (!state.policyServiceAvailable) {
            return deny(DecisionReason.SERVICE_UNAVAILABLE)
        }
        if (!state.managementStateConsistent) {
            return deny(DecisionReason.INCONSISTENT_MANAGEMENT_STATE)
        }
        if (state.profileOwner) {
            return deny(DecisionReason.PROFILE_OWNER_NOT_ALLOWED)
        }
        if (!state.expectedAdminReceiverRegistered) {
            return deny(DecisionReason.ADMIN_RECEIVER_NOT_REGISTERED)
        }
        if (!state.expectedAdminActive) {
            return deny(DecisionReason.ADMIN_NOT_ACTIVE)
        }
        if (!state.verifiedDeviceOwner) {
            return deny(DecisionReason.DEVICE_OWNER_NOT_VERIFIED)
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
                "correlation_id" to evaluation.request.correlationId,
                "caller_request_id" to evaluation.request.callerRequestId,
            ),
        )
        return ActionDecision.Approved(
            approval = approvalAuthority.issue(
                request = evaluation.request,
                issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
            ),
        )
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
