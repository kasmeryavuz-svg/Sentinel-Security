package com.example.devicemanagement.management

internal interface CameraPolicy {
    fun applyDisabled(disabled: Boolean): CameraPolicyMutation
}

internal sealed interface CameraPolicyMutation {
    data class Applied(
        val requestedDisabled: Boolean,
        val observedDisabled: Boolean,
    ) : CameraPolicyMutation

    data class Denied(val reason: String) : CameraPolicyMutation

    data class Failed(val reason: String) : CameraPolicyMutation
}

internal class DefaultCameraPolicy(
    private val deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    private val platform: DevicePolicyPlatform,
    private val logger: DeviceManagementLogger,
) : CameraPolicy {
    override fun applyDisabled(disabled: Boolean): CameraPolicyMutation {
        val validation = try {
            deviceOwnerValidationProvider.currentValidation()
        } catch (error: Throwable) {
            return fail("device_owner_validation_failed", error)
        }
        val denial = validation.denialReason()
        if (denial != null) {
            logger.warn(
                event = "camera_policy_denied",
                fields = mapOf("reason" to denial, "requested_disabled" to disabled),
            )
            return CameraPolicyMutation.Denied(denial)
        }

        val service = try {
            platform.cameraPolicyService()
        } catch (error: Throwable) {
            return fail("policy_service_unavailable", error)
        } ?: return CameraPolicyMutation.Failed("policy_service_unavailable")

        return try {
            service.setCameraDisabled(disabled)
            val observedDisabled = service.isCameraDisabled()
            if (observedDisabled != disabled) {
                logger.error(
                    event = "camera_policy_verification_failed",
                    fields = mapOf(
                        "requested_disabled" to disabled,
                        "observed_disabled" to observedDisabled,
                    ),
                    throwable = null,
                )
                CameraPolicyMutation.Failed("post_write_read_back_mismatch")
            } else {
                logger.info(
                    event = "camera_policy_applied",
                    fields = mapOf(
                        "requested_disabled" to disabled,
                        "observed_disabled" to observedDisabled,
                    ),
                )
                CameraPolicyMutation.Applied(
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
    ): CameraPolicyMutation.Failed {
        logger.error(
            event = "camera_policy_failed",
            fields = mapOf("reason" to reason),
            throwable = error,
        )
        return CameraPolicyMutation.Failed(reason)
    }
}
