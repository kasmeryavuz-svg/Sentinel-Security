package com.example.devicemanagement.action

import com.example.devicemanagement.integration.SensitiveActionPolicyBackend

internal class StatusBarPolicyAction(
    override val type: DeviceActionType,
    private val disabled: Boolean,
    private val backend: SensitiveActionPolicyBackend,
) : DeviceAction {
    init {
        require(
            type == DeviceActionType.DISABLE_STATUS_BAR ||
                type == DeviceActionType.ENABLE_STATUS_BAR,
        )
    }

    override fun execute(request: ActionRequest): ActionResult {
        val operation = if (disabled) {
            SensitiveActionOperation.DISABLE_STATUS_BAR
        } else {
            SensitiveActionOperation.ENABLE_STATUS_BAR
        }
        return backend.applyStatusBarDisabled(
            disabled = disabled,
            correlationId = request.correlationId,
        )
            .toActionResult(operation, request.correlationId)
    }
}
