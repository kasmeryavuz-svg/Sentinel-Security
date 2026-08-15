package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.security.SecureRandom
import java.util.IdentityHashMap

/**
 * Authority-issued operator challenge. Opaque process-local material, not
 * a fixed magic string. Caller-constructed instances are not registered.
 */
internal class DestructiveOperatorChallenge private constructor(
    private val nonce: ByteArray,
) {
    fun nonceCopy(): ByteArray = nonce.copyOf()

    companion object {
        fun create(nonce: ByteArray): DestructiveOperatorChallenge {
            return DestructiveOperatorChallenge(nonce.copyOf())
        }
    }
}

/**
 * Authority-issued response bound to one challenge. Not a reusable secret.
 */
internal class DestructiveOperatorChallengeResponse private constructor() {
    companion object {
        fun create(): DestructiveOperatorChallengeResponse = DestructiveOperatorChallengeResponse()
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
        val response: DestructiveOperatorChallengeResponse,
    ) : DestructiveChallengeIssueResult

    data class Failed(val reason: String) : DestructiveChallengeIssueResult
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
 * Separate destructive human-approval domain. Test code may construct this
 * authority. Production DeviceManagement composition does not mint here.
 *
 * Approval is explicit, short-lived, single-use, and bound to correlation
 * ID, target binding, scope, artifact identity, and the pending attempt
 * lease. A Boolean cannot authorize. Reversible Approval cannot satisfy
 * this type.
 */
internal class DestructiveHumanApprovalAuthority(
    private val monotonicTimeSource: MonotonicTimeSource,
    private val nonceGenerator: () -> ByteArray = Companion::unpredictableNonce,
    private val maxAgeMillis: Long = MAX_APPROVAL_AGE_MILLIS,
) {
    private val challenges = IdentityHashMap<DestructiveOperatorChallenge, ChallengeRecord>()
    private val responses = IdentityHashMap<DestructiveOperatorChallengeResponse, DestructiveOperatorChallenge>()
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
        val response = DestructiveOperatorChallengeResponse.create()
        val now = monotonicTimeSource.nowMillis()
        challenges[challenge] = ChallengeRecord(
            correlationId = correlationId,
            binding = binding,
            scope = scope,
            artifactIdentity = artifactIdentity,
            attemptLease = attemptLease,
            issuedAtMonotonicMillis = now,
        )
        responses[response] = challenge
        return DestructiveChallengeIssueResult.Issued(challenge, response)
    }

    @Synchronized
    fun redeem(
        challenge: DestructiveOperatorChallenge,
        response: DestructiveOperatorChallengeResponse,
        correlationId: DestructiveCorrelationId,
        binding: DestructiveTargetBinding,
        scope: DestructiveScope,
        artifactIdentity: DestructiveArtifactIdentity,
        attemptLease: DestructiveAttemptLease,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): DestructiveHumanApprovalResult {
        val boundChallenge = responses.remove(response)
            ?: return DestructiveHumanApprovalResult.Failed("human_approval_response_not_issued_or_already_used")
        if (boundChallenge !== challenge) {
            challenges.remove(challenge)
            return DestructiveHumanApprovalResult.Failed("human_approval_response_challenge_mismatch")
        }
        val record = challenges.remove(challenge)
            ?: return DestructiveHumanApprovalResult.Failed("human_approval_challenge_not_issued_or_already_used")
        bindingMismatch(record, correlationId, binding, scope, artifactIdentity, attemptLease)?.let { reason ->
            return DestructiveHumanApprovalResult.Failed(reason)
        }
        freshnessFailure(record.issuedAtMonotonicMillis, nowMonotonicMillis)?.let { reason ->
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
 * Production mint source for destructive human approval. Not invoked by
 * DeviceManagement or UI. Constructing this object does not approve anything.
 */
internal object UnwiredDestructiveHumanApprovalMint
