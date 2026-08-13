package com.example.devicemanagement.action

import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.logging.StructuredLogger

internal class ActionExecutor(
    actions: Set<DeviceAction>,
    private val approvalAuthority: ApprovalAuthority,
    private val logger: StructuredLogger,
) {
    private val actionsByType = actions.associateBy(DeviceAction::type)

    fun execute(decision: ActionDecision): ActionResult {
        if (decision !is ActionDecision.Approved) {
            return reject("decision_not_approved")
        }

        val request = approvalAuthority.consume(decision.approval)
            ?: return reject("approval_not_issued_or_already_consumed")
        val action = actionsByType[request.type]
            ?: return reject(
                reason = "action_not_registered",
                fields = mapOf(
                    "action" to request.type.name,
                    "request_id" to request.requestId,
                ),
            )

        return try {
            val result = action.execute(request)
            logger.info(
                event = "action_execution_completed",
                fields = mapOf(
                    "action" to request.type.name,
                    "request_id" to request.requestId,
                    "result" to result::class.simpleName,
                ),
            )
            result
        } catch (error: Throwable) {
            logger.error(
                event = "action_execution_failed",
                fields = mapOf(
                    "action" to request.type.name,
                    "request_id" to request.requestId,
                ),
                throwable = error,
            )
            ActionResult.Failed(error::class.simpleName ?: "unknown_error")
        }
    }

    private fun reject(
        reason: String,
        fields: Map<String, Any?> = emptyMap(),
    ): ActionResult.Rejected {
        logger.warn(
            event = "action_execution_rejected",
            fields = fields + ("reason" to reason),
        )
        return ActionResult.Rejected(reason)
    }
}
