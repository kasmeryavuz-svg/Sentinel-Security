package com.example.devicemanagement.trigger

import com.example.devicemanagement.action.ActionRequest
import com.example.devicemanagement.action.DeviceActionType

internal fun interface TriggerEvaluator {
    fun evaluate(trigger: Trigger?, nowEpochMillis: Long): TriggerEvaluation
}

internal class DefaultTriggerEvaluator : TriggerEvaluator {
    override fun evaluate(trigger: Trigger?, nowEpochMillis: Long): TriggerEvaluation {
        if (trigger == null) {
            return TriggerEvaluation.Invalid(reason = "missing_trigger")
        }

        val command = trigger.command?.trim()
        if (command.isNullOrEmpty()) {
            return TriggerEvaluation.Invalid(reason = "missing_command")
        }

        val requestId = trigger.requestId?.trim()
        if (requestId.isNullOrEmpty()) {
            return TriggerEvaluation.Invalid(reason = "missing_request_id")
        }

        val expiresAt = trigger.expiresAtEpochMillis
            ?: return TriggerEvaluation.Invalid(reason = "missing_expiration")
        if (expiresAt <= nowEpochMillis) {
            return TriggerEvaluation.Invalid(reason = "expired_trigger")
        }

        val actionType = when (command) {
            MOCK_WIPE_COMMAND -> DeviceActionType.MOCK_WIPE
            else -> return TriggerEvaluation.Invalid(
                reason = "unknown_command",
                detail = command,
            )
        }

        return TriggerEvaluation.Valid(
            ActionRequest(
                type = actionType,
                requestId = requestId,
            ),
        )
    }

    private companion object {
        const val MOCK_WIPE_COMMAND = "mock_wipe"
    }
}
