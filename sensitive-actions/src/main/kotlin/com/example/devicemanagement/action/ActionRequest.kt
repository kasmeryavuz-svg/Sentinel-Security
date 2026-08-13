package com.example.devicemanagement.action

internal enum class DeviceActionType {
    MOCK_WIPE,
    UNSUPPORTED,
}

internal data class ActionRequest(
    val type: DeviceActionType,
    val requestId: String,
)
