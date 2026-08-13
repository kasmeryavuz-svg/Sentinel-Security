package com.example.devicemanagement.management

/**
 * Closed allowlist of real policy mutations.
 *
 * Adding a capability requires a new explicit variant and an exhaustive dispatch
 * branch containing both its typed setter and matching typed read-back. The sealed
 * variant set is reflected by VerifiedPolicyMutationTest, whose exhaustive expected
 * sequence requires final validation, setter, getter, and mismatch behavior.
 */
internal sealed interface VerifiedPolicyMutation {
    data class ScreenCapture(val disabled: Boolean) : VerifiedPolicyMutation

    data class Camera(val disabled: Boolean) : VerifiedPolicyMutation
}

internal sealed interface PolicyMutation {
    data class Applied(
        val requestedDisabled: Boolean,
        val observedDisabled: Boolean,
    ) : PolicyMutation

    data class Denied(val reason: String) : PolicyMutation

    data class Failed(val reason: String) : PolicyMutation
}

/**
 * Executes only the sealed mutations above. It accepts no callbacks, method names,
 * reflection targets, generic argument maps, or caller-supplied DPM operations.
 */
internal class VerifiedPolicyMutationExecutor(
    private val deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    private val platform: DevicePolicyPlatform,
    private val logger: DeviceManagementLogger,
) {
    fun execute(
        mutation: VerifiedPolicyMutation,
        correlationId: String,
    ): PolicyMutation {
        return when (mutation) {
            is VerifiedPolicyMutation.ScreenCapture ->
                executeScreenCapture(mutation, correlationId)
            is VerifiedPolicyMutation.Camera -> executeCamera(mutation, correlationId)
        }
    }

    private fun executeScreenCapture(
        mutation: VerifiedPolicyMutation.ScreenCapture,
        correlationId: String,
    ): PolicyMutation {
        val service = try {
            platform.screenCapturePolicyService()
        } catch (error: Throwable) {
            return fail("screen_capture", "policy_service_unavailable", correlationId, error)
        } ?: return PolicyMutation.Failed("policy_service_unavailable")

        val validation = try {
            deviceOwnerValidationProvider.currentValidation()
        } catch (error: Throwable) {
            return fail(
                "screen_capture",
                "device_owner_validation_failed",
                correlationId,
                error,
            )
        }
        val denial = DeviceOwnerMutationGuard.denialReason(validation)
        if (denial != null) {
            return deny("screen_capture", denial, mutation.disabled, correlationId)
        }

        return try {
            service.setScreenCaptureDisabled(mutation.disabled)
            val observedDisabled = service.isScreenCaptureDisabled()
            verify(
                capability = "screen_capture",
                requestedDisabled = mutation.disabled,
                observedDisabled = observedDisabled,
                correlationId = correlationId,
            )
        } catch (error: SecurityException) {
            fail("screen_capture", "security_exception", correlationId, error)
        } catch (error: Throwable) {
            fail(
                "screen_capture",
                "unexpected_exception:${error::class.simpleName ?: "unknown"}",
                correlationId,
                error,
            )
        }
    }

    private fun executeCamera(
        mutation: VerifiedPolicyMutation.Camera,
        correlationId: String,
    ): PolicyMutation {
        val service = try {
            platform.cameraPolicyService()
        } catch (error: Throwable) {
            return fail("camera", "policy_service_unavailable", correlationId, error)
        } ?: return PolicyMutation.Failed("policy_service_unavailable")

        val validation = try {
            deviceOwnerValidationProvider.currentValidation()
        } catch (error: Throwable) {
            return fail("camera", "device_owner_validation_failed", correlationId, error)
        }
        val denial = DeviceOwnerMutationGuard.denialReason(validation)
        if (denial != null) {
            return deny("camera", denial, mutation.disabled, correlationId)
        }

        return try {
            service.setCameraDisabled(mutation.disabled)
            val observedDisabled = service.isCameraDisabled()
            verify(
                capability = "camera",
                requestedDisabled = mutation.disabled,
                observedDisabled = observedDisabled,
                correlationId = correlationId,
            )
        } catch (error: SecurityException) {
            fail("camera", "security_exception", correlationId, error)
        } catch (error: Throwable) {
            fail(
                "camera",
                "unexpected_exception:${error::class.simpleName ?: "unknown"}",
                correlationId,
                error,
            )
        }
    }

    private fun verify(
        capability: String,
        requestedDisabled: Boolean,
        observedDisabled: Boolean,
        correlationId: String,
    ): PolicyMutation {
        val fields = mapOf(
            "correlation_id" to correlationId,
            "requested_disabled" to requestedDisabled,
            "observed_disabled" to observedDisabled,
        )
        if (observedDisabled != requestedDisabled) {
            logger.error(
                event = "${capability}_policy_verification_failed",
                fields = fields,
                throwable = null,
            )
            return PolicyMutation.Failed("post_write_read_back_mismatch")
        }

        logger.info(event = "${capability}_policy_applied", fields = fields)
        return PolicyMutation.Applied(requestedDisabled, observedDisabled)
    }

    private fun deny(
        capability: String,
        reason: String,
        requestedDisabled: Boolean,
        correlationId: String,
    ): PolicyMutation.Denied {
        logger.warn(
            event = "${capability}_policy_denied",
            fields = mapOf(
                "correlation_id" to correlationId,
                "reason" to reason,
                "requested_disabled" to requestedDisabled,
            ),
        )
        return PolicyMutation.Denied(reason)
    }

    private fun fail(
        capability: String,
        reason: String,
        correlationId: String,
        error: Throwable,
    ): PolicyMutation.Failed {
        logger.error(
            event = "${capability}_policy_failed",
            fields = mapOf("correlation_id" to correlationId, "reason" to reason),
            throwable = error,
        )
        return PolicyMutation.Failed(reason)
    }
}
