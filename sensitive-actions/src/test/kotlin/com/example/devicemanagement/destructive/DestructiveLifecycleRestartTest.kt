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
