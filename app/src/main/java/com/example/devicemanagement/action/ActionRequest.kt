package com.example.devicemanagement.action

enum class DeviceActionType {
    MOCK_WIPE,
    UNSUPPORTED,
}

data class ActionRequest(
    val type: DeviceActionType,
    val requestId: String,
)
