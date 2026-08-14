package com.example.devicemanagement.ui

import com.example.devicemanagement.action.ActionResult

/**
 * NON-PERSISTENT session history for the current process only.
 *
 * Entries live in memory and are discarded when the process or app restarts.
 * This is not an audit log and must not be written to durable storage.
 * Persistent or auditable storage belongs to a later checkpoint.
 *
 * The optional timestamp function is presentation-only. It is not an
 * authorization clock and is never used to approve a mutation.
 */
class SessionActivityStore(
    private val sessionTimestampMillis: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val entries = ArrayDeque<SessionActivityEntry>()

    fun record(
        capability: PolicyCapability,
        requestedDisabled: Boolean,
        result: ActionResult,
    ): SessionActivityEntry {
        val entry = SessionActivityEntry(
            capability = capability,
            requestedDisabled = requestedDisabled,
            outcome = result.toPresentationOutcome(),
            correlationId = result.presentationCorrelationId(),
            sessionTimestampMillis = sessionTimestampMillis(),
            reason = result.presentationReason(),
        )
        entries.addFirst(entry)
        while (entries.size > maxEntries) {
            entries.removeLast()
        }
        return entry
    }

    fun entries(): List<SessionActivityEntry> = entries.toList()

    companion object {
        const val DEFAULT_MAX_ENTRIES = 20
        const val UNAVAILABLE_CORRELATION_ID = "unavailable"
    }
}

data class SessionActivityEntry(
    val capability: PolicyCapability,
    val requestedDisabled: Boolean,
    val outcome: OperationOutcomePresentation,
    val correlationId: String,
    val sessionTimestampMillis: Long,
    val reason: String?,
)

internal fun ActionResult.toPresentationOutcome(): OperationOutcomePresentation {
    return when (this) {
        is ActionResult.Applied -> OperationOutcomePresentation.APPLIED
        is ActionResult.Rejected -> OperationOutcomePresentation.DENIED
        is ActionResult.Failed -> OperationOutcomePresentation.FAILED
        is ActionResult.Simulated -> OperationOutcomePresentation.FAILED
    }
}

internal fun ActionResult.presentationCorrelationId(): String {
    return when (this) {
        is ActionResult.Applied -> correlationId
        is ActionResult.Rejected ->
            correlationId ?: SessionActivityStore.UNAVAILABLE_CORRELATION_ID
        is ActionResult.Failed ->
            correlationId ?: SessionActivityStore.UNAVAILABLE_CORRELATION_ID
        is ActionResult.Simulated -> correlationId
    }
}

internal fun ActionResult.presentationReason(): String? {
    return when (this) {
        is ActionResult.Applied -> null
        is ActionResult.Rejected -> reason
        is ActionResult.Failed -> reason
        is ActionResult.Simulated -> message
    }
}
