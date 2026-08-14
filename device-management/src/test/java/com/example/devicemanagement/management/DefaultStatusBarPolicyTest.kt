package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStatusBarPolicyTest {
    private val logger = RecordingLogger()

    @Test
    fun `verified Device Owner can disable status bar with immediate read back`() {
        val operations = mutableListOf<String>()
        val service = FakeStatusBarService(false, operations = operations)

        val result = policy(verifiedValidation(), service, operations)
            .applyDisabled(true, CORRELATION_ID)

        assertEquals(PolicyMutation.Applied(true, true), result)
        assertEquals(listOf("service", "validate", "set:true", "get"), operations)
    }

    @Test
    fun `verified Device Owner can restore status bar with immediate read back`() {
        val service = FakeStatusBarService(initialDisabled = true)

        val result = policy(verifiedValidation(), service).applyDisabled(false, CORRELATION_ID)

        assertEquals(PolicyMutation.Applied(false, false), result)
        assertEquals(listOf("set:false", "get"), service.operations)
    }

    @Test
    fun `setter rejection fails closed without claiming success`() {
        val service = FakeStatusBarService(
            initialDisabled = false,
            setterAccepted = false,
        )

        val result = policy(verifiedValidation(), service).applyDisabled(true, CORRELATION_ID)

        assertEquals(PolicyMutation.Failed("setter_rejected"), result)
        assertEquals(listOf("set:true"), service.operations)
    }

    @Test
    fun `post-write read back mismatch fails closed`() {
        val service = FakeStatusBarService(
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
        val ordinaryService = FakeStatusBarService(false)
        val profileService = FakeStatusBarService(false)

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
    fun `unavailable status bar policy service fails closed`() {
        val result = policy(verifiedValidation(), service = null)
            .applyDisabled(true, CORRELATION_ID)

        assertEquals(
            PolicyMutation.Failed("policy_service_unavailable"),
            result,
        )
    }

    @Test
    fun `SecurityException fails closed`() {
        val service = FakeStatusBarService(
            initialDisabled = false,
            setError = SecurityException("denied"),
        )

        val result = policy(verifiedValidation(), service).applyDisabled(true, CORRELATION_ID)

        assertEquals(PolicyMutation.Failed("security_exception"), result)
        assertEquals(listOf("set:true"), service.operations)
    }

    private fun policy(
        validation: DeviceOwnerValidation,
        service: DevicePolicyStatusBarService?,
        operations: MutableList<String>? = null,
    ): DefaultStatusBarPolicy {
        return DefaultStatusBarPolicy(
            deviceOwnerValidationProvider = DeviceOwnerValidationProvider {
                operations?.add("validate")
                validation
            },
            platform = FakePlatform(service, operations),
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
        private val service: DevicePolicyStatusBarService?,
        private val operations: MutableList<String>? = null,
    ) : DevicePolicyPlatform {
        override fun policyService(): DevicePolicyReadService? = null

        override fun statusBarPolicyService(): DevicePolicyStatusBarService? {
            operations?.add("service")
            return service
        }

        override fun isExpectedAdminReceiverRegistered(): Boolean = true
    }

    private class FakeStatusBarService(
        initialDisabled: Boolean,
        private val ignoreWrites: Boolean = false,
        private val setterAccepted: Boolean = true,
        private val setError: Throwable? = null,
        private val getError: Throwable? = null,
        val operations: MutableList<String> = mutableListOf(),
    ) : DevicePolicyStatusBarService {
        private var disabled = initialDisabled

        override fun isStatusBarDisabled(): Boolean {
            operations += "get"
            getError?.let { throw it }
            return disabled
        }

        override fun setStatusBarDisabled(disabled: Boolean): Boolean {
            operations += "set:$disabled"
            setError?.let { throw it }
            if (!setterAccepted) {
                return false
            }
            if (!ignoreWrites) {
                this.disabled = disabled
            }
            return true
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
