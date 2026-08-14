package com.example.devicemanagement.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeviceManagementUiBoundaryTest {
    @Test
    fun `UI cannot reference policy infrastructure or sensitive action internals`() {
        val uiSources = uiKotlinSources()

        assertFalse(uiSources.contains("import android.app.admin.DevicePolicyManager"))
        assertFalse(uiSources.contains("DevicePolicyManager"))
        assertFalse(uiSources.contains("AndroidDevicePolicyPlatform"))
        assertFalse(uiSources.contains("DevicePolicyReadService"))
        assertFalse(uiSources.contains("DevicePolicyScreenCaptureService"))
        assertFalse(uiSources.contains("DevicePolicyCameraService"))
        assertFalse(uiSources.contains("DevicePolicyStatusBarService"))
        assertFalse(uiSources.contains("SensitiveActionPolicyBackend"))
        assertFalse(uiSources.contains("DeviceManagementSensitiveActionControllerFactory"))
        assertFalse(uiSources.contains("DeviceManagementImplementation"))
        assertFalse(uiSources.contains("MonotonicTimeSource"))
        assertFalse(uiSources.contains("AndroidElapsedRealtimeMonotonicTimeSource"))
        assertFalse(uiSources.contains("SystemClock"))
        assertFalse(uiSources.contains("elapsedRealtime"))
        assertFalse(uiSources.contains("createControlled"))
        assertFalse(uiSources.contains("SensitiveActionRegistry"))
        assertFalse(uiSources.contains("VerifiedPolicyMutation"))
        assertFalse(uiSources.contains("VerifiedPolicyMutationExecutor"))
        assertFalse(uiSources.contains("DeviceOwnerMutationGuard"))
        assertFalse(uiSources.contains("DeviceManagementSensitiveActionBackend"))
        assertFalse(uiSources.contains("DefaultScreenCapturePolicy"))
        assertFalse(uiSources.contains("DefaultCameraPolicy"))
        assertFalse(uiSources.contains("DefaultStatusBarPolicy"))
        assertFalse(
            uiSources.contains(
                "import com.example.devicemanagement.management.ScreenCapturePolicy\n",
            ),
        )
        assertFalse(
            uiSources.contains(
                "import com.example.devicemanagement.management.CameraPolicy\n",
            ),
        )
        assertFalse(
            uiSources.contains(
                "import com.example.devicemanagement.management.StatusBarPolicy\n",
            ),
        )
        assertFalse(uiSources.contains("setScreenCaptureDisabled"))
        assertFalse(uiSources.contains("setCameraDisabled"))
        assertFalse(uiSources.contains("setStatusBarDisabled"))
        assertFalse(uiSources.contains("ActionExecutor"))
        assertFalse(uiSources.contains("ApprovalAuthority"))
        assertFalse(uiSources.contains("SensitiveActionAuditWriter"))
        assertFalse(uiSources.contains("DurableAuditRepository"))
        assertFalse(uiSources.contains("SqliteAuditRecordStore"))
        assertFalse(uiSources.contains("SentinelAuditOpenHelper"))
        assertFalse(uiSources.contains("AuditPersistedCodec"))
        assertFalse(uiSources.contains("AuditSqliteIdentity"))
        assertFalse(uiSources.contains("openOrCreateDatabase"))
        assertFalse(uiSources.contains("deleteDatabase"))
        assertFalse(uiSources.contains("getDatabasePath"))
        assertFalse(uiSources.contains("moveDatabaseFrom"))
        assertFalse(uiSources.contains("DatabaseUtils"))
        assertFalse(uiSources.contains("android.system.Os"))
        assertFalse(uiSources.contains("OsConstants"))
        assertFalse(uiSources.contains("sentinel_audit.db"))
        assertFalse(uiSources.contains("AuditRecordStore"))
        assertFalse(uiSources.contains("DeviceAction"))
        assertFalse(uiSources.contains("wipeData"))
        assertFalse(uiSources.contains("wipeDevice"))
        assertFalse(uiSources.contains("lockNow"))
        assertFalse(uiSources.contains("resetPassword"))
        assertFalse(uiSources.contains("Class.forName"))
        assertFalse(uiSources.contains("System.loadLibrary"))
    }

    @Test
    fun `app production sources stay on the public controller and status providers`() {
        val appSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(appSources.contains("import android.app.admin.DevicePolicyManager"))
        assertFalse(appSources.contains("VerifiedPolicyMutationExecutor"))
        assertFalse(appSources.contains("SensitiveActionPolicyBackend"))
        assertFalse(appSources.contains("ActionExecutor"))
        assertFalse(appSources.contains("ApprovalAuthority"))
        assertFalse(appSources.contains("MonotonicTimeSource"))
        assertFalse(appSources.contains("DeviceManagementImplementation"))
        assertFalse(appSources.contains("DefaultScreenCapturePolicy"))
        assertFalse(appSources.contains("DefaultCameraPolicy"))
        assertFalse(appSources.contains("DefaultStatusBarPolicy"))
        assertFalse(appSources.contains("wipeData"))
        assertFalse(appSources.contains("wipeDevice"))
        assertFalse(appSources.contains("lockNow"))
        assertFalse(appSources.contains("resetPassword"))
        assertFalse(appSources.contains("removeUser"))
        assertFalse(appSources.contains("uninstallPackageWithActiveAdmins"))
        assertFalse(appSources.contains("clearApplicationUserData"))
        assertFalse(appSources.contains("setLockTaskPackages"))
        assertFalse(appSources.contains("setLockTaskFeatures"))
        assertFalse(appSources.contains("ACTION_PROVISION_MANAGED_DEVICE"))
        assertFalse(appSources.contains("provisioningqr"))
        assertFalse(appSources.contains(":provisioning-qr"))
        assertFalse(appSources.contains("apksig"))
        assertFalse(appSources.contains("ApkVerifier"))
        assertTrue(appSources.contains("SensitiveActionController"))
        assertTrue(appSources.contains("AuditHistoryProvider"))
        assertTrue(appSources.contains("AuditStorageStatusProvider"))
        assertTrue(appSources.contains("RecoveryInspectionProvider"))
        assertFalse(appSources.contains("AuditRecoveryInspector"))
        assertFalse(appSources.contains("DeviceManagementRecoveryInspectionFactory"))
        assertFalse(appSources.contains("SensitiveActionAuditWriter"))
        assertFalse(appSources.contains("DurableAuditRepository"))
        assertFalse(appSources.contains("SqliteAuditRecordStore"))
        assertFalse(appSources.contains("openOrCreateDatabase"))
        assertFalse(appSources.contains("deleteDatabase"))
        assertFalse(appSources.contains("getDatabasePath"))
        assertFalse(appSources.contains("moveDatabaseFrom"))
        assertFalse(appSources.contains("DatabaseUtils"))
        assertFalse(appSources.contains("android.system.Os"))
        assertFalse(appSources.contains("OsConstants"))
        assertFalse(appSources.contains("sentinel_audit.db"))
        assertFalse(appSources.contains("SQLiteOpenHelper"))
        assertFalse(appSources.contains("SQLiteDatabase"))
    }

    @Test
    fun `UI mutation surface uses only the six trusted public commands`() {
        val uiSources = uiKotlinSources()
        val allowedCommands = listOf(
            "DISABLE_SCREEN_CAPTURE",
            "ENABLE_SCREEN_CAPTURE",
            "DISABLE_CAMERA",
            "ENABLE_CAMERA",
            "DISABLE_STATUS_BAR",
            "ENABLE_STATUS_BAR",
        )
        allowedCommands.forEach { command ->
            assertTrue(uiSources.contains("SensitiveActionCommands.$command"))
        }
        assertFalse(uiSources.contains("MOCK_WIPE"))
        assertFalse(uiSources.contains("mock_wipe"))
        assertFalse(uiSources.contains("::class.java.getMethod"))
        assertFalse(uiSources.contains("getDeclaredMethod"))
    }

    private fun uiKotlinSources(): String {
        return File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/ui",
        ).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
    }
}
