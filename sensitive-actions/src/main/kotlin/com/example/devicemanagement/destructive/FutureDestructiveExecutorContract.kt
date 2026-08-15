package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Narrow future destructive executor type boundary.
 *
 * There is no general [create] mint and no public companion/object mint
 * for the handoff bundle or the final permit. The exported types are
 * sealed interfaces. Only file-private issued implementations in this
 * file can be constructed, and only
 * [FutureDestructiveRealChainBoundary] can ask those implementations
 * to mint after live validation.
 *
 * [FutureDestructiveExecutorContract.execute] consumes a single-use
 * registered bundle. A reflected or caller-constructed instance is not
 * registered and cannot authorize [onAuthorizedHandoff]. Production
 * bytecode allows [execute] only from
 * [FutureDestructiveRealChainBoundary.assembleAndHandoff].
 *
 * There is no Android policy-manager implementation of this contract
 * in this checkpoint. Production DeviceManagement does not implement or
 * wire it.
 */
internal typealias FutureDestructiveExecutorContract =
    FutureDestructiveRealChainBoundary.FutureDestructiveExecutorContract

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
 * Opaque process-local real-chain execution bundle. The only value a
 * [FutureDestructiveExecutorContract] may accept. Not serializable.
 * Sealed: no companion mint on this type. The issued implementation is
 * file-private and cannot be named from other same-module files.
 */
internal sealed interface FutureDestructiveExecutionBundle

/**
 * Ultra-short-lived process-local permit minted only after fresh live
 * validation. Never returned to callers. Never persisted. Sealed: no
 * companion [create] on this type. The issued implementation is
 * file-private and cannot be named from other same-module files.
 */
internal sealed interface RealChainFinalLiveValidationPermit

/**
 * Production stand-in. Not an executor. Does not extend
 * [FutureDestructiveExecutorContract]. Constructing this object does not
 * wipe, authorize, or hand off.
 */
internal object UnwiredFutureDestructiveExecutor

private object HandoffRegistry {
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

private class IssuedRealChainFinalLiveValidationPermit private constructor() :
    RealChainFinalLiveValidationPermit {
    companion object {
        fun mintFinalLiveValidationPermit(): IssuedRealChainFinalLiveValidationPermit {
            val permit = IssuedRealChainFinalLiveValidationPermit()
            HandoffRegistry.registerIssuedPermit(permit)
            return permit
        }
    }
}

private class IssuedFutureDestructiveExecutionBundle private constructor() :
    FutureDestructiveExecutionBundle {
    companion object {
        fun assembleBundleFromPermit(
            permit: RealChainFinalLiveValidationPermit,
        ): IssuedFutureDestructiveExecutionBundle? {
            if (!HandoffRegistry.consumeIssuedPermit(permit)) {
                return null
            }
            val bundle = IssuedFutureDestructiveExecutionBundle()
            HandoffRegistry.registerIssuedBundle(bundle)
            return bundle
        }
    }
}

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
 * [RuntimeDurablePreExecutionCommitAuthority] performs the paired append
 * through the trusted runtime durability capability. Production still
 * does not wire this boundary or an executor. Append failure means no
 * permit, no bundle, and no executor call.
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
     * Abstract future executor. [execute] is the only entry. It consumes
     * a registered bundle and only then calls [onAuthorizedHandoff].
     * Production bytecode allows [execute] only from [assembleAndHandoff]
     * and [onAuthorizedHandoff] only from [execute].
     */
    abstract class FutureDestructiveExecutorContract {
        fun execute(
            bundle: FutureDestructiveExecutionBundle,
        ): FutureDestructiveHandoffAcknowledgement {
            if (!HandoffRegistry.consumeIssuedBundle(bundle)) {
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

    /**
     * Sole boundary-side permit mint. Not [create]. Callable only from
     * [assembleAndHandoff] after fresh live validation. Production
     * bytecode allows this call only from that method.
     */
    private fun mintFinalLiveValidationPermit(): RealChainFinalLiveValidationPermit {
        return IssuedRealChainFinalLiveValidationPermit.mintFinalLiveValidationPermit()
    }

    /**
     * Sole boundary-side bundle assembler. Not [create]. Consumes the
     * exact live permit minted by [mintFinalLiveValidationPermit].
     * Production bytecode allows this call only from [assembleAndHandoff].
     */
    private fun assembleBundleFromPermit(
        permit: RealChainFinalLiveValidationPermit,
    ): FutureDestructiveExecutionBundle? {
        return IssuedFutureDestructiveExecutionBundle.assembleBundleFromPermit(permit)
    }
}
