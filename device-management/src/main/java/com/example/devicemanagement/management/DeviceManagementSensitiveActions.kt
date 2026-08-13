package com.example.devicemanagement.management

import android.content.Context
import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.action.DeviceManagementSensitiveActionControllerFactory
import com.example.devicemanagement.integration.PolicyMutationResult
import com.example.devicemanagement.integration.SensitiveActionAuthorization
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.logging.StructuredLogger

object DeviceManagementSensitiveActions {
    fun create(
        context: Context,
        sensitiveActionLogger: StructuredLogger,
        deviceManagementLogger: DeviceManagementLogger,
    ): SensitiveActionController {
        val platform = AndroidDevicePolicyPlatform(context.applicationContext)
        val validationProvider = DeviceManagementDiagnostics.createDeviceOwnerValidationProvider(
            platform = platform,
            logger = deviceManagementLogger,
        )
        val screenCapturePolicy = DefaultScreenCapturePolicy(
            deviceOwnerValidationProvider = validationProvider,
            platform = platform,
            logger = deviceManagementLogger,
        )
        val cameraPolicy = DefaultCameraPolicy(
            deviceOwnerValidationProvider = validationProvider,
            platform = platform,
            logger = deviceManagementLogger,
        )
        val backend = DeviceManagementSensitiveActionBackend(
            deviceOwnerValidationProvider = validationProvider,
            screenCapturePolicy = screenCapturePolicy,
            cameraPolicy = cameraPolicy,
            logger = deviceManagementLogger,
        )
        return DeviceManagementSensitiveActionControllerFactory.create(
            backend = backend,
            logger = sensitiveActionLogger,
        )
    }
}

internal class DeviceManagementSensitiveActionBackend(
    private val deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    private val screenCapturePolicy: ScreenCapturePolicy,
    private val cameraPolicy: CameraPolicy,
    private val logger: DeviceManagementLogger,
) : SensitiveActionPolicyBackend {
    override fun currentAuthorization(): SensitiveActionAuthorization {
        return try {
            val validation = deviceOwnerValidationProvider.currentValidation()
            val status = validation.managementStatus
            SensitiveActionAuthorization(
                policyServiceAvailable = status.isPolicyServiceAvailable,
                sensitiveActionsEnabled = true,
                verifiedDeviceOwner =
                    validation.result == DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER,
                profileOwner = status.isProfileOwner,
                expectedAdminReceiverRegistered =
                    status.isExpectedAdminReceiverRegistered,
                expectedAdminActive = status.isAdminActive,
                managementStateConsistent =
                    validation.result != DeviceOwnerValidationResult.UNAVAILABLE &&
                        status.isInternallyConsistent(),
            )
        } catch (error: Throwable) {
            logger.error(
                event = "sensitive_action_authorization_failed",
                fields = mapOf("outcome" to "denied"),
                throwable = error,
            )
            SensitiveActionAuthorization(
                policyServiceAvailable = false,
                sensitiveActionsEnabled = false,
                verifiedDeviceOwner = false,
                profileOwner = false,
                expectedAdminReceiverRegistered = false,
                expectedAdminActive = false,
                managementStateConsistent = false,
            )
        }
    }

    override fun applyScreenCaptureDisabled(
        disabled: Boolean,
        correlationId: String,
    ): PolicyMutationResult {
        return screenCapturePolicy.applyDisabled(disabled, correlationId).toBackendResult()
    }

    override fun applyCameraDisabled(
        disabled: Boolean,
        correlationId: String,
    ): PolicyMutationResult {
        return cameraPolicy.applyDisabled(disabled, correlationId).toBackendResult()
    }

    private fun PolicyMutation.toBackendResult(): PolicyMutationResult {
        return when (this) {
            is PolicyMutation.Applied -> PolicyMutationResult.Applied(
                requestedDisabled = requestedDisabled,
                observedDisabled = observedDisabled,
            )
            is PolicyMutation.Denied -> PolicyMutationResult.Denied(reason)
            is PolicyMutation.Failed -> PolicyMutationResult.Failed(reason)
        }
    }

    private fun DeviceManagementStatus.isInternallyConsistent(): Boolean {
        return when (mode) {
            ManagementMode.DEVICE_OWNER -> isDeviceOwner && !isProfileOwner
            ManagementMode.PROFILE_OWNER -> isProfileOwner && !isDeviceOwner
            ManagementMode.ORDINARY_APP -> !isDeviceOwner && !isProfileOwner
            ManagementMode.UNAVAILABLE -> false
        }
    }
}
