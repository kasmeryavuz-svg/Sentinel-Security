package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceManagementStatusMapperTest {
    @Test
    fun `ordinary app state is mapped distinctly`() {
        val status = DeviceManagementStatusMapper.map(healthySnapshot())

        assertEquals(ManagementMode.ORDINARY_APP, status.mode)
        assertFalse(status.isDeviceOwner)
        assertFalse(status.isProfileOwner)
        assertTrue(
            ManagementCapability.POLICY_SERVICE_AVAILABLE in status.availableCapabilities,
        )
    }

    @Test
    fun `device owner state exposes only device owner capability`() {
        val status = DeviceManagementStatusMapper.map(
            healthySnapshot(isDeviceOwner = true, isAdminActive = true),
        )

        assertEquals(ManagementMode.DEVICE_OWNER, status.mode)
        assertTrue(status.isDeviceOwner)
        assertFalse(status.isProfileOwner)
        assertTrue(ManagementCapability.DEVICE_OWNER in status.availableCapabilities)
        assertFalse(ManagementCapability.PROFILE_OWNER in status.availableCapabilities)
    }

    @Test
    fun `profile owner state exposes only profile owner capability`() {
        val status = DeviceManagementStatusMapper.map(
            healthySnapshot(isProfileOwner = true, isAdminActive = true),
        )

        assertEquals(ManagementMode.PROFILE_OWNER, status.mode)
        assertFalse(status.isDeviceOwner)
        assertTrue(status.isProfileOwner)
        assertFalse(ManagementCapability.DEVICE_OWNER in status.availableCapabilities)
        assertTrue(ManagementCapability.PROFILE_OWNER in status.availableCapabilities)
    }

    @Test
    fun `unavailable policy service fails closed`() {
        val status = DeviceManagementStatusMapper.map(
            healthySnapshot(
                isPolicyServiceAvailable = false,
                isDeviceOwner = true,
                isAdminActive = true,
            ),
        )

        assertEquals(ManagementMode.UNAVAILABLE, status.mode)
        assertFalse(status.isDeviceOwner)
        assertFalse(status.isProfileOwner)
        assertFalse(status.isAdminActive)
        assertFalse(ManagementCapability.DEVICE_OWNER in status.availableCapabilities)
    }

    @Test
    fun `unreliable checks cannot produce an authorized state`() {
        val status = DeviceManagementStatusMapper.map(
            healthySnapshot(
                isDeviceOwner = true,
                isAdminActive = true,
                checksReliable = false,
                errors = listOf("device_owner check failed"),
            ),
        )

        assertEquals(ManagementMode.UNAVAILABLE, status.mode)
        assertFalse(status.isDeviceOwner)
        assertFalse(status.isAdminActive)
        assertTrue(status.diagnostics.any { it.contains("authorization was rejected") })
    }

    @Test
    fun `contradictory owner state fails closed`() {
        val status = DeviceManagementStatusMapper.map(
            healthySnapshot(
                isDeviceOwner = true,
                isProfileOwner = true,
                isAdminActive = true,
            ),
        )

        assertEquals(ManagementMode.UNAVAILABLE, status.mode)
        assertFalse(status.isDeviceOwner)
        assertFalse(status.isProfileOwner)
        assertFalse(status.isAdminActive)
        assertFalse(ManagementCapability.DEVICE_OWNER in status.availableCapabilities)
        assertFalse(ManagementCapability.PROFILE_OWNER in status.availableCapabilities)
    }

    @Test
    fun `missing expected receiver is reported without assuming registration`() {
        val status = DeviceManagementStatusMapper.map(
            healthySnapshot(isExpectedAdminReceiverRegistered = false),
        )

        assertEquals(ManagementMode.ORDINARY_APP, status.mode)
        assertFalse(status.isExpectedAdminReceiverRegistered)
        assertFalse(
            ManagementCapability.EXPECTED_ADMIN_RECEIVER_REGISTERED in
                status.availableCapabilities,
        )
        assertTrue(status.diagnostics.any { it.contains("not registered correctly") })
    }

    private fun healthySnapshot(
        isPolicyServiceAvailable: Boolean = true,
        isExpectedAdminReceiverRegistered: Boolean = true,
        isDeviceOwner: Boolean = false,
        isProfileOwner: Boolean = false,
        isAdminActive: Boolean = false,
        checksReliable: Boolean = true,
        errors: List<String> = emptyList(),
    ) = PolicyCheckSnapshot(
        isPolicyServiceAvailable = isPolicyServiceAvailable,
        isExpectedAdminReceiverRegistered = isExpectedAdminReceiverRegistered,
        isAdminActive = isAdminActive,
        isDeviceOwner = isDeviceOwner,
        isProfileOwner = isProfileOwner,
        checksReliable = checksReliable,
        errors = errors,
    )
}
