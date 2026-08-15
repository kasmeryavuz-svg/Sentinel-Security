package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource

/**
 * Trusted production retainer for the Checkpoint 19B disposable-device
 * factory-reset implementation.
 *
 * Holding this object keeps the executor and port reachable in the
 * production process. It does not expose a UI command, does not call
 * [FutureDestructiveRealChainBoundary.assembleAndHandoff], and does not
 * assemble the real chain while the disposable-device artifact digest
 * remains unrecorded.
 *
 * Production bytecode allows [retainForProduction] only from
 * DeviceManagement composition.
 */
class ProductionDestructiveRetainer internal constructor(
    internal val factoryResetPort: AuthorizedFactoryResetPort,
    internal val executor: FutureDestructiveExecutorContract,
    internal val durability: RuntimeDestructiveSafetyDurability?,
    internal val liveFacts: DestructiveLiveFactsSource,
    internal val boundary: FutureDestructiveRealChainBoundary?,
)

object ProductionDestructiveRealChain {
    fun retainForProduction(
        factoryReset: AuthorizedFactoryResetPort?,
        liveFacts: DestructiveLiveFactsSource,
        clock: MonotonicTimeSource,
        durability: RuntimeDestructiveSafetyDurability?,
    ): ProductionDestructiveRetainer {
        val port = factoryReset ?: UnavailableAuthorizedFactoryResetPort
        val executor = AndroidFutureDestructiveExecutor(port)
        val boundary = assembleIfPossible(
            durability = durability,
            liveFacts = liveFacts,
            clock = clock,
        )
        return ProductionDestructiveRetainer(
            factoryResetPort = port,
            executor = executor,
            durability = durability,
            liveFacts = liveFacts,
            boundary = boundary,
        )
    }

    private fun assembleIfPossible(
        durability: RuntimeDestructiveSafetyDurability?,
        liveFacts: DestructiveLiveFactsSource,
        clock: MonotonicTimeSource,
    ): FutureDestructiveRealChainBoundary? {
        val expected = TrustedDestructiveArtifactValidationSource.trustedExpectation()
            ?: return null
        if (durability == null) {
            return null
        }
        val cooldown = DestructiveDenyOnlyCooldown(
            store = durability.cooldown.markerStore(),
            monotonicTimeSource = clock,
        )
        val admission = DestructiveAttemptAdmissionAuthority(
            cooldown = cooldown,
            monotonicTimeSource = clock,
        )
        val arming = DestructiveArmingAuthority(
            monotonicTimeSource = clock,
            admissionAuthority = admission,
        )
        return FutureDestructiveRealChainBoundary(
            durability = durability,
            authorizationAuthority = DestructiveAuthorizationAuthority(
                armingAuthority = arming,
                monotonicTimeSource = clock,
                admissionAuthority = admission,
            ),
            admissionAuthority = admission,
            armingAuthority = arming,
            artifactAuthority = DestructiveArtifactIdentityAuthority(
                expected = expected,
                monotonicTimeSource = clock,
            ),
            humanApprovalAuthority = DestructiveHumanApprovalAuthority(
                monotonicTimeSource = clock,
            ),
            wipePolicyAuthority = DestructiveWipeOptionPolicyAuthority(),
            liveFactsSource = liveFacts,
            cooldown = cooldown,
            monotonicTimeSource = clock,
        )
    }
}
