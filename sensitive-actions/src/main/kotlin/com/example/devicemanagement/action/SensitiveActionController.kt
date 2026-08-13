package com.example.devicemanagement.action

import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.decision.DecisionEngine
import com.example.devicemanagement.decision.FailSafeDecisionEngine
import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.InMemoryStateRepository
import com.example.devicemanagement.persistence.ManagementState
import com.example.devicemanagement.persistence.PolicyBackendStateRepository
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
    private val correlationIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    fun submit(trigger: Trigger?): ActionResult {
        val correlationId = correlationIdGenerator()
        return when (val decision = decisionEngine.decide(trigger, correlationId)) {
            is ActionDecision.Approved -> actionExecutor.execute(decision)
            is ActionDecision.Denied -> ActionResult.Rejected(
                reason = "decision_denied:${decision.reason.name}",
                correlationId = correlationId,
            )
        }
    }

    companion object {
        fun createFailSafe(logger: StructuredLogger): SensitiveActionController {
            val approvalAuthority = ApprovalAuthority()
            val registry = SensitiveActionRegistry.failSafe(logger)
            val stateRepository = InMemoryStateRepository(
                ManagementState(
                    policyServiceAvailable = false,
                    sensitiveActionsEnabled = false,
                    verifiedDeviceOwner = false,
                    profileOwner = false,
                    expectedAdminReceiverRegistered = false,
                    expectedAdminActive = false,
                    managementStateConsistent = false,
                ),
            )
            return SensitiveActionController(
                decisionEngine = FailSafeDecisionEngine(
                    triggerEvaluator = DefaultTriggerEvaluator(registry),
                    stateRepository = stateRepository,
                    approvalAuthority = approvalAuthority,
                    logger = logger,
                ),
                actionExecutor = ActionExecutor(
                    registry = registry,
                    approvalAuthority = approvalAuthority,
                    logger = logger,
                ),
            )
        }

        fun createControlled(
            backend: SensitiveActionPolicyBackend,
            logger: StructuredLogger,
            nowEpochMillis: () -> Long = System::currentTimeMillis,
            monotonicTimeSource: MonotonicTimeSource =
                MonotonicTimeSource { System.nanoTime() / 1_000_000L },
        ): SensitiveActionController {
            val approvalAuthority = ApprovalAuthority()
            val registry = SensitiveActionRegistry.controlled(backend)
            return SensitiveActionController(
                decisionEngine = FailSafeDecisionEngine(
                    triggerEvaluator = DefaultTriggerEvaluator(registry),
                    stateRepository = PolicyBackendStateRepository(backend),
                    approvalAuthority = approvalAuthority,
                    logger = logger,
                    nowEpochMillis = nowEpochMillis,
                    monotonicTimeSource = monotonicTimeSource,
                ),
                actionExecutor = ActionExecutor(
                    registry = registry,
                    approvalAuthority = approvalAuthority,
                    logger = logger,
                    nowEpochMillis = nowEpochMillis,
                    monotonicTimeSource = monotonicTimeSource,
                ),
            )
        }
    }
}
