package com.example.devicemanagement.management

import android.content.Context
import android.os.SystemClock
import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.action.DeviceManagementSensitiveActionControllerFactory
import com.example.devicemanagement.audit.AndroidAuditPersistence
import com.example.devicemanagement.audit.AuditHistoryProvider
import com.example.devicemanagement.audit.AuditStorageStatusProvider
import com.example.devicemanagement.audit.DurableAuditRepository
import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.integration.PolicyMutationResult
import com.example.devicemanagement.integration.SensitiveActionAuthorization
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.destructive.ProductionDestructiveRealChain
import com.example.devicemanagement.destructive.ProductionDestructiveRetainer
import com.example.devicemanagement.logging.StructuredLogger
import com.example.devicemanagement.persistence.AndroidDestructiveSafetyPersistence
import com.example.devicemanagement.recovery.DeviceManagementRecoveryInspectionFactory
import com.example.devicemanagement.recovery.RecoveryInspectionProvider

internal object DeviceManagementComposition {
    fun create(
        context: Context,
        logger: StructuredLogger,
    ): DeviceManagementServices {
        val deviceManagementLogger = StructuredDeviceManagementLogger(logger)
        val audit = AndroidAuditPersistence.create(context, logger)
        val recoveryInspection = DeviceManagementRecoveryInspectionFactory.create(
            history = audit,
            logger = logger,
        )
        val applicationContext = context.applicationContext
        val platform = AndroidDevicePolicyPlatform(applicationContext)
        val productionDestructiveRetainer = retainProductionDestructiveImplementation(
            context = applicationContext,
            logger = logger,
            deviceManagementLogger = deviceManagementLogger,
            platform = platform,
        )
        val sensitiveActions = createSensitiveActions(
                context = context,
                sensitiveActionLogger = logger,
                deviceManagementLogger = deviceManagementLogger,
                auditWriter = audit,
            )
        val deviceManagementStatus =
            DeviceManagementDiagnostics.create(context, deviceManagementLogger)
        val provisioningReadiness =
            DeviceManagementDiagnostics.createProvisioningReadiness(
                context,
                deviceManagementLogger,
            )
        val deviceOwnerValidation =
            DeviceManagementDiagnostics.createDeviceOwnerValidation(
                context,
                deviceManagementLogger,
            )
        val screenCapturePolicyStatus =
            DeviceManagementDiagnostics.createScreenCapturePolicyStatus(
                context,
                deviceManagementLogger,
            )
        val cameraPolicyStatus =
            DeviceManagementDiagnostics.createCameraPolicyStatus(
                context,
                deviceManagementLogger,
            )
        val statusBarPolicyStatus =
            DeviceManagementDiagnostics.createStatusBarPolicyStatus(
                context,
                deviceManagementLogger,
            )
        return ComposedDeviceManagementServices(
            sensitiveActions = sensitiveActions,
            deviceManagementStatus = deviceManagementStatus,
            provisioningReadiness = provisioningReadiness,
            deviceOwnerValidation = deviceOwnerValidation,
            screenCapturePolicyStatus = screenCapturePolicyStatus,
            cameraPolicyStatus = cameraPolicyStatus,
            statusBarPolicyStatus = statusBarPolicyStatus,
            auditHistory = audit,
            auditStorageStatus = audit,
            recoveryInspection = recoveryInspection,
            productionDestructiveRetainer = productionDestructiveRetainer,
        )
    }

    private fun createSensitiveActions(
        context: Context,
        sensitiveActionLogger: StructuredLogger,
        deviceManagementLogger: DeviceManagementLogger,
        auditWriter: DurableAuditRepository,
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
        val statusBarPolicy = DefaultStatusBarPolicy(
            deviceOwnerValidationProvider = validationProvider,
            platform = platform,
            logger = deviceManagementLogger,
        )
        val backend = DeviceManagementSensitiveActionBackend(
            deviceOwnerValidationProvider = validationProvider,
            screenCapturePolicy = screenCapturePolicy,
            cameraPolicy = cameraPolicy,
            statusBarPolicy = statusBarPolicy,
            logger = deviceManagementLogger,
        )
        return DeviceManagementSensitiveActionControllerFactory.create(
            backend = backend,
            logger = sensitiveActionLogger,
            monotonicTimeSource = AndroidElapsedRealtimeMonotonicTimeSource,
            auditWriter = auditWriter,
        )
    }

    private fun retainProductionDestructiveImplementation(
        context: Context,
        logger: StructuredLogger,
        deviceManagementLogger: DeviceManagementLogger,
        platform: AndroidDevicePolicyPlatform,
    ): ProductionDestructiveRetainer {
        return ProductionDestructiveRealChain.retainForProduction(
            factoryReset = platform.factoryResetService(),
            liveFacts = AndroidDestructiveLiveFactsSource(
                validationProvider = DeviceManagementDiagnostics.createDeviceOwnerValidationProvider(
                    platform = platform,
                    logger = deviceManagementLogger,
                ),
                platform = platform,
            ),
            clock = AndroidElapsedRealtimeMonotonicTimeSource,
            durability = AndroidDestructiveSafetyPersistence.issueRuntimeDurability(
                context = context,
                logger = logger,
            ),
        )
    }
}

internal class ComposedDeviceManagementServices(
    override val sensitiveActions: SensitiveActionController,
    override val deviceManagementStatus: DeviceManagementStatusProvider,
    override val provisioningReadiness: ProvisioningReadinessProvider,
    override val deviceOwnerValidation: DeviceOwnerValidationProvider,
    override val screenCapturePolicyStatus: ScreenCapturePolicyStatusProvider,
    override val cameraPolicyStatus: CameraPolicyStatusProvider,
    override val statusBarPolicyStatus: StatusBarPolicyStatusProvider,
    override val auditHistory: AuditHistoryProvider,
    override val auditStorageStatus: AuditStorageStatusProvider,
    override val recoveryInspection: RecoveryInspectionProvider,
    @Suppress("unused")
    private val productionDestructiveRetainer: ProductionDestructiveRetainer,
) : DeviceManagementServices

/**
 * Production Android monotonic source for approval freshness. Owned exclusively by
 * trusted device-management composition; not injectable from app/UI code.
 */
internal object AndroidElapsedRealtimeMonotonicTimeSource : MonotonicTimeSource {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

internal class DeviceManagementSensitiveActionBackend(
    private val deviceOwnerValidationProvider: DeviceOwnerValidationProvider,
    private val screenCapturePolicy: ScreenCapturePolicy,
    private val cameraPolicy: CameraPolicy,
    private val statusBarPolicy: StatusBarPolicy,
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

    override fun applyStatusBarDisabled(
        disabled: Boolean,
        correlationId: String,
    ): PolicyMutationResult {
        return statusBarPolicy.applyDisabled(disabled, correlationId).toBackendResult()
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
