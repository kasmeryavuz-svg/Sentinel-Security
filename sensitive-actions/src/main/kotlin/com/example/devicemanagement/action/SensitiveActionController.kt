package com.example.devicemanagement.action

import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.decision.DecisionEngine
import com.example.devicemanagement.decision.DecisionReason
import com.example.devicemanagement.decision.FailSafeDecisionEngine
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.InMemoryStateRepository
import com.example.devicemanagement.persistence.ManagementState
import com.example.devicemanagement.trigger.DefaultTriggerEvaluator
import com.example.devicemanagement.trigger.Trigger
import java.util.UUID

/**
 * The sole public entry point for sensitive actions.
 *
 * Callers can submit only raw trigger input. Decisions, approvals, actions,
 * and the executor are module-private and cannot be supplied by app code.
 */
class SensitiveActionController internal constructor(
    private val decisionEngine: DecisionEngine,
    private val actionExecutor: ActionExecutor,
    private val logger: StructuredLogger,
    private val correlationIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    fun submit(trigger: Trigger?): SensitiveActionResult {
        val correlationId = correlationIdFor(trigger)
        logger.info(
            event = "sensitive_action_submitted",
            fields = mapOf("correlation_id" to correlationId),
        )

        val result = when (val decision = decisionEngine.decide(trigger)) {
            is ActionDecision.Approved -> when (val execution = actionExecutor.execute(decision)) {
                is ActionResult.Simulated -> SensitiveActionResult.Approved(
                    correlationId = correlationId,
                    message = execution.message,
                )
                is ActionResult.Rejected -> SensitiveActionResult.Denied(
                    correlationId = correlationId,
                    reason = humanExecutionReason(execution.reason),
                )
                is ActionResult.Failed -> SensitiveActionResult.Denied(
                    correlationId = correlationId,
                    reason = "The simulation could not be completed safely.",
                )
            }
            is ActionDecision.Denied -> SensitiveActionResult.Denied(
                correlationId = correlationId,
                reason = humanDecisionReason(decision),
            )
        }

        logger.info(
            event = "sensitive_action_completed",
            fields = mapOf(
                "correlation_id" to correlationId,
                "outcome" to when (result) {
                    is SensitiveActionResult.Approved -> "approved_simulation"
                    is SensitiveActionResult.Denied -> "denied"
                },
            ),
        )
        return result
    }

    companion object {
        fun createFailSafe(logger: StructuredLogger): SensitiveActionController {
            return create(
                logger = logger,
                state = ManagementState(
                    serviceAvailable = false,
                    sensitiveActionsEnabled = false,
                ),
            )
        }

        fun createSimulation(logger: StructuredLogger): SensitiveActionController {
            return create(
                logger = logger,
                state = ManagementState(
                    serviceAvailable = true,
                    sensitiveActionsEnabled = true,
                ),
            )
        }

        private fun create(
            logger: StructuredLogger,
            state: ManagementState,
        ): SensitiveActionController {
            val approvalAuthority = ApprovalAuthority()
            val stateRepository = InMemoryStateRepository(state)
            return SensitiveActionController(
                decisionEngine = FailSafeDecisionEngine(
                    triggerEvaluator = DefaultTriggerEvaluator(),
                    stateRepository = stateRepository,
                    approvalAuthority = approvalAuthority,
                    logger = logger,
                ),
                actionExecutor = ActionExecutor(
                    actions = setOf(SafeMockWipeAction(logger)),
                    approvalAuthority = approvalAuthority,
                    logger = logger,
                ),
                logger = logger,
            )
        }
    }

    private fun correlationIdFor(trigger: Trigger?): String {
        val supplied = trigger?.requestId?.trim()
        if (!supplied.isNullOrEmpty()) return supplied
        return runCatching(correlationIdGenerator)
            .getOrDefault("correlation-unavailable")
            .ifBlank { "correlation-unavailable" }
    }

    private fun humanDecisionReason(decision: ActionDecision.Denied): String {
        return when (decision.reason) {
            DecisionReason.INVALID_TRIGGER -> when (decision.detail) {
                "missing_trigger" -> "No request was provided."
                "missing_command" -> "The request command is missing."
                "missing_request_id" -> "The request correlation ID is missing."
                "missing_expiration" -> "The request expiration is missing."
                "expired_trigger" -> "The request has expired."
                "unknown_command" -> "The request command is not recognized."
                else -> "The request is malformed."
            }
            DecisionReason.MISSING_STATE -> "Management state is unavailable."
            DecisionReason.SERVICE_UNAVAILABLE -> "The management service is unavailable."
            DecisionReason.SENSITIVE_ACTIONS_DISABLED ->
                "Sensitive action simulations are disabled."
            DecisionReason.EVALUATION_ERROR -> "The request could not be evaluated safely."
            DecisionReason.APPROVED_BY_POLICY -> "The request was not approved."
        }
    }

    private fun humanExecutionReason(reason: String): String {
        return when (reason) {
            "approval_not_issued_or_already_consumed" ->
                "The approval is invalid or has already been used."
            "action_not_registered" -> "The requested simulation is unavailable."
            else -> "The simulation was rejected safely."
        }
    }
}
