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
 * expectation, a challenge, an attempt lease, or a destructive approval.
 * The method obtains the authorized lease and authority-issued challenge
 * internally, then requests confirmation against those exact identities.
 */
internal class ProductionDestructiveRealChainOrchestrator internal constructor(
    private val executor: AndroidFutureDestructiveExecutor,
    private val liveFacts: DestructiveLiveFactsSource,
    private val clock: MonotonicTimeSource,
    private val durability: RuntimeDestructiveSafetyDurability?,
    private val artifactExpectationSource: DestructiveTrustedArtifactExpectationSource,
    private val confirmationSource: ProductionDestructiveHumanConfirmationSource,
) {
    fun assembleAlreadyBoundDeviceFactoryReset(
        boundAttempt: ProductionBoundDeviceFactoryResetAttempt,
    ): FutureDestructiveHandoffResult {
        if (boundAttempt.binding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return FutureDestructiveHandoffResult.Failed("real_chain_scope_denied")
        }
        val expected = artifactExpectationSource.trustedExpectation()
            ?: return FutureDestructiveHandoffResult.Failed("missing_trusted_artifact_expectation")
        val runtimeDurability = durability
            ?: return FutureDestructiveHandoffResult.Failed("runtime_durability_unavailable")
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
        val issued = assembled.humanApprovalAuthority.issueChallenge(
            correlationId = boundAttempt.binding.correlationId,
            binding = boundAttempt.binding,
            scope = boundAttempt.binding.scope,
            artifactIdentity = boundAttempt.observedIdentity,
            attemptLease = authorized.attemptLease,
        )
        if (issued !is DestructiveChallengeIssueResult.Issued) {
            val failed = issued as DestructiveChallengeIssueResult.Failed
            return FutureDestructiveHandoffResult.Failed(failed.reason)
        }
        var mintedConfirmation: DestructiveHumanConfirmation? = null
        var mintedApproval: DestructiveHumanApproval? = null
        try {
            val confirmationResult = confirmationSource.confirm(
                correlationId = boundAttempt.binding.correlationId,
                binding = boundAttempt.binding,
                scope = boundAttempt.binding.scope,
                artifactIdentity = boundAttempt.observedIdentity,
                challenge = issued.challenge,
                attemptLease = authorized.attemptLease,
                nowMonotonicMillis = clock.nowMillis(),
            )
            val confirmation = when (confirmationResult) {
                is DestructiveHumanConfirmationResult.Confirmed -> confirmationResult.confirmation
                is DestructiveHumanConfirmationResult.Failed -> {
                    return FutureDestructiveHandoffResult.Failed(confirmationResult.reason)
                }
            }
            mintedConfirmation = confirmation
            val redeemed = assembled.humanApprovalAuthority.redeem(
                challenge = issued.challenge,
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
            mintedConfirmation = null
            mintedApproval = humanApproval
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
            val handoff = assembled.boundary.assembleAndHandoff(
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
            if (handoff is FutureDestructiveHandoffResult.Acknowledged) {
                mintedApproval = null
            }
            return handoff
        } finally {
            mintedConfirmation?.let { leftover ->
                DestructiveHumanConfirmationMint.consume(leftover)
            }
            mintedApproval?.let { leftover ->
                assembled.humanApprovalAuthority.invalidate(leftover)
            }
            assembled.humanApprovalAuthority.abandon(issued.challenge)
        }
    }
}
