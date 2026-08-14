package com.example.devicemanagement.recovery

/**
 * Read-only classification of durable audit evidence after a later process
 * start. This API cannot approve, retry, replay, submit, or execute actions.
 *
 * An unmatched REQUESTED event is interrupted evidence only. It is never an
 * ActionRequest, approval, or authorization to continue the original work.
 */
enum class RecoveryInspectionHealth {
    HEALTHY,
    UNAVAILABLE,
}

data class InterruptedRequest(
    val correlationId: String,
    val actionName: String,
    val sequence: Long,
    val presentationWallClockMillis: Long,
)

data class RecoveryInspection(
    val health: RecoveryInspectionHealth,
    val interruptedCount: Int,
    val interruptedCorrelationIds: List<String>,
    val interruptedRequests: List<InterruptedRequest>,
)

fun interface RecoveryInspectionProvider {
    fun inspect(): RecoveryInspection
}
