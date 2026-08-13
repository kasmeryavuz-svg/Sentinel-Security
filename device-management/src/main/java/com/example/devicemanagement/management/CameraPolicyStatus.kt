package com.example.devicemanagement.management

internal class DefaultCameraPolicyStatusProvider(
    private val deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    private val platform: DevicePolicyPlatform,
    private val logger: DeviceManagementLogger,
) : CameraPolicyStatusProvider {
    override fun currentStatus(): CameraPolicyStatus {
        return try {
            val validation = deviceOwnerValidationProvider.currentValidation()
            if (validation.result != DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER) {
                unavailable(validation.reasons)
            } else {
                val service = platform.cameraPolicyService()
                    ?: return unavailable(listOf("DevicePolicyManager is unavailable."))
                val disabled = service.isCameraDisabled()
                logger.info(
                    event = "camera_policy_status",
                    fields = mapOf("disabled" to disabled, "success" to true),
                )
                CameraPolicyStatus(
                    state = if (disabled) {
                        CameraPolicyState.DISABLED
                    } else {
                        CameraPolicyState.ENABLED
                    },
                    reasons = emptyList(),
                )
            }
        } catch (error: Throwable) {
            logger.error(
                event = "camera_policy_status",
                fields = mapOf("success" to false),
                throwable = error,
            )
            unavailable(
                listOf(
                    "Camera policy status unavailable: " +
                        (error.javaClass.simpleName.ifEmpty { "unexpected error" }),
                ),
            )
        }
    }

    private fun unavailable(reasons: List<String>) = CameraPolicyStatus(
        state = CameraPolicyState.UNAVAILABLE,
        reasons = reasons.ifEmpty { listOf("Camera policy status is unavailable.") },
    )
}
