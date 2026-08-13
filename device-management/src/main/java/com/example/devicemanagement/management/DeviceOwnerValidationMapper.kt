package com.example.devicemanagement.management

internal data class DeviceOwnerValidationSnapshot(
    val configuration: AdminComponentConfiguration,
    val managementStatus: DeviceManagementStatus,
    val provisioningReadiness: ProvisioningReadiness,
    val isPolicyServiceAvailable: Boolean,
    val dpmReportsDeviceOwner: Boolean,
    val dpmReportsProfileOwner: Boolean,
    val isExpectedAdminActive: Boolean,
    val activeAdminComponents: Set<String>,
    val checksReliable: Boolean,
    val errors: List<String> = emptyList(),
)

internal object DeviceOwnerValidationMapper {
    fun map(snapshot: DeviceOwnerValidationSnapshot): DeviceOwnerValidation {
        val status = snapshot.managementStatus
        val configuration = snapshot.configuration
        val expectedComponent = configuration.expectedComponentName
        val expectedAppearsActive = expectedComponent in snapshot.activeAdminComponents

        val statusConsistent = when (status.mode) {
            ManagementMode.DEVICE_OWNER -> status.isDeviceOwner && !status.isProfileOwner
            ManagementMode.PROFILE_OWNER -> status.isProfileOwner && !status.isDeviceOwner
            ManagementMode.ORDINARY_APP -> !status.isDeviceOwner && !status.isProfileOwner
            ManagementMode.UNAVAILABLE -> false
        }
        val ownershipQueriesConsistent =
            !(snapshot.dpmReportsDeviceOwner && snapshot.dpmReportsProfileOwner) &&
                snapshot.dpmReportsDeviceOwner == status.isDeviceOwner &&
                snapshot.dpmReportsProfileOwner == status.isProfileOwner
        val activeQueriesConsistent =
            snapshot.isExpectedAdminActive == expectedAppearsActive &&
                status.isAdminActive == snapshot.isExpectedAdminActive
        val readinessConsistent =
            snapshot.provisioningReadiness.managementStatus == status

        val configurationReasons = buildList {
            if (configuration.packageName.isBlank()) {
                add("The application package name is unavailable.")
            }
            if (
                expectedComponent.isBlank() ||
                !expectedComponent.startsWith("${configuration.packageName}/")
            ) {
                add("The expected admin receiver component does not belong to Sentinel.")
            }
            if (!configuration.isExpectedReceiverRegisteredCorrectly) {
                add("The expected Sentinel DeviceAdminReceiver is missing or misconfigured.")
            }
            if (configuration.registeredSentinelAdminComponents.size != 1) {
                add("Sentinel has an ambiguous or duplicate device-admin configuration.")
            } else if (
                configuration.registeredSentinelAdminComponents.single() != expectedComponent
            ) {
                add("The registered Sentinel admin component does not match the expected component.")
            }
        }

        val unavailableReasons = buildList {
            addAll(snapshot.errors)
            if (!snapshot.isPolicyServiceAvailable || !status.isPolicyServiceAvailable) {
                add("DevicePolicyManager is unavailable.")
            }
            if (!snapshot.checksReliable) {
                add("One or more Device Owner validation checks failed.")
            }
            if (!statusConsistent) {
                add("The current management status is unavailable or contradictory.")
            }
            if (!ownershipQueriesConsistent) {
                add("Device Owner and Profile Owner query results are internally inconsistent.")
            }
            if (!activeQueriesConsistent) {
                add("Active-admin query results are internally inconsistent.")
            }
            if (!readinessConsistent) {
                add("Provisioning readiness does not match the current management status.")
            }
        }.distinct()

        val result: DeviceOwnerValidationResult
        val reasons: List<String>
        when {
            unavailableReasons.isNotEmpty() -> {
                result = DeviceOwnerValidationResult.UNAVAILABLE
                reasons = unavailableReasons
            }
            configurationReasons.isNotEmpty() -> {
                result = DeviceOwnerValidationResult.CONFIGURATION_ERROR
                reasons = configurationReasons
            }
            snapshot.dpmReportsProfileOwner -> {
                result = DeviceOwnerValidationResult.NOT_DEVICE_OWNER
                reasons = listOf(
                    "Sentinel is the Profile Owner, not the Device Owner.",
                )
            }
            !snapshot.dpmReportsDeviceOwner -> {
                result = DeviceOwnerValidationResult.NOT_DEVICE_OWNER
                reasons = listOf("DevicePolicyManager does not report Sentinel as Device Owner.")
            }
            !snapshot.isExpectedAdminActive -> {
                result = DeviceOwnerValidationResult.CONFIGURATION_ERROR
                reasons = listOf(
                    "Sentinel is reported as Device Owner, but its expected admin component " +
                        "is not active.",
                )
            }
            else -> {
                result = DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER
                reasons = listOf(
                    "DevicePolicyManager reports Sentinel as Device Owner and the expected " +
                        "Sentinel admin component is registered and active.",
                )
            }
        }

        return DeviceOwnerValidation(
            result = result,
            packageName = configuration.packageName,
            expectedAdminReceiverComponent = expectedComponent,
            registeredSentinelAdminComponents =
                configuration.registeredSentinelAdminComponents.toSet(),
            managementStatus = status,
            provisioningReadiness = snapshot.provisioningReadiness,
            reasons = reasons,
        )
    }
}
