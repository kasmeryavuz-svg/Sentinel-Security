package com.example.devicemanagement.ui

import com.example.devicemanagement.audit.AuditHistoryProvider
import com.example.devicemanagement.audit.AuditStorageStatusProvider
import com.example.devicemanagement.recovery.RecoveryInspectionProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class AuditPresentationGuardTest {
    @Test
    fun `dashboard reads history only through read-only audit API`() {
        val presenter = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/ui/DashboardPresenter.kt",
        ).readText()
        val mapper = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/ui/DashboardStateMapper.kt",
        ).readText()
        val activity = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/ui/MainActivity.kt",
        ).readText()

        assertTrue(presenter.contains("AuditHistoryProvider"))
        assertTrue(presenter.contains("AuditStorageStatusProvider"))
        assertTrue(presenter.contains("auditHistory.latest"))
        assertFalse(presenter.contains("SensitiveActionAuditWriter"))
        assertFalse(presenter.contains(".append("))
        assertFalse(presenter.contains("SessionActivityStore"))
        assertFalse(mapper.contains("ActionResult"))
        assertTrue(mapper.contains("never treats an unmatched"))
        assertTrue(activity.contains("container.auditHistory"))
        assertTrue(activity.contains("container.auditStorageStatus"))
        assertFalse(activity.contains("SessionActivityStore"))
    }

    @Test
    fun `UI sources do not persist audit data or expose mutation controls`() {
        val uiSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/ui",
        ).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        val layout = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "res/layout/activity_main.xml",
        ).readText()
        val strings = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "res/values/strings.xml",
        ).readText()

        assertFalse(uiSources.contains("SharedPreferences"))
        assertFalse(uiSources.contains("SQLite"))
        assertFalse(uiSources.contains("SQLiteOpenHelper"))
        assertFalse(uiSources.contains("FileOutputStream"))
        assertFalse(uiSources.contains("openFileOutput"))
        assertFalse(uiSources.contains("getFilesDir"))
        assertFalse(uiSources.contains("openOrCreateDatabase"))
        assertFalse(uiSources.contains("deleteDatabase"))
        assertFalse(uiSources.contains("getDatabasePath"))
        assertFalse(uiSources.contains("moveDatabaseFrom"))
        assertFalse(uiSources.contains("DatabaseUtils"))
        assertFalse(uiSources.contains("android.system.Os"))
        assertFalse(uiSources.contains("OsConstants"))
        assertFalse(uiSources.contains("sentinel_audit.db"))
        assertFalse(uiSources.contains("java.io.File"))
        assertFalse(uiSources.contains("Room"))
        assertFalse(uiSources.contains("DataStore"))
        assertFalse(uiSources.contains("SensitiveActionAuditWriter"))
        assertFalse(uiSources.contains("DurableAuditRepository"))
        assertFalse(uiSources.contains("SqliteAuditRecordStore"))
        assertFalse(layout.contains("clear audit"))
        assertFalse(layout.contains("delete"))
        assertFalse(layout.contains("export"))
        assertFalse(layout.contains("retry"))
        assertTrue(strings.contains("Audit log"))
        assertTrue(strings.contains("not cryptographically tamper-proof"))
        assertFalse(strings.contains("Clear audit"))
        assertFalse(strings.contains("Delete history"))
    }

    @Test
    fun `public audit providers expose no mutation methods`() {
        val forbidden = setOf(
            "append",
            "insert",
            "update",
            "delete",
            "clear",
            "execute",
            "approve",
            "retry",
        )
        listOf(
            AuditHistoryProvider::class.java,
            AuditStorageStatusProvider::class.java,
            RecoveryInspectionProvider::class.java,
        ).forEach { type ->
            type.methods.filter { Modifier.isPublic(it.modifiers) }.forEach { method ->
                assertFalse(forbidden.contains(method.name))
            }
        }
    }
}
