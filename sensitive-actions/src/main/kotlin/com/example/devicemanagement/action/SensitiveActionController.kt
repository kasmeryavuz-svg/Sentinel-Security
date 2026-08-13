package com.example.devicemanagement.action

import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.decision.DecisionEngine
import com.example.devicemanagement.decision.FailSafeDecisionEngine
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.InMemoryStateRepository
import com.example.devicemanagement.persistence.ManagementState
import com.example.devicemanagement.trigger.DefaultTriggerEvaluator
import com.example.devicemanagement.trigger.Trigger

/**
 * The sole public entry point for sensitive actions.
 *
 * Callers can submit only raw trigger input. Decisions, approvals, actions,
 * and the executor are module-private and cannot be supplied by app code.
 */
class SensitiveActionController internal constructor(
    private val decisionEngine: DecisionEngine,
    private val actionExecutor: ActionExecutor,
) {
    fun submit(trigger: Trigger?): ActionResult {
        return when (val decision = decisionEngine.decide(trigger)) {
            is ActionDecision.Approved -> actionExecutor.execute(decision)
            is ActionDecision.Denied -> ActionResult.Rejected(
                reason = "decision_denied:${decision.reason.name}",
            )
        }
    }

    companion object {
        fun createFailSafe(logger: StructuredLogger): SensitiveActionController {
            val approvalAuthority = ApprovalAuthority()
            val stateRepository = InMemoryStateRepository(
                ManagementState(
                    serviceAvailable = false,
                    sensitiveActionsEnabled = false,
                ),
            )
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
            )
        }
    }
}
