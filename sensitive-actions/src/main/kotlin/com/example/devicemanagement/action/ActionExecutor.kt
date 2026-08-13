package com.example.devicemanagement.action

import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.logging.StructuredLogger

internal class ActionExecutor(
    private val registry: SensitiveActionRegistry,
    private val approvalAuthority: ApprovalAuthority,
    private val logger: StructuredLogger,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val monotonicTimeSource: MonotonicTimeSource =
        MonotonicTimeSource { System.nanoTime() / 1_000_000L },
) {
    fun execute(decision: ActionDecision): ActionResult {
        if (decision !is ActionDecision.Approved) {
            return reject("decision_not_approved")
        }

        val request = when (
            val consumption = approvalAuthority.consume(
                approval = decision.approval,
                nowEpochMillis = nowEpochMillis(),
                nowMonotonicMillis = monotonicTimeSource.nowMillis(),
            )
        ) {
            is ApprovalConsumption.Accepted -> consumption.request
            is ApprovalConsumption.Rejected -> return reject(
                reason = consumption.reason,
                correlationId = consumption.correlationId,
            )
        }
        val action = registry.actionForType(request.type)
            ?: return reject(
                reason = "action_not_registered",
                correlationId = request.correlationId,
                fields = mapOf(
                    "action" to request.type.name,
                    "correlation_id" to request.correlationId,
                    "caller_request_id" to request.callerRequestId,
                ),
            )

        return try {
            val result = action.execute(request)
            logger.info(
                event = "action_execution_completed",
                fields = mapOf(
                    "action" to request.type.name,
                    "correlation_id" to request.correlationId,
                    "caller_request_id" to request.callerRequestId,
                    "result" to result::class.simpleName,
                ),
            )
            result
        } catch (error: Throwable) {
            logger.error(
                event = "action_execution_failed",
                fields = mapOf(
                    "action" to request.type.name,
                    "correlation_id" to request.correlationId,
                    "caller_request_id" to request.callerRequestId,
                ),
                throwable = error,
            )
            ActionResult.Failed(
                reason = error::class.simpleName ?: "unknown_error",
                correlationId = request.correlationId,
            )
        }
    }

    private fun reject(
        reason: String,
        correlationId: String? = null,
        fields: Map<String, Any?> = emptyMap(),
    ): ActionResult.Rejected {
        logger.warn(
            event = "action_execution_rejected",
            fields = fields + ("reason" to reason),
        )
        return ActionResult.Rejected(reason, correlationId)
    }
}
