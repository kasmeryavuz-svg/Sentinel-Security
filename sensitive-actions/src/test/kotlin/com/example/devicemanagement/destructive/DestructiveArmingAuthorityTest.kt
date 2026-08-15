package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveArmingAuthorityTest {
    private val clock = MutableMonotonicClock(1_000L)
    private val bundle = DestructiveAuthorityBundle(clock)
    private val authority = bundle.arming
    private val binding = verifiedBinding()

    @Test
    fun `valid arm is live for the bound target and scope`() {
        val (lease, token) = bundle.arm(binding)
        val check = authority.requireLive(token, binding, lease)

        assertTrue(check is ArmingCheck.Live)
        assertEquals(binding, (check as ArmingCheck.Live).binding)
        assertEquals(lease, check.attemptLease)
    }

    @Test
    fun `duplicate arm is rejected without silent replace`() {
        val (lease, firstToken) = bundle.arm(binding)
        val second = authority.arm(binding, lease)

        assertTrue(second is ArmingIssueResult.Rejected)
        assertEquals("arm_already_live", (second as ArmingIssueResult.Rejected).reason)
        assertTrue(authority.requireLive(firstToken, binding, lease) is ArmingCheck.Live)
    }

    @Test
    fun `stale arm is rejected`() {
        val (lease, token) = bundle.arm(binding)
        clock.now = 1_000L + DestructiveArmingAuthority.MAX_ARM_AGE_MILLIS + 1L

        val check = authority.requireLive(token, binding, lease)
        assertEquals("arm_stale", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `negative monotonic delta fails closed`() {
        val (lease, token) = bundle.arm(binding)
        clock.now = 999L

        val check = authority.requireLive(token, binding, lease)
        assertEquals("arm_negative_monotonic_delta", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `foreign token is rejected`() {
        val foreign = DestructiveAuthorityBundle(MutableMonotonicClock(1_000L))
        val (foreignLease, foreignToken) = foreign.arm(binding)

        val check = authority.requireLive(foreignToken, binding, foreignLease)
        assertEquals("arm_not_issued_or_already_consumed", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `wrong target is rejected`() {
        val (lease, token) = bundle.arm(binding)
        val other = verifiedBinding(
            facts = verifiedFacts(runningPackage = "com.example.other"),
        )

        val check = authority.requireLive(token, other, lease)
        assertEquals("arm_target_mismatch", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `wrong scope is rejected`() {
        val (lease, token) = bundle.arm(binding)
        val mismatched = binding.copy(scope = DestructiveScope.USER_SCOPED_WIPE)

        val check = authority.requireLive(token, mismatched, lease)
        assertEquals("arm_scope_mismatch", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `cancellation destroys the token`() {
        val (lease, token) = bundle.arm(binding)
        val cancelled = authority.disarm(token)

        assertTrue(cancelled is ArmingCancelResult.Cancelled)
        assertEquals(
            "arm_not_issued_or_already_consumed",
            (authority.requireLive(token, binding, lease) as ArmingCheck.Dead).reason,
        )
    }

    @Test
    fun `replay of a disarmed token is rejected`() {
        val (_, token) = bundle.arm(binding)
        authority.disarm(token)
        val replay = authority.disarm(token)

        assertEquals(
            "arm_not_issued_or_already_consumed",
            (replay as ArmingCancelResult.Rejected).reason,
        )
    }

    @Test
    fun `stale arm still occupies the single live slot`() {
        val (lease, token) = bundle.arm(binding)
        clock.now = 1_000L + DestructiveArmingAuthority.MAX_ARM_AGE_MILLIS + 1L
        assertTrue(authority.requireLive(token, binding, lease) is ArmingCheck.Dead)

        val second = authority.arm(binding, lease)
        assertEquals("arm_already_live", (second as ArmingIssueResult.Rejected).reason)
    }

    @Test
    fun `arming without a live attempt lease is rejected`() {
        val forged = DestructiveAttemptLease.create()
        val armed = authority.arm(binding, forged)
        assertEquals(
            "attempt_lease_not_issued_or_already_consumed",
            (armed as ArmingIssueResult.Rejected).reason,
        )
    }
}
