package com.example.devicemanagement.trigger

import com.example.devicemanagement.action.ActionRequest
import com.example.devicemanagement.action.SensitiveActionRegistry

internal fun interface TriggerEvaluator {
    fun evaluate(
        trigger: Trigger?,
        nowEpochMillis: Long,
        authoritativeCorrelationId: String,
    ): TriggerEvaluation
}

internal class DefaultTriggerEvaluator(
    private val registry: SensitiveActionRegistry,
) : TriggerEvaluator {
    override fun evaluate(
        trigger: Trigger?,
        nowEpochMillis: Long,
        authoritativeCorrelationId: String,
    ): TriggerEvaluation {
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
        if (expiresAt - nowEpochMillis > MAX_REQUEST_LIFETIME_MILLIS) {
            return TriggerEvaluation.Invalid(reason = "request_lifetime_too_long")
        }

        val actionType = registry.actionTypeForCommand(command)
            ?: return TriggerEvaluation.Invalid(
                reason = "unknown_command",
                detail = command,
            )

        return TriggerEvaluation.Valid(
            ActionRequest(
                type = actionType,
                correlationId = authoritativeCorrelationId,
                callerRequestId = requestId,
                expiresAtEpochMillis = expiresAt,
            ),
        )
    }

    private companion object {
        const val MAX_REQUEST_LIFETIME_MILLIS = 60_000L
    }
}

object SensitiveActionCommands {
    const val DISABLE_SCREEN_CAPTURE = "disable_screen_capture"
    const val ENABLE_SCREEN_CAPTURE = "enable_screen_capture"
    const val DISABLE_CAMERA = "disable_camera"
    const val ENABLE_CAMERA = "enable_camera"
    internal const val MOCK_WIPE_SIMULATION = "mock_wipe"
}
