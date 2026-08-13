package com.example.devicemanagement.action

import com.example.devicemanagement.integration.PolicyMutationResult
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
        return when (
            val result = backend.applyScreenCaptureDisabled(
                disabled = disabled,
                correlationId = request.requestId,
            )
        ) {
            is PolicyMutationResult.Applied -> ActionResult.Applied(
                operation = if (disabled) {
                    SensitiveActionOperation.DISABLE_SCREEN_CAPTURE
                } else {
                    SensitiveActionOperation.ENABLE_SCREEN_CAPTURE
                },
                requestedDisabled = result.requestedDisabled,
                observedDisabled = result.observedDisabled,
                correlationId = request.requestId,
            )
            is PolicyMutationResult.Denied -> ActionResult.Rejected(
                reason = result.reason,
                correlationId = request.requestId,
            )
            is PolicyMutationResult.Failed -> ActionResult.Failed(
                reason = result.reason,
                correlationId = request.requestId,
            )
        }
    }
}
