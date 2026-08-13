package com.example.devicemanagement.management

enum class ManagementMode {
    DEVICE_OWNER,
    PROFILE_OWNER,
    ORDINARY_APP,
    UNAVAILABLE,
}

enum class ManagementCapability {
    POLICY_SERVICE_AVAILABLE,
    EXPECTED_ADMIN_RECEIVER_REGISTERED,
    ADMIN_ACTIVE,
    DEVICE_OWNER,
    PROFILE_OWNER,
}

data class DeviceManagementStatus(
    val mode: ManagementMode,
    val isPolicyServiceAvailable: Boolean,
    val isExpectedAdminReceiverRegistered: Boolean,
    val isAdminActive: Boolean,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val availableCapabilities: Set<ManagementCapability>,
    val diagnostics: List<String>,
)

fun interface DeviceManagementStatusProvider {
    fun currentStatus(): DeviceManagementStatus
}
