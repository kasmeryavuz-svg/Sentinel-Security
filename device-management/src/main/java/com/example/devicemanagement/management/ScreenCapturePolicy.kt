package com.example.devicemanagement.management

internal interface ScreenCapturePolicy {
    fun applyDisabled(disabled: Boolean, correlationId: String): PolicyMutation
}

internal class DefaultScreenCapturePolicy(
    deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    platform: DevicePolicyPlatform,
    logger: DeviceManagementLogger,
) : ScreenCapturePolicy {
    private val executor = VerifiedPolicyMutationExecutor(
        deviceOwnerValidationProvider = deviceOwnerValidationProvider,
        platform = platform,
        logger = logger,
    )

    override fun applyDisabled(
        disabled: Boolean,
        correlationId: String,
    ): PolicyMutation {
        return executor.execute(
            mutation = VerifiedPolicyMutation.ScreenCapture(disabled),
            correlationId = correlationId,
        )
    }
}
