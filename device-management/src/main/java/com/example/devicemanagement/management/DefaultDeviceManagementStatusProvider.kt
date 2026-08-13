package com.example.devicemanagement.management

internal class DefaultDeviceManagementStatusProvider(
    private val platform: DevicePolicyPlatform,
    private val logger: DeviceManagementLogger,
) : DeviceManagementStatusProvider {
    override fun currentStatus(): DeviceManagementStatus {
        val errors = mutableListOf<String>()
        val receiverCheck = check(
            capability = "expected_admin_receiver_registered",
            errors = errors,
        ) {
            platform.isExpectedAdminReceiverRegistered()
        }
        val serviceCheck = check(
            capability = "policy_service_available",
            errors = errors,
        ) {
            platform.policyService()
        }
        val service = serviceCheck.value

        if (service == null) {
            logUnavailableQuery("device_owner")
            logUnavailableQuery("profile_owner")
            logUnavailableQuery("expected_admin_active")
            return DeviceManagementStatusMapper.map(
                PolicyCheckSnapshot(
                    isPolicyServiceAvailable = false,
                    isExpectedAdminReceiverRegistered = receiverCheck.value == true,
                    isAdminActive = false,
                    isDeviceOwner = false,
                    isProfileOwner = false,
                    checksReliable = receiverCheck.success && serviceCheck.success,
                    errors = errors,
                ),
            )
        }

        val deviceOwnerCheck = check("device_owner", errors, service::isDeviceOwnerApp)
        val profileOwnerCheck = check("profile_owner", errors, service::isProfileOwnerApp)
        val adminActiveCheck = check(
            "expected_admin_active",
            errors,
            service::isExpectedAdminActive,
        )

        return DeviceManagementStatusMapper.map(
            PolicyCheckSnapshot(
                isPolicyServiceAvailable = true,
                isExpectedAdminReceiverRegistered = receiverCheck.value == true,
                isAdminActive = adminActiveCheck.value == true,
                isDeviceOwner = deviceOwnerCheck.value == true,
                isProfileOwner = profileOwnerCheck.value == true,
                checksReliable = listOf(
                    receiverCheck,
                    serviceCheck,
                    deviceOwnerCheck,
                    profileOwnerCheck,
                    adminActiveCheck,
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
                event = "device_management_capability_check",
                fields = mapOf(
                    "capability" to capability,
                    "result" to (value ?: "unavailable"),
                    "success" to true,
                ),
            )
            CheckResult(value = value, success = true)
        } catch (error: Throwable) {
            val diagnostic =
                "$capability check failed: ${error.javaClass.simpleName.ifEmpty { "error" }}"
            errors += diagnostic
            logger.error(
                event = "device_management_capability_check",
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
            event = "device_management_capability_check",
            fields = mapOf(
                "capability" to capability,
                "reason" to "policy_service_unavailable",
                "result" to false,
                "success" to false,
            ),
        )
    }

    private data class CheckResult<T>(
        val value: T?,
        val success: Boolean,
    )
}
