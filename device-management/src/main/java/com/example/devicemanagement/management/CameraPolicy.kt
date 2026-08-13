package com.example.devicemanagement.management

internal interface CameraPolicy {
    fun applyDisabled(disabled: Boolean, correlationId: String): PolicyMutation
}

internal class DefaultCameraPolicy(
    deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    platform: DevicePolicyPlatform,
    logger: DeviceManagementLogger,
) : CameraPolicy {
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
            mutation = VerifiedPolicyMutation.Camera(disabled),
            correlationId = correlationId,
        )
    }
}
