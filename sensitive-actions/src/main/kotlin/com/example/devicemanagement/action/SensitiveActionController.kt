package com.example.devicemanagement.action

import com.example.devicemanagement.decision.ActionDecision
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
 * Internal implementation of the app-visible submit-only controller.
 */
internal class DefaultSensitiveActionController(
    private val decisionEngine: com.example.devicemanagement.decision.DecisionEngine,
    private val actionExecutor: ActionExecutor,
    private val correlationIdGenerator: () -> String = { UUID.randomUUID().toString() },
) : SensitiveActionController {
    override fun submit(trigger: Trigger?): ActionResult {
        val correlationId = correlationIdGenerator()
        return when (val decision = decisionEngine.decide(trigger, correlationId)) {
            is ActionDecision.Approved -> actionExecutor.execute(decision)
            is ActionDecision.Denied -> ActionResult.Rejected(
                reason = "decision_denied:${decision.reason.name}",
                correlationId = correlationId,
            )
        }
    }
}

/**
 * Production composition entry point. This implementation module is not present
 * on the app compile classpath; app receives only SensitiveActionController API.
 */
object DeviceManagementSensitiveActionControllerFactory {
    fun create(
        backend: SensitiveActionPolicyBackend,
        logger: StructuredLogger,
    ): SensitiveActionController {
        return createControlledController(backend = backend, logger = logger)
    }
}

internal fun createFailSafeController(
    logger: StructuredLogger,
): SensitiveActionController {
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
    return DefaultSensitiveActionController(
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

internal fun createControlledController(
    backend: SensitiveActionPolicyBackend,
    logger: StructuredLogger,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
    monotonicTimeSource: MonotonicTimeSource =
        MonotonicTimeSource { System.nanoTime() / 1_000_000L },
): SensitiveActionController {
    val approvalAuthority = ApprovalAuthority()
    val registry = SensitiveActionRegistry.controlled(backend)
    return DefaultSensitiveActionController(
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
