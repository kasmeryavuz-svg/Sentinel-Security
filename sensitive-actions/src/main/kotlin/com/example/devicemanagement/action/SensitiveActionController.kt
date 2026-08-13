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

        fun createControlled(
            backend: SensitiveActionPolicyBackend,
            logger: StructuredLogger,
            nowEpochMillis: () -> Long = System::currentTimeMillis,
            monotonicTimeSource: MonotonicTimeSource =
                MonotonicTimeSource { System.nanoTime() / 1_000_000L },
        ): SensitiveActionController {
            val approvalAuthority = ApprovalAuthority()
            return SensitiveActionController(
                decisionEngine = FailSafeDecisionEngine(
                    triggerEvaluator = DefaultTriggerEvaluator(),
                    stateRepository = PolicyBackendStateRepository(backend),
                    approvalAuthority = approvalAuthority,
                    logger = logger,
                    nowEpochMillis = nowEpochMillis,
                    monotonicTimeSource = monotonicTimeSource,
                ),
                actionExecutor = ActionExecutor(
                    actions = setOf(
                        ScreenCapturePolicyAction(
                            type = DeviceActionType.DISABLE_SCREEN_CAPTURE,
                            disabled = true,
                            backend = backend,
                        ),
                        ScreenCapturePolicyAction(
                            type = DeviceActionType.ENABLE_SCREEN_CAPTURE,
                            disabled = false,
                            backend = backend,
                        ),
                        CameraPolicyAction(
                            type = DeviceActionType.DISABLE_CAMERA,
                            disabled = true,
                            backend = backend,
                        ),
                        CameraPolicyAction(
                            type = DeviceActionType.ENABLE_CAMERA,
                            disabled = false,
                            backend = backend,
                        ),
                    ),
                    approvalAuthority = approvalAuthority,
                    logger = logger,
                    nowEpochMillis = nowEpochMillis,
                    monotonicTimeSource = monotonicTimeSource,
                ),
            )
        }
    }
}
