package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningReadinessMapperTest {
    @Test
    fun `Device Owner provisioning allowed is reported for ready ordinary app`() {
        val readiness = map(deviceOwnerAllowed = true)

        assertEquals(
            ProvisioningAvailability.ALLOWED,
            readiness.deviceOwnerProvisioning.availability,
        )
        assertTrue(readiness.deviceOwnerProvisioning.isAllowed)
    }

    @Test
    fun `Device Owner provisioning not allowed is reported without assumptions`() {
        val readiness = map(deviceOwnerAllowed = false)

        assertEquals(
            ProvisioningAvailability.NOT_ALLOWED,
            readiness.deviceOwnerProvisioning.availability,
        )
        assertFalse(readiness.deviceOwnerProvisioning.isAllowed)
    }

    @Test
    fun `Profile Owner provisioning allowed is reported for ready ordinary app`() {
        val readiness = map(profileOwnerAllowed = true)

        assertEquals(
            ProvisioningAvailability.ALLOWED,
            readiness.profileOwnerProvisioning.availability,
        )
        assertTrue(readiness.profileOwnerProvisioning.isAllowed)
    }

    @Test
    fun `Profile Owner provisioning not allowed is reported without assumptions`() {
        val readiness = map(profileOwnerAllowed = false)

        assertEquals(
            ProvisioningAvailability.NOT_ALLOWED,
            readiness.profileOwnerProvisioning.availability,
        )
        assertFalse(readiness.profileOwnerProvisioning.isAllowed)
    }

    @Test
    fun `already provisioned Device Owner cannot be provisioned again`() {
        val readiness = map(
            status = readyStatus(
                mode = ManagementMode.DEVICE_OWNER,
                isDeviceOwner = true,
            ),
            deviceOwnerAllowed = true,
            profileOwnerAllowed = true,
        )

        assertEquals(
            ProvisioningAvailability.NOT_ALLOWED,
            readiness.deviceOwnerProvisioning.availability,
        )
        assertEquals(
            ProvisioningAvailability.NOT_ALLOWED,
            readiness.profileOwnerProvisioning.availability,
        )
        assertTrue(
            readiness.deviceOwnerProvisioning.reasons.any { it.contains("already") },
        )
    }

    @Test
    fun `already provisioned Profile Owner cannot be provisioned again`() {
        val readiness = map(
            status = readyStatus(
                mode = ManagementMode.PROFILE_OWNER,
                isProfileOwner = true,
            ),
            deviceOwnerAllowed = true,
            profileOwnerAllowed = true,
        )

        assertEquals(
            ProvisioningAvailability.NOT_ALLOWED,
            readiness.deviceOwnerProvisioning.availability,
        )
        assertEquals(
            ProvisioningAvailability.NOT_ALLOWED,
            readiness.profileOwnerProvisioning.availability,
        )
        assertTrue(
            readiness.profileOwnerProvisioning.reasons.any { it.contains("already") },
        )
    }

    @Test
    fun `unavailable policy service makes both provisioning checks unavailable`() {
        val readiness = map(
            status = readyStatus(
                mode = ManagementMode.UNAVAILABLE,
                isPolicyServiceAvailable = false,
            ),
            deviceOwnerAllowed = true,
            profileOwnerAllowed = true,
        )

        assertBothUnavailable(readiness)
    }

    @Test
    fun `invalid receiver registration fails closed`() {
        val readiness = map(
            status = readyStatus(isExpectedAdminReceiverRegistered = false),
            deviceOwnerAllowed = true,
            profileOwnerAllowed = true,
        )

        assertBothUnavailable(readiness)
        assertTrue(
            readiness.deviceOwnerProvisioning.reasons.any {
                it.contains("not registered correctly")
            },
        )
    }

    @Test
    fun `contradictory management status fails closed`() {
        val readiness = map(
            status = readyStatus(
                mode = ManagementMode.ORDINARY_APP,
                isDeviceOwner = true,
                isProfileOwner = true,
            ),
            deviceOwnerAllowed = true,
            profileOwnerAllowed = true,
        )

        assertBothUnavailable(readiness)
    }

    @Test
    fun `unreliable provisioning query fails closed`() {
        val readiness = map(
            deviceOwnerAllowed = true,
            profileOwnerAllowed = true,
            checksReliable = false,
            errors = listOf("SecurityException"),
        )

        assertBothUnavailable(readiness)
        assertTrue(
            readiness.deviceOwnerProvisioning.reasons.any {
                it.contains("SecurityException")
            },
        )
    }

    private fun map(
        status: DeviceManagementStatus = readyStatus(),
        deviceOwnerAllowed: Boolean = false,
        profileOwnerAllowed: Boolean = false,
        checksReliable: Boolean = true,
        errors: List<String> = emptyList(),
    ): ProvisioningReadiness {
        return ProvisioningReadinessMapper.map(
            ProvisioningCheckSnapshot(
                managementStatus = status,
                isDeviceOwnerProvisioningAllowed = deviceOwnerAllowed,
                isProfileOwnerProvisioningAllowed = profileOwnerAllowed,
                checksReliable = checksReliable,
                errors = errors,
            ),
        )
    }

    private fun readyStatus(
        mode: ManagementMode = ManagementMode.ORDINARY_APP,
        isPolicyServiceAvailable: Boolean = true,
        isExpectedAdminReceiverRegistered: Boolean = true,
        isDeviceOwner: Boolean = false,
        isProfileOwner: Boolean = false,
    ) = DeviceManagementStatus(
        mode = mode,
        isPolicyServiceAvailable = isPolicyServiceAvailable,
        isExpectedAdminReceiverRegistered = isExpectedAdminReceiverRegistered,
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
        assertFalse(readiness.deviceOwnerProvisioning.isAllowed)
        assertFalse(readiness.profileOwnerProvisioning.isAllowed)
    }
}
