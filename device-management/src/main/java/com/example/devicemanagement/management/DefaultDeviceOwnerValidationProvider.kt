package com.example.devicemanagement.management

internal class DefaultDeviceOwnerValidationProvider(
    private val managementStatusProvider: DeviceManagementStatusProvider,
    private val provisioningReadinessProvider: ProvisioningReadinessProvider,
    private val platform: DevicePolicyPlatform,
    private val logger: DeviceManagementLogger,
) : DeviceOwnerValidationProvider {
    override fun currentValidation(): DeviceOwnerValidation {
        val errors = mutableListOf<String>()
        val statusCheck = check("management_status", errors) {
            managementStatusProvider.currentStatus()
        }
        val status = statusCheck.value ?: unavailableManagementStatus()

        val readinessCheck = check("provisioning_readiness", errors) {
            provisioningReadinessProvider.currentReadiness()
        }
        val readiness = readinessCheck.value ?: unavailableProvisioningReadiness(status)

        val configurationCheck = check("admin_component_configuration", errors) {
            platform.adminComponentConfiguration()
        }
        val configuration = configurationCheck.value ?: unavailableConfiguration()

        val serviceCheck = check("validation_policy_service", errors) {
            platform.policyService()
        }
        val service = serviceCheck.value
        if (service == null) {
            logUnavailableQuery("device_owner")
            logUnavailableQuery("profile_owner")
            logUnavailableQuery("expected_admin_active")
            logUnavailableQuery("active_admin_components")
            return DeviceOwnerValidationMapper.map(
                DeviceOwnerValidationSnapshot(
                    configuration = configuration,
                    managementStatus = status,
                    provisioningReadiness = readiness,
                    isPolicyServiceAvailable = false,
                    dpmReportsDeviceOwner = false,
                    dpmReportsProfileOwner = false,
                    isExpectedAdminActive = false,
                    activeAdminComponents = emptySet(),
                    checksReliable = false,
                    errors = errors + "DevicePolicyManager is unavailable.",
                ),
            )
        }

        val deviceOwnerCheck = check("device_owner", errors, service::isDeviceOwnerApp)
        val profileOwnerCheck = check("profile_owner", errors, service::isProfileOwnerApp)
        val activeCheck = check(
            "expected_admin_active",
            errors,
            service::isExpectedAdminActive,
        )
        val activeComponentsCheck = check(
            "active_admin_components",
            errors,
            service::activeAdminComponentNames,
        )

        return DeviceOwnerValidationMapper.map(
            DeviceOwnerValidationSnapshot(
                configuration = configuration,
                managementStatus = status,
                provisioningReadiness = readiness,
                isPolicyServiceAvailable = true,
                dpmReportsDeviceOwner = deviceOwnerCheck.value == true,
                dpmReportsProfileOwner = profileOwnerCheck.value == true,
                isExpectedAdminActive = activeCheck.value == true,
                activeAdminComponents = activeComponentsCheck.value.orEmpty(),
                checksReliable = listOf(
                    statusCheck,
                    readinessCheck,
                    configurationCheck,
                    serviceCheck,
                    deviceOwnerCheck,
                    profileOwnerCheck,
                    activeCheck,
                    activeComponentsCheck,
                ).all { it.success },
                errors = errors,
            ),
        )
    }

    private fun <T> check(
        capability: String,
        errors: MutableList<String>,
        query: () -> T,
    ): CheckResult<T> {
        return try {
            val value = query()
            logger.info(
                event = "device_owner_validation_check",
                fields = mapOf(
                    "capability" to capability,
                    "result" to (value ?: "unavailable"),
                    "success" to true,
                ),
            )
            CheckResult(value = value, success = true)
        } catch (error: Throwable) {
            errors +=
                "$capability check failed: ${error.javaClass.simpleName.ifEmpty { "error" }}"
            logger.error(
                event = "device_owner_validation_check",
                fields = mapOf(
                    "capability" to capability,
                    "result" to false,
                    "success" to false,
                ),
                throwable = error,
            )
            CheckResult(value = null, success = false)
        }
    }

    private fun logUnavailableQuery(capability: String) {
        logger.warn(
            event = "device_owner_validation_check",
            fields = mapOf(
                "capability" to capability,
                "reason" to "policy_service_unavailable",
                "result" to false,
                "success" to false,
            ),
        )
    }

    private fun unavailableManagementStatus() = DeviceManagementStatus(
        mode = ManagementMode.UNAVAILABLE,
        isPolicyServiceAvailable = false,
        isExpectedAdminReceiverRegistered = false,
        isAdminActive = false,
        isDeviceOwner = false,
        isProfileOwner = false,
        availableCapabilities = emptySet(),
        diagnostics = listOf("Management status is unavailable."),
    )

    private fun unavailableProvisioningReadiness(
        status: DeviceManagementStatus,
    ): ProvisioningReadiness {
        val unavailable = ProvisioningOption(
            availability = ProvisioningAvailability.UNAVAILABLE,
            reasons = listOf("Provisioning readiness is unavailable."),
        )
        return ProvisioningReadiness(
            managementStatus = status,
            deviceOwnerProvisioning = unavailable,
            profileOwnerProvisioning = unavailable,
        )
    }

    private fun unavailableConfiguration() = AdminComponentConfiguration(
        packageName = "",
        expectedComponentName = "",
        registeredSentinelAdminComponents = emptyList(),
        isExpectedReceiverRegisteredCorrectly = false,
    )

    private data class CheckResult<T>(
        val value: T?,
        val success: Boolean,
    )
}
