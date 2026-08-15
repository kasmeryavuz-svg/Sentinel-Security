package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Serializable

class FinalExecutionPermitTest {
    @Test
    fun `there is no raw binding-only permit minting API`() {
        val types = listOf(
            DestructiveFinalExecutionGate::class.java,
            Checkpoint17ASimulationSink::class.java,
            SimulatedDestructiveExecutor::class.java,
        )
        types.forEach { type ->
            assertFalse(
                type.methods.any { method ->
                    method.name == "issue" &&
                        method.parameterTypes.contentEquals(arrayOf(DestructiveTargetBinding::class.java))
                },
            )
        }
        assertFalse(
            DestructiveFinalExecutionGate::class.java.methods.any { method ->
                method.name == "issue"
            },
        )
        val validate = DestructiveFinalExecutionGate::class.java.declaredMethods
            .filter { it.name == "validateAndIssue" }
        assertTrue(validate.isNotEmpty())
        validate.forEach { method ->
            assertTrue(PreExecutionEvidenceCommitProof::class.java in method.parameterTypes)
        }
    }

    @Test
    fun `raw permit construction plus sink cannot invoke without the gate`() {
        val composition = DestructiveSimulationComposition.create()
        val denied = composition.sink.invoke(FinalExecutionPermit.create(), verifiedBinding())
        assertTrue(denied is SimulationSinkResult.Denied)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `consume then final gate without pre-execution evidence proof issues no permit`() {
        val composition = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        val consumed = composition.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted

        val issued = composition.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = PreExecutionEvidenceCommitProof.create(),
        )

        assertTrue(issued is FinalExecutionGateResult.Failed)
        assertEquals(
            "pre_execution_evidence_not_committed_or_already_consumed",
            (issued as FinalExecutionGateResult.Failed).reason,
        )
        assertEquals(0, composition.sink.invocationCount())
        assertTrue(
            composition.evidenceWriter.records().none {
                it.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED
            },
        )
    }

