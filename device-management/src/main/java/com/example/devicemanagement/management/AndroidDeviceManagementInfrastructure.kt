package com.example.devicemanagement.management

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Query-only surface over DevicePolicyManager.
 *
 * Mutating policy operations are deliberately absent.
 */
internal interface DevicePolicyReadService {
    fun isDeviceOwnerApp(): Boolean

    fun isProfileOwnerApp(): Boolean

    fun isExpectedAdminActive(): Boolean
}

internal interface DevicePolicyPlatform {
    fun policyService(): DevicePolicyReadService?

    fun isExpectedAdminReceiverRegistered(): Boolean
}

internal class AndroidDevicePolicyPlatform(
    private val context: Context,
) : DevicePolicyPlatform {
    private val adminComponent = ComponentName(context, SentinelDeviceAdminReceiver::class.java)

    override fun policyService(): DevicePolicyReadService? {
        val manager = context.getSystemService(DevicePolicyManager::class.java) ?: return null
        return AndroidDevicePolicyReadService(
            manager = manager,
            packageName = context.packageName,
            adminComponent = adminComponent,
        )
    }

    override fun isExpectedAdminReceiverRegistered(): Boolean {
        val receiverInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getReceiverInfo(
                adminComponent,
                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getReceiverInfo(adminComponent, PackageManager.GET_META_DATA)
        }

        val metadataResource = receiverInfo.metaData?.getInt(DEVICE_ADMIN_METADATA, 0) ?: 0
        return receiverInfo.enabled &&
            receiverInfo.exported &&
            receiverInfo.permission == Manifest.permission.BIND_DEVICE_ADMIN &&
            metadataResource != 0
    }

    private companion object {
        const val DEVICE_ADMIN_METADATA = "android.app.device_admin"
    }
}

internal class AndroidDevicePolicyReadService(
    private val manager: DevicePolicyManager,
    private val packageName: String,
    private val adminComponent: ComponentName,
) : DevicePolicyReadService {
    override fun isDeviceOwnerApp(): Boolean = manager.isDeviceOwnerApp(packageName)

    override fun isProfileOwnerApp(): Boolean = manager.isProfileOwnerApp(packageName)

    override fun isExpectedAdminActive(): Boolean = manager.isAdminActive(adminComponent)
}

object DeviceManagementDiagnostics {
    fun create(
        context: Context,
        logger: DeviceManagementLogger,
    ): DeviceManagementStatusProvider {
        return DefaultDeviceManagementStatusProvider(
            platform = AndroidDevicePolicyPlatform(context.applicationContext),
            logger = logger,
        )
    }
}
