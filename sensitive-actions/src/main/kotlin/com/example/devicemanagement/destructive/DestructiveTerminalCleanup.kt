package com.example.devicemanagement.destructive

/**
 * Explicit terminal close for a destructive attempt. Invalidates the
 * process-local lease, any remaining arm, and any remaining consumed
 * authorization proof. Never clears or shortens the persisted deny-only
 * cooldown.
 */
internal class DestructiveTerminalCleanup(
    private val admissionAuthority: DestructiveAttemptAdmissionAuthority,
    private val armingAuthority: DestructiveArmingAuthority,
    private val authorizationAuthority: DestructiveAuthorizationAuthority,
    private val preExecutionAuthority: PreExecutionEvidenceCommitAuthority? = null,
) {
    fun close(
        lease: DestructiveAttemptLease?,
        armToken: DestructiveArmingToken? = null,
        consumptionProof: ConsumedDestructiveAuthorizationProof? = null,
    ) {
        if (armToken != null) {
            armingAuthority.invalidate(armToken)
        }
        if (lease != null) {
            armingAuthority.invalidateForLease(lease)
            authorizationAuthority.invalidateForLease(lease)
            preExecutionAuthority?.invalidateForLease(lease)
            admissionAuthority.close(lease)
        }
        if (consumptionProof != null) {
            authorizationAuthority.invalidateProof(consumptionProof)
        }
    }
}
