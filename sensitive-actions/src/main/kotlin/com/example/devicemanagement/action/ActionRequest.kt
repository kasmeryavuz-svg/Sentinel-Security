package com.example.devicemanagement.action

internal enum class DeviceActionType {
    MOCK_WIPE,
    DISABLE_SCREEN_CAPTURE,
    ENABLE_SCREEN_CAPTURE,
    DISABLE_CAMERA,
    ENABLE_CAMERA,
    DISABLE_STATUS_BAR,
    ENABLE_STATUS_BAR,
}

internal data class ActionRequest(
    val type: DeviceActionType,
    val correlationId: String,
    val callerRequestId: String,
    val expiresAtEpochMillis: Long,
)
