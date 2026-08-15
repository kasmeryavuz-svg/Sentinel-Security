package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.security.SecureRandom
import java.util.IdentityHashMap

/**
 * Process-local challenge identity. Distinct from the nonce bytes so a
 * confirmation can be bound to one issued challenge object.
 */
internal class DestructiveChallengeIdentity private constructor() {
    companion object {
        fun create(): DestructiveChallengeIdentity = DestructiveChallengeIdentity()
    }
}

/**
 * Authority-issued operator challenge. Opaque process-local material, not
 * a fixed magic string. Caller-constructed instances are not registered.
 * Issuing a challenge never returns a confirmation, response, or approval.
 */
internal class DestructiveOperatorChallenge private constructor(
    val identity: DestructiveChallengeIdentity,
    private val nonce: ByteArray,
) {
    fun nonceCopy(): ByteArray = nonce.copyOf()

    companion object {
        fun create(nonce: ByteArray): DestructiveOperatorChallenge {
            return DestructiveOperatorChallenge(
                identity = DestructiveChallengeIdentity.create(),
                nonce = nonce.copyOf(),
            )
        }
    }
}

/**
 * Distinct human/operator confirmation. Not issued with the challenge.
 * Caller-constructed instances are not registered and cannot mint approval.
 */
internal class DestructiveHumanConfirmation private constructor() {
    companion object {
        fun create(): DestructiveHumanConfirmation = DestructiveHumanConfirmation()
    }
}

/**
 * Process-local destructive human approval. Separate from reversible
 * [com.example.devicemanagement.action.Approval]. Never authorization by
 * itself. Dies with the process.
 */
internal class DestructiveHumanApproval private constructor() {
    companion object {
        fun create(): DestructiveHumanApproval = DestructiveHumanApproval()
    }
}

internal sealed interface DestructiveChallengeIssueResult {
    data class Issued(
        val challenge: DestructiveOperatorChallenge,
    ) : DestructiveChallengeIssueResult

    data class Failed(val reason: String) : DestructiveChallengeIssueResult
}

internal sealed interface DestructiveHumanConfirmationResult {
    data class Confirmed(
        val confirmation: DestructiveHumanConfirmation,
    ) : DestructiveHumanConfirmationResult

    data class Failed(val reason: String) : DestructiveHumanConfirmationResult
}

internal sealed interface DestructiveHumanApprovalResult {
    data class Approved(val approval: DestructiveHumanApproval) : DestructiveHumanApprovalResult

    data class Failed(val reason: String) : DestructiveHumanApprovalResult
}

internal sealed interface DestructiveHumanApprovalCheck {
    data object Accepted : DestructiveHumanApprovalCheck

    data class Rejected(val reason: String) : DestructiveHumanApprovalCheck
}

/**
 * Sole mint path for a destructive human confirmation. Production bytecode
 * allows this call only from [DestructiveHumanConfirmationAuthority.confirm].
 * [DestructiveHumanApprovalAuthority] cannot invoke it. Production
 * composition does not call confirm, so this mint stays unwired.
 */
internal object DestructiveHumanConfirmationMint {
    private val issued = IdentityHashMap<DestructiveHumanConfirmation, ConfirmationRecord>()

    @JvmStatic
    @Synchronized
    fun issueFromTrustedConfirmationSource(
        challenge: DestructiveOperatorChallenge,
        correlationId: DestructiveCorrelationId,
        binding: DestructiveTargetBinding,
        scope: DestructiveScope,
        artifactIdentity: DestructiveArtifactIdentity,
        attemptLease: DestructiveAttemptLease,
        issuedAtMonotonicMillis: Long,
    ): DestructiveHumanConfirmation? {
        if (binding.correlationId != correlationId) {
            return null
        }
        if (binding.scope != scope) {
            return null
        }
        if (artifactIdentity.buildPurpose !=
            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION
        ) {
            return null
        }
        val confirmation = DestructiveHumanConfirmation.create()
        issued[confirmation] = ConfirmationRecord(
            challenge = challenge,
            challengeIdentity = challenge.identity,
            correlationId = correlationId,
            binding = binding,
            scope = scope,
            artifactIdentity = artifactIdentity,
            attemptLease = attemptLease,
            issuedAtMonotonicMillis = issuedAtMonotonicMillis,
        )
        return confirmation
    }

