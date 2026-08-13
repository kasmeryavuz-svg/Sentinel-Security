package com.example.devicemanagement.action

internal enum class DeviceActionType {
    MOCK_WIPE,
    DISABLE_SCREEN_CAPTURE,
    ENABLE_SCREEN_CAPTURE,
    UNSUPPORTED,
}

internal data class ActionRequest(
    val type: DeviceActionType,
    val requestId: String,
    val expiresAtEpochMillis: Long,
)
