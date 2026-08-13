package com.example.devicemanagement.action

internal interface DeviceAction {
    val type: DeviceActionType

    fun execute(request: ActionRequest): ActionResult
}
