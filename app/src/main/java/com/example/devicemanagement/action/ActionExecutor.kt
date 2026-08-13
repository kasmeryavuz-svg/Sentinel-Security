package com.example.devicemanagement.action

import com.example.devicemanagement.decision.ActionDecision
import com.example.devicemanagement.logging.StructuredLogger

class ActionExecutor(
    actions: Set<DeviceAction>,
    private val logger: StructuredLogger,
) {
    private val actionsByType = actions.associateBy(DeviceAction::type)

    fun execute(decision: ActionDecision): ActionResult {
        if (decision !is ActionDecision.Approved) {
            logger.warn(
                event = "action_execution_rejected",
                fields = mapOf("reason" to "decision_not_approved"),
            )
            return ActionResult.Rejected("decision_not_approved")
        }

        val request = decision.request
        val action = actionsByType[request.type]
        if (action == null) {
            logger.warn(
                event = "action_execution_rejected",
                fields = mapOf(
                    "action" to request.type.name,
                    "reason" to "action_not_registered",
                    "request_id" to request.requestId,
                ),
            )
            return ActionResult.Rejected("action_not_registered")
        }
        if (action.type != request.type) {
            logger.error(
                event = "action_execution_rejected",
                fields = mapOf(
                    "reason" to "action_type_mismatch",
                    "request_id" to request.requestId,
                ),
            )
            return ActionResult.Rejected("action_type_mismatch")
        }

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
}
