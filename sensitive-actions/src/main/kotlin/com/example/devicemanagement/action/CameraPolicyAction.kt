@file:OptIn(com.example.devicemanagement.integration.SensitiveActionCompositionApi::class)

package com.example.devicemanagement.action

import com.example.devicemanagement.integration.SensitiveActionPolicyBackend

internal class CameraPolicyAction(
    override val type: DeviceActionType,
    private val disabled: Boolean,
    private val backend: SensitiveActionPolicyBackend,
) : DeviceAction {
    init {
        require(
            type == DeviceActionType.DISABLE_CAMERA ||
                type == DeviceActionType.ENABLE_CAMERA,
        )
    }

    override fun execute(request: ActionRequest): ActionResult {
        val operation = if (disabled) {
            SensitiveActionOperation.DISABLE_CAMERA
        } else {
            SensitiveActionOperation.ENABLE_CAMERA
        }
        return backend.applyCameraDisabled(
            disabled = disabled,
            correlationId = request.correlationId,
        )
            .toActionResult(operation, request.correlationId)
    }
}
