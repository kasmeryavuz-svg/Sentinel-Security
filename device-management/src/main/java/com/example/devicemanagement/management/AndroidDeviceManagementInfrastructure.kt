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

    fun isDeviceOwnerProvisioningAllowed(): Boolean

    fun isProfileOwnerProvisioningAllowed(): Boolean

    fun activeAdminComponentNames(): Set<String> = emptySet()
}

internal interface DevicePolicyPlatform {
    fun policyService(): DevicePolicyReadService?

    fun isExpectedAdminReceiverRegistered(): Boolean

    fun adminComponentConfiguration(): AdminComponentConfiguration =
        AdminComponentConfiguration(
            packageName = "",
            expectedComponentName = "",
            registeredSentinelAdminComponents = emptyList(),
            isExpectedReceiverRegisteredCorrectly = false,
        )
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
        return adminComponentConfiguration().isExpectedReceiverRegisteredCorrectly
    }

    override fun adminComponentConfiguration(): AdminComponentConfiguration {
        val flags = PackageManager.GET_RECEIVERS or PackageManager.GET_META_DATA
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
        val receivers = packageInfo.receivers.orEmpty().toList()
        val registeredAdminComponents = receivers
            .filter { receiver ->
                receiver.permission == Manifest.permission.BIND_DEVICE_ADMIN &&
                    (receiver.metaData?.getInt(DEVICE_ADMIN_METADATA, 0) ?: 0) != 0
            }
            .map { receiver ->
                ComponentName(receiver.packageName, receiver.name).flattenToString()
            }
        val expectedReceiver = receivers.singleOrNull { receiver ->
            ComponentName(receiver.packageName, receiver.name) == adminComponent
        }
        val expectedMetadata =
            expectedReceiver?.metaData?.getInt(DEVICE_ADMIN_METADATA, 0) ?: 0
        val expectedRegisteredCorrectly =
            expectedReceiver != null &&
                expectedReceiver.enabled &&
                expectedReceiver.exported &&
                expectedReceiver.permission == Manifest.permission.BIND_DEVICE_ADMIN &&
                expectedMetadata != 0

        return AdminComponentConfiguration(
            packageName = context.packageName,
            expectedComponentName = adminComponent.flattenToString(),
            registeredSentinelAdminComponents = registeredAdminComponents,
            isExpectedReceiverRegisteredCorrectly = expectedRegisteredCorrectly,
        )
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

    override fun isDeviceOwnerProvisioningAllowed(): Boolean {
        return manager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE)
    }

    override fun isProfileOwnerProvisioningAllowed(): Boolean {
        return manager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
    }

    override fun activeAdminComponentNames(): Set<String> {
        return manager.activeAdmins
            .orEmpty()
            .map(ComponentName::flattenToString)
            .toSet()
    }
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

    fun createProvisioningReadiness(
        context: Context,
        logger: DeviceManagementLogger,
    ): ProvisioningReadinessProvider {
        val platform = AndroidDevicePolicyPlatform(context.applicationContext)
        return DefaultProvisioningReadinessProvider(
            managementStatusProvider = DefaultDeviceManagementStatusProvider(
                platform = platform,
                logger = logger,
            ),
            platform = platform,
            logger = logger,
        )
    }

    fun createDeviceOwnerValidation(
        context: Context,
        logger: DeviceManagementLogger,
    ): DeviceOwnerValidationProvider {
        val platform = AndroidDevicePolicyPlatform(context.applicationContext)
        val statusProvider = DefaultDeviceManagementStatusProvider(
            platform = platform,
            logger = logger,
        )
        val readinessProvider = DefaultProvisioningReadinessProvider(
            managementStatusProvider = statusProvider,
            platform = platform,
            logger = logger,
        )
        return DefaultDeviceOwnerValidationProvider(
            managementStatusProvider = statusProvider,
            provisioningReadinessProvider = readinessProvider,
            platform = platform,
            logger = logger,
        )
    }
}
