package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.Serializable

class DurableDestructivePreExecutionAuditTest {
    @Test
    fun `durable append failure makes simulated execution impossible`() {
        val durable = InMemoryDestructivePreExecutionDurableStore(
            SharedDestructivePreExecutionDurableState().apply { failWrites = true },
        )
        val composition = DestructiveSimulationComposition.create(durableStore = durable)

        val result = composition.pipeline.submit(validRequest())

        assertEquals(DestructiveSimulationOutcome.FAILED_PRE_EXECUTION, result.outcome)
        assertEquals("audit_persistence_unavailable", result.reason)
        assertEquals(0, composition.sink.invocationCount())
        assertEquals(0, durable.count())
    }

    @Test
    fun `durable PRE_EXECUTION row is written before live validation`() {
        val durable = InMemoryDestructivePreExecutionDurableStore()
        val evidence = InMemoryDestructiveSimulationEvidenceWriter()
        val composition = DestructiveSimulationComposition.create(
            evidenceWriter = evidence,
            durableStore = durable,
        )
        val original = composition.liveFacts.facts
        evidence.appendHook = { ev ->
            if (ev.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED) {
                assertEquals(1, durable.count())
                composition.liveFacts.facts = original.copy(isDeviceOwner = false)
            }
        }

        val result = composition.pipeline.submit(validRequest())

        assertEquals(DestructiveSimulationOutcome.FAILED_PRE_EXECUTION, result.outcome)
        assertEquals("device_owner_not_verified", result.reason)
        assertEquals(0, composition.sink.invocationCount())
        assertEquals(1, durable.count())
        assertEquals(
            DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED,
            durable.latest(1).records.single().phase,
        )
    }

    @Test
    fun `surviving durable rows cannot reconstruct a proof or resume execution`() {
        val state = SharedDestructivePreExecutionDurableState()
        val first = DestructiveSimulationComposition.create(
            durableStore = InMemoryDestructivePreExecutionDurableStore(state),
        )
        assertEquals(
            DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE,
            first.pipeline.submit(validRequest()).outcome,
        )
        assertTrue(state.rows.isNotEmpty())

        val reconstructed = DestructiveSimulationComposition.create(
            durableStore = InMemoryDestructivePreExecutionDurableStore(state),
        )
        assertEquals(0, reconstructed.sink.invocationCount())
        val forged = reconstructed.gate.validateAndIssue(
            binding = verifiedBinding(),
            armToken = DestructiveArmingToken.create(),
            attemptLease = DestructiveAttemptLease.create(),
            consumptionProof = ConsumedDestructiveAuthorizationProof.create(),
            preExecutionProof = PreExecutionEvidenceCommitProof.create(),
        )
        assertTrue(forged is FinalExecutionGateResult.Failed)
        assertEquals(0, reconstructed.sink.invocationCount())
        assertTrue(
            reconstructed.durableRepository.latest(8).records.all {
                it.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED
            },
        )
    }

    @Test
    fun `durable record type cannot carry authorization material`() {
        val fields = DestructivePreExecutionDurableRecord::class.java.declaredFields.map { it.name }
        assertFalse(fields.any { it.contains("capability", ignoreCase = true) })
        assertFalse(fields.any { it.contains("permit", ignoreCase = true) })
        assertFalse(fields.any { it.contains("lease", ignoreCase = true) })
        assertFalse(fields.any { it.contains("approval", ignoreCase = true) })
        assertFalse(fields.any { it.contains("token", ignoreCase = true) })
        assertFalse(Serializable::class.java.isAssignableFrom(PreExecutionEvidenceCommitProof::class.java))
    }

    @Test
    fun `repository rejects non pre-execution phases and wrong scope`() {
        val repository = DurableDestructivePreExecutionRepository(
            InMemoryDestructivePreExecutionDurableStore(),
        )
        val ok = validDurableRecord()
        assertTrue(repository.append(ok) is DestructiveEvidenceAppendResult.Recorded)
        assertTrue(
            repository.append(ok.copy(phase = DestructiveEvidencePhase.REQUESTED))
                is DestructiveEvidenceAppendResult.Failed,
        )
        assertTrue(
            repository.append(ok.copy(boundScope = DestructiveScope.USER_SCOPED_WIPE))
                is DestructiveEvidenceAppendResult.Failed,
        )
        assertTrue(
            repository.append(ok.copy(actionName = "mock_wipe"))
                is DestructiveEvidenceAppendResult.Failed,
        )
        assertTrue(
            repository.append(ok.copy(correlationId = " "))
                is DestructiveEvidenceAppendResult.Failed,
        )
    }

    @Test
    fun `unavailable durable store cannot mint a proof`() {
        val composition = DestructiveSimulationComposition.create(
            durableStore = InMemoryDestructivePreExecutionDurableStore(
                SharedDestructivePreExecutionDurableState().apply { unavailable = true },
            ),
        )
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        val committed = composition.preExecutionAuthority.commit(
            evidence = simulationEvidence(
                correlationId = binding.correlationId.value,
                phase = DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED,
                presentationWallClockMillis = 0L,
                binding = binding,
            ),
            binding = binding,
            attemptLease = authorized.attemptLease,
        )
        assertTrue(committed is PreExecutionEvidenceCommitResult.Failed)
    }

    @Test
    fun `durable store has no delete or execute API`() {
        val methods = DestructivePreExecutionDurableStore::class.java.methods.map { it.name }.toSet()
        assertTrue(methods.contains("insert"))
        assertTrue(methods.contains("latest"))
        assertTrue(methods.contains("count"))
        assertFalse(methods.contains("delete"))
        assertFalse(methods.contains("deleteOldest"))
        assertFalse(methods.contains("execute"))
        assertFalse(methods.contains("authorize"))
        assertFalse(methods.contains("arm"))
    }

    @Test
    fun `durable path remains isolated from production audit schema v1`() {
        val source = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DurableDestructivePreExecutionRepository.kt",
        ).readText()
        assertTrue(source.contains("sentinel_audit.db"))
        assertTrue(source.contains("never authorization"))
        assertFalse(source.contains("AuditEventPhase.APPLIED"))
        assertFalse(source.contains("wipeDevice"))
        assertFalse(source.contains("wipeData"))
    }

    private fun validDurableRecord(): DestructivePreExecutionDurableRecord {
        return DestructivePreExecutionDurableRecord(
            eventId = "event-1",
            correlationId = "authoritative-correlation",
            actionName = DestructiveSimulationActionNames.FACTORY_RESET_SIMULATION,
            phase = DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED,
            presentationWallClockMillis = 0L,
            boundPackage = "com.example.devicemanagement",
            boundAdminComponent =
                "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
            boundScope = DestructiveScope.DEVICE_FACTORY_RESET,
            reasonCode = null,
        )
    }
}
