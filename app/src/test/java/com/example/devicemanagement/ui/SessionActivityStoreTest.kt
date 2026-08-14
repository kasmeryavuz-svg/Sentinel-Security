package com.example.devicemanagement.ui

import com.example.devicemanagement.action.ActionResult
import com.example.devicemanagement.action.SensitiveActionOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionActivityStoreTest {
    @Test
    fun `store starts empty and keeps entries only in memory`() {
        val store = SessionActivityStore(sessionTimestampMillis = { 1_700L })
        assertTrue(store.entries().isEmpty())

        store.record(
            capability = PolicyCapability.CAMERA,
            requestedDisabled = true,
            result = ActionResult.Applied(
                operation = SensitiveActionOperation.DISABLE_CAMERA,
                requestedDisabled = true,
                observedDisabled = true,
                correlationId = "authoritative-1",
            ),
        )

        assertEquals(1, store.entries().size)
        assertEquals("authoritative-1", store.entries().single().correlationId)
        assertEquals(1_700L, store.entries().single().sessionTimestampMillis)
        assertTrue(SessionActivityStore(sessionTimestampMillis = { 0L }).entries().isEmpty())
    }

    @Test
    fun `correlation IDs survive into session activity for Applied Denied and Failed`() {
        val store = SessionActivityStore(sessionTimestampMillis = { 42L })

        store.record(
            capability = PolicyCapability.SCREEN_CAPTURE,
            requestedDisabled = true,
            result = ActionResult.Applied(
                operation = SensitiveActionOperation.DISABLE_SCREEN_CAPTURE,
                requestedDisabled = true,
                observedDisabled = true,
                correlationId = "applied-corr",
            ),
        )
        store.record(
            capability = PolicyCapability.CAMERA,
            requestedDisabled = false,
            result = ActionResult.Rejected(
                reason = "decision_denied:DEVICE_OWNER_NOT_VERIFIED",
                correlationId = "denied-corr",
            ),
        )
        store.record(
            capability = PolicyCapability.STATUS_BAR,
            requestedDisabled = true,
            result = ActionResult.Failed(
                reason = "post_write_read_back_mismatch",
                correlationId = "failed-corr",
            ),
        )

        val byCapability = store.entries().associateBy { it.capability }
        assertEquals(OperationOutcomePresentation.APPLIED, byCapability.getValue(PolicyCapability.SCREEN_CAPTURE).outcome)
        assertEquals("applied-corr", byCapability.getValue(PolicyCapability.SCREEN_CAPTURE).correlationId)
        assertNull(byCapability.getValue(PolicyCapability.SCREEN_CAPTURE).reason)
        assertEquals(OperationOutcomePresentation.DENIED, byCapability.getValue(PolicyCapability.CAMERA).outcome)
        assertEquals("denied-corr", byCapability.getValue(PolicyCapability.CAMERA).correlationId)
        assertEquals(OperationOutcomePresentation.FAILED, byCapability.getValue(PolicyCapability.STATUS_BAR).outcome)
        assertEquals("failed-corr", byCapability.getValue(PolicyCapability.STATUS_BAR).correlationId)
        assertEquals(
            "post_write_read_back_mismatch",
            byCapability.getValue(PolicyCapability.STATUS_BAR).reason,
        )
    }

    @Test
    fun `missing correlation IDs are presented as unavailable without claiming success`() {
        val store = SessionActivityStore(sessionTimestampMillis = { 1L })

        val denied = store.record(
            capability = PolicyCapability.CAMERA,
            requestedDisabled = true,
            result = ActionResult.Rejected(reason = "decision_denied:INVALID_TRIGGER"),
        )
        val failed = store.record(
            capability = PolicyCapability.CAMERA,
            requestedDisabled = true,
            result = ActionResult.Failed(reason = "policy_service_unavailable"),
        )

        assertEquals(SessionActivityStore.UNAVAILABLE_CORRELATION_ID, denied.correlationId)
        assertEquals(SessionActivityStore.UNAVAILABLE_CORRELATION_ID, failed.correlationId)
        assertNotEquals(OperationOutcomePresentation.APPLIED, denied.outcome)
        assertNotEquals(OperationOutcomePresentation.APPLIED, failed.outcome)
    }

    @Test
    fun `simulated results are never presented as Applied`() {
        val store = SessionActivityStore(sessionTimestampMillis = { 1L })

        val entry = store.record(
            capability = PolicyCapability.SCREEN_CAPTURE,
            requestedDisabled = true,
            result = ActionResult.Simulated(
                message = "WIPE WOULD EXECUTE",
                correlationId = "sim-corr",
            ),
        )

        assertEquals(OperationOutcomePresentation.FAILED, entry.outcome)
        assertEquals("sim-corr", entry.correlationId)
        assertEquals("WIPE WOULD EXECUTE", entry.reason)
    }

    @Test
    fun `newest session entries are first and bounded in memory`() {
        val store = SessionActivityStore(
            sessionTimestampMillis = { 9L },
            maxEntries = 2,
        )

        store.record(
            capability = PolicyCapability.CAMERA,
            requestedDisabled = true,
            result = applied("first"),
        )
        store.record(
            capability = PolicyCapability.CAMERA,
            requestedDisabled = false,
            result = applied("second"),
        )
        store.record(
            capability = PolicyCapability.CAMERA,
            requestedDisabled = true,
            result = applied("third"),
        )

        assertEquals(listOf("third", "second"), store.entries().map { it.correlationId })
    }

    @Test
    fun `session activity sources are documented as NON-PERSISTENT and do not persist`() {
        val uiSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/ui",
        ).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.name to it.readText() }
        val storeSource = requireNotNull(uiSources["SessionActivityStore.kt"])
        val allUiSources = uiSources.values.joinToString("\n")

        assertTrue(storeSource.contains("NON-PERSISTENT"))
        assertTrue(storeSource.contains("not an audit log"))
        assertFalse(allUiSources.contains("SharedPreferences"))
        assertFalse(allUiSources.contains("SQLite"))
        assertFalse(allUiSources.contains("SQLiteOpenHelper"))
        assertFalse(allUiSources.contains("FileOutputStream"))
        assertFalse(allUiSources.contains("openFileOutput"))
        assertFalse(allUiSources.contains("getFilesDir"))
        assertFalse(allUiSources.contains("getCacheDir"))
        assertFalse(allUiSources.contains("Room"))
        assertFalse(allUiSources.contains("DataStore"))
        assertFalse(allUiSources.contains("EncryptedFile"))
        assertFalse(allUiSources.contains("openOrCreateDatabase"))
    }

    private fun applied(correlationId: String): ActionResult {
        return ActionResult.Applied(
            operation = SensitiveActionOperation.DISABLE_CAMERA,
            requestedDisabled = true,
            observedDisabled = true,
            correlationId = correlationId,
        )
    }
}
