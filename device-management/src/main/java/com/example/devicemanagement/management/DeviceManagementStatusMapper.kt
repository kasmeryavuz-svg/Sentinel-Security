package com.example.devicemanagement.management

internal data class PolicyCheckSnapshot(
    val isPolicyServiceAvailable: Boolean,
    val isExpectedAdminReceiverRegistered: Boolean,
    val isAdminActive: Boolean,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val checksReliable: Boolean,
    val errors: List<String> = emptyList(),
)

internal object DeviceManagementStatusMapper {
    fun map(snapshot: PolicyCheckSnapshot): DeviceManagementStatus {
        val contradictoryOwnership = snapshot.isDeviceOwner && snapshot.isProfileOwner
        val authorizationReliable =
            snapshot.isPolicyServiceAvailable &&
                snapshot.checksReliable &&
                !contradictoryOwnership

        val isDeviceOwner = authorizationReliable && snapshot.isDeviceOwner
        val isProfileOwner = authorizationReliable && snapshot.isProfileOwner
        val isAdminActive = authorizationReliable && snapshot.isAdminActive

        val mode = when {
            !authorizationReliable -> ManagementMode.UNAVAILABLE
            isDeviceOwner -> ManagementMode.DEVICE_OWNER
            isProfileOwner -> ManagementMode.PROFILE_OWNER
            else -> ManagementMode.ORDINARY_APP
        }

        val capabilities = buildSet {
            if (snapshot.isPolicyServiceAvailable) {
                add(ManagementCapability.POLICY_SERVICE_AVAILABLE)
            }
            if (snapshot.isExpectedAdminReceiverRegistered) {
                add(ManagementCapability.EXPECTED_ADMIN_RECEIVER_REGISTERED)
            }
            if (isAdminActive) {
                add(ManagementCapability.ADMIN_ACTIVE)
            }
            if (isDeviceOwner) {
                add(ManagementCapability.DEVICE_OWNER)
            }
            if (isProfileOwner) {
                add(ManagementCapability.PROFILE_OWNER)
            }
        }

        val diagnostics = buildList {
            addAll(snapshot.errors)
            if (!snapshot.isPolicyServiceAvailable) {
                add("DevicePolicyManager is unavailable.")
            }
            if (!snapshot.isExpectedAdminReceiverRegistered) {
                add("The expected device-admin component is not registered correctly.")
            }
            if (contradictoryOwnership) {
                add("Conflicting owner states were reported; authorization was rejected.")
            }
            if (snapshot.isPolicyServiceAvailable && !snapshot.checksReliable) {
                add("One or more management checks failed; authorization was rejected.")
            }
            when (mode) {
                ManagementMode.DEVICE_OWNER -> add("The app is the Device Owner.")
                ManagementMode.PROFILE_OWNER -> add("The app is the Profile Owner.")
                ManagementMode.ORDINARY_APP -> add("The app is not a device or profile owner.")
                ManagementMode.UNAVAILABLE -> Unit
            }
        }.distinct()

        return DeviceManagementStatus(
            mode = mode,
            isPolicyServiceAvailable = snapshot.isPolicyServiceAvailable,
            isExpectedAdminReceiverRegistered = snapshot.isExpectedAdminReceiverRegistered,
            isAdminActive = isAdminActive,
            isDeviceOwner = isDeviceOwner,
            isProfileOwner = isProfileOwner,
            availableCapabilities = capabilities,
            diagnostics = diagnostics,
        )
    }
}
