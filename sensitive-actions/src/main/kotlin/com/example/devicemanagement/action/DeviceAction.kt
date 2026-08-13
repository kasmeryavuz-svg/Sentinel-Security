package com.example.devicemanagement.action

internal interface DeviceAction {
    val type: DeviceActionType

    fun execute(request: ActionRequest): ActionResult
}

sealed interface ActionResult {
    data class Simulated(val message: String) : ActionResult

    data class Applied(
        val operation: SensitiveActionOperation,
        val requestedDisabled: Boolean,
        val observedDisabled: Boolean,
        val correlationId: String,
    ) : ActionResult

    data class Rejected(
        val reason: String,
        val correlationId: String? = null,
    ) : ActionResult

    data class Failed(
        val reason: String,
        val correlationId: String? = null,
    ) : ActionResult
}

enum class SensitiveActionOperation {
    DISABLE_SCREEN_CAPTURE,
    ENABLE_SCREEN_CAPTURE,
}
