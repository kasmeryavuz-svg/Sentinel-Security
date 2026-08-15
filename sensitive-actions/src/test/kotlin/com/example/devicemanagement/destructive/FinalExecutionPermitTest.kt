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
    }

    @Test
    fun `raw permit construction plus sink cannot invoke without the gate`() {
        val composition = DestructiveSimulationComposition.create()
        val denied = composition.sink.invoke(FinalExecutionPermit.create(), verifiedBinding())
        assertTrue(denied is SimulationSinkResult.Denied)
        assertEquals(0, composition.sink.invocationCount())
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
        ) as FinalExecutionGateResult.Issued
        composition.clock.now =
            1_000L + DestructiveFinalExecutionGate.MAX_PERMIT_AGE_MILLIS + 1L
        assertEquals(
            "permit_stale",
            (composition.gate.consume(issued.permit, binding) as PermitConsumption.Rejected).reason,
        )
    }

    @Test
    fun `permit is not serializable`() {
        assertTrue(!Serializable::class.java.isAssignableFrom(FinalExecutionPermit::class.java))
        assertTrue(
            !Serializable::class.java.isAssignableFrom(DestructiveFinalExecutionGate::class.java),
        )
    }
}
