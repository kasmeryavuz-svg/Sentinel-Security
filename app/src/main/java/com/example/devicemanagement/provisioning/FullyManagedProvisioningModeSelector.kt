package com.example.devicemanagement.provisioning

/**
 * Public Android 12+ fully-managed provisioning contract constants.
 *
 * Integer values and extra names match the platform provisioning contract
 * without importing policy-manager types into app or UI code.
 *
 * Fully-managed Device Owner mode = 1
 * Managed-profile mode = 2
 */
object FullyManagedProvisioningContract {
    const val ACTION_GET_PROVISIONING_MODE = "android.app.action.GET_PROVISIONING_MODE"
    const val ACTION_ADMIN_POLICY_COMPLIANCE = "android.app.action.ADMIN_POLICY_COMPLIANCE"
    const val EXTRA_ALLOWED_PROVISIONING_MODES =
        "android.app.extra.PROVISIONING_ALLOWED_PROVISIONING_MODES"
    const val EXTRA_PROVISIONING_MODE = "android.app.extra.PROVISIONING_MODE"
    const val MODE_FULLY_MANAGED_DEVICE = 1
    const val MODE_MANAGED_PROFILE = 2
    const val EXPECTED_ADMIN_COMPONENT =
        "com.example.devicemanagement/.management.SentinelDeviceAdminReceiver"
}

sealed class ProvisioningModeSelection {
    data class Selected(val mode: Int) : ProvisioningModeSelection()
    data class Rejected(val reason: String) : ProvisioningModeSelection()
}

/**
 * Accepts only a platform ArrayList of integer provisioning modes.
 *
 * Any other extra type is treated as missing so the selector fails closed.
 */
object ProvisioningAllowedModesParser {
    fun parse(raw: Any?): List<Int>? {
        if (raw == null) {
            return null
        }
        if (raw !is ArrayList<*>) {
            return null
        }
        if (raw.any { value -> value !is Int }) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return raw as List<Int>
    }
}

/**
 * Selects only fully-managed Device Owner provisioning.
 *
 * Managed-profile modes are never chosen. Missing, empty, or otherwise
 * unusable allowed-mode lists fail closed.
 */
object FullyManagedProvisioningModeSelector {
    fun select(allowedModes: List<Int>?): ProvisioningModeSelection {
        if (allowedModes == null) {
            return ProvisioningModeSelection.Rejected("allowed_modes_missing")
        }
        if (allowedModes.isEmpty()) {
            return ProvisioningModeSelection.Rejected("allowed_modes_empty")
        }
        if (allowedModes.any { mode -> mode < 1 }) {
            return ProvisioningModeSelection.Rejected("allowed_modes_invalid")
        }
        if (FullyManagedProvisioningContract.MODE_FULLY_MANAGED_DEVICE !in allowedModes) {
            return ProvisioningModeSelection.Rejected("fully_managed_mode_not_offered")
        }
        return ProvisioningModeSelection.Selected(
            FullyManagedProvisioningContract.MODE_FULLY_MANAGED_DEVICE,
        )
    }
}
