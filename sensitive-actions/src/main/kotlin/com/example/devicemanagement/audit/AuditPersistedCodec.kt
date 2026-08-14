package com.example.devicemanagement.audit

/**
 * Decode persisted audit rows. Unknown or malformed phase values are storage
 * corruption, not a synthesized terminal outcome.
 *
 * This codec is an implementation artifact. It is not present on the app
 * compile classpath and must never authorize DevicePolicyManager changes.
 */
object AuditPersistedCodec {
    private val terminalPhases = setOf(
        AuditEventPhase.APPLIED,
        AuditEventPhase.REJECTED,
        AuditEventPhase.FAILED,
        AuditEventPhase.SIMULATED,
    )

    fun decodePhase(raw: String?): AuditEventPhase {
        if (raw.isNullOrBlank()) {
            throw AuditStoreException("audit phase is missing")
        }
        val phase = runCatching { AuditEventPhase.valueOf(raw) }.getOrNull()
            ?: throw AuditStoreException("unreadable audit phase")
        return phase
    }

    fun tryDecodePhase(raw: String?): AuditEventPhase? {
        return try {
            decodePhase(raw)
        } catch (_: AuditStoreException) {
            null
        }
    }

    fun isTerminal(phase: AuditEventPhase): Boolean = phase in terminalPhases
}
