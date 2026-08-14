package com.example.devicemanagement.audit

data class NewAuditRecord(
    val eventId: String,
    val correlationId: String,
    val actionName: String,
    val phase: AuditEventPhase,
    val presentationWallClockMillis: Long,
    val reasonCode: AuditReasonCode?,
)

data class PersistedAuditRecord(
    val sequence: Long,
    val eventId: String,
    val correlationId: String,
    val actionName: String,
    val phase: AuditEventPhase,
    val presentationWallClockMillis: Long,
    val reasonCode: AuditReasonCode?,
) {
    fun toEvent(): AuditEvent {
        return AuditEvent(
            sequence = sequence,
            eventId = eventId,
            correlationId = correlationId,
            actionName = actionName,
            phase = phase,
            presentationWallClockMillis = presentationWallClockMillis,
            reasonCode = reasonCode,
        )
    }
}

data class AuditRecordRead(
    val records: List<PersistedAuditRecord>,
    val unreadableRecords: Boolean = false,
)

class AuditStoreException(message: String) : Exception(message)

/**
 * Persistence adapter used by [DurableAuditRepository].
 *
 * Implementations must not call DevicePolicyManager, consume approvals, or
 * authorize actions. Retention pruning belongs to the repository, not callers.
 * Production bytecode allows [insert] and [deleteOldest] only from
 * [DurableAuditRepository.append].
 */
interface AuditRecordStore {
    fun insert(record: NewAuditRecord): Long

    fun latest(limit: Int): AuditRecordRead

    fun count(): Int

    fun deleteOldest(count: Int)
}
