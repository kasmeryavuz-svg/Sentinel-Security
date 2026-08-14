package com.example.devicemanagement.audit

import com.example.devicemanagement.logging.StructuredLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class DurableAuditRepositoryTest {
    private val logger = NoOpLogger()

    @Test
    fun `durable history survives repository recreation`() {
        val state = InMemoryAuditState()
        val first = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        first.append(request("corr-1", AuditEventPhase.REQUESTED, 10L))
        first.append(request("corr-1", AuditEventPhase.APPLIED, 11L))

        val second = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        val events = second.latest(10).events

        assertEquals(2, events.size)
        assertEquals(listOf(2L, 1L), events.map { it.sequence })
        assertEquals("corr-1", events.first().correlationId)
        assertEquals(AuditEventPhase.APPLIED, events.first().phase)
    }

    @Test
    fun `incomplete REQUESTED remains incomplete after recreation and is never Applied`() {
        val state = InMemoryAuditState()
        DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
            .append(request("interrupted", AuditEventPhase.REQUESTED, 5L))

        val restarted = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)
        val events = restarted.latest(10).events

        assertEquals(1, events.size)
        assertEquals(AuditEventPhase.REQUESTED, events.single().phase)
        assertNotEquals(AuditEventPhase.APPLIED, events.single().phase)
        assertEquals("interrupted", events.single().correlationId)
    }

    @Test
    fun `retention bound prunes oldest records without resetting sequence`() {
        val repository = DurableAuditRepository(
            records = InMemoryAuditRecordStore(),
            logger = logger,
            retentionBound = 3,
        )
        repeat(5) { index ->
            repository.append(
                request(
                    correlationId = "corr-$index",
                    phase = AuditEventPhase.REQUESTED,
                    timestamp = index.toLong(),
                    eventId = "event-$index",
                ),
            )
        }

        val history = repository.latest(10)
        assertEquals(3, history.events.size)
        assertEquals(3, history.storedCount)
        assertEquals(3, history.retentionBound)
        assertEquals(listOf(5L, 4L, 3L), history.events.map { it.sequence })
        assertEquals(listOf("corr-4", "corr-3", "corr-2"), history.events.map { it.correlationId })
        assertFalse(history.events.any { it.sequence == 1L || it.sequence == 2L })
    }

    @Test
    fun `write failure marks storage degraded without fabricating events`() {
        val state = InMemoryAuditState().apply { remainingSuccessfulWrites = 1 }
        val repository = DurableAuditRepository(InMemoryAuditRecordStore(state), logger)

        val requested = repository.append(request("corr", AuditEventPhase.REQUESTED, 1L))
        val terminal = repository.append(request("corr", AuditEventPhase.APPLIED, 2L, "event-2"))

        assertTrue(requested is AuditAppendResult.Persisted)
        assertTrue(terminal is AuditAppendResult.Failed)
        assertEquals(AuditStorageHealth.DEGRADED, repository.currentStatus().health)
        val events = repository.latest(10).events
        assertEquals(1, events.size)
        assertEquals(AuditEventPhase.REQUESTED, events.single().phase)
        assertFalse(events.any { it.phase == AuditEventPhase.APPLIED })
    }

    @Test
    fun `unavailable store reports unavailable and stores nothing`() {
        val repository = DurableAuditRepository(UnavailableAuditRecordStore(), logger)

        val result = repository.append(request("corr", AuditEventPhase.REQUESTED, 1L))

        assertTrue(result is AuditAppendResult.Failed)
        assertEquals(AuditStorageHealth.UNAVAILABLE, repository.currentStatus().health)
        assertTrue(repository.latest(10).events.isEmpty())
    }

    @Test
    fun `schema version and retention are explicit and stable`() {
        assertEquals(1, AuditSchema.VERSION)
        assertEquals(8_000, AuditSchema.RETENTION_BOUND)
        assertTrue(AuditSchema.RETENTION_BOUND in 5_000..10_000)
        assertFalse(
            AuditSchema::class.java.fields.any { field ->
                field.name == "DATABASE_NAME" || field.name == "TABLE_NAME"
            },
        )
    }

    @Test
    fun `unknown or corrupt persisted phases are not synthesized as terminal outcomes`() {
        val unknown = listOf(
            "UNKNOWN",
            "CORRUPT",
            "failed",
            "APPLIED ",
            "REQUESTED_APPLIED",
            "",
            null,
        )
        unknown.forEach { raw ->
            val decoded = AuditPersistedCodec.tryDecodePhase(raw)
            assertEquals("decoded $raw", null, decoded)
            if (decoded != null) {
                assertFalse(AuditPersistedCodec.isTerminal(decoded))
            }
        }
        val thrown = runCatching { AuditPersistedCodec.decodePhase("NOT_A_PHASE") }.exceptionOrNull()
        assertTrue(thrown is AuditStoreException)
        assertEquals(AuditEventPhase.REQUESTED, AuditPersistedCodec.decodePhase("REQUESTED"))
        assertEquals(AuditEventPhase.FAILED, AuditPersistedCodec.decodePhase("FAILED"))
    }

    @Test
    fun `unreadable terminal phase keeps REQUESTED and marks storage degraded`() {
        val store = RawPhaseAuditRecordStore(
            listOf(
                RawAuditRow(1L, "event-1", "corr", "disable_camera", "REQUESTED", 10L),
                RawAuditRow(2L, "event-2", "corr", "disable_camera", "NOT_A_REAL_PHASE", 11L),
            ),
        )
        val repository = DurableAuditRepository(store, logger)
        val history = repository.latest(10)

        assertEquals(1, history.events.size)
        assertEquals(AuditEventPhase.REQUESTED, history.events.single().phase)
        assertFalse(history.events.any { AuditPersistedCodec.isTerminal(it.phase) })
        assertEquals(AuditStorageHealth.DEGRADED, repository.currentStatus().health)
        assertEquals(
            AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE,
            repository.currentStatus().reasonCode,
        )
    }

    @Test
    fun `corrupt phase-only rows never become APPLIED REJECTED FAILED or SIMULATED`() {
        val store = RawPhaseAuditRecordStore(
            listOf(
                RawAuditRow(1L, "event-1", "corr", "disable_camera", "GARBAGE", 10L),
            ),
        )
        val repository = DurableAuditRepository(store, logger)
        val history = repository.latest(10)

        assertTrue(history.events.isEmpty())
        val terminals = setOf(
            AuditEventPhase.APPLIED,
            AuditEventPhase.REJECTED,
            AuditEventPhase.FAILED,
            AuditEventPhase.SIMULATED,
        )
        assertFalse(history.events.any { it.phase in terminals })
        assertEquals(AuditStorageHealth.DEGRADED, repository.currentStatus().health)
    }

    @Test
    fun `read-only audit API exposes no clear delete append or retry methods`() {
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
        val types = listOf(
            AuditHistoryProvider::class.java,
            AuditStorageStatusProvider::class.java,
            com.example.devicemanagement.recovery.RecoveryInspectionProvider::class.java,
        )
        types.forEach { type ->
            val names = type.methods
                .filter { Modifier.isPublic(it.modifiers) }
                .map { it.name }
            forbidden.forEach { method ->
                assertFalse("$type exposes $method", names.contains(method))
            }
        }
    }

    @Test
    fun `unknown commands and reasons are sanitized`() {
        assertEquals(AuditActionNames.UNRECOGNIZED, AuditActionNames.canonicalize("rm -rf /"))
        assertEquals(
            AuditActionNames.DISABLE_CAMERA,
            AuditActionNames.canonicalize("disable_camera"),
        )
        assertEquals(
            AuditReasonCode.SANITIZED_UNRECOGNIZED,
            AuditReasonCodes.sanitize("java.lang.IllegalStateException: boom at CameraPolicy.kt:12"),
        )
        assertEquals(
            AuditReasonCode.DEVICE_OWNER_NOT_VERIFIED,
            AuditReasonCodes.sanitize("decision_denied:DEVICE_OWNER_NOT_VERIFIED"),
        )
    }

    @Test
    fun `repository source has no public clear API and does not use wall clock for authority`() {
        val source = java.io.File(
            "src/main/kotlin/com/example/devicemanagement/audit/DurableAuditRepository.kt",
        ).readText()
        assertFalse(source.contains("fun clear"))
        assertFalse(source.contains("fun delete"))
        assertFalse(source.contains("DevicePolicyManager"))
        assertFalse(source.contains("setCameraDisabled"))
        assertFalse(source.contains("ApprovalAuthority"))
        assertTrue(source.contains("Retention pruning of oldest records happens only here"))
        assertTrue(source.contains("not cryptographically tamper-proof"))
    }

    private data class RawAuditRow(
        val sequence: Long,
        val eventId: String,
        val correlationId: String,
        val actionName: String,
        val phase: String,
        val presentationWallClockMillis: Long,
    )

    private class RawPhaseAuditRecordStore(
        private val rows: List<RawAuditRow>,
    ) : AuditRecordStore {
        override fun insert(record: NewAuditRecord): Long {
            throw AuditStoreException("raw phase fixture is read-only")
        }

        override fun latest(limit: Int): AuditRecordRead {
            val decoded = ArrayList<PersistedAuditRecord>()
            var unreadable = false
            rows.sortedByDescending { it.sequence }.take(limit.coerceAtLeast(0)).forEach { row ->
                val phase = AuditPersistedCodec.tryDecodePhase(row.phase)
                if (phase == null) {
                    unreadable = true
                } else {
                    decoded += PersistedAuditRecord(
                        sequence = row.sequence,
                        eventId = row.eventId,
                        correlationId = row.correlationId,
                        actionName = row.actionName,
                        phase = phase,
                        presentationWallClockMillis = row.presentationWallClockMillis,
                        reasonCode = null,
                    )
                }
            }
            return AuditRecordRead(records = decoded, unreadableRecords = unreadable)
        }

        override fun count(): Int = rows.size

        override fun deleteOldest(count: Int) {
            throw AuditStoreException("raw phase fixture cannot prune")
        }
    }

    private fun request(
        correlationId: String,
        phase: AuditEventPhase,
        timestamp: Long,
        eventId: String = "event-$phase-$timestamp",
    ): AuditAppendRequest {
        return AuditAppendRequest(
            eventId = eventId,
            correlationId = correlationId,
            actionName = AuditActionNames.DISABLE_CAMERA,
            phase = phase,
            presentationWallClockMillis = timestamp,
            reasonCode = null,
        )
    }

    private class NoOpLogger : StructuredLogger {
        override fun info(event: String, fields: Map<String, Any?>) = Unit
        override fun warn(event: String, fields: Map<String, Any?>) = Unit
        override fun error(
            event: String,
            fields: Map<String, Any?>,
            throwable: Throwable?,
        ) = Unit
    }
}
