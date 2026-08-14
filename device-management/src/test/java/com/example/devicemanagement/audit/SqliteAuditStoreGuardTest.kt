package com.example.devicemanagement.audit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SqliteAuditStoreGuardTest {
    @Test
    fun `SQLite helper uses explicit schema version and never recreates destructively`() {
        val source = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java/com/example/devicemanagement/audit/SqliteAuditStore.kt",
        ).readText()

        assertTrue(source.contains("SQLiteOpenHelper"))
        assertTrue(source.contains("AuditSchema.VERSION"))
        assertTrue(source.contains("AuditSqliteIdentity.DATABASE_NAME"))
        assertTrue(source.contains("AuditSqliteIdentity.TABLE_NAME"))
        assertTrue(source.contains("NonDestructiveAuditDatabaseErrorHandler"))
        assertTrue(source.contains("audit schema upgrade"))
        assertTrue(source.contains("audit schema downgrade"))
        assertTrue(source.contains("override fun onCreate"))
        val onUpgrade = source.substringAfter("override fun onUpgrade")
            .substringBefore("override fun onDowngrade")
        val onDowngrade = source.substringAfter("override fun onDowngrade")
            .substringBefore("internal class NonDestructive")
        assertTrue(onUpgrade.contains("throw SQLiteException"))
        assertTrue(onDowngrade.contains("throw SQLiteException"))
        assertFalse(onUpgrade.contains("onCreate"))
        assertFalse(onUpgrade.contains("DROP"))
        assertFalse(onDowngrade.contains("onCreate"))
        assertFalse(onDowngrade.contains("DROP"))
        assertFalse(source.contains("deleteDatabase"))
        assertFalse(source.contains("openOrCreateDatabase"))
        assertFalse(source.contains("moveDatabaseFrom"))
        assertFalse(source.contains("DatabaseUtils"))
        assertFalse(source.contains("android.system.Os"))
        assertFalse(source.contains("OsConstants"))
        assertFalse(source.contains("getOrDefault(AuditEventPhase.FAILED)"))
        assertTrue(source.contains("AuditPersistedCodec.tryDecodePhase"))
        assertTrue(source.contains("unreadableRecords"))
        assertFalse(source.contains("setCameraDisabled"))
        assertFalse(source.contains("DevicePolicyManager"))
        assertFalse(source.contains("wipeData"))
        assertFalse(source.contains("lockNow"))
        assertFalse(source.contains("SharedPreferences"))
        assertTrue(source.contains("insertOrThrow"))
        assertTrue(source.contains("LIMIT ?"))
        assertTrue(source.contains("not cryptographically tamper-proof"))
    }

    @Test
    fun `production composition wires audit writer without using wall clock for authorization`() {
        val composition = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java/com/example/devicemanagement/management/DeviceManagementSensitiveActions.kt",
        ).readText()

        assertTrue(composition.contains("AndroidAuditPersistence.create"))
        assertTrue(composition.contains("auditWriter = audit"))
        assertTrue(composition.contains("override val auditHistory = audit"))
        assertTrue(composition.contains("override val auditStorageStatus = audit"))
        assertTrue(composition.contains("override val recoveryInspection = recoveryInspection"))
        assertTrue(composition.contains("DeviceManagementRecoveryInspectionFactory.create"))
        assertTrue(composition.contains("AndroidElapsedRealtimeMonotonicTimeSource"))
        assertFalse(composition.contains("currentTimeMillis"))
        assertFalse(composition.contains("InMemoryAuditRecordStore"))
    }
}
