package com.example.devicemanagement.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import com.example.devicemanagement.app.DeviceManagementApp
import com.example.devicemanagement.management.DeviceManagementStatus
import com.example.devicemanagement.management.ManagementMode

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = (application as DeviceManagementApp)
            .container
            .deviceManagementStatus
            .currentStatus()

        setContentView(
            TextView(this).apply {
                gravity = Gravity.CENTER
                setPadding(32, 32, 32, 32)
                text = status.toDiagnosticsText()
                textSize = 16f
            },
        )
    }

    private fun DeviceManagementStatus.toDiagnosticsText(): String {
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
            Device Management Diagnostics

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

    private fun Boolean.toYesNo(): String = if (this) "Yes" else "No"
}
