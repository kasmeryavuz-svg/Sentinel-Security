package com.example.devicemanagement.management

enum class DeviceOwnerValidationResult {
    VERIFIED_DEVICE_OWNER,
    NOT_DEVICE_OWNER,
    CONFIGURATION_ERROR,
    UNAVAILABLE,
}

data class DeviceOwnerValidation(
    val result: DeviceOwnerValidationResult,
    val packageName: String,
    val expectedAdminReceiverComponent: String,
    val registeredSentinelAdminComponents: Set<String>,
    val managementStatus: DeviceManagementStatus,
    val provisioningReadiness: ProvisioningReadiness,
    val reasons: List<String>,
)

fun interface DeviceOwnerValidationProvider {
    fun currentValidation(): DeviceOwnerValidation
}
