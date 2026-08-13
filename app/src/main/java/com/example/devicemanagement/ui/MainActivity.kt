package com.example.devicemanagement.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import com.example.devicemanagement.app.DeviceManagementApp
import com.example.devicemanagement.management.DeviceManagementStatus
import com.example.devicemanagement.management.DeviceOwnerValidation
import com.example.devicemanagement.management.DeviceOwnerValidationResult
import com.example.devicemanagement.management.ManagementMode
import com.example.devicemanagement.management.ProvisioningAvailability
import com.example.devicemanagement.management.ProvisioningOption
import com.example.devicemanagement.management.ProvisioningReadiness

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val validation = (application as DeviceManagementApp)
            .container
            .deviceOwnerValidation
            .currentValidation()

        setContentView(
            TextView(this).apply {
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
                text = validation.toDiagnosticsText()
                textSize = 16f
            },
        )
    }

    private fun DeviceOwnerValidation.toDiagnosticsText(): String {
        val resultLabel = when (result) {
            DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER -> "Verified Device Owner"
            DeviceOwnerValidationResult.NOT_DEVICE_OWNER -> "Not Device Owner"
            DeviceOwnerValidationResult.CONFIGURATION_ERROR -> "Configuration error"
            DeviceOwnerValidationResult.UNAVAILABLE -> "Unavailable"
        }
        val registeredComponents = registeredSentinelAdminComponents
            .sorted()
            .ifEmpty { listOf("none") }
            .joinToString()
        val validationReasons = reasons.joinToString(
            separator = "\n• ",
            prefix = "• ",
        )

        return """
            TEST-DEVICE DEVICE OWNER VALIDATION

            Package: $packageName
            Expected admin receiver: $expectedAdminReceiverComponent
            Registered Sentinel admin components: $registeredComponents
            Device Owner verification: $resultLabel
            Profile Owner: ${managementStatus.isProfileOwner.toYesNo()}

            ${provisioningReadiness.toDiagnosticsText()}

            Validation details:
            $validationReasons
        """.trimIndent()
    }

    private fun ProvisioningReadiness.toDiagnosticsText(): String {
        val status = managementStatus
        return """
            ${status.toManagementDiagnosticsText()}

            Device Owner provisioning:
            ${deviceOwnerProvisioning.toDisplayText()}

            Profile Owner provisioning:
            ${profileOwnerProvisioning.toDisplayText()}
        """.trimIndent()
    }

    private fun DeviceManagementStatus.toManagementDiagnosticsText(): String {
        val modeLabel = when (mode) {
            ManagementMode.DEVICE_OWNER -> "Device Owner"
            ManagementMode.PROFILE_OWNER -> "Profile Owner"
            ManagementMode.ORDINARY_APP -> "Ordinary app"
            ManagementMode.UNAVAILABLE -> "Unavailable / not authorized"
        }
        val capabilities = availableCapabilities
            .map { it.name.replace('_', ' ').lowercase() }
            .sorted()
            .ifEmpty { listOf("none") }
            .joinToString(separator = "\n• ", prefix = "• ")
        val diagnosticText = diagnostics
            .ifEmpty { listOf("No additional diagnostics.") }
            .joinToString("\n")

        return """
            Provisioning Readiness Diagnostics

            Management state: $modeLabel
            DevicePolicyManager available: ${isPolicyServiceAvailable.toYesNo()}
            Expected admin receiver registered: ${isExpectedAdminReceiverRegistered.toYesNo()}
            Expected admin active: ${isAdminActive.toYesNo()}
            Device Owner: ${isDeviceOwner.toYesNo()}
            Profile Owner: ${isProfileOwner.toYesNo()}

            Available capabilities:
            $capabilities

            $diagnosticText
        """.trimIndent()
    }

    private fun ProvisioningOption.toDisplayText(): String {
        val availabilityLabel = when (availability) {
            ProvisioningAvailability.ALLOWED -> "Allowed"
            ProvisioningAvailability.NOT_ALLOWED -> "Not allowed"
            ProvisioningAvailability.UNAVAILABLE -> "Unavailable"
        }
        return buildString {
            append(availabilityLabel)
            reasons.forEach { reason ->
                append("\n• ")
                append(reason)
            }
        }
    }

    private fun Boolean.toYesNo(): String = if (this) "Yes" else "No"
}
