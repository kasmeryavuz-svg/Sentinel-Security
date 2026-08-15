package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveLifecycleRestartTest {
    @Test
    fun `reconstructed services cannot resume an armed request`() {
        val first = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = first.admitBindAuthorize(binding)
        val reconstructed = DestructiveSimulationComposition.create()

        val check = reconstructed.armingAuthority.requireLive(
            authorized.armToken,
            binding,
            authorized.attemptLease,
        )
        assertEquals(
            "arm_not_issued_or_already_consumed",
            (check as ArmingCheck.Dead).reason,
        )
        assertEquals(
            "attempt_lease_not_issued_or_already_consumed",
            (
                reconstructed.admissionAuthority.requireLive(authorized.attemptLease, binding)
                    as AttemptLeaseCheck.Dead
                ).reason,
        )
        assertEquals(0, reconstructed.sink.invocationCount())
    }

    @Test
    fun `reconstructed services cannot consume a pre-restart capability`() {
        val first = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = first.admitBindAuthorize(binding)
        val reconstructed = DestructiveSimulationComposition.create()

        val consume = reconstructed.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        )
        assertEquals(
            "capability_not_issued_or_already_consumed",
            (consume as DestructiveCapabilityConsumption.Rejected).reason,
        )
        val executed = reconstructed.executor.execute(
            authorized.capability,
            binding,
            authorized.attemptLease,
        )
        assertEquals(DestructiveSimulationOutcome.REJECTED, executed.outcome)
        assertEquals(0, reconstructed.sink.invocationCount())
    }

    @Test
    fun `pre-execution evidence cannot resume execution after reconstruction`() {
        val sharedEvidence = InMemoryDestructiveSimulationEvidenceWriter()
        val first = DestructiveSimulationComposition.create(evidenceWriter = sharedEvidence)
        first.pipeline.submit(validRequest())
        assertTrue(
            sharedEvidence.records().any { it.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED },
        )

        val reconstructed = DestructiveSimulationComposition.create(evidenceWriter = sharedEvidence)
        assertEquals(0, reconstructed.sink.invocationCount())
        assertTrue(sharedEvidence.records().isNotEmpty())
        assertEquals(
            "capability_not_issued_or_already_consumed",
            (
                reconstructed.authorizationAuthority.consume(
                    DestructiveCapability.create(),
                    verifiedBinding(),
                    DestructiveAttemptLease.create(),
                ) as DestructiveCapabilityConsumption.Rejected
                ).reason,
        )
        assertEquals(0, reconstructed.sink.invocationCount())
    }

    @Test
    fun `audit recovery cannot create authority or call the sink`() {
        val source = java.io.File("src/main/kotlin/com/example/devicemanagement/recovery")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertTrue(!source.contains("DestructiveArmingAuthority"))
        assertTrue(!source.contains("DestructiveAuthorizationAuthority"))
        assertTrue(!source.contains("DestructiveAttemptAdmissionAuthority"))
        assertTrue(!source.contains("DestructiveFinalExecutionGate"))
        assertTrue(!source.contains("DestructiveCapability"))
        assertTrue(!source.contains("DestructiveAttemptLease"))
        assertTrue(!source.contains("ConsumedDestructiveAuthorizationProof"))
        assertTrue(!source.contains("CountedAttemptProof"))
        assertTrue(!source.contains("PreExecutionEvidenceCommitProof"))
        assertTrue(!source.contains("FinalExecutionPermit"))
        assertTrue(!source.contains("SimulatedDestructiveExecutor"))
        assertTrue(!source.contains("Checkpoint17ASimulationSink"))
        assertTrue(!source.contains("DenyOnlyCooldownMarkerStore"))
        assertTrue(!source.contains("TrustedRuntimeDenyOnlyCooldownMarkerStore"))
        assertTrue(!source.contains("DurableDestructivePreExecutionRepository"))
        assertTrue(!source.contains("DestructivePreExecutionDurableStore"))
        assertTrue(!source.contains("RuntimeDenyOnlyCooldownStore"))
        assertTrue(!source.contains("RuntimeDestructivePreExecutionStore"))
        assertTrue(!source.contains("RuntimeDestructiveSafetyDurability"))
        assertTrue(!source.contains("AndroidDestructiveSafetyPersistence"))
        assertTrue(!source.contains("DestructiveArtifactIdentityAuthority"))
        assertTrue(!source.contains("DestructiveHumanApprovalAuthority"))
        assertTrue(!source.contains("DestructiveWipeOptionPolicy"))
        assertTrue(!source.contains("FutureDestructiveExecutorContract"))
        assertTrue(!source.contains("FutureDestructiveRealChainBoundary"))
        assertTrue(!source.contains("assembleAndHandoff"))
        assertTrue(!source.contains("RuntimeDurablePreExecutionCommitProof"))
        assertTrue(!source.contains("RealChainFinalLiveValidationPermit"))
        assertTrue(!source.contains("DestructiveWipeOptionPolicyProof"))
    }

    @Test
    fun `crash after durable pre-execution append cannot replay or invoke`() {
        val state = SharedDestructivePreExecutionDurableState()
        val first = DestructiveSimulationComposition.create(
            durableStore = InMemoryDestructivePreExecutionDurableStore(state),
        )
        val binding = verifiedBinding()
        val authorized = first.admitBindAuthorize(binding)
        val consumed = first.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val committed = first.preExecutionAuthority.commit(
            evidence = simulationEvidence(
                correlationId = binding.correlationId.value,
                phase = DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED,
                presentationWallClockMillis = 0L,
                binding = binding,
            ),
            binding = binding,
            attemptLease = consumed.attemptLease,
        )
        assertTrue(committed is PreExecutionEvidenceCommitResult.Committed)
        assertTrue(state.rows.isNotEmpty())

        val reconstructed = DestructiveSimulationComposition.create(
            durableStore = InMemoryDestructivePreExecutionDurableStore(state),
        )
        assertEquals(0, reconstructed.sink.invocationCount())
        val replay = reconstructed.executor.execute(
            authorized.capability,
            binding,
            authorized.attemptLease,
        )
        assertEquals(DestructiveSimulationOutcome.REJECTED, replay.outcome)
        assertEquals(0, reconstructed.sink.invocationCount())
    }

    @Test
    fun `successful simulation does not automatically invoke a second time after reconstruction`() {
        val state = SharedDestructivePreExecutionDurableState()
        val first = DestructiveSimulationComposition.create(
            durableStore = InMemoryDestructivePreExecutionDurableStore(state),
        )
        assertEquals(
            DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE,
            first.pipeline.submit(validRequest()).outcome,
        )
        assertEquals(1, first.sink.invocationCount())

        val reconstructed = DestructiveSimulationComposition.create(
            durableStore = InMemoryDestructivePreExecutionDurableStore(state),
        )
        assertEquals(0, reconstructed.sink.invocationCount())
        assertTrue(state.rows.isNotEmpty())
    }

    @Test
    fun `new process requires a new request even when evidence survives`() {
        val state = SharedDenyOnlyMarkerState()
        val store = InMemoryDenyOnlyCooldownMarkerStore(state)
        val first = DestructiveSimulationComposition.create(store = store)
        val firstResult = first.pipeline.submit(validRequest())
        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, firstResult.outcome)

        val reconstructed = DestructiveSimulationComposition.create(
            store = InMemoryDenyOnlyCooldownMarkerStore(state),
        )
        val second = reconstructed.pipeline.submit(validRequest())
        assertEquals("cooldown_active", second.reason)
        assertEquals(0, reconstructed.sink.invocationCount())
    }
}