    @Synchronized
    fun consume(confirmation: DestructiveHumanConfirmation): ConfirmationRecord? {
        return issued.remove(confirmation)
    }

    internal data class ConfirmationRecord(
        val challenge: DestructiveOperatorChallenge,
        val challengeIdentity: DestructiveChallengeIdentity,
        val correlationId: DestructiveCorrelationId,
        val binding: DestructiveTargetBinding,
        val scope: DestructiveScope,
        val artifactIdentity: DestructiveArtifactIdentity,
        val attemptLease: DestructiveAttemptLease,
        val issuedAtMonotonicMillis: Long,
    )
}

/**
 * Distinct trusted confirmation authority. This is not
 * [DestructiveHumanApprovalAuthority]. Holding the approval authority does
 * not construct or invoke this type. Production DeviceManagement does not
 * mint here. [UnwiredDestructiveHumanConfirmationSource] returns no
 * confirmation.
 */
internal class DestructiveHumanConfirmationAuthority {
    fun confirm(
        challenge: DestructiveOperatorChallenge,
        correlationId: DestructiveCorrelationId,
        binding: DestructiveTargetBinding,
        scope: DestructiveScope,
        artifactIdentity: DestructiveArtifactIdentity,
        attemptLease: DestructiveAttemptLease,
        nowMonotonicMillis: Long,
    ): DestructiveHumanConfirmationResult {
        if (binding.correlationId != correlationId) {
            return DestructiveHumanConfirmationResult.Failed("human_confirmation_correlation_mismatch")
        }
        if (binding.scope != scope) {
            return DestructiveHumanConfirmationResult.Failed("human_confirmation_scope_mismatch")
        }
        if (artifactIdentity.buildPurpose !=
            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION
        ) {
            return DestructiveHumanConfirmationResult.Failed(
                "human_confirmation_artifact_purpose_not_disposable_validation",
            )
        }
        val confirmation = DestructiveHumanConfirmationMint.issueFromTrustedConfirmationSource(
            challenge = challenge,
            correlationId = correlationId,
            binding = binding,
            scope = scope,
            artifactIdentity = artifactIdentity,
            attemptLease = attemptLease,
            issuedAtMonotonicMillis = nowMonotonicMillis,
        ) ?: return DestructiveHumanConfirmationResult.Failed("human_confirmation_mint_rejected")
        return DestructiveHumanConfirmationResult.Confirmed(confirmation)
    }
}

/**
 * Separate destructive human-approval domain. Test code may construct this
 * authority. Production DeviceManagement composition does not mint here.
 *
 * [issueChallenge] returns challenge material only. It never returns a
 * confirmation, response, token, or approval. Redeem requires a distinct
 * [DestructiveHumanConfirmation] minted by
 * [DestructiveHumanConfirmationAuthority], bound to correlation ID, target
 * binding, scope, artifact identity, attempt lease, and challenge identity.
 * A Boolean cannot authorize. Reversible Approval cannot satisfy this type.
 */
