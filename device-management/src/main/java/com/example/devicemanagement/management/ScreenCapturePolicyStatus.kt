package com.example.devicemanagement.management

internal class DefaultScreenCapturePolicyStatusProvider(
    private val deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    private val platform: DevicePolicyPlatform,
    private val logger: DeviceManagementLogger,
) : ScreenCapturePolicyStatusProvider {
    override fun currentStatus(): ScreenCapturePolicyStatus {
        return try {
            val validation = deviceOwnerValidationProvider.currentValidation()
            if (validation.result != DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER) {
                unavailable(validation.reasons)
            } else {
                val service = platform.screenCapturePolicyService()
                    ?: return unavailable(listOf("DevicePolicyManager is unavailable."))
                val disabled = service.isScreenCaptureDisabled()
                logger.info(
                    event = "screen_capture_policy_status",
                    fields = mapOf("disabled" to disabled, "success" to true),
                )
                ScreenCapturePolicyStatus(
                    state = if (disabled) {
                        ScreenCapturePolicyState.DISABLED
                    } else {
                        ScreenCapturePolicyState.ENABLED
                    },
                    reasons = emptyList(),
                )
            }
        } catch (error: Throwable) {
            logger.error(
                event = "screen_capture_policy_status",
                fields = mapOf("success" to false),
                throwable = error,
            )
            unavailable(
                listOf(
                    "Screen-capture policy status unavailable: " +
                        (error.javaClass.simpleName.ifEmpty { "unexpected error" }),
                ),
            )
        }
    }

    private fun unavailable(reasons: List<String>) = ScreenCapturePolicyStatus(
        state = ScreenCapturePolicyState.UNAVAILABLE,
        reasons = reasons.ifEmpty { listOf("Screen-capture policy status is unavailable.") },
    )
}
