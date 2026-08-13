package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceOwnerValidationMapperTest {
    private val packageName = "com.example.devicemanagement"
    private val expectedComponent =
        "$packageName/com.example.devicemanagement.management.SentinelDeviceAdminReceiver"

    @Test
    fun `verified Device Owner allows unrelated active admins`() {
        val status = status(
            mode = ManagementMode.DEVICE_OWNER,
            isDeviceOwner = true,
            isAdminActive = true,
        )
        val validation = map(
            status = status,
            dpmDeviceOwner = true,
            expectedActive = true,
            activeComponents = setOf(
                expectedComponent,
                "com.unrelated.admin/com.unrelated.admin.Receiver",
            ),
        )

        assertEquals(
            DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER,
            validation.result,
        )
    }

    @Test
    fun `ordinary app is not Device Owner`() {
        val validation = map(
            activeComponents = setOf(
                "com.unrelated.admin/com.unrelated.admin.Receiver",
            ),
        )

        assertEquals(DeviceOwnerValidationResult.NOT_DEVICE_OWNER, validation.result)
    }

    @Test
    fun `Profile Owner is not Device Owner`() {
        val status = status(
            mode = ManagementMode.PROFILE_OWNER,
            isProfileOwner = true,
            isAdminActive = true,
        )
        val validation = map(
            status = status,
            dpmProfileOwner = true,
            expectedActive = true,
            activeComponents = setOf(expectedComponent),
        )

        assertEquals(DeviceOwnerValidationResult.NOT_DEVICE_OWNER, validation.result)
        assertTrue(validation.reasons.any { it.contains("Profile Owner") })
    }

    @Test
    fun `incorrect Sentinel admin component is a configuration error`() {
        val validation = map(
            configuration = configuration(
                registeredComponents = listOf(
                    "$packageName/com.example.devicemanagement.management.WrongReceiver",
                ),
            ),
        )

        assertEquals(
            DeviceOwnerValidationResult.CONFIGURATION_ERROR,
            validation.result,
        )
    }

    @Test
    fun `missing Sentinel receiver is a configuration error`() {
        val validation = map(
            configuration = configuration(
                registeredComponents = emptyList(),
                registeredCorrectly = false,
            ),
        )

        assertEquals(
            DeviceOwnerValidationResult.CONFIGURATION_ERROR,
            validation.result,
        )
    }

    @Test
    fun `duplicate Sentinel admin configuration is rejected without considering other packages`() {
        val validation = map(
            configuration = configuration(
                registeredComponents = listOf(
                    expectedComponent,
                    "$packageName/com.example.devicemanagement.management.SecondReceiver",
                ),
            ),
        )

        assertEquals(
            DeviceOwnerValidationResult.CONFIGURATION_ERROR,
            validation.result,
        )
        assertTrue(validation.reasons.any { it.contains("ambiguous or duplicate") })
    }

    @Test
    fun `unavailable DevicePolicyManager fails closed`() {
        val unavailableStatus = status(
            mode = ManagementMode.UNAVAILABLE,
            serviceAvailable = false,
        )
        val validation = map(
            status = unavailableStatus,
            serviceAvailable = false,
            checksReliable = false,
        )

        assertEquals(DeviceOwnerValidationResult.UNAVAILABLE, validation.result)
    }

    @Test
    fun `contradictory ownership state fails closed`() {
        val contradictoryStatus = status(
            mode = ManagementMode.UNAVAILABLE,
            isDeviceOwner = false,
            isProfileOwner = false,
        )
        val validation = map(
            status = contradictoryStatus,
            dpmDeviceOwner = true,
            dpmProfileOwner = true,
        )

        assertEquals(DeviceOwnerValidationResult.UNAVAILABLE, validation.result)
    }

    @Test
    fun `mismatched repeated ownership query fails closed`() {
        val validation = map(dpmDeviceOwner = true)

        assertEquals(DeviceOwnerValidationResult.UNAVAILABLE, validation.result)
    }

    @Test
    fun `Device Owner with inactive expected component is a configuration error`() {
        val deviceOwnerStatus = status(
            mode = ManagementMode.DEVICE_OWNER,
            isDeviceOwner = true,
            isAdminActive = false,
        )
        val validation = map(
            status = deviceOwnerStatus,
            dpmDeviceOwner = true,
            expectedActive = false,
            activeComponents = emptySet(),
        )

        assertEquals(
            DeviceOwnerValidationResult.CONFIGURATION_ERROR,
            validation.result,
        )
    }

    private fun map(
        configuration: AdminComponentConfiguration = configuration(),
        status: DeviceManagementStatus = status(),
        serviceAvailable: Boolean = true,
        dpmDeviceOwner: Boolean = false,
        dpmProfileOwner: Boolean = false,
        expectedActive: Boolean = false,
        activeComponents: Set<String> = emptySet(),
        checksReliable: Boolean = true,
        errors: List<String> = emptyList(),
    ): DeviceOwnerValidation {
        return DeviceOwnerValidationMapper.map(
            DeviceOwnerValidationSnapshot(
                configuration = configuration,
                managementStatus = status,
                provisioningReadiness = readiness(status),
                isPolicyServiceAvailable = serviceAvailable,
                dpmReportsDeviceOwner = dpmDeviceOwner,
                dpmReportsProfileOwner = dpmProfileOwner,
                isExpectedAdminActive = expectedActive,
                activeAdminComponents = activeComponents,
                checksReliable = checksReliable,
                errors = errors,
            ),
        )
    }

    private fun configuration(
        registeredComponents: List<String> = listOf(expectedComponent),
        registeredCorrectly: Boolean = true,
    ) = AdminComponentConfiguration(
        packageName = packageName,
        expectedComponentName = expectedComponent,
        registeredSentinelAdminComponents = registeredComponents,
        isExpectedReceiverRegisteredCorrectly = registeredCorrectly,
    )

    private fun status(
        mode: ManagementMode = ManagementMode.ORDINARY_APP,
        serviceAvailable: Boolean = true,
        isDeviceOwner: Boolean = false,
        isProfileOwner: Boolean = false,
        isAdminActive: Boolean = false,
    ) = DeviceManagementStatus(
        mode = mode,
        isPolicyServiceAvailable = serviceAvailable,
        isExpectedAdminReceiverRegistered = true,
        isAdminActive = isAdminActive,
        isDeviceOwner = isDeviceOwner,
        isProfileOwner = isProfileOwner,
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
}
