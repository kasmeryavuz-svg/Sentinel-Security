package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource

/**
 * Narrow future destructive executor entrypoint.
 *
 * The only input is [FutureDestructiveExecutionBundle]. This interface
 * accepts no Boolean, raw digest string, reversible Approval, generic
 * persistence, caller-selected artifact hash, recovered authority, or
 * simulation-only proof type.
 *
 * There is no Android policy-manager implementation of this contract
 * in this checkpoint. Production DeviceManagement does not implement or
 * wire it.
 */
internal interface FutureDestructiveExecutorContract {
    fun execute(bundle: FutureDestructiveExecutionBundle): FutureDestructiveHandoffAcknowledgement
}

/**
 * Opaque process-local real-chain execution bundle. The only value a
 * [FutureDestructiveExecutorContract] may accept. Not serializable. Not
 * reconstructable. Caller-created instances are not assembled by
 * [FutureDestructiveRealChainBoundary] and cannot be obtained from a
 * public assemble API.
 */
internal class FutureDestructiveExecutionBundle private constructor() {
    companion object {
        fun create(): FutureDestructiveExecutionBundle = FutureDestructiveExecutionBundle()
    }
}

/**
 * Ultra-short-lived process-local permit issued only after a fresh live
 * validation that itself happens after the runtime-durable pre-execution
 * proof is consumed. Never returned to callers. Never persisted.
 */
internal class RealChainFinalLiveValidationPermit private constructor() {
    companion object {
        fun create(): RealChainFinalLiveValidationPermit = RealChainFinalLiveValidationPermit()
    }
}

internal sealed interface FutureDestructiveHandoffAcknowledgement {
    data class Refused(val reason: String) : FutureDestructiveHandoffAcknowledgement
}

internal sealed interface FutureDestructiveHandoffResult {
    data class Failed(val reason: String) : FutureDestructiveHandoffResult

    data class Acknowledged(
        val acknowledgement: FutureDestructiveHandoffAcknowledgement,
    ) : FutureDestructiveHandoffResult
}

/**
 * Production stand-in. Not an executor. Does not implement
 * [FutureDestructiveExecutorContract]. Constructing this object does not
 * wipe, authorize, or hand off.
 */
internal object UnwiredFutureDestructiveExecutor

/**
 * Future real-chain boundary. Structurally requires
 * [RuntimeDestructiveSafetyDurability] at construction. The only public
 * progression method is synchronous [assembleAndHandoff]: it never
 * returns the bundle or the final permit, never hops threads, and never
 * calls an Android policy manager.
 *
 * [assembleAndHandoff] cannot complete in this checkpoint because
 * [RuntimeDurablePreExecutionCommitAuthority] has no issuer. That is
 * intentional: no fake durable evidence is recorded.
 */
