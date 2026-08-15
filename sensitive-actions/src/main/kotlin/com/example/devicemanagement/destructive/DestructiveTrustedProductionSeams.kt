package com.example.devicemanagement.destructive

/**
 * Bound production seam for the trusted artifact expectation. Production
 * [retainForProduction] constructs the null source internally. Test
 * implementations exist only in test sources and must not appear in
 * release DEX.
 */
internal interface DestructiveTrustedArtifactExpectationSource {
    fun trustedExpectation(): DestructiveArtifactIdentityExpectation?
}

/**
 * Result of consuming a one-attempt trusted confirmation record against
 * the exact authority-issued challenge and authorized attempt lease.
 */
internal sealed interface DestructiveTrustedConfirmationRecordConsumeResult {
    data class Available(
        val facts: TrustedPerAttemptConfirmationFacts,
    ) : DestructiveTrustedConfirmationRecordConsumeResult

    data object Missing : DestructiveTrustedConfirmationRecordConsumeResult

    data object AlreadyConsumed : DestructiveTrustedConfirmationRecordConsumeResult
}

/**
 * Bound production seam for the one-shot trusted per-attempt confirmation
 * record. Callers cannot supply a substitute record object.
 */
internal interface DestructiveTrustedPerAttemptConfirmationRecordSource {
    fun consumeMatching(
        challenge: DestructiveOperatorChallenge,
        attemptLease: DestructiveAttemptLease,
    ): DestructiveTrustedConfirmationRecordConsumeResult
}

/**
 * Trusted UTC wall clock for confirmation timestamp checks. Distinct from
 * the process-local monotonic clock used after UTC validation succeeds.
 */
internal interface DestructiveUtcClock {
    fun nowEpochMillis(): Long
}

/**
 * Bound production seam for the approved disposable-device build
 * revision. Production records none.
 */
internal interface DestructiveTrustedApprovedBuildRevisionSource {
    fun recorded(): String?
}

/**
 * Production artifact-expectation source. Delegates to the dedicated
 * trusted validation source, which remains null. This type does not mint.
 */
internal class ProductionDestructiveTrustedArtifactExpectationSource internal constructor() :
    DestructiveTrustedArtifactExpectationSource {
    override fun trustedExpectation(): DestructiveArtifactIdentityExpectation? {
        return TrustedDestructiveArtifactValidationSource.trustedExpectation()
    }
}

/**
 * Production one-shot confirmation-record source. Probes the dedicated
 * null record and never yields a consumable production record.
 */
internal class ProductionDestructiveTrustedPerAttemptConfirmationRecordSource internal constructor() :
    DestructiveTrustedPerAttemptConfirmationRecordSource {
    override fun consumeMatching(
        challenge: DestructiveOperatorChallenge,
        attemptLease: DestructiveAttemptLease,
    ): DestructiveTrustedConfirmationRecordConsumeResult {
        TrustedPerAttemptDestructiveConfirmationRecord.current()
        return DestructiveTrustedConfirmationRecordConsumeResult.Missing
    }
}

/**
 * Production UTC clock. Uses the device epoch clock; it does not record a
 * device serial or mint confirmation timestamps.
 */
internal class ProductionDestructiveUtcClock internal constructor() : DestructiveUtcClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

/**
 * Production approved-build source. No disposable-device build revision is
 * recorded.
 */
internal class ProductionDestructiveApprovedBuildRevisionSource internal constructor() :
    DestructiveTrustedApprovedBuildRevisionSource {
    override fun recorded(): String? = null
}
