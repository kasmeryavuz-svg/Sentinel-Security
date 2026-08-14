package com.example.devicemanagement.provisioning

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Android 12+ GET_PROVISIONING_MODE handler.
 *
 * Returns immediately with fully-managed Device Owner mode when that mode is
 * offered. It does not show unrelated UI, start services, or mutate policy.
 */
class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action != FullyManagedProvisioningContract.ACTION_GET_PROVISIONING_MODE) {
            Log.w(TAG, "provisioning_mode_rejected reason=unexpected_action")
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        val selection = FullyManagedProvisioningModeSelector.select(
            ProvisioningAllowedModesParser.parse(allowedModesExtra(intent)),
        )
        when (selection) {
            is ProvisioningModeSelection.Selected -> {
                Log.i(TAG, "provisioning_mode_selected mode=${selection.mode}")
                setResult(
                    RESULT_OK,
                    Intent().putExtra(
                        FullyManagedProvisioningContract.EXTRA_PROVISIONING_MODE,
                        selection.mode,
                    ),
                )
            }
            is ProvisioningModeSelection.Rejected -> {
                Log.w(TAG, "provisioning_mode_rejected reason=${selection.reason}")
                setResult(RESULT_CANCELED)
            }
        }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun allowedModesExtra(intent: Intent): Any? {
        return intent.extras?.get(
            FullyManagedProvisioningContract.EXTRA_ALLOWED_PROVISIONING_MODES,
        )
    }

    private companion object {
        const val TAG = "SentinelProvisioning"
    }
}
