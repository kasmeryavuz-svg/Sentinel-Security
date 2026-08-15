package com.example.devicemanagement.management

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Build
import com.example.devicemanagement.destructive.AuthorizedFactoryResetPort
import com.example.devicemanagement.destructive.AuthorizedFactoryResetResult

/**
 * Sole production origin for whole-device factory reset.
 *
 * The platform call uses flags `0` only. This class never calls the
 * legacy user-scoped wipe API, never sets extra wipe option bits, and
 * fails closed below API 34. Production bytecode allows this method only
 * from [com.example.devicemanagement.destructive.AndroidFutureDestructiveExecutor].
 */
internal class AndroidDevicePolicyFactoryResetService(
    private val manager: DevicePolicyManager,
    private val packageName: String,
    private val adminComponent: ComponentName,
) : AuthorizedFactoryResetPort {
    override fun performAuthorizedFactoryReset(): AuthorizedFactoryResetResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return AuthorizedFactoryResetResult.Refused("factory_reset_requires_api_34")
        }
        if (!manager.isAdminActive(adminComponent)) {
            return AuthorizedFactoryResetResult.Refused("device_admin_inactive")
        }
        if (!manager.isDeviceOwnerApp(packageName)) {
            return AuthorizedFactoryResetResult.Refused("not_device_owner")
        }
        return try {
            @Suppress("NewApi")
            manager.wipeDevice(0)
            AuthorizedFactoryResetResult.Initiated
        } catch (thrown: Throwable) {
            AuthorizedFactoryResetResult.Refused(
                "wipe_device_failed:${thrown.javaClass.simpleName}",
            )
        }
    }
}
