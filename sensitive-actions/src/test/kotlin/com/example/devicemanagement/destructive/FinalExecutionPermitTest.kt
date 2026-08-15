package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Serializable

class FinalExecutionPermitTest {
    @Test
    fun `permit is single use target bound and monotonically short lived`() {
        val clock = MutableMonotonicClock(1_000L)
        val authority = FinalExecutionPermitAuthority(clock)
        val binding = verifiedBinding()
        val permit = authority.issue(binding)

        val first = authority.consume(permit, binding)
        val replay = authority.consume(permit, binding)
        assertTrue(first is PermitConsumption.Accepted)
        assertEquals(
            "permit_not_issued_or_already_consumed",
            (replay as PermitConsumption.Rejected).reason,
        )

        val second = authority.issue(binding)
        clock.now = 1_000L + FinalExecutionPermitAuthority.MAX_PERMIT_AGE_MILLIS + 1L
        assertEquals(
            "permit_stale",
            (authority.consume(second, binding) as PermitConsumption.Rejected).reason,
        )
    }

    @Test
    fun `foreign permit and wrong target are rejected`() {
        val clock = MutableMonotonicClock(1_000L)
        val authority = FinalExecutionPermitAuthority(clock)
        val foreign = FinalExecutionPermitAuthority(clock).issue(verifiedBinding())
        assertEquals(
            "permit_not_issued_or_already_consumed",
            (authority.consume(foreign, verifiedBinding()) as PermitConsumption.Rejected).reason,
        )
        val permit = authority.issue(verifiedBinding())
        val other = verifiedBinding(
            facts = verifiedFacts(runningPackage = "com.example.other"),
        )
        assertEquals(
            "permit_target_mismatch",
            (authority.consume(permit, other) as PermitConsumption.Rejected).reason,
        )
        assertTrue(!Serializable::class.java.isAssignableFrom(FinalExecutionPermit::class.java))
    }
}
