package com.example.devicemanagement.management

internal interface ScreenCapturePolicy {
    fun applyDisabled(disabled: Boolean): ScreenCapturePolicyMutation
}

internal sealed interface ScreenCapturePolicyMutation {
    data class Applied(
        val requestedDisabled: Boolean,
        val observedDisabled: Boolean,
    ) : ScreenCapturePolicyMutation

    data class Denied(val reason: String) : ScreenCapturePolicyMutation

    data class Failed(val reason: String) : ScreenCapturePolicyMutation
}

internal class DefaultScreenCapturePolicy(
    private val deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    private val platform: DevicePolicyPlatform,
    private val logger: DeviceManagementLogger,
) : ScreenCapturePolicy {
    override fun applyDisabled(disabled: Boolean): ScreenCapturePolicyMutation {
        val validation = try {
            deviceOwnerValidationProvider.currentValidation()
        } catch (error: Throwable) {
            return fail("device_owner_validation_failed", error)
        }
        val denial = validation.denialReason()
        if (denial != null) {
            logger.warn(
                event = "screen_capture_policy_denied",
                fields = mapOf("reason" to denial, "requested_disabled" to disabled),
            )
            return ScreenCapturePolicyMutation.Denied(denial)
        }

        val service = try {
            platform.screenCapturePolicyService()
        } catch (error: Throwable) {
            return fail("policy_service_unavailable", error)
        } ?: return ScreenCapturePolicyMutation.Failed("policy_service_unavailable")

        return try {
            service.setScreenCaptureDisabled(disabled)
            val observedDisabled = service.isScreenCaptureDisabled()
            if (observedDisabled != disabled) {
                logger.error(
                    event = "screen_capture_policy_verification_failed",
                    fields = mapOf(
                        "requested_disabled" to disabled,
                        "observed_disabled" to observedDisabled,
                    ),
                    throwable = null,
                )
                ScreenCapturePolicyMutation.Failed("post_write_read_back_mismatch")
            } else {
                logger.info(
                    event = "screen_capture_policy_applied",
                    fields = mapOf(
                        "requested_disabled" to disabled,
                        "observed_disabled" to observedDisabled,
                    ),
                )
                ScreenCapturePolicyMutation.Applied(
                    requestedDisabled = disabled,
                    observedDisabled = observedDisabled,
                )
            }
        } catch (error: SecurityException) {
            fail("security_exception", error)
        } catch (error: Throwable) {
            fail("unexpected_exception:${error::class.simpleName ?: "unknown"}", error)
        }
    }

    private fun DeviceOwnerValidation.denialReason(): String? {
        val status = managementStatus
        return when {
            result != DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER ->
                "device_owner_not_verified:${result.name}"
            !status.isPolicyServiceAvailable -> "policy_service_unavailable"
            status.mode != ManagementMode.DEVICE_OWNER ||
                !status.isDeviceOwner ||
                status.isProfileOwner -> "management_state_inconsistent"
            !status.isExpectedAdminReceiverRegistered -> "admin_receiver_not_registered"
            !status.isAdminActive -> "expected_admin_not_active"
            expectedAdminReceiverComponent !in registeredSentinelAdminComponents ->
                "expected_admin_component_mismatch"
            else -> null
        }
    }

    private fun fail(
        reason: String,
        error: Throwable,
    ): ScreenCapturePolicyMutation.Failed {
        logger.error(
            event = "screen_capture_policy_failed",
            fields = mapOf("reason" to reason),
            throwable = error,
        )
        return ScreenCapturePolicyMutation.Failed(reason)
    }
}
