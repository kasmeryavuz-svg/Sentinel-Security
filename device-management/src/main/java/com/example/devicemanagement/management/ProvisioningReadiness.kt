package com.example.devicemanagement.management

enum class ProvisioningAvailability {
    ALLOWED,
    NOT_ALLOWED,
    UNAVAILABLE,
}

data class ProvisioningOption(
    val availability: ProvisioningAvailability,
    val reasons: List<String>,
) {
    val isAllowed: Boolean
        get() = availability == ProvisioningAvailability.ALLOWED
}

data class ProvisioningReadiness(
    val managementStatus: DeviceManagementStatus,
    val deviceOwnerProvisioning: ProvisioningOption,
    val profileOwnerProvisioning: ProvisioningOption,
)

fun interface ProvisioningReadinessProvider {
    fun currentReadiness(): ProvisioningReadiness
}
