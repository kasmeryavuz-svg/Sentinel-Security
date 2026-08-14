package com.example.devicemanagement.audit

import java.util.concurrent.atomic.AtomicLong

internal class InMemoryAuditState {
    val rows = mutableListOf<PersistedAuditRecord>()
    val nextSequence = AtomicLong(1L)
    var failWrites: Boolean = false
    var failReads: Boolean = false
    var remainingSuccessfulWrites: Int? = null
}

/**
 * Process-local durable store for tests and fail-safe composition.
 *
 * Shared [InMemoryAuditState] survives repository recreation the same way a
 * database file survives process restart. This is not the production Android
 * SQLite implementation.
 */
internal class InMemoryAuditRecordStore(
    private val state: InMemoryAuditState = InMemoryAuditState(),
) : AuditRecordStore {
    override fun insert(record: NewAuditRecord): Long {
        if (state.failWrites) {
            throw AuditStoreException("audit write failed")
        }
        val remaining = state.remainingSuccessfulWrites
        if (remaining != null) {
            if (remaining <= 0) {
                throw AuditStoreException("audit write failed")
            }
            state.remainingSuccessfulWrites = remaining - 1
        }
        val sequence = state.nextSequence.getAndIncrement()
        state.rows += PersistedAuditRecord(
            sequence = sequence,
            eventId = record.eventId,
            correlationId = record.correlationId,
            actionName = record.actionName,
            phase = record.phase,
            presentationWallClockMillis = record.presentationWallClockMillis,
            reasonCode = record.reasonCode,
        )
        return sequence
    }

    override fun latest(limit: Int): AuditRecordRead {
        failIfReadsBroken()
        return AuditRecordRead(
            records = state.rows.sortedByDescending { it.sequence }.take(limit),
        )
    }

    override fun count(): Int {
        failIfReadsBroken()
        return state.rows.size
    }

    override fun deleteOldest(count: Int): Unit {
        failIfReadsBroken()
        if (count <= 0) {
            return
        }
        val oldest = state.rows.sortedBy { it.sequence }.take(count).toSet()
        state.rows.removeAll(oldest)
    }

    private fun failIfReadsBroken() {
        if (state.failReads) {
            throw AuditStoreException("audit read failed")
        }
    }
}

class UnavailableAuditRecordStore : AuditRecordStore {
    override fun insert(record: NewAuditRecord): Long {
        throw AuditStoreException("audit store unavailable")
    }

    override fun latest(limit: Int): AuditRecordRead {
        throw AuditStoreException("audit store unavailable")
    }

    override fun count(): Int {
        throw AuditStoreException("audit store unavailable")
    }

    override fun deleteOldest(count: Int) {
        throw AuditStoreException("audit store unavailable")
    }
}