internal class DestructiveHumanApprovalAuthority(
    private val monotonicTimeSource: MonotonicTimeSource,
    private val nonceGenerator: () -> ByteArray = Companion::unpredictableNonce,
    private val maxAgeMillis: Long = MAX_APPROVAL_AGE_MILLIS,
) {
    private val challenges = IdentityHashMap<DestructiveOperatorChallenge, ChallengeRecord>()
    private val approvals = IdentityHashMap<DestructiveHumanApproval, ApprovalRecord>()

    @Synchronized
    fun issueChallenge(
        correlationId: DestructiveCorrelationId,
        binding: DestructiveTargetBinding,
        scope: DestructiveScope,
        artifactIdentity: DestructiveArtifactIdentity,
        attemptLease: DestructiveAttemptLease,
    ): DestructiveChallengeIssueResult {
        if (binding.correlationId != correlationId) {
            return DestructiveChallengeIssueResult.Failed("human_approval_correlation_mismatch")
        }
        if (binding.scope != scope) {
            return DestructiveChallengeIssueResult.Failed("human_approval_scope_mismatch")
        }
        if (artifactIdentity.buildPurpose !=
            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION
        ) {
            return DestructiveChallengeIssueResult.Failed(
                "human_approval_artifact_purpose_not_disposable_validation",
            )
        }
        val nonce = nonceGenerator().copyOf()
        if (nonce.size < MIN_NONCE_BYTES || nonce.all { it == 0.toByte() }) {
            return DestructiveChallengeIssueResult.Failed("human_approval_challenge_material_unusable")
        }
        val challenge = DestructiveOperatorChallenge.create(nonce)
        val now = monotonicTimeSource.nowMillis()
        challenges[challenge] = ChallengeRecord(
            correlationId = correlationId,
            binding = binding,
            scope = scope,
            artifactIdentity = artifactIdentity,
            attemptLease = attemptLease,
            challengeIdentity = challenge.identity,
            issuedAtMonotonicMillis = now,
        )
        return DestructiveChallengeIssueResult.Issued(challenge)
    }

    @Synchronized
    fun redeem(
        challenge: DestructiveOperatorChallenge,
        confirmation: DestructiveHumanConfirmation,
        correlationId: DestructiveCorrelationId,
        binding: DestructiveTargetBinding,
        scope: DestructiveScope,
        artifactIdentity: DestructiveArtifactIdentity,
        attemptLease: DestructiveAttemptLease,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): DestructiveHumanApprovalResult {
        val confirmationRecord = DestructiveHumanConfirmationMint.consume(confirmation)
            ?: return DestructiveHumanApprovalResult.Failed(
                "human_approval_confirmation_not_issued_or_already_used",
            )
        if (confirmationRecord.challenge !== challenge ||
            confirmationRecord.challengeIdentity !== challenge.identity
        ) {
            challenges.remove(challenge)
            return DestructiveHumanApprovalResult.Failed("human_approval_confirmation_challenge_mismatch")
        }
        val record = challenges.remove(challenge)
            ?: return DestructiveHumanApprovalResult.Failed("human_approval_challenge_not_issued_or_already_used")
        if (record.challengeIdentity !== challenge.identity) {
            return DestructiveHumanApprovalResult.Failed("human_approval_challenge_identity_mismatch")
        }
        bindingMismatch(record, correlationId, binding, scope, artifactIdentity, attemptLease)?.let { reason ->
            return DestructiveHumanApprovalResult.Failed(reason)
        }
        bindingMismatch(
            ChallengeRecord(
                correlationId = confirmationRecord.correlationId,
                binding = confirmationRecord.binding,
                scope = confirmationRecord.scope,
                artifactIdentity = confirmationRecord.artifactIdentity,
                attemptLease = confirmationRecord.attemptLease,
                challengeIdentity = confirmationRecord.challengeIdentity,
                issuedAtMonotonicMillis = confirmationRecord.issuedAtMonotonicMillis,
            ),
            correlationId,
            binding,
            scope,
            artifactIdentity,
            attemptLease,
        )?.let { reason ->
            return DestructiveHumanApprovalResult.Failed(reason)
        }
        freshnessFailure(record.issuedAtMonotonicMillis, nowMonotonicMillis)?.let { reason ->
            return DestructiveHumanApprovalResult.Failed(reason)
        }
        freshnessFailure(confirmationRecord.issuedAtMonotonicMillis, nowMonotonicMillis)?.let { reason ->
            return DestructiveHumanApprovalResult.Failed(reason)
        }
        val approval = DestructiveHumanApproval.create()
        approvals[approval] = ApprovalRecord(
            correlationId = record.correlationId,
            binding = record.binding,
            scope = record.scope,
            artifactIdentity = record.artifactIdentity,
            attemptLease = record.attemptLease,
            issuedAtMonotonicMillis = nowMonotonicMillis,
        )
        return DestructiveHumanApprovalResult.Approved(approval)
    }

    @Synchronized
    fun consume(
        approval: DestructiveHumanApproval,
        expectedCorrelationId: DestructiveCorrelationId,
        expectedBinding: DestructiveTargetBinding,
        expectedScope: DestructiveScope,
        expectedIdentity: DestructiveArtifactIdentity,
        expectedLease: DestructiveAttemptLease,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): DestructiveHumanApprovalCheck {
        val record = approvals.remove(approval)
            ?: return DestructiveHumanApprovalCheck.Rejected(
                "human_approval_not_issued_or_already_consumed",
            )
        bindingMismatch(
            record.toChallengeRecord(),
            expectedCorrelationId,
            expectedBinding,
            expectedScope,
            expectedIdentity,
            expectedLease,
        )?.let { reason ->
            return DestructiveHumanApprovalCheck.Rejected(reason)
        }
        freshnessFailure(record.issuedAtMonotonicMillis, nowMonotonicMillis)?.let { reason ->
            return DestructiveHumanApprovalCheck.Rejected(reason)
        }
        return DestructiveHumanApprovalCheck.Accepted
    }

    @Synchronized
    fun invalidate(approval: DestructiveHumanApproval) {
        approvals.remove(approval)
    }

    private fun bindingMismatch(
        record: ChallengeRecord,
        correlationId: DestructiveCorrelationId,
        binding: DestructiveTargetBinding,
        scope: DestructiveScope,
        artifactIdentity: DestructiveArtifactIdentity,
        attemptLease: DestructiveAttemptLease,
    ): String? {
        if (record.attemptLease !== attemptLease) {
            return "human_approval_attempt_lease_mismatch"
        }
        if (record.correlationId != correlationId) {
            return "human_approval_correlation_mismatch"
        }
        if (record.binding != binding) {
            return "human_approval_target_mismatch"
        }
        if (record.scope != scope) {
            return "human_approval_scope_mismatch"
        }
        if (record.artifactIdentity != artifactIdentity) {
            return "human_approval_artifact_identity_mismatch"
        }
        return null
    }

    private fun freshnessFailure(issuedAt: Long, now: Long): String? {
        val age = now - issuedAt
        if (age < 0L) {
            return "human_approval_negative_monotonic_delta"
        }
        if (age > maxAgeMillis) {
            return "human_approval_stale"
        }
        return null
    }

    private data class ChallengeRecord(
        val correlationId: DestructiveCorrelationId,
        val binding: DestructiveTargetBinding,
        val scope: DestructiveScope,
        val artifactIdentity: DestructiveArtifactIdentity,
        val attemptLease: DestructiveAttemptLease,
        val challengeIdentity: DestructiveChallengeIdentity,
        val issuedAtMonotonicMillis: Long,
    )

    private data class ApprovalRecord(
        val correlationId: DestructiveCorrelationId,
        val binding: DestructiveTargetBinding,
        val scope: DestructiveScope,
        val artifactIdentity: DestructiveArtifactIdentity,
        val attemptLease: DestructiveAttemptLease,
        val issuedAtMonotonicMillis: Long,
    ) {
        fun toChallengeRecord(): ChallengeRecord {
            return ChallengeRecord(
                correlationId = correlationId,
                binding = binding,
                scope = scope,
                artifactIdentity = artifactIdentity,
                attemptLease = attemptLease,
                challengeIdentity = DestructiveChallengeIdentity.create(),
                issuedAtMonotonicMillis = issuedAtMonotonicMillis,
            )
        }
    }

    internal companion object {
        const val MAX_APPROVAL_AGE_MILLIS = 5_000L
        const val MIN_NONCE_BYTES = 32

        fun unpredictableNonce(): ByteArray {
            val nonce = ByteArray(MIN_NONCE_BYTES)
            SecureRandom().nextBytes(nonce)
            return nonce
        }
    }
}

