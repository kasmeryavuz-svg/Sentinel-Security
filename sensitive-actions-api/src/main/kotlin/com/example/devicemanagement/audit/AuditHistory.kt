package com.example.devicemanagement.audit

/**
 * Read-only durable audit history. This API cannot append, mutate, clear,
 * approve, retry, or execute actions.
 *
 * Local retention may prune oldest records. Remaining sequence numbers are
 * never reset. This is not tamper-proof archival.
 */
data class AuditHistory(
    val events: List<AuditEvent>,
    val storedCount: Int,
    val retentionBound: Int,
)

fun interface AuditHistoryProvider {
    fun latest(limit: Int): AuditHistory
}

enum class AuditStorageHealth {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
}

data class AuditStorageStatus(
    val health: AuditStorageHealth,
    val reasonCode: AuditReasonCode?,
)

fun interface AuditStorageStatusProvider {
    fun currentStatus(): AuditStorageStatus
}