internal class FutureDestructiveRealChainBoundary(
    private val durability: RuntimeDestructiveSafetyDurability,
    private val authorizationAuthority: DestructiveAuthorizationAuthority,
    private val admissionAuthority: DestructiveAttemptAdmissionAuthority,
    private val armingAuthority: DestructiveArmingAuthority,
    private val artifactAuthority: DestructiveArtifactIdentityAuthority,
    private val humanApprovalAuthority: DestructiveHumanApprovalAuthority,
    private val wipePolicyAuthority: DestructiveWipeOptionPolicyAuthority,
    private val liveFactsSource: DestructiveLiveFactsSource,
    private val cooldown: DestructiveDenyOnlyCooldown,
    private val monotonicTimeSource: MonotonicTimeSource,
) {
    private val runtimePreExecutionAuthority = RuntimeDurablePreExecutionCommitAuthority(durability)

    fun assembleAndHandoff(
        executor: FutureDestructiveExecutorContract,
        binding: DestructiveTargetBinding,
        attemptLease: DestructiveAttemptLease,
        capability: DestructiveCapability,
        armToken: DestructiveArmingToken,
        artifactMatchProof: DestructiveArtifactIdentityMatchProof,
        observedIdentity: DestructiveArtifactIdentity,
        humanApproval: DestructiveHumanApproval,
        runtimePreExecutionProof: RuntimeDurablePreExecutionCommitProof,
        wipeOptionPolicyProof: DestructiveWipeOptionPolicyProof,
    ): FutureDestructiveHandoffResult {
        durability.cooldown
        durability.preExecution
        if (binding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return FutureDestructiveHandoffResult.Failed("real_chain_scope_denied")
        }
        when (val wipe = wipePolicyAuthority.consume(wipeOptionPolicyProof, binding.scope)) {
            is WipeOptionPolicyCheck.Rejected -> {
                return FutureDestructiveHandoffResult.Failed(wipe.reason)
            }
            WipeOptionPolicyCheck.Accepted -> Unit
        }
        when (val artifact = artifactAuthority.consume(artifactMatchProof, observedIdentity)) {
            is ArtifactIdentityCheck.Rejected -> {
                return FutureDestructiveHandoffResult.Failed(artifact.reason)
            }
            ArtifactIdentityCheck.Accepted -> Unit
        }
        when (
            val approval = humanApprovalAuthority.consume(
                approval = humanApproval,
                expectedCorrelationId = binding.correlationId,
                expectedBinding = binding,
                expectedScope = binding.scope,
                expectedIdentity = observedIdentity,
                expectedLease = attemptLease,
            )
        ) {
            is DestructiveHumanApprovalCheck.Rejected -> {
                return FutureDestructiveHandoffResult.Failed(approval.reason)
            }
            DestructiveHumanApprovalCheck.Accepted -> Unit
        }
        val consumed = when (
            val consumption = authorizationAuthority.consume(
                capability = capability,
                expectedBinding = binding,
                expectedLease = attemptLease,
            )
        ) {
            is DestructiveCapabilityConsumption.Rejected -> {
                return FutureDestructiveHandoffResult.Failed(consumption.reason)
            }
            is DestructiveCapabilityConsumption.Accepted -> consumption
        }
        when (
            val runtime = runtimePreExecutionAuthority.consume(
                proof = runtimePreExecutionProof,
                expectedBinding = consumed.binding,
                expectedLease = consumed.attemptLease,
            )
        ) {
            is RuntimeDurablePreExecutionCheck.Rejected -> {
                authorizationAuthority.invalidateProof(consumed.proof)
                return FutureDestructiveHandoffResult.Failed(runtime.reason)
            }
            RuntimeDurablePreExecutionCheck.Accepted -> Unit
        }
        val facts = try {
            liveFactsSource.currentFacts()
        } catch (_: Throwable) {
            authorizationAuthority.invalidateProof(consumed.proof)
            return FutureDestructiveHandoffResult.Failed("live_facts_unavailable")
        }
        DestructiveTargetRules.denyReason(consumed.binding, facts)?.let { reason ->
            authorizationAuthority.invalidateProof(consumed.proof)
            return FutureDestructiveHandoffResult.Failed(reason)
        }
        when (
            val fresh = authorizationAuthority.requireConsumedFresh(
                proof = consumed.proof,
                expectedBinding = consumed.binding,
                expectedArmToken = armToken,
                expectedLease = consumed.attemptLease,
                nowMonotonicMillis = monotonicTimeSource.nowMillis(),
            )
        ) {
            is ConsumedAuthorizationCheck.Rejected -> {
                return FutureDestructiveHandoffResult.Failed(fresh.reason)
            }
            is ConsumedAuthorizationCheck.Accepted -> Unit
        }
        when (val admitted = admissionAuthority.requireLive(consumed.attemptLease, consumed.binding)) {
            is AttemptLeaseCheck.Dead -> {
                return FutureDestructiveHandoffResult.Failed(admitted.reason)
            }
            is AttemptLeaseCheck.Live -> Unit
        }
        when (
            val arm = armingAuthority.requireLive(
                token = armToken,
                expectedBinding = consumed.binding,
                expectedLease = consumed.attemptLease,
            )
        ) {
            is ArmingCheck.Dead -> {
                return FutureDestructiveHandoffResult.Failed(arm.reason)
            }
            is ArmingCheck.Live -> Unit
        }
        when (val marker = cooldown.assertCurrentAttemptMarkerPresent()) {
            is CooldownUsable.Unusable -> {
                return FutureDestructiveHandoffResult.Failed(marker.reason)
            }
            CooldownUsable.Usable -> Unit
        }
        RealChainFinalLiveValidationPermit.create()
        val bundle = FutureDestructiveExecutionBundle.create()
        return when (val acknowledgement = executor.execute(bundle)) {
            is FutureDestructiveHandoffAcknowledgement.Refused -> {
                FutureDestructiveHandoffResult.Acknowledged(acknowledgement)
            }
        }
    }
}
