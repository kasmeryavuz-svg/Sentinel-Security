package com.example.devicemanagement.action

internal interface DeviceAction {
    val type: DeviceActionType

    fun execute(request: ActionRequest): ActionResult
}

internal sealed interface ActionResult {
    data class Simulated(val message: String) : ActionResult

    data class Rejected(val reason: String) : ActionResult

    data class Failed(val reason: String) : ActionResult
}

sealed interface SensitiveActionResult {
    val correlationId: String

    data class Approved(
        override val correlationId: String,
        val message: String,
    ) : SensitiveActionResult

    data class Denied(
        override val correlationId: String,
        val reason: String,
    ) : SensitiveActionResult
}
