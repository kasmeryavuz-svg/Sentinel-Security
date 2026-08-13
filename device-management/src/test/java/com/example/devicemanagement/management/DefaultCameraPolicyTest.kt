package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCameraPolicyTest {
    private val logger = RecordingLogger()

    @Test
    fun `verified Device Owner can disable camera with immediate component read back`() {
        val operations = mutableListOf<String>()
        val service = FakeCameraService(false, operations = operations)

        val result = policy(verifiedValidation(), service, operations)
            .applyDisabled(true, CORRELATION_ID)

        assertEquals(PolicyMutation.Applied(true, true), result)
        assertEquals(listOf("service", "validate", "set:true", "get"), operations)
    }

    @Test
    fun `verified Device Owner can restore camera with immediate component read back`() {
        val service = FakeCameraService(initialDisabled = true)

        val result = policy(verifiedValidation(), service).applyDisabled(false, CORRELATION_ID)

        assertEquals(PolicyMutation.Applied(false, false), result)
        assertEquals(listOf("set:false", "get"), service.operations)
    }

    @Test
    fun `post-write read back mismatch fails closed`() {
        val service = FakeCameraService(
            initialDisabled = false,
            ignoreWrites = true,
        )

        val result = policy(verifiedValidation(), service).applyDisabled(true, CORRELATION_ID)

        assertEquals(
            PolicyMutation.Failed("post_write_read_back_mismatch"),
            result,
        )
        assertEquals(listOf("set:true", "get"), service.operations)
    }

    @Test
    fun `ordinary app and Profile Owner are denied before writer`() {
        val ordinaryService = FakeCameraService(false)
        val profileService = FakeCameraService(false)

        val ordinaryResult = policy(
            validation(ManagementMode.ORDINARY_APP),
            ordinaryService,
        ).applyDisabled(true, CORRELATION_ID)
        val profileResult = policy(
            validation(ManagementMode.PROFILE_OWNER),
            profileService,
        ).applyDisabled(true, CORRELATION_ID)

        assertTrue(ordinaryResult is PolicyMutation.Denied)
        assertTrue(profileResult is PolicyMutation.Denied)
        assertTrue(ordinaryService.operations.isEmpty())
        assertTrue(profileService.operations.isEmpty())
    }

    @Test
    fun `inactive or mismatched expected admin is denied before writer`() {
        val inactiveService = FakeCameraService(false)
        val mismatchedService = FakeCameraService(false)

        val inactive = policy(
            verifiedValidation(isAdminActive = false),
            inactiveService,
        ).applyDisabled(true, CORRELATION_ID)
        val mismatched = policy(
            verifiedValidation(registeredComponents = emptySet()),
            mismatchedService,
        ).applyDisabled(true, CORRELATION_ID)

        assertEquals(
            PolicyMutation.Denied("expected_admin_not_active"),
            inactive,
        )
        assertEquals(
            PolicyMutation.Denied("expected_admin_component_mismatch"),
            mismatched,
        )
        assertTrue(inactiveService.operations.isEmpty())
        assertTrue(mismatchedService.operations.isEmpty())
    }

    @Test
    fun `validation uncertainty fails closed before policy service access`() {
        val operations = mutableListOf<String>()
        val policy = DefaultCameraPolicy(
            deviceOwnerValidationProvider = DeviceOwnerValidationProvider {
                operations += "validate"
                error("validation unavailable")
            },
            platform = FakePlatform(FakeCameraService(false), operations),
            logger = logger,
        )

        val result = policy.applyDisabled(true, CORRELATION_ID)

        assertEquals(
            PolicyMutation.Failed("device_owner_validation_failed"),
            result,
        )
        assertEquals(listOf("service", "validate"), operations)
    }

    @Test
    fun `unavailable camera policy service fails closed`() {
        val result = policy(verifiedValidation(), service = null)
            .applyDisabled(true, CORRELATION_ID)

        assertEquals(
            PolicyMutation.Failed("policy_service_unavailable"),
            result,
        )
    }

    @Test
    fun `SecurityException fails closed`() {
        val service = FakeCameraService(
            initialDisabled = false,
            setError = SecurityException("denied"),
        )

        val result = policy(verifiedValidation(), service).applyDisabled(true, CORRELATION_ID)

        assertEquals(PolicyMutation.Failed("security_exception"), result)
        assertEquals(listOf("set:true"), service.operations)
    }

    @Test
    fun `unexpected read exception fails closed`() {
        val service = FakeCameraService(
            initialDisabled = false,
            getError = IllegalStateException("unavailable"),
        )

        val result = policy(verifiedValidation(), service).applyDisabled(true, CORRELATION_ID)

        assertEquals(
            PolicyMutation.Failed(
                "unexpected_exception:IllegalStateException",
            ),
            result,
        )
        assertEquals(listOf("set:true", "get"), service.operations)
    }

    private fun policy(
        validation: DeviceOwnerValidation,
        service: DevicePolicyCameraService?,
        operations: MutableList<String>? = null,
    ): DefaultCameraPolicy {
        return DefaultCameraPolicy(
            deviceOwnerValidationProvider = DeviceOwnerValidationProvider {
                operations?.add("validate")
                validation
            },
            platform = FakePlatform(service, operations),
            logger = logger,
        )
    }

    private fun verifiedValidation(
        isAdminActive: Boolean = true,
        registeredComponents: Set<String> = setOf(EXPECTED_COMPONENT),
    ) = validation(
        mode = ManagementMode.DEVICE_OWNER,
        isAdminActive = isAdminActive,
        registeredComponents = registeredComponents,
    )

    private fun validation(
        mode: ManagementMode,
        isAdminActive: Boolean = true,
        registeredComponents: Set<String> = setOf(EXPECTED_COMPONENT),
    ): DeviceOwnerValidation {
        val deviceOwner = mode == ManagementMode.DEVICE_OWNER
        val profileOwner = mode == ManagementMode.PROFILE_OWNER
        val status = DeviceManagementStatus(
            mode = mode,
            isPolicyServiceAvailable = true,
            isExpectedAdminReceiverRegistered = true,
            isAdminActive = isAdminActive,
            isDeviceOwner = deviceOwner,
            isProfileOwner = profileOwner,
            availableCapabilities = emptySet(),
            diagnostics = emptyList(),
        )
        return DeviceOwnerValidation(
            result = if (deviceOwner) {
                DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER
            } else {
                DeviceOwnerValidationResult.NOT_DEVICE_OWNER
            },
            packageName = "com.example.devicemanagement",
            expectedAdminReceiverComponent = EXPECTED_COMPONENT,
            registeredSentinelAdminComponents = registeredComponents,
            managementStatus = status,
            provisioningReadiness = ProvisioningReadiness(
                managementStatus = status,
                deviceOwnerProvisioning = ProvisioningOption(
                    ProvisioningAvailability.NOT_ALLOWED,
                    emptyList(),
                ),
                profileOwnerProvisioning = ProvisioningOption(
                    ProvisioningAvailability.NOT_ALLOWED,
                    emptyList(),
                ),
            ),
            reasons = emptyList(),
        )
    }

    private class FakePlatform(
        private val service: DevicePolicyCameraService?,
        private val operations: MutableList<String>? = null,
    ) : DevicePolicyPlatform {
        override fun policyService(): DevicePolicyReadService? = null

        override fun cameraPolicyService(): DevicePolicyCameraService? {
            operations?.add("service")
            return service
        }

        override fun isExpectedAdminReceiverRegistered(): Boolean = true
    }

    private class FakeCameraService(
        initialDisabled: Boolean,
        private val ignoreWrites: Boolean = false,
        private val setError: Throwable? = null,
        private val getError: Throwable? = null,
        val operations: MutableList<String> = mutableListOf(),
    ) : DevicePolicyCameraService {
        private var disabled = initialDisabled

        override fun isCameraDisabled(): Boolean {
            operations += "get"
            getError?.let { throw it }
            return disabled
        }

        override fun setCameraDisabled(disabled: Boolean) {
            operations += "set:$disabled"
            setError?.let { throw it }
            if (!ignoreWrites) {
                this.disabled = disabled
            }
        }
    }

    private class RecordingLogger : DeviceManagementLogger {
        override fun info(event: String, fields: Map<String, Any?>) = Unit
        override fun warn(event: String, fields: Map<String, Any?>) = Unit
        override fun error(
            event: String,
            fields: Map<String, Any?>,
            throwable: Throwable?,
        ) = Unit
    }

    private companion object {
        const val CORRELATION_ID = "authoritative-correlation"
        const val EXPECTED_COMPONENT =
            "com.example.devicemanagement/.management.SentinelDeviceAdminReceiver"
    }
}
