package com.example.devicemanagement.destructive

/**
 * Purpose-specific durable pre-execution evidence store.
 *
 * Records are evidence only. They never authorize, arm, resume, or execute.
 * There is no delete API. Callers cannot choose a path or issue arbitrary
 * SQL. Production bytecode allows [insert] only from
 * [DurableDestructivePreExecutionRepository.append].
 *
 * This store is isolated from production `sentinel_audit.db` schema v1.
 *
 * TEST/SIMULATION implementations may satisfy this interface. They cannot
 * satisfy [RuntimeDestructivePreExecutionStore] or
 * [RuntimeDestructiveSafetyDurability]. A future real destructive chain
 * must require the runtime capability, not this generic interface.
 */
interface DestructivePreExecutionDurableStore {
    fun insert(record: DestructivePreExecutionDurableRecord): Long

    fun latest(limit: Int): DestructivePreExecutionDurableRead

    fun count(): Int
}

data class DestructivePreExecutionDurableRecord(
    val eventId: String,
    val correlationId: String,
    val actionName: String,
    val phase: DestructiveEvidencePhase,
    val presentationWallClockMillis: Long,
    val boundPackage: String?,
    val boundAdminComponent: String?,
    val boundScope: DestructiveScope?,
    val reasonCode: String?,
)

data class DestructivePreExecutionDurableRead(
    val records: List<DestructivePreExecutionDurableRecord>,
    val unreadableRecords: Boolean = false,
)

class DestructivePreExecutionStoreException(message: String) : Exception(message)

object DestructivePreExecutionStorageIdentity {
    const val TABLE_NAME = "destructive_pre_execution_events"
    const val MAX_EVENT_ID_CHARS = 128
    const val MAX_CORRELATION_CHARS = 128
    const val MAX_ACTION_NAME_CHARS = 128
    const val MAX_BOUND_FIELD_CHARS = 256
    const val MAX_REASON_CHARS = 128
}