    @Test
    fun `successful evidence append proof then gate then sink succeeds in simulation`() {
        val composition = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        val consumed = composition.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val preExecutionProof = composition.commitPreExecution(binding, consumed.attemptLease)

        val issued = composition.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = preExecutionProof,
        )
        assertTrue(issued is FinalExecutionGateResult.Issued)
        val invoked = composition.sink.invoke(
            (issued as FinalExecutionGateResult.Issued).permit,
            binding,
        )
        assertTrue(invoked is SimulationSinkResult.Invoked)
        assertEquals(1, composition.sink.invocationCount())
        assertTrue(
            composition.evidenceWriter.records().any {
                it.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED
            },
        )
    }

    @Test
    fun `evidence records themselves never become a pre-execution proof`() {
        val composition = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        val consumed = composition.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        composition.evidenceWriter.append(
            simulationEvidence(
                correlationId = binding.correlationId.value,
                phase = DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED,
                presentationWallClockMillis = 0L,
                binding = binding,
            ),
        )

        val issued = composition.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = PreExecutionEvidenceCommitProof.create(),
        )
        assertTrue(issued is FinalExecutionGateResult.Failed)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `failed pre-execution append creates no proof and cannot reach the gate`() {
        val evidence = InMemoryDestructiveSimulationEvidenceWriter().apply { failAlways = true }
        val composition = DestructiveSimulationComposition.create(evidenceWriter = evidence)
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        val consumed = composition.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted

        val committed = composition.preExecutionAuthority.commit(
            evidence = simulationEvidence(
                correlationId = binding.correlationId.value,
                phase = DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED,
                presentationWallClockMillis = 0L,
                binding = binding,
            ),
            binding = binding,
            attemptLease = consumed.attemptLease,
        )
        assertTrue(committed is PreExecutionEvidenceCommitResult.Failed)

        val issued = composition.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = PreExecutionEvidenceCommitProof.create(),
        )
        assertTrue(issued is FinalExecutionGateResult.Failed)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `replayed or foreign pre-execution proof cannot issue a permit`() {
        val first = DestructiveSimulationComposition.create()
        val second = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = first.admitBindAuthorize(binding)
        val consumed = first.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val proof = first.commitPreExecution(binding, consumed.attemptLease)

        val firstIssue = first.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = proof,
        )
        assertTrue(firstIssue is FinalExecutionGateResult.Issued)

        val replay = first.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = proof,
        )
        assertEquals(
            "pre_execution_evidence_not_committed_or_already_consumed",
            (replay as FinalExecutionGateResult.Failed).reason,
        )

        val foreignBinding = verifiedBinding()
        val foreignAuthorized = second.admitBindAuthorize(foreignBinding)
        val foreignConsumed = second.authorizationAuthority.consume(
            foreignAuthorized.capability,
            foreignBinding,
            foreignAuthorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val foreignProof = second.commitPreExecution(foreignBinding, foreignConsumed.attemptLease)
        val foreignOnFirst = first.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = foreignProof,
        )
        assertEquals(
            "pre_execution_evidence_not_committed_or_already_consumed",
            (foreignOnFirst as FinalExecutionGateResult.Failed).reason,
        )
    }

    @Test
    fun `gate-issued permit is single use target bound and monotonically short lived`() {
        val composition = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        val consumed = composition.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val issued = composition.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = composition.commitPreExecution(binding, consumed.attemptLease),
        )
        assertTrue(issued is FinalExecutionGateResult.Issued)
        val permit = (issued as FinalExecutionGateResult.Issued).permit

        val first = composition.gate.consume(permit, binding)
        val replay = composition.gate.consume(permit, binding)
        assertTrue(first is PermitConsumption.Accepted)
        assertEquals(
            "permit_not_issued_or_already_consumed",
            (replay as PermitConsumption.Rejected).reason,
        )
    }

    @Test
    fun `gate-issued permit becomes stale after the permit window`() {
        val composition = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        val consumed = composition.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val issued = composition.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = composition.commitPreExecution(binding, consumed.attemptLease),
        ) as FinalExecutionGateResult.Issued
        composition.clock.now =
            1_000L + DestructiveFinalExecutionGate.MAX_PERMIT_AGE_MILLIS + 1L
        assertEquals(
            "permit_stale",
            (composition.gate.consume(issued.permit, binding) as PermitConsumption.Rejected).reason,
        )
    }

    @Test
    fun `sink constructor accepts only the concrete final execution gate`() {
        val constructors = Checkpoint17ASimulationSink::class.java.declaredConstructors
        assertEquals(1, constructors.size)
        assertTrue(
            constructors.single().parameterTypes.contentEquals(
                arrayOf(DestructiveFinalExecutionGate::class.java),
            ),
        )
        val sources = java.io.File("src/main/kotlin/com/example/devicemanagement/destructive")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(sources.contains("FinalExecutionPermitConsumer"))
    }

    @Test
    fun `foreign gate permit cannot invoke this sink`() {
        val first = DestructiveSimulationComposition.create()
        val second = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = first.admitBindAuthorize(binding)
        val consumed = first.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val issued = first.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = first.commitPreExecution(binding, consumed.attemptLease),
        ) as FinalExecutionGateResult.Issued

        val denied = second.sink.invoke(issued.permit, binding)
        assertTrue(denied is SimulationSinkResult.Denied)
        assertEquals(0, second.sink.invocationCount())
        assertEquals(0, first.sink.invocationCount())
    }

    @Test
    fun `permit and pre-execution proof are not serializable`() {
        assertTrue(!Serializable::class.java.isAssignableFrom(FinalExecutionPermit::class.java))
        assertTrue(
            !Serializable::class.java.isAssignableFrom(DestructiveFinalExecutionGate::class.java),
        )
        assertTrue(
            !Serializable::class.java.isAssignableFrom(PreExecutionEvidenceCommitProof::class.java),
        )
        assertTrue(
            !Serializable::class.java.isAssignableFrom(PreExecutionEvidenceCommitAuthority::class.java),
        )
    }
}
