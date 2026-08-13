package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceOwnerValidationProviderTest {
    private val packageName = "com.example.devicemanagement"
    private val expectedComponent =
        "$packageName/com.example.devicemanagement.management.SentinelDeviceAdminReceiver"
    private val logger = RecordingLogger()

    @Test
    fun `provider verifies Device Owner with expected active Sentinel component`() {
        val status = deviceOwnerStatus()
        val service = FakeReadService(
            deviceOwner = true,
            adminActive = true,
            activeComponents = setOf(
                expectedComponent,
                "com.unrelated.admin/com.unrelated.admin.Receiver",
            ),
        )

        val validation = provider(status, service).currentValidation()

        assertEquals(
            DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER,
            validation.result,
        )
    }

    @Test
    fun `null DevicePolicyManager fails closed`() {
        val validation = provider(status(), service = null).currentValidation()

        assertEquals(DeviceOwnerValidationResult.UNAVAILABLE, validation.result)
    }

    @Test
    fun `SecurityException fails closed`() {
        val service = object : ReadServiceAdapter() {
            override fun isDeviceOwnerApp(): Boolean {
                throw SecurityException("query denied")
            }
        }

        val validation = provider(status(), service).currentValidation()

        assertEquals(DeviceOwnerValidationResult.UNAVAILABLE, validation.result)
        assertTrue(validation.reasons.any { it.contains("SecurityException") })
    }

    @Test
    fun `admin component inspection failure fails closed`() {
        val platform = object : DevicePolicyPlatform {
            override fun policyService(): DevicePolicyReadService = FakeReadService()

            override fun isExpectedAdminReceiverRegistered(): Boolean = true

            override fun adminComponentConfiguration(): AdminComponentConfiguration {
                error("package manager failure")
            }
        }
        val ordinaryStatus = status()

        val validation = DefaultDeviceOwnerValidationProvider(
            managementStatusProvider = DeviceManagementStatusProvider { ordinaryStatus },
            provisioningReadinessProvider = ProvisioningReadinessProvider {
                readiness(ordinaryStatus)
            },
            platform = platform,
            logger = logger,
        ).currentValidation()

        assertEquals(DeviceOwnerValidationResult.UNAVAILABLE, validation.result)
    }

    @Test
    fun `validation emits structured logs for every direct policy query`() {
        provider(status(), FakeReadService()).currentValidation()

        val capabilities = logger.events
            .filter { it.event == "device_owner_validation_check" }
            .mapNotNull { it.fields["capability"] as? String }
            .toSet()

        assertTrue("device_owner" in capabilities)
        assertTrue("profile_owner" in capabilities)
        assertTrue("expected_admin_active" in capabilities)
        assertTrue("active_admin_components" in capabilities)
        assertTrue("admin_component_configuration" in capabilities)
    }

    private fun provider(
        status: DeviceManagementStatus,
        service: DevicePolicyReadService?,
    ): DefaultDeviceOwnerValidationProvider {
        val platform = object : DevicePolicyPlatform {
            override fun policyService(): DevicePolicyReadService? = service

            override fun isExpectedAdminReceiverRegistered(): Boolean = true

            override fun adminComponentConfiguration(): AdminComponentConfiguration =
                configuration()
        }
        return DefaultDeviceOwnerValidationProvider(
            managementStatusProvider = DeviceManagementStatusProvider { status },
            provisioningReadinessProvider = ProvisioningReadinessProvider {
                readiness(status)
            },
            platform = platform,
            logger = logger,
        )
    }

    private open class ReadServiceAdapter : DevicePolicyReadService {
        override fun isDeviceOwnerApp(): Boolean = false

        override fun isProfileOwnerApp(): Boolean = false

        override fun isExpectedAdminActive(): Boolean = false

        override fun isDeviceOwnerProvisioningAllowed(): Boolean = false

        override fun isProfileOwnerProvisioningAllowed(): Boolean = false

        override fun activeAdminComponentNames(): Set<String> = emptySet()
    }

    private class FakeReadService(
        private val deviceOwner: Boolean = false,
        private val profileOwner: Boolean = false,
        private val adminActive: Boolean = false,
        private val activeComponents: Set<String> = emptySet(),
    ) : ReadServiceAdapter() {
        override fun isDeviceOwnerApp(): Boolean = deviceOwner

        override fun isProfileOwnerApp(): Boolean = profileOwner

        override fun isExpectedAdminActive(): Boolean = adminActive

        override fun activeAdminComponentNames(): Set<String> = activeComponents
    }

    private fun configuration() = AdminComponentConfiguration(
        packageName = packageName,
        expectedComponentName = expectedComponent,
        registeredSentinelAdminComponents = listOf(expectedComponent),
        isExpectedReceiverRegisteredCorrectly = true,
    )

    private fun status() = DeviceManagementStatus(
        mode = ManagementMode.ORDINARY_APP,
        isPolicyServiceAvailable = true,
        isExpectedAdminReceiverRegistered = true,
        isAdminActive = false,
        isDeviceOwner = false,
        isProfileOwner = false,
        availableCapabilities = emptySet(),
        diagnostics = emptyList(),
    )

    private fun deviceOwnerStatus() = DeviceManagementStatus(
        mode = ManagementMode.DEVICE_OWNER,
        isPolicyServiceAvailable = true,
        isExpectedAdminReceiverRegistered = true,
        isAdminActive = true,
        isDeviceOwner = true,
        isProfileOwner = false,
        availableCapabilities = emptySet(),
        diagnostics = emptyList(),
    )

    private fun readiness(status: DeviceManagementStatus): ProvisioningReadiness {
        val option = ProvisioningOption(
            availability = ProvisioningAvailability.NOT_ALLOWED,
            reasons = listOf("Test"),
        )
        return ProvisioningReadiness(
            managementStatus = status,
            deviceOwnerProvisioning = option,
            profileOwnerProvisioning = option,
        )
    }

    private class RecordingLogger : DeviceManagementLogger {
        val events = mutableListOf<LogEvent>()

        override fun info(event: String, fields: Map<String, Any?>) {
            events += LogEvent(event, fields)
        }

        override fun warn(event: String, fields: Map<String, Any?>) {
            events += LogEvent(event, fields)
        }

        override fun error(
            event: String,
            fields: Map<String, Any?>,
            throwable: Throwable?,
        ) {
            events += LogEvent(event, fields)
        }
    }

    private data class LogEvent(
        val event: String,
        val fields: Map<String, Any?>,
    )
}
