package com.example.devicemanagement.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.devicemanagement.action.ActionResult
import com.example.devicemanagement.app.DeviceManagementApp
import com.example.devicemanagement.management.CameraPolicyStatus
import com.example.devicemanagement.management.DeviceManagementStatus
import com.example.devicemanagement.management.DeviceOwnerValidation
import com.example.devicemanagement.management.DeviceOwnerValidationResult
import com.example.devicemanagement.management.ManagementMode
import com.example.devicemanagement.management.ProvisioningAvailability
import com.example.devicemanagement.management.ProvisioningOption
import com.example.devicemanagement.management.ProvisioningReadiness
import com.example.devicemanagement.management.ScreenCapturePolicyStatus
import com.example.devicemanagement.trigger.SensitiveActionCommands
import com.example.devicemanagement.trigger.Trigger
import java.util.UUID

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as DeviceManagementApp).container
        val validationText = TextView(this)
        val screenCapturePolicyStatusText = TextView(this)
        val cameraPolicyStatusText = TextView(this)
        val operationResultText = TextView(this).apply {
            text = "Operation result: No operation requested.\n" +
                "Failure/denial reason: none\nCorrelation ID: none"
        }

        fun refreshStatus() {
            val validation = container.deviceOwnerValidation.currentValidation()
            validationText.text = validation.toDiagnosticsText()
            screenCapturePolicyStatusText.text = container.screenCapturePolicyStatus
                .currentStatus()
                .toDisplayText(validation.result)
            cameraPolicyStatusText.text = container.cameraPolicyStatus
                .currentStatus()
                .toDisplayText(validation.result)
        }

        fun submit(command: String) {
            val correlationId = UUID.randomUUID().toString()
            val result = container.sensitiveActions.submit(
                Trigger(
                    command = command,
                    requestId = correlationId,
                    expiresAtEpochMillis =
                        System.currentTimeMillis() + REQUEST_LIFETIME_MILLIS,
                ),
            )
            operationResultText.text = result.toDisplayText(correlationId)
            refreshStatus()
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 32, 32, 32)
            addView(validationText)
            addView(TextView(context).apply {
                text = "\nTEST DEVICE — SCREEN CAPTURE POLICY"
                textSize = 20f
            })
            addView(screenCapturePolicyStatusText)
            addView(Button(context).apply {
                text = "Disable screen capture"
                setOnClickListener {
                    submit(SensitiveActionCommands.DISABLE_SCREEN_CAPTURE)
                }
            })
            addView(Button(context).apply {
                text = "Enable screen capture"
                setOnClickListener {
                    submit(SensitiveActionCommands.ENABLE_SCREEN_CAPTURE)
                }
            })
            addView(TextView(context).apply {
                text = "\nTEST DEVICE — CAMERA POLICY"
                textSize = 20f
            })
            addView(cameraPolicyStatusText)
            addView(Button(context).apply {
                text = "Disable camera"
                setOnClickListener {
                    submit(SensitiveActionCommands.DISABLE_CAMERA)
                }
            })
            addView(Button(context).apply {
                text = "Enable camera"
                setOnClickListener {
                    submit(SensitiveActionCommands.ENABLE_CAMERA)
                }
            })
            addView(operationResultText)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        refreshStatus()
    }

    private fun ScreenCapturePolicyStatus.toDisplayText(
        validationResult: DeviceOwnerValidationResult,
    ): String {
        val reason = reasons.ifEmpty { listOf("none") }.joinToString("\n• ")
        return """
            Current screen-capture policy: ${state.name.lowercase()}
            Device Owner verification state: ${validationResult.name}
            Status reason:
            • $reason
        """.trimIndent()
    }

    private fun CameraPolicyStatus.toDisplayText(
        validationResult: DeviceOwnerValidationResult,
    ): String {
        val reason = reasons.ifEmpty { listOf("none") }.joinToString("\n• ")
        return """
            Current camera policy: ${state.name.lowercase()}
            Device Owner verification state: ${validationResult.name}
            Status reason:
            • $reason
        """.trimIndent()
    }

    private fun ActionResult.toDisplayText(fallbackCorrelationId: String): String {
        val result: String
        val reason: String
        val correlationId: String
        when (this) {
            is ActionResult.Applied -> {
                result = "${operation.name}: requested disabled=$requestedDisabled, " +
                    "observed disabled=$observedDisabled"
                reason = "none"
                correlationId = this.correlationId
            }
            is ActionResult.Rejected -> {
                result = "Denied"
                reason = this.reason
                correlationId = this.correlationId ?: fallbackCorrelationId
            }
            is ActionResult.Failed -> {
                result = "Failed"
                reason = this.reason
                correlationId = this.correlationId ?: fallbackCorrelationId
            }
            is ActionResult.Simulated -> {
                result = "Simulation"
                reason = message
                correlationId = fallbackCorrelationId
            }
        }
        return """
            Operation result: $result
            Failure/denial reason: $reason
            Correlation ID: $correlationId
        """.trimIndent()
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

    private companion object {
        const val REQUEST_LIFETIME_MILLIS = 30_000L
    }
}
