package com.example.devicemanagement.action

sealed interface ActionResult {
    data class Simulated(
        val message: String,
        val correlationId: String,
    ) : ActionResult

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
    DISABLE_CAMERA,
    ENABLE_CAMERA,
    DISABLE_STATUS_BAR,
    ENABLE_STATUS_BAR,
}
