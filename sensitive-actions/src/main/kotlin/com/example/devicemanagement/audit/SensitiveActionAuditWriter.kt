package com.example.devicemanagement.audit

/**
 * Trusted-pipeline append surface. This type is not present on the app compile
 * classpath and must never be used to authorize, approve, or mutate policy.
 *
 * Audit state is evidence only. Persistence success or failure must not grant
 * Device Owner status, consume approvals, or call DevicePolicyManager.
 */
interface SensitiveActionAuditWriter {
    fun append(request: AuditAppendRequest): AuditAppendResult
}

data class AuditAppendRequest(
    val eventId: String,
    val correlationId: String,
    val actionName: String,
    val phase: AuditEventPhase,
    val presentationWallClockMillis: Long,
    val reasonCode: AuditReasonCode?,
)

sealed interface AuditAppendResult {
    data class Persisted(
        val sequence: Long,
        val eventId: String,
    ) : AuditAppendResult

    data class Failed(
        val health: AuditStorageHealth,
    ) : AuditAppendResult
}
