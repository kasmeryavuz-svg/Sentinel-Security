@file:OptIn(com.example.devicemanagement.integration.SensitiveActionCompositionApi::class)

package com.example.devicemanagement.action

import com.example.devicemanagement.integration.SensitiveActionPolicyBackend

internal class ScreenCapturePolicyAction(
    override val type: DeviceActionType,
    private val disabled: Boolean,
    private val backend: SensitiveActionPolicyBackend,
) : DeviceAction {
    init {
        require(
            type == DeviceActionType.DISABLE_SCREEN_CAPTURE ||
                type == DeviceActionType.ENABLE_SCREEN_CAPTURE,
        )
    }

    override fun execute(request: ActionRequest): ActionResult {
        val operation = if (disabled) {
            SensitiveActionOperation.DISABLE_SCREEN_CAPTURE
        } else {
            SensitiveActionOperation.ENABLE_SCREEN_CAPTURE
        }
        return backend.applyScreenCaptureDisabled(
            disabled = disabled,
            correlationId = request.correlationId,
        )
            .toActionResult(operation, request.correlationId)
    }
}
