package com.example.devicemanagement.action

import com.example.devicemanagement.audit.AuditActionNames
import com.example.devicemanagement.audit.AuditAppendRequest
import com.example.devicemanagement.audit.AuditAppendResult
import com.example.devicemanagement.audit.AuditEventPhase
import com.example.devicemanagement.audit.AuditReasonCodes
import com.example.devicemanagement.audit.DurableAuditRepository
import com.example.devicemanagement.audit.InMemoryAuditRecordStore
import com.example.devicemanagement.audit.SensitiveActionAuditWriter
import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.decision.FailSafeDecisionEngine
import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.integration.SystemMonotonicTimeSource
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.InMemoryStateRepository
import com.example.devicemanagement.persistence.ManagementState
import com.example.devicemanagement.persistence.PolicyBackendStateRepository
import com.example.devicemanagement.trigger.DefaultTriggerEvaluator
import com.example.devicemanagement.trigger.Trigger
import java.util.UUID

/**
 * Internal implementation of the app-visible submit-only controller.
 *
 * Durable REQUESTED audit events are written here, before decision or
 * execution. Audit persistence never authorizes, approves, or mutates policy.
 */
internal class DefaultSensitiveActionController(
    private val decisionEngine: com.example.devicemanagement.decision.DecisionEngine,
    private val actionExecutor: ActionExecutor,
    private val auditWriter: SensitiveActionAuditWriter,
    private val logger: StructuredLogger,
    private val correlationIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val eventIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val presentationWallClockMillis: () -> Long = System::currentTimeMillis,
) : SensitiveActionController {
    override fun submit(trigger: Trigger?): ActionResult {
        val correlationId = correlationIdGenerator()
        val actionName = AuditActionNames.canonicalize(trigger?.command)
        val requested = auditWriter.append(
            AuditAppendRequest(
                eventId = eventIdGenerator(),
                correlationId = correlationId,
                actionName = actionName,
                phase = AuditEventPhase.REQUESTED,
                presentationWallClockMillis = presentationWallClockMillis(),
                reasonCode = null,
            ),
        )
        if (requested is AuditAppendResult.Failed) {
            logger.warn(
                event = "audit_requested_persist_failed",
                fields = mapOf(
                    "correlation_id" to correlationId,
                    "action" to actionName,
                ),
            )
            return ActionResult.Rejected(
                reason = "audit_persistence_unavailable",
                correlationId = correlationId,
            )
        }

        val result = when (val decision = decisionEngine.decide(trigger, correlationId)) {
            is ActionDecision.Approved -> actionExecutor.execute(decision)
            is ActionDecision.Denied -> ActionResult.Rejected(
                reason = "decision_denied:${decision.reason.name}",
                correlationId = correlationId,
            )
        }

        val terminal = auditWriter.append(
            AuditAppendRequest(
                eventId = eventIdGenerator(),
                correlationId = correlationId,
                actionName = actionName,
                phase = AuditReasonCodes.toPhase(result),
                presentationWallClockMillis = presentationWallClockMillis(),
                reasonCode = AuditReasonCodes.fromActionResult(result),
            ),
        )
        if (terminal is AuditAppendResult.Failed) {
            logger.warn(
                event = "audit_terminal_persist_failed",
                fields = mapOf(
                    "correlation_id" to correlationId,
                    "action" to actionName,
                    "result" to result.javaClass.simpleName,
                ),
            )
        }
        return result
    }
}

/**
 * Production composition entry point. This implementation module is not present
 * on the app compile classpath; app receives only SensitiveActionController API.
 * Clock injection is intentionally unavailable to app/UI and is supplied only by
 * trusted device-management composition.
 */
object DeviceManagementSensitiveActionControllerFactory {
    fun create(
        backend: SensitiveActionPolicyBackend,
        logger: StructuredLogger,
        monotonicTimeSource: MonotonicTimeSource,
        auditWriter: SensitiveActionAuditWriter,
    ): SensitiveActionController {
        return createControlledController(
            backend = backend,
            logger = logger,
            monotonicTimeSource = monotonicTimeSource,
            auditWriter = auditWriter,
        )
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
        auditWriter = DurableAuditRepository(InMemoryAuditRecordStore(), logger),
        logger = logger,
    )
}

/**
 * Trusted composition always creates a brand-new process-local
 * [ApprovalAuthority]. Restart or recreation cannot reuse, persist, or
 * consume a pre-restart approval. A later process must submit a fresh
 * trigger through this path.
 */
internal fun createControlledController(
    backend: SensitiveActionPolicyBackend,
    logger: StructuredLogger,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
    monotonicTimeSource: MonotonicTimeSource = SystemMonotonicTimeSource,
    auditWriter: SensitiveActionAuditWriter? = null,
    presentationWallClockMillis: () -> Long = System::currentTimeMillis,
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
        auditWriter = auditWriter ?: DurableAuditRepository(InMemoryAuditRecordStore(), logger),
        logger = logger,
        presentationWallClockMillis = presentationWallClockMillis,
    )
}
