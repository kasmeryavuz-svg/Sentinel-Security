package com.example.devicemanagement.management

import android.os.Build

internal class DefaultStatusBarPolicyStatusProvider(
    private val deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    private val platform: DevicePolicyPlatform,
    private val logger: DeviceManagementLogger,
) : StatusBarPolicyStatusProvider {
    override fun currentStatus(): StatusBarPolicyStatus {
        return try {
            if (Build.VERSION.SDK_INT < STATUS_BAR_POLICY_MIN_SDK) {
                return unavailable(
                    listOf(
                        "Status-bar policy requires Android 14 (API 34) or newer " +
                            "for verified setter/read-back. " +
                            "Status-bar disabling does not apply on the lock screen; " +
                            "LockTask is a separate capability and is not used here.",
                    ),
                )
            }
            val validation = deviceOwnerValidationProvider.currentValidation()
            if (validation.result != DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER) {
                unavailable(validation.reasons)
            } else {
                val service = platform.statusBarPolicyService()
                    ?: return unavailable(listOf("DevicePolicyManager is unavailable."))
                val disabled = service.isStatusBarDisabled()
                logger.info(
                    event = "status_bar_policy_status",
                    fields = mapOf("disabled" to disabled, "success" to true),
                )
                StatusBarPolicyStatus(
                    state = if (disabled) {
                        StatusBarPolicyState.DISABLED
                    } else {
                        StatusBarPolicyState.ENABLED
                    },
                    reasons = emptyList(),
                )
            }
        } catch (error: Throwable) {
            logger.error(
                event = "status_bar_policy_status",
                fields = mapOf("success" to false),
                throwable = error,
            )
            unavailable(
                listOf(
                    "Status-bar policy status unavailable: " +
                        (error.javaClass.simpleName.ifEmpty { "unexpected error" }),
                ),
            )
        }
    }

    private fun unavailable(reasons: List<String>) = StatusBarPolicyStatus(
        state = StatusBarPolicyState.UNAVAILABLE,
        reasons = reasons.ifEmpty {
            listOf("Status-bar policy status is unavailable.")
        },
    )

    internal companion object {
        const val STATUS_BAR_POLICY_MIN_SDK = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}
