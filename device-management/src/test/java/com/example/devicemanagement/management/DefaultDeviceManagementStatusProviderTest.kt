package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceManagementStatusProviderTest {
    private val logger = RecordingLogger()

    @Test
    fun `null DevicePolicyManager reports unavailable and not authorized`() {
        val provider = provider(service = null)

        val status = provider.currentStatus()

        assertEquals(ManagementMode.UNAVAILABLE, status.mode)
        assertFalse(status.isPolicyServiceAvailable)
        assertFalse(status.isDeviceOwner)
        assertFalse(status.isProfileOwner)
        assertFalse(status.isAdminActive)
    }

    @Test
    fun `fake ordinary app is detected`() {
        val status = provider(service = FakeReadService()).currentStatus()

        assertEquals(ManagementMode.ORDINARY_APP, status.mode)
        assertFalse(status.isDeviceOwner)
        assertFalse(status.isProfileOwner)
    }

    @Test
    fun `fake device owner is detected`() {
        val status = provider(
            service = FakeReadService(deviceOwner = true, adminActive = true),
        ).currentStatus()

        assertEquals(ManagementMode.DEVICE_OWNER, status.mode)
        assertTrue(status.isDeviceOwner)
        assertFalse(status.isProfileOwner)
    }

    @Test
    fun `fake profile owner is detected`() {
        val status = provider(
            service = FakeReadService(profileOwner = true, adminActive = true),
        ).currentStatus()

        assertEquals(ManagementMode.PROFILE_OWNER, status.mode)
        assertFalse(status.isDeviceOwner)
        assertTrue(status.isProfileOwner)
    }

    @Test
    fun `query exception fails closed even when another query reports owner`() {
        val service = object : DevicePolicyReadService {
            override fun isDeviceOwnerApp(): Boolean = error("service failure")

            override fun isProfileOwnerApp(): Boolean = true

            override fun isExpectedAdminActive(): Boolean = true
        }

        val status = provider(service = service).currentStatus()

        assertEquals(ManagementMode.UNAVAILABLE, status.mode)
        assertFalse(status.isDeviceOwner)
        assertFalse(status.isProfileOwner)
        assertFalse(status.isAdminActive)
    }

    @Test
    fun `receiver lookup exception fails closed`() {
        val platform = object : DevicePolicyPlatform {
            override fun policyService(): DevicePolicyReadService = FakeReadService(
                deviceOwner = true,
                adminActive = true,
            )

            override fun isExpectedAdminReceiverRegistered(): Boolean {
                error("package manager failure")
            }
        }

        val status = DefaultDeviceManagementStatusProvider(platform, logger).currentStatus()

        assertEquals(ManagementMode.UNAVAILABLE, status.mode)
        assertFalse(status.isExpectedAdminReceiverRegistered)
        assertFalse(status.isDeviceOwner)
    }

    @Test
    fun `every capability check emits structured logging`() {
        provider(service = FakeReadService()).currentStatus()

        val checkedCapabilities = logger.events
            .filter { it.event == "device_management_capability_check" }
            .mapNotNull { it.fields["capability"] as? String }
            .toSet()

        assertEquals(
            setOf(
                "expected_admin_receiver_registered",
                "policy_service_available",
                "device_owner",
                "profile_owner",
                "expected_admin_active",
            ),
            checkedCapabilities,
        )
    }

    private fun provider(
        service: DevicePolicyReadService?,
    ): DefaultDeviceManagementStatusProvider {
        val platform = object : DevicePolicyPlatform {
            override fun policyService(): DevicePolicyReadService? = service

            override fun isExpectedAdminReceiverRegistered(): Boolean = true
        }
        return DefaultDeviceManagementStatusProvider(platform, logger)
    }

    private data class FakeReadService(
        val deviceOwner: Boolean = false,
        val profileOwner: Boolean = false,
        val adminActive: Boolean = false,
    ) : DevicePolicyReadService {
        override fun isDeviceOwnerApp(): Boolean = deviceOwner

        override fun isProfileOwnerApp(): Boolean = profileOwner

        override fun isExpectedAdminActive(): Boolean = adminActive
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
