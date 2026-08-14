package com.example.devicemanagement.management

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Passive marker receiver used for component, ownership, and provisioning
 * completion diagnostics.
 *
 * Provisioning completion is NON-PERSISTENT. This callback does not mutate
 * policy, persist state, or enable camera/screen-capture/status-bar controls.
 */
class SentinelDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(
            TAG,
            "provisioning_complete event=profile_provisioning_complete " +
                "policy_mutation=false persistence=false",
        )
    }

    private companion object {
        const val TAG = "SentinelProvisioning"
    }
}
