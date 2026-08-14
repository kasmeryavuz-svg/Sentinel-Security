package com.example.devicemanagement.ui

import com.example.devicemanagement.trigger.SensitiveActionCommands

/**
 * Explicit mapping from dashboard buttons to the six trusted commands.
 *
 * This is not generic method-name dispatch. Each capability has a fixed
 * enable and disable command constant.
 */
object DashboardCommands {
    fun disableCommand(capability: PolicyCapability): String {
        return when (capability) {
            PolicyCapability.SCREEN_CAPTURE ->
                SensitiveActionCommands.DISABLE_SCREEN_CAPTURE
            PolicyCapability.CAMERA ->
                SensitiveActionCommands.DISABLE_CAMERA
            PolicyCapability.STATUS_BAR ->
                SensitiveActionCommands.DISABLE_STATUS_BAR
        }
    }

    fun enableCommand(capability: PolicyCapability): String {
        return when (capability) {
            PolicyCapability.SCREEN_CAPTURE ->
                SensitiveActionCommands.ENABLE_SCREEN_CAPTURE
            PolicyCapability.CAMERA ->
                SensitiveActionCommands.ENABLE_CAMERA
            PolicyCapability.STATUS_BAR ->
                SensitiveActionCommands.ENABLE_STATUS_BAR
        }
    }

    fun command(capability: PolicyCapability, disable: Boolean): String {
        return if (disable) {
            disableCommand(capability)
        } else {
            enableCommand(capability)
        }
    }

    fun trustedCommands(): Set<String> {
        return setOf(
            SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
            SensitiveActionCommands.ENABLE_SCREEN_CAPTURE,
            SensitiveActionCommands.DISABLE_CAMERA,
            SensitiveActionCommands.ENABLE_CAMERA,
            SensitiveActionCommands.DISABLE_STATUS_BAR,
            SensitiveActionCommands.ENABLE_STATUS_BAR,
        )
    }
}
