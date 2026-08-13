package com.example.devicemanagement.action

internal interface DeviceAction {
    val type: DeviceActionType

    fun execute(request: ActionRequest): ActionResult
}

sealed interface ActionResult {
    data class Simulated(val message: String) : ActionResult

    data class Rejected(val reason: String) : ActionResult

    data class Failed(val reason: String) : ActionResult
}
