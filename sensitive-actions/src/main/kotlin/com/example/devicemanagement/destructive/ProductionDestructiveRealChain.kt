package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource

/**
 * Trusted production retainer for the Checkpoint 19B disposable-device
 * factory-reset implementation and the Checkpoint 19D real-chain
 * assembly path.
 *
 * Holding this object keeps the executor, port, and production
 * orchestrator reachable in the production process. DeviceManagement
 * composition does not call
 * [ProductionDestructiveRealChainOrchestrator.assembleAlreadyBoundDeviceFactoryReset]
 * and does not expose that method on the public facade.
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
    internal val orchestrator: ProductionDestructiveRealChainOrchestrator,
)

internal class ProductionRealChainAssemblyMaterials(
    val boundary: FutureDestructiveRealChainBoundary,
    val admission: DestructiveAttemptAdmissionAuthority,
    val arming: DestructiveArmingAuthority,
    val authorization: DestructiveAuthorizationAuthority,
    val artifactAuthority: DestructiveArtifactIdentityAuthority,
    val humanApprovalAuthority: DestructiveHumanApprovalAuthority,
    val wipePolicyAuthority: DestructiveWipeOptionPolicyAuthority,
) {
    fun authorize(binding: DestructiveTargetBinding): DestructiveAuthorizationResult {
        val admitted = admission.admit(binding.correlationId, binding.scope)
        if (admitted !is AttemptAdmissionResult.Admitted) {
            return DestructiveAuthorizationResult.Rejected(
                (admitted as AttemptAdmissionResult.Rejected).reason,
            )
        }
        val bound = admission.bindTarget(admitted.lease, binding)
        if (bound !is AttemptBindResult.Bound) {
            return DestructiveAuthorizationResult.Rejected(
                (bound as AttemptBindResult.Rejected).reason,
            )
        }
        val armed = arming.arm(binding, admitted.lease)
        if (armed !is ArmingIssueResult.Armed) {
            return DestructiveAuthorizationResult.Rejected(
                (armed as ArmingIssueResult.Rejected).reason,
            )
        }
        return authorization.authorize(armed.token, binding, admitted.lease)
    }
}

internal fun assembleProductionRealChainBoundary(
    expected: DestructiveArtifactIdentityExpectation,
    durability: RuntimeDestructiveSafetyDurability,
    liveFacts: DestructiveLiveFactsSource,
    clock: MonotonicTimeSource,
): FutureDestructiveRealChainBoundary {
    return assembleProductionRealChainMaterials(
        expected = expected,
        durability = durability,
        liveFacts = liveFacts,
        clock = clock,
    ).boundary
}

internal fun assembleProductionRealChainMaterials(
    expected: DestructiveArtifactIdentityExpectation,
    durability: RuntimeDestructiveSafetyDurability,
    liveFacts: DestructiveLiveFactsSource,
    clock: MonotonicTimeSource,
): ProductionRealChainAssemblyMaterials {
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
    val authorization = DestructiveAuthorizationAuthority(
        armingAuthority = arming,
        monotonicTimeSource = clock,
        admissionAuthority = admission,
    )
    val artifactAuthority = DestructiveArtifactIdentityAuthority(
        expected = expected,
        monotonicTimeSource = clock,
    )
    val humanApprovalAuthority = DestructiveHumanApprovalAuthority(
        monotonicTimeSource = clock,
    )
    val wipePolicyAuthority = DestructiveWipeOptionPolicyAuthority()
    val boundary = FutureDestructiveRealChainBoundary(
        durability = durability,
        authorizationAuthority = authorization,
        admissionAuthority = admission,
        armingAuthority = arming,
        artifactAuthority = artifactAuthority,
        humanApprovalAuthority = humanApprovalAuthority,
        wipePolicyAuthority = wipePolicyAuthority,
        liveFactsSource = liveFacts,
        cooldown = cooldown,
        monotonicTimeSource = clock,
    )
    return ProductionRealChainAssemblyMaterials(
        boundary = boundary,
        admission = admission,
        arming = arming,
        authorization = authorization,
        artifactAuthority = artifactAuthority,
        humanApprovalAuthority = humanApprovalAuthority,
        wipePolicyAuthority = wipePolicyAuthority,
    )
}

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
        val artifactExpectationSource = ProductionDestructiveTrustedArtifactExpectationSource()
        val confirmationSource = ProductionDestructiveHumanConfirmationSource(
            recordSource = ProductionDestructiveTrustedPerAttemptConfirmationRecordSource(),
            utcClock = ProductionDestructiveUtcClock(),
            approvedBuildRevision = ProductionDestructiveApprovedBuildRevisionSource(),
            liveFacts = liveFacts,
            artifactExpectationSource = artifactExpectationSource,
        )
        val orchestrator = ProductionDestructiveRealChainOrchestrator(
            executor = executor,
            liveFacts = liveFacts,
            clock = clock,
            durability = durability,
            artifactExpectationSource = artifactExpectationSource,
            confirmationSource = confirmationSource,
        )
        return ProductionDestructiveRetainer(
            factoryResetPort = port,
            executor = executor,
            durability = durability,
            liveFacts = liveFacts,
            boundary = boundary,
            orchestrator = orchestrator,
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
        return assembleProductionRealChainBoundary(
            expected = expected,
            durability = durability,
            liveFacts = liveFacts,
            clock = clock,
        )
    }
}
