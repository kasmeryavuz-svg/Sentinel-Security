package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultScreenCapturePolicyTest {
    private val logger = RecordingLogger()

    @Test
    fun `verified Device Owner can disable screen capture with immediate read back`() {
        val service = FakeScreenCaptureService(initialDisabled = false)
        val result = policy(verifiedValidation(), service).applyDisabled(true, CORRELATION_ID)

        assertEquals(PolicyMutation.Applied(true, true), result)
        assertEquals(listOf("set:true", "get"), service.operations)
    }

    @Test
    fun `verified Device Owner can restore screen capture with immediate read back`() {
        val service = FakeScreenCaptureService(initialDisabled = true)
        val result = policy(verifiedValidation(), service).applyDisabled(false, CORRELATION_ID)

        assertEquals(PolicyMutation.Applied(false, false), result)
        assertEquals(listOf("set:false", "get"), service.operations)
    }

    @Test
    fun `read back mismatch fails`() {
        val service = FakeScreenCaptureService(
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
    fun `ordinary app is denied before writer`() {
        val service = FakeScreenCaptureService(false)

        val result = policy(validation(ManagementMode.ORDINARY_APP), service)
            .applyDisabled(true, CORRELATION_ID)

        assertTrue(result is PolicyMutation.Denied)
        assertTrue(service.operations.isEmpty())
    }

    @Test
    fun `Profile Owner is denied before writer`() {
        val service = FakeScreenCaptureService(false)

        val result = policy(validation(ManagementMode.PROFILE_OWNER), service)
            .applyDisabled(true, CORRELATION_ID)

        assertTrue(result is PolicyMutation.Denied)
        assertTrue(service.operations.isEmpty())
    }

    @Test
    fun `unavailable policy service fails closed`() {
        val result = policy(verifiedValidation(), service = null)
            .applyDisabled(true, CORRELATION_ID)

        assertEquals(
            PolicyMutation.Failed("policy_service_unavailable"),
            result,
        )
    }

    @Test
    fun `SecurityException fails closed`() {
        val service = FakeScreenCaptureService(
            initialDisabled = false,
            setError = SecurityException("denied"),
        )

        val result = policy(verifiedValidation(), service).applyDisabled(true, CORRELATION_ID)

        assertEquals(PolicyMutation.Failed("security_exception"), result)
        assertEquals(listOf("set:true"), service.operations)
    }

    @Test
    fun `unexpected read exception fails closed`() {
        val service = FakeScreenCaptureService(
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
        service: DevicePolicyScreenCaptureService?,
    ): DefaultScreenCapturePolicy {
        return DefaultScreenCapturePolicy(
            deviceOwnerValidationProvider = DeviceOwnerValidationProvider { validation },
            platform = FakePlatform(service),
            logger = logger,
        )
    }

    private fun verifiedValidation() = validation(ManagementMode.DEVICE_OWNER)

    private fun validation(mode: ManagementMode): DeviceOwnerValidation {
        val deviceOwner = mode == ManagementMode.DEVICE_OWNER
        val profileOwner = mode == ManagementMode.PROFILE_OWNER
        val status = DeviceManagementStatus(
            mode = mode,
            isPolicyServiceAvailable = true,
            isExpectedAdminReceiverRegistered = true,
            isAdminActive = true,
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
            registeredSentinelAdminComponents = setOf(EXPECTED_COMPONENT),
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
        private val service: DevicePolicyScreenCaptureService?,
    ) : DevicePolicyPlatform {
        override fun policyService(): DevicePolicyReadService? = null

        override fun screenCapturePolicyService(): DevicePolicyScreenCaptureService? = service

        override fun isExpectedAdminReceiverRegistered(): Boolean = true
    }

    private class FakeScreenCaptureService(
        initialDisabled: Boolean,
        private val ignoreWrites: Boolean = false,
        private val setError: Throwable? = null,
        private val getError: Throwable? = null,
    ) : DevicePolicyScreenCaptureService {
        private var disabled = initialDisabled
        val operations = mutableListOf<String>()

        override fun isScreenCaptureDisabled(): Boolean {
            operations += "get"
            getError?.let { throw it }
            return disabled
        }

        override fun setScreenCaptureDisabled(disabled: Boolean) {
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
