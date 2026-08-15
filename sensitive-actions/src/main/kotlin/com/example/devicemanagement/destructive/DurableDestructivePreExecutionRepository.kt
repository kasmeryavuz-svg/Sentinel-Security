package com.example.devicemanagement.destructive

/**
 * Paired durable pre-execution append authority surface.
 *
 * Append success is evidence that the pipeline intended to proceed. It is
 * never authorization, never a resume token, and never a permit. Production
 * Production bytecode allows [append] only from the simulation commit
 * authority and the exact runtime commit-after-consume authority.
 *
 * Isolated from production `sentinel_audit.db` schema v1 so reversible audit
 * and dashboard recovery cannot treat these rows as `APPLIED` or replay them.
 *
 * The generic [DestructivePreExecutionDurableStore] parameter is the
 * TEST/SIMULATION persistence surface. In-memory stores may back this
 * repository in 17A/17B tests. They cannot satisfy
 * [RuntimeDestructivePreExecutionStore]. A future real destructive chain
 * must require [RuntimeDestructiveSafetyDurability].
 */
internal class DurableDestructivePreExecutionRepository(
    private val store: DestructivePreExecutionDurableStore,
) {
    fun append(record: DestructivePreExecutionDurableRecord): DestructiveEvidenceAppendResult {
        if (!isAcceptable(record)) {
            return DestructiveEvidenceAppendResult.Failed
        }
        return try {
            val sequence = store.insert(record)
            if (sequence <= 0L) {
                DestructiveEvidenceAppendResult.Failed
            } else {
                DestructiveEvidenceAppendResult.Recorded(record.eventId)
            }
        } catch (_: Throwable) {
            DestructiveEvidenceAppendResult.Failed
        }
    }

    fun latest(limit: Int): DestructivePreExecutionDurableRead {
        return try {
            store.latest(limit.coerceAtLeast(0))
        } catch (_: Throwable) {
            DestructivePreExecutionDurableRead(records = emptyList(), unreadableRecords = true)
        }
    }

    private fun isAcceptable(record: DestructivePreExecutionDurableRecord): Boolean {
        if (record.phase != DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED) {
            return false
        }
        if (record.eventId.isBlank() || record.eventId.length > DestructivePreExecutionStorageIdentity.MAX_EVENT_ID_CHARS) {
            return false
        }
        if (record.correlationId.isBlank() ||
            record.correlationId.length > DestructivePreExecutionStorageIdentity.MAX_CORRELATION_CHARS
        ) {
            return false
        }
        if (
            record.actionName !in setOf(
                DestructiveSimulationActionNames.FACTORY_RESET_SIMULATION,
                DestructiveRuntimeActionNames.FUTURE_REAL_CHAIN_FACTORY_RESET,
            )
        ) {
            return false
        }
        if (record.boundScope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return false
        }
        if (record.boundPackage.isNullOrBlank() ||
            record.boundPackage.length > DestructivePreExecutionStorageIdentity.MAX_BOUND_FIELD_CHARS
        ) {
            return false
        }
        if (record.boundAdminComponent.isNullOrBlank() ||
            record.boundAdminComponent.length > DestructivePreExecutionStorageIdentity.MAX_BOUND_FIELD_CHARS
        ) {
            return false
        }
        val reason = record.reasonCode
        if (reason != null && reason.length > DestructivePreExecutionStorageIdentity.MAX_REASON_CHARS) {
            return false
        }
        return true
    }
}