/**
 * Required fields for a later, separately approved per-attempt trusted
 * confirmation record. No such record exists. This type has no production
 * mint.
 */
internal class TrustedPerAttemptConfirmationFacts private constructor(
    val operatorIdentity: String,
    val utcTimestamp: String,
    val deviceSerial: String,
    val packageName: String,
    val adminComponent: String,
    val certificateSha256: String,
    val artifactSha256: String,
    val scope: DestructiveScope,
    val flagsLiteralZero: Int,
    val buildRevision: String,
    val oneAttemptOnly: Boolean,
    val attemptId: String,
    val validUntilUtc: String,
    val challenge: DestructiveOperatorChallenge,
    val correlationId: DestructiveCorrelationId,
    val binding: DestructiveTargetBinding,
    val artifactIdentity: DestructiveArtifactIdentity,
    val attemptLease: DestructiveAttemptLease,
    val nowMonotonicMillis: Long,
)

/**
 * Production trusted per-attempt confirmation record. Fail-closed until a
 * separately approved real record exists. Observed identity, Boolean
 * shortcuts, and caller-constructed values cannot populate this source.
 */
internal object TrustedPerAttemptDestructiveConfirmationRecord {
    fun current(): TrustedPerAttemptConfirmationFacts? = null
}

/**
 * Production confirmation boundary. Structurally wired to
 * [DestructiveHumanConfirmationAuthority.confirm], but returns null until
 * [TrustedPerAttemptDestructiveConfirmationRecord.current] yields a real
 * trusted record. DeviceManagement and UI do not invoke this type.
 */
