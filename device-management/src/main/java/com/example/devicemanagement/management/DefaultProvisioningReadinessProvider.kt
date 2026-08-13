package com.example.devicemanagement.management

internal class DefaultProvisioningReadinessProvider(
    private val managementStatusProvider: DeviceManagementStatusProvider,
    private val platform: DevicePolicyPlatform,
    private val logger: DeviceManagementLogger,
) : ProvisioningReadinessProvider {
    override fun currentReadiness(): ProvisioningReadiness {
        val status = try {
            managementStatusProvider.currentStatus()
        } catch (error: Throwable) {
            logger.error(
                event = "provisioning_readiness_check",
                fields = mapOf(
                    "capability" to "management_status",
                    "result" to false,
                    "success" to false,
                ),
                throwable = error,
            )
            return mapWithoutQueries(
                status = unavailableManagementStatus(),
                checksReliable = false,
                errors = listOf(
                    "Management status check failed: " +
                        (error::class.simpleName ?: "error"),
                ),
            )
        }

        if (status.mode == ManagementMode.DEVICE_OWNER) {
            logNotQueried("device_owner_provisioning", "already_device_owner")
            logNotQueried("profile_owner_provisioning", "already_device_owner")
            return mapWithoutQueries(status, checksReliable = true)
        }
        if (status.mode == ManagementMode.PROFILE_OWNER) {
            logNotQueried("device_owner_provisioning", "already_profile_owner")
            logNotQueried("profile_owner_provisioning", "already_profile_owner")
            return mapWithoutQueries(status, checksReliable = true)
        }
        if (
            status.mode == ManagementMode.UNAVAILABLE ||
            !status.isPolicyServiceAvailable ||
            !status.isExpectedAdminReceiverRegistered
        ) {
            logNotQueried("device_owner_provisioning", "management_status_unavailable")
            logNotQueried("profile_owner_provisioning", "management_status_unavailable")
            return mapWithoutQueries(
                status = status,
                checksReliable = false,
                errors = status.diagnostics,
            )
        }

        val errors = mutableListOf<String>()
        val serviceCheck = check(
            capability = "provisioning_policy_service",
            errors = errors,
        ) {
            platform.policyService()
        }
        val service = serviceCheck.value
        if (service == null) {
            logNotQueried("device_owner_provisioning", "policy_service_unavailable")
            logNotQueried("profile_owner_provisioning", "policy_service_unavailable")
            return mapWithoutQueries(
                status = status,
                checksReliable = false,
                errors = errors + "DevicePolicyManager is unavailable.",
            )
        }

        val deviceOwnerCheck = check(
            capability = "device_owner_provisioning",
            errors = errors,
            query = service::isDeviceOwnerProvisioningAllowed,
        )
        val profileOwnerCheck = check(
            capability = "profile_owner_provisioning",
            errors = errors,
            query = service::isProfileOwnerProvisioningAllowed,
        )

        return ProvisioningReadinessMapper.map(
            ProvisioningCheckSnapshot(
                managementStatus = status,
                isDeviceOwnerProvisioningAllowed = deviceOwnerCheck.value == true,
                isProfileOwnerProvisioningAllowed = profileOwnerCheck.value == true,
                checksReliable = listOf(
                    serviceCheck,
                    deviceOwnerCheck,
                    profileOwnerCheck,
                ).all { it.success },
                errors = errors,
            ),
        )
    }

    private fun mapWithoutQueries(
        status: DeviceManagementStatus,
        checksReliable: Boolean,
        errors: List<String> = emptyList(),
    ): ProvisioningReadiness {
        return ProvisioningReadinessMapper.map(
            ProvisioningCheckSnapshot(
                managementStatus = status,
                isDeviceOwnerProvisioningAllowed = false,
                isProfileOwnerProvisioningAllowed = false,
                checksReliable = checksReliable,
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
                event = "provisioning_readiness_check",
                fields = mapOf(
                    "capability" to capability,
                    "result" to (value ?: "unavailable"),
                    "success" to true,
                ),
            )
            CheckResult(value = value, success = true)
        } catch (error: Throwable) {
            errors += "$capability check failed: ${error::class.simpleName ?: "error"}"
            logger.error(
                event = "provisioning_readiness_check",
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

    private fun logNotQueried(capability: String, reason: String) {
        logger.warn(
            event = "provisioning_readiness_check",
            fields = mapOf(
                "capability" to capability,
                "queried" to false,
                "reason" to reason,
                "result" to false,
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

    private data class CheckResult<T>(
        val value: T?,
        val success: Boolean,
    )
}
