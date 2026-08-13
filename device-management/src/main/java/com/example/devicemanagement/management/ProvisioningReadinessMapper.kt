package com.example.devicemanagement.management

internal data class ProvisioningCheckSnapshot(
    val managementStatus: DeviceManagementStatus,
    val isDeviceOwnerProvisioningAllowed: Boolean,
    val isProfileOwnerProvisioningAllowed: Boolean,
    val checksReliable: Boolean,
    val errors: List<String> = emptyList(),
)

internal object ProvisioningReadinessMapper {
    fun map(snapshot: ProvisioningCheckSnapshot): ProvisioningReadiness {
        val status = snapshot.managementStatus
        val statusConsistent = when (status.mode) {
            ManagementMode.DEVICE_OWNER -> status.isDeviceOwner && !status.isProfileOwner
            ManagementMode.PROFILE_OWNER -> status.isProfileOwner && !status.isDeviceOwner
            ManagementMode.ORDINARY_APP -> !status.isDeviceOwner && !status.isProfileOwner
            ManagementMode.UNAVAILABLE -> false
        }

        val sharedUnavailableReasons = buildList {
            addAll(snapshot.errors)
            if (!status.isPolicyServiceAvailable) {
                add("DevicePolicyManager is unavailable.")
            }
            if (!status.isExpectedAdminReceiverRegistered) {
                add("The expected device-admin receiver is not registered correctly.")
            }
            if (!statusConsistent) {
                add("The current management state is unavailable or contradictory.")
            }
            if (!snapshot.checksReliable) {
                add("Provisioning availability checks did not complete reliably.")
            }
        }.distinct()

        val baseReady =
            status.isPolicyServiceAvailable &&
                status.isExpectedAdminReceiverRegistered &&
                statusConsistent &&
                snapshot.checksReliable

        val deviceOwnerOption = when {
            !baseReady -> unavailable(sharedUnavailableReasons)
            status.mode == ManagementMode.DEVICE_OWNER -> notAllowed(
                "The app is already the Device Owner.",
            )
            status.mode == ManagementMode.PROFILE_OWNER -> notAllowed(
                "The app is already the Profile Owner; Device Owner provisioning is unavailable.",
            )
            snapshot.isDeviceOwnerProvisioningAllowed -> allowed(
                "Android reports that Device Owner provisioning is currently allowed. " +
                    "No provisioning has been started.",
            )
            else -> notAllowed(
                "Android reports that Device Owner provisioning is not currently allowed.",
            )
        }

        val profileOwnerOption = when {
            !baseReady -> unavailable(sharedUnavailableReasons)
            status.mode == ManagementMode.PROFILE_OWNER -> notAllowed(
                "The app is already the Profile Owner.",
            )
            status.mode == ManagementMode.DEVICE_OWNER -> notAllowed(
                "The app is already the Device Owner; Profile Owner provisioning is unavailable.",
            )
            snapshot.isProfileOwnerProvisioningAllowed -> allowed(
                "Android reports that Profile Owner provisioning is currently allowed. " +
                    "No provisioning has been started.",
            )
            else -> notAllowed(
                "Android reports that Profile Owner provisioning is not currently allowed.",
            )
        }

        return ProvisioningReadiness(
            managementStatus = status,
            deviceOwnerProvisioning = deviceOwnerOption,
            profileOwnerProvisioning = profileOwnerOption,
        )
    }

    private fun allowed(reason: String) = ProvisioningOption(
        availability = ProvisioningAvailability.ALLOWED,
        reasons = listOf(reason),
    )

    private fun notAllowed(reason: String) = ProvisioningOption(
        availability = ProvisioningAvailability.NOT_ALLOWED,
        reasons = listOf(reason),
    )

    private fun unavailable(reasons: List<String>) = ProvisioningOption(
        availability = ProvisioningAvailability.UNAVAILABLE,
        reasons = reasons.ifEmpty {
            listOf("Provisioning readiness is unavailable.")
        },
    )
}
