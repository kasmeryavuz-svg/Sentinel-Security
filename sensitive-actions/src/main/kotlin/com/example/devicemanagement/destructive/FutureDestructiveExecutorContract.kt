package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Narrow future destructive executor type boundary.
 *
 * There is no general [create] mint for the handoff bundle or the final
 * permit. Those values are nested private-constructor types of
 * [FutureDestructiveRealChainBoundary] and can be assembled only after a
 * live permit is minted by that boundary.
 *
 * [execute] consumes a single-use registered bundle. A reflected or
 * caller-constructed instance is not registered and cannot authorize
 * [onAuthorizedHandoff]. Production bytecode allows [execute] only from
 * [FutureDestructiveRealChainBoundary.assembleAndHandoff].
 *
 * There is no Android policy-manager implementation of this contract
 * in this checkpoint. Production DeviceManagement does not implement or
 * wire it.
 */
internal typealias FutureDestructiveExecutorContract =
    FutureDestructiveRealChainBoundary.FutureDestructiveExecutorContract

internal typealias FutureDestructiveExecutionBundle =
    FutureDestructiveRealChainBoundary.FutureDestructiveExecutionBundle

internal typealias RealChainFinalLiveValidationPermit =
    FutureDestructiveRealChainBoundary.RealChainFinalLiveValidationPermit

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
 * Production stand-in. Not an executor. Does not extend
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
 * After capability consumption the boundary invokes
 * [RuntimeDurablePreExecutionCommitAuthority.commitAfterConsumedAuthorization].
 * It does not accept a caller-supplied runtime pre-execution proof.
 *
 * [assembleAndHandoff] cannot complete in this checkpoint because
 * [RuntimeDurablePreExecutionCommitAuthority] has no issuer. That is
 * intentional: no fake durable evidence is recorded. Append failure
 * means no permit, no bundle, and no executor call.
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
    /**
     * Opaque process-local real-chain execution bundle. The only value a
     * [FutureDestructiveExecutorContract] may accept. Not serializable.
     * No companion mint. Only [assembleBundleFromPermit] can construct
     * and register an instance, and only from an exact live permit.
     */
    class FutureDestructiveExecutionBundle private constructor()

    /**
     * Ultra-short-lived process-local permit minted only by
     * [mintFinalLiveValidationPermit] after fresh live validation.
     * Never returned to callers. Never persisted. No companion mint.
     */
    class RealChainFinalLiveValidationPermit private constructor()

    abstract class FutureDestructiveExecutorContract {
        fun execute(
            bundle: FutureDestructiveExecutionBundle,
        ): FutureDestructiveHandoffAcknowledgement {
            if (!consumeIssuedBundle(bundle)) {
                return FutureDestructiveHandoffAcknowledgement.Refused(
                    "forged_or_consumed_bundle",
                )
            }
            return onAuthorizedHandoff()
        }

        protected abstract fun onAuthorizedHandoff(): FutureDestructiveHandoffAcknowledgement
    }

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
        val committed = when (
            val commit = runtimePreExecutionAuthority.commitAfterConsumedAuthorization(
                consumedProof = consumed.proof,
                expectedBinding = consumed.binding,
                expectedLease = consumed.attemptLease,
                expectedArmToken = consumed.armToken,
                authorizationAuthority = authorizationAuthority,
            )
        ) {
            is RuntimeDurablePreExecutionCommitResult.Failed -> {
                authorizationAuthority.invalidateProof(consumed.proof)
                return FutureDestructiveHandoffResult.Failed(commit.reason)
            }
            is RuntimeDurablePreExecutionCommitResult.Committed -> commit
        }
        when (
            val runtime = runtimePreExecutionAuthority.consume(
                proof = committed.proof,
                expectedBinding = consumed.binding,
                expectedLease = consumed.attemptLease,
                expectedConsumedProof = consumed.proof,
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
        val permit = mintFinalLiveValidationPermit()
        val bundle = assembleBundleFromPermit(permit)
            ?: return FutureDestructiveHandoffResult.Failed("real_chain_permit_not_live")
        return when (val acknowledgement = executor.execute(bundle)) {
            is FutureDestructiveHandoffAcknowledgement.Refused -> {
                FutureDestructiveHandoffResult.Acknowledged(acknowledgement)
            }
        }
    }

    private fun mintFinalLiveValidationPermit(): RealChainFinalLiveValidationPermit {
        val permit = RealChainFinalLiveValidationPermit()
        registerIssuedPermit(permit)
        return permit
    }

    private fun assembleBundleFromPermit(
        permit: RealChainFinalLiveValidationPermit,
    ): FutureDestructiveExecutionBundle? {
        if (!consumeIssuedPermit(permit)) {
            return null
        }
        val bundle = FutureDestructiveExecutionBundle()
        registerIssuedBundle(bundle)
        return bundle
    }

    private companion object {
        private val issuedPermits =
            IdentityHashMap<RealChainFinalLiveValidationPermit, Unit>()
        private val issuedBundles =
            IdentityHashMap<FutureDestructiveExecutionBundle, Unit>()

        @Synchronized
        fun registerIssuedPermit(permit: RealChainFinalLiveValidationPermit) {
            issuedPermits[permit] = Unit
        }

        @Synchronized
        fun consumeIssuedPermit(permit: RealChainFinalLiveValidationPermit): Boolean {
            return issuedPermits.remove(permit) != null
        }

        @Synchronized
        fun registerIssuedBundle(bundle: FutureDestructiveExecutionBundle) {
            issuedBundles[bundle] = Unit
        }

        @Synchronized
        fun consumeIssuedBundle(bundle: FutureDestructiveExecutionBundle): Boolean {
            return issuedBundles.remove(bundle) != null
        }
    }
}
