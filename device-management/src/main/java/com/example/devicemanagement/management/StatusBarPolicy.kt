package com.example.devicemanagement.management

internal interface StatusBarPolicy {
    fun applyDisabled(disabled: Boolean, correlationId: String): PolicyMutation
}

internal class DefaultStatusBarPolicy(
    deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    platform: DevicePolicyPlatform,
    logger: DeviceManagementLogger,
) : StatusBarPolicy {
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
            mutation = VerifiedPolicyMutation.StatusBar(disabled),
            correlationId = correlationId,
        )
    }
}
