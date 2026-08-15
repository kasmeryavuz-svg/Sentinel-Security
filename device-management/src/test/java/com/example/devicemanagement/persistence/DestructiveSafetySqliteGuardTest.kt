package com.example.devicemanagement.persistence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DestructiveSafetySqliteGuardTest {
    @Test
    fun `Android safety stores are purpose-specific and unwired`() {
        val source = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java/com/example/devicemanagement/persistence/SqliteDenyOnlyMarkerStore.kt",
        ).readText()
        val composition = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java/com/example/devicemanagement/management/DeviceManagementSensitiveActions.kt",
        ).readText()
        val implementation = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java/com/example/devicemanagement/internal/DeviceManagementImplementation.kt",
        ).readText()

        assertTrue(source.contains("Purpose-specific"))
        assertTrue(source.contains("DestructiveSafetySqliteIdentity.DENY_ONLY_DATABASE_NAME"))
        assertTrue(source.contains("sentinel_deny_only_cooldown.db"))
        assertTrue(source.contains("sentinel_destructive_pre_execution_evidence.db"))
        assertTrue(source.contains("throw SQLiteException"))
        assertTrue(source.contains("NonDestructiveSafetyDatabaseErrorHandler"))
        assertFalse(source.contains("deleteDatabase"))
        assertFalse(source.contains("openOrCreateDatabase"))
        assertFalse(source.contains("wipeData"))
        assertFalse(source.contains("wipeDevice"))
        assertFalse(source.contains("DevicePolicyManager"))
        assertFalse(source.contains("SimulatedDestructiveExecutor"))
        assertFalse(source.contains("DestructiveArmingAuthority"))
        assertFalse(source.contains("BOOT_COMPLETED"))
        assertFalse(source.contains("deleteOldest"))
        assertTrue(composition.contains("AndroidDestructiveSafetyPersistence"))
        assertFalse(composition.contains("SqliteDenyOnlyMarkerStore"))
        assertFalse(composition.contains("SqliteDestructivePreExecutionStore"))
        assertTrue(composition.contains("issueRuntimeDurability"))
        assertFalse(implementation.contains("AndroidDestructiveSafetyPersistence"))
        assertFalse(implementation.contains("issueRuntimeDurability"))
        assertFalse(composition.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(composition.contains("assembleAndHandoff"))
        assertFalse(implementation.contains("FutureDestructiveRealChainBoundary"))
        assertTrue(source.contains("DeviceManagement composition may issue"))
        assertTrue(source.contains("fun issueRuntimeDurability"))
        assertTrue(source.contains("issueFromTrustedAndroidStores"))
        assertTrue(source.contains("RuntimeDestructiveSafetyDurability"))
    }
}
