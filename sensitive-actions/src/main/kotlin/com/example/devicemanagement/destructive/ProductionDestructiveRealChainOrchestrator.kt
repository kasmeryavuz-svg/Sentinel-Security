package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource

/**
 * Opaque already-bound DEVICE_FACTORY_RESET attempt.
 *
 * This type carries only the target binding and observed artifact identity
 * for one attempt. It is not an approval, not a trusted artifact
 * expectation, not wipe flags, not an executor, and not authorization.
 * Observed identity cannot become a trusted expectation.
 */
internal class ProductionBoundDeviceFactoryResetAttempt private constructor(
    internal val binding: DestructiveTargetBinding,
    internal val observedIdentity: DestructiveArtifactIdentity,
) {
    companion object {
        fun bindAlreadyAuthorizedDeviceFactoryReset(
            binding: DestructiveTargetBinding,
            observedIdentity: DestructiveArtifactIdentity,
        ): ProductionBoundDeviceFactoryResetAttempt? {
            if (binding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
                return null
            }
            return ProductionBoundDeviceFactoryResetAttempt(
                binding = binding,
                observedIdentity = observedIdentity,
            )
        }
    }
}

/**
 * Narrow internal production orchestration for the Checkpoint 19D
 * real-chain assembly path.
 *
 * The only destructive progression method is
 * [assembleAlreadyBoundDeviceFactoryReset]. Production bytecode binds
 * [FutureDestructiveRealChainBoundary.assembleAndHandoff] exclusively
 * to that method and authorizes no production trigger origin for the
 * progression method itself.
 *
 * Callers cannot supply wipe flags, an executor, a trusted artifact
 * expectation, or a destructive approval. Missing trusted artifact
 * expectation and missing per-attempt confirmation fail closed.
 */
internal class ProductionDestructiveRealChainOrchestrator internal constructor(
    private val executor: AndroidFutureDestructiveExecutor,
    private val liveFacts: DestructiveLiveFactsSource,
    private val clock: MonotonicTimeSource,
    private val durability: RuntimeDestructiveSafetyDurability?,
) {
    fun assembleAlreadyBoundDeviceFactoryReset(
        boundAttempt: ProductionBoundDeviceFactoryResetAttempt,
    ): FutureDestructiveHandoffResult {
        if (boundAttempt.binding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return FutureDestructiveHandoffResult.Failed("real_chain_scope_denied")
        }
        val expected = TrustedDestructiveArtifactValidationSource.trustedExpectation()
        val confirmation = ProductionDestructiveHumanConfirmationSource.confirm(
            correlationId = boundAttempt.binding.correlationId,
            binding = boundAttempt.binding,
            scope = boundAttempt.binding.scope,
            artifactIdentity = boundAttempt.observedIdentity,
            nowMonotonicMillis = clock.nowMillis(),
        )
        if (expected == null) {
            return FutureDestructiveHandoffResult.Failed("missing_trusted_artifact_expectation")
        }
        if (confirmation == null) {
            return FutureDestructiveHandoffResult.Failed("missing_per_attempt_human_confirmation")
        }
        val runtimeDurability = durability
        if (runtimeDurability == null) {
            return FutureDestructiveHandoffResult.Failed("runtime_durability_unavailable")
        }
        val assembled = assembleProductionRealChainMaterials(
            expected = expected,
            durability = runtimeDurability,
            liveFacts = liveFacts,
            clock = clock,
        )
        val authorized = assembled.authorize(boundAttempt.binding)
        if (authorized !is DestructiveAuthorizationResult.Authorized) {
            val rejected = authorized as DestructiveAuthorizationResult.Rejected
            return FutureDestructiveHandoffResult.Failed(rejected.reason)
        }
        val artifactAdmitted = assembled.artifactAuthority.admit(boundAttempt.observedIdentity)
        val artifactProof = when (artifactAdmitted) {
            is ArtifactIdentityAdmitResult.Admitted -> artifactAdmitted.proof
            is ArtifactIdentityAdmitResult.Failed -> {
                return FutureDestructiveHandoffResult.Failed(artifactAdmitted.reason)
            }
        }
        val challenge = assembled.humanApprovalAuthority.issueChallenge(
            correlationId = boundAttempt.binding.correlationId,
            binding = boundAttempt.binding,
            scope = boundAttempt.binding.scope,
            artifactIdentity = boundAttempt.observedIdentity,
            attemptLease = authorized.attemptLease,
        )
        if (challenge !is DestructiveChallengeIssueResult.Issued) {
            val failed = challenge as DestructiveChallengeIssueResult.Failed
            return FutureDestructiveHandoffResult.Failed(failed.reason)
        }
        val redeemed = assembled.humanApprovalAuthority.redeem(
            challenge = challenge.challenge,
            confirmation = confirmation,
            correlationId = boundAttempt.binding.correlationId,
            binding = boundAttempt.binding,
            scope = boundAttempt.binding.scope,
            artifactIdentity = boundAttempt.observedIdentity,
            attemptLease = authorized.attemptLease,
        )
        val humanApproval = when (redeemed) {
            is DestructiveHumanApprovalResult.Approved -> redeemed.approval
            is DestructiveHumanApprovalResult.Failed -> {
                return FutureDestructiveHandoffResult.Failed(redeemed.reason)
            }
        }
        val wipeVerified = assembled.wipePolicyAuthority.verifyDefaultDeny(
            boundAttempt.binding.scope,
            emptySet(),
        )
        val wipeProof = when (wipeVerified) {
            is WipeOptionPolicyVerifyResult.Verified -> wipeVerified.proof
            is WipeOptionPolicyVerifyResult.Failed -> {
                return FutureDestructiveHandoffResult.Failed(wipeVerified.reason)
            }
        }
        return assembled.boundary.assembleAndHandoff(
            executor = executor,
            binding = boundAttempt.binding,
            attemptLease = authorized.attemptLease,
            capability = authorized.capability,
            armToken = authorized.armToken,
            artifactMatchProof = artifactProof,
            observedIdentity = boundAttempt.observedIdentity,
            humanApproval = humanApproval,
            wipeOptionPolicyProof = wipeProof,
        )
    }
}