internal object ProductionDestructiveHumanConfirmationSource {
    fun confirm(
        correlationId: DestructiveCorrelationId,
        binding: DestructiveTargetBinding,
        scope: DestructiveScope,
        artifactIdentity: DestructiveArtifactIdentity,
        nowMonotonicMillis: Long,
    ): DestructiveHumanConfirmation? {
        val trusted = TrustedPerAttemptDestructiveConfirmationRecord.current()
        if (trusted == null) {
            return null
        }
        if (trusted.operatorIdentity.isBlank() ||
            trusted.utcTimestamp.isBlank() ||
            trusted.deviceSerial.isBlank() ||
            trusted.packageName != binding.runningPackage ||
            trusted.adminComponent != binding.expectedAdminComponent ||
            trusted.certificateSha256.isBlank() ||
            trusted.artifactSha256.isBlank() ||
            trusted.scope != scope ||
            trusted.scope != DestructiveScope.DEVICE_FACTORY_RESET ||
            trusted.flagsLiteralZero != 0 ||
            trusted.buildRevision.isBlank() ||
            !trusted.oneAttemptOnly ||
            trusted.attemptId.isBlank() ||
            trusted.validUntilUtc.isBlank() ||
            trusted.correlationId != correlationId ||
            trusted.binding != binding ||
            trusted.artifactIdentity != artifactIdentity
        ) {
            return null
        }
        val minted = DestructiveHumanConfirmationAuthority().confirm(
            challenge = trusted.challenge,
            correlationId = trusted.correlationId,
            binding = trusted.binding,
            scope = trusted.scope,
            artifactIdentity = trusted.artifactIdentity,
            attemptLease = trusted.attemptLease,
            nowMonotonicMillis = nowMonotonicMillis,
        )
        return when (minted) {
            is DestructiveHumanConfirmationResult.Confirmed -> minted.confirmation
            is DestructiveHumanConfirmationResult.Failed -> null
        }
    }
}

/**
 * Production confirmation source. Not invoked by DeviceManagement or UI.
 * Constructing this object does not confirm or approve anything. No
 * disposable-device human confirmation is recorded.
 */
internal object UnwiredDestructiveHumanConfirmationSource {
    fun confirm(
        challenge: DestructiveOperatorChallenge,
        correlationId: DestructiveCorrelationId,
        binding: DestructiveTargetBinding,
        scope: DestructiveScope,
        artifactIdentity: DestructiveArtifactIdentity,
        attemptLease: DestructiveAttemptLease,
    ): DestructiveHumanConfirmation? = null
}

/**
 * Production mint source for destructive human approval. Not invoked by
 * DeviceManagement or UI. Constructing this object does not approve anything.
 */
internal object UnwiredDestructiveHumanApprovalMint
