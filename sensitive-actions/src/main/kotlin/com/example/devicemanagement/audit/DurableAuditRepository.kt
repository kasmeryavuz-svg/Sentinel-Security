package com.example.devicemanagement.audit

import com.example.devicemanagement.logging.StructuredLogger

/**
 * Append-only durable audit repository.
 *
 * Retention pruning of oldest records happens only here. There is no public
 * clear/delete API, and this class cannot authorize or replay actions.
 *
 * Ordinary app-private storage is not cryptographically tamper-proof.
 * Unreadable persisted phases are omitted and mark storage degraded; they
 * never become fabricated terminal outcomes.
 */
class DurableAuditRepository(
    private val records: AuditRecordStore,
    private val logger: StructuredLogger,
    private val retentionBound: Int = AuditSchema.RETENTION_BOUND,
) : SensitiveActionAuditWriter, AuditHistoryProvider, AuditStorageStatusProvider {
    private val lock = Any()
    private var health = AuditStorageHealth.HEALTHY
    private var healthReason: AuditReasonCode? = null

    init {
        require(retentionBound > 0) { "audit retention bound must be positive" }
        refreshReadHealth()
    }

    override fun append(request: AuditAppendRequest): AuditAppendResult {
        val record = NewAuditRecord(
            eventId = request.eventId,
            correlationId = request.correlationId,
            actionName = AuditActionNames.canonicalize(request.actionName),
            phase = request.phase,
            presentationWallClockMillis = request.presentationWallClockMillis,
            reasonCode = request.reasonCode,
        )
        return synchronized(lock) {
            try {
                val sequence = records.insert(record)
                val extra = records.count() - retentionBound
                if (extra > 0) {
                    records.deleteOldest(extra)
                }
                markHealthy()
                AuditAppendResult.Persisted(sequence = sequence, eventId = record.eventId)
            } catch (error: Throwable) {
                markWriteFailed()
                logger.warn(
                    event = "audit_append_failed",
                    fields = mapOf(
                        "correlation_id" to record.correlationId,
                        "phase" to record.phase.name,
                        "error_type" to error.javaClass.simpleName,
                    ),
                )
                AuditAppendResult.Failed(health)
            }
        }
    }

    override fun latest(limit: Int): AuditHistory {
        val safeLimit = limit.coerceAtLeast(0)
        return synchronized(lock) {
            try {
                val page = records.latest(safeLimit)
                val events = page.records.map(PersistedAuditRecord::toEvent)
                val storedCount = records.count()
                if (page.unreadableRecords) {
                    health = AuditStorageHealth.DEGRADED
                    healthReason = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE
                } else if (health == AuditStorageHealth.UNAVAILABLE) {
                    health = AuditStorageHealth.DEGRADED
                    healthReason = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE
                }
                AuditHistory(
                    events = events,
                    storedCount = storedCount,
                    retentionBound = retentionBound,
                )
            } catch (_: Throwable) {
                health = AuditStorageHealth.UNAVAILABLE
                healthReason = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE
                AuditHistory(
                    events = emptyList(),
                    storedCount = 0,
                    retentionBound = retentionBound,
                )
            }
        }
    }

    override fun currentStatus(): AuditStorageStatus {
        synchronized(lock) {
            refreshReadHealth()
            return AuditStorageStatus(health = health, reasonCode = healthReason)
        }
    }

    private fun refreshReadHealth() {
        try {
            records.count()
            if (health == AuditStorageHealth.UNAVAILABLE) {
                health = AuditStorageHealth.DEGRADED
                healthReason = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE
            }
        } catch (_: Throwable) {
            health = AuditStorageHealth.UNAVAILABLE
            healthReason = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE
        }
    }

    private fun markHealthy() {
        health = AuditStorageHealth.HEALTHY
        healthReason = null
    }

    private fun markWriteFailed() {
        health = try {
            records.count()
            AuditStorageHealth.DEGRADED
        } catch (_: Throwable) {
            AuditStorageHealth.UNAVAILABLE
        }
        healthReason = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE
    }
}
