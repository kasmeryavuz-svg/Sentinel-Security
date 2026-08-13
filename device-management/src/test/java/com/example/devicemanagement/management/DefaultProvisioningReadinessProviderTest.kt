package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvisioningReadinessProviderTest {
    private val logger = RecordingLogger()

    @Test
    fun `fake service reports independent Device Owner and Profile Owner availability`() {
        val readiness = provider(
            service = FakeReadService(
                deviceOwnerProvisioningAllowed = true,
                profileOwnerProvisioningAllowed = false,
            ),
        ).currentReadiness()

        assertEquals(
            ProvisioningAvailability.ALLOWED,
            readiness.deviceOwnerProvisioning.availability,
        )
        assertEquals(
            ProvisioningAvailability.NOT_ALLOWED,
            readiness.profileOwnerProvisioning.availability,
        )
    }

    @Test
    fun `null DevicePolicyManager fails closed`() {
        val readiness = provider(service = null).currentReadiness()

        assertBothUnavailable(readiness)
    }

    @Test
    fun `SecurityException fails closed`() {
        val service = object : ReadServiceAdapter() {
            override fun isDeviceOwnerProvisioningAllowed(): Boolean {
                throw SecurityException("query denied")
            }

            override fun isProfileOwnerProvisioningAllowed(): Boolean = true
        }

        val readiness = provider(service = service).currentReadiness()

        assertBothUnavailable(readiness)
        assertTrue(
            readiness.deviceOwnerProvisioning.reasons.any {
                it.contains("SecurityException")
            },
        )
    }

    @Test
    fun `unsupported API fails closed`() {
        val service = object : ReadServiceAdapter() {
            override fun isDeviceOwnerProvisioningAllowed(): Boolean {
                throw UnsupportedOperationException("unsupported")
            }

            override fun isProfileOwnerProvisioningAllowed(): Boolean = true
        }

        val readiness = provider(service = service).currentReadiness()

        assertBothUnavailable(readiness)
    }

    @Test
    fun `other query failure fails closed`() {
        val service = object : ReadServiceAdapter() {
            override fun isDeviceOwnerProvisioningAllowed(): Boolean = true

            override fun isProfileOwnerProvisioningAllowed(): Boolean {
                error("unexpected failure")
            }
        }

        val readiness = provider(service = service).currentReadiness()

        assertBothUnavailable(readiness)
    }

    @Test
    fun `management status failure fails closed`() {
        val readiness = DefaultProvisioningReadinessProvider(
            managementStatusProvider = DeviceManagementStatusProvider {
                error("status failure")
            },
            platform = ThrowIfAccessedPlatform(),
            logger = logger,
        ).currentReadiness()

        assertBothUnavailable(readiness)
        assertEquals(ManagementMode.UNAVAILABLE, readiness.managementStatus.mode)
    }

    @Test
    fun `invalid receiver registration prevents provisioning queries`() {
        val status = readyStatus().copy(
            isExpectedAdminReceiverRegistered = false,
        )
        val platform = ThrowIfAccessedPlatform()

        val readiness = DefaultProvisioningReadinessProvider(
            managementStatusProvider = DeviceManagementStatusProvider { status },
            platform = platform,
            logger = logger,
        ).currentReadiness()

        assertBothUnavailable(readiness)
        assertFalse(platform.wasAccessed)
    }

    @Test
    fun `already Device Owner does not query provisioning service`() {
        val status = readyStatus(
            mode = ManagementMode.DEVICE_OWNER,
            isDeviceOwner = true,
        )
        val platform = ThrowIfAccessedPlatform()

        val readiness = DefaultProvisioningReadinessProvider(
            managementStatusProvider = DeviceManagementStatusProvider { status },
            platform = platform,
            logger = logger,
        ).currentReadiness()

        assertBothNotAllowed(readiness)
        assertFalse(platform.wasAccessed)
    }

    @Test
    fun `already Profile Owner does not query provisioning service`() {
        val status = readyStatus(
            mode = ManagementMode.PROFILE_OWNER,
            isProfileOwner = true,
        )
        val platform = ThrowIfAccessedPlatform()

        val readiness = DefaultProvisioningReadinessProvider(
            managementStatusProvider = DeviceManagementStatusProvider { status },
            platform = platform,
            logger = logger,
        ).currentReadiness()

        assertBothNotAllowed(readiness)
        assertFalse(platform.wasAccessed)
    }

    @Test
    fun `provisioning checks emit structured logs`() {
        provider(service = FakeReadService()).currentReadiness()

        val capabilities = logger.events
            .filter { it.event == "provisioning_readiness_check" }
            .mapNotNull { it.fields["capability"] as? String }
            .toSet()

        assertTrue("device_owner_provisioning" in capabilities)
        assertTrue("profile_owner_provisioning" in capabilities)
        assertTrue("provisioning_policy_service" in capabilities)
    }

    private fun provider(
        service: DevicePolicyReadService?,
        status: DeviceManagementStatus = readyStatus(),
    ): DefaultProvisioningReadinessProvider {
        val platform = object : DevicePolicyPlatform {
            override fun policyService(): DevicePolicyReadService? = service

            override fun isExpectedAdminReceiverRegistered(): Boolean = true
        }
        return DefaultProvisioningReadinessProvider(
            managementStatusProvider = DeviceManagementStatusProvider { status },
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
    }

    private class FakeReadService(
        private val deviceOwnerProvisioningAllowed: Boolean = false,
        private val profileOwnerProvisioningAllowed: Boolean = false,
    ) : ReadServiceAdapter() {
        override fun isDeviceOwnerProvisioningAllowed(): Boolean =
            deviceOwnerProvisioningAllowed

        override fun isProfileOwnerProvisioningAllowed(): Boolean =
            profileOwnerProvisioningAllowed
    }

    private class ThrowIfAccessedPlatform : DevicePolicyPlatform {
        var wasAccessed = false

        override fun policyService(): DevicePolicyReadService {
            wasAccessed = true
            error("Provisioning service should not be queried")
        }

        override fun isExpectedAdminReceiverRegistered(): Boolean = true
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

    private fun readyStatus(
        mode: ManagementMode = ManagementMode.ORDINARY_APP,
        isDeviceOwner: Boolean = false,
        isProfileOwner: Boolean = false,
    ) = DeviceManagementStatus(
        mode = mode,
        isPolicyServiceAvailable = true,
        isExpectedAdminReceiverRegistered = true,
        isAdminActive = isDeviceOwner || isProfileOwner,
        isDeviceOwner = isDeviceOwner,
        isProfileOwner = isProfileOwner,
        availableCapabilities = emptySet(),
        diagnostics = emptyList(),
    )

    private fun assertBothUnavailable(readiness: ProvisioningReadiness) {
        assertEquals(
            ProvisioningAvailability.UNAVAILABLE,
            readiness.deviceOwnerProvisioning.availability,
        )
        assertEquals(
            ProvisioningAvailability.UNAVAILABLE,
            readiness.profileOwnerProvisioning.availability,
        )
    }

    private fun assertBothNotAllowed(readiness: ProvisioningReadiness) {
        assertEquals(
            ProvisioningAvailability.NOT_ALLOWED,
            readiness.deviceOwnerProvisioning.availability,
        )
        assertEquals(
            ProvisioningAvailability.NOT_ALLOWED,
            readiness.profileOwnerProvisioning.availability,
        )
    }
}
