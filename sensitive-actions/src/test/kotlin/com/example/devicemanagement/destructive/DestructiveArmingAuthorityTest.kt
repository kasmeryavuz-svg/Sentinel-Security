package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveArmingAuthorityTest {
    private val clock = MutableMonotonicClock(1_000L)
    private val authority = DestructiveArmingAuthority(clock)
    private val binding = verifiedBinding()

    @Test
    fun `valid arm is live for the bound target and scope`() {
        val armed = authority.arm(binding) as ArmingIssueResult.Armed
        val check = authority.requireLive(armed.token, binding)

        assertTrue(check is ArmingCheck.Live)
        assertEquals(binding, (check as ArmingCheck.Live).binding)
    }

    @Test
    fun `duplicate arm is rejected without silent replace`() {
        val first = authority.arm(binding) as ArmingIssueResult.Armed
        val second = authority.arm(binding)

        assertTrue(second is ArmingIssueResult.Rejected)
        assertEquals("arm_already_live", (second as ArmingIssueResult.Rejected).reason)
        assertTrue(authority.requireLive(first.token, binding) is ArmingCheck.Live)
    }

    @Test
    fun `stale arm is rejected`() {
        val armed = authority.arm(binding) as ArmingIssueResult.Armed
        clock.now = 1_000L + DestructiveArmingAuthority.MAX_ARM_AGE_MILLIS + 1L

        val check = authority.requireLive(armed.token, binding)
        assertEquals("arm_stale", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `negative monotonic delta fails closed`() {
        val armed = authority.arm(binding) as ArmingIssueResult.Armed
        clock.now = 999L

        val check = authority.requireLive(armed.token, binding)
        assertEquals("arm_negative_monotonic_delta", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `foreign token is rejected`() {
        val foreign = DestructiveArmingAuthority(clock).arm(binding) as ArmingIssueResult.Armed

        val check = authority.requireLive(foreign.token, binding)
        assertEquals("arm_not_issued_or_already_consumed", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `wrong target is rejected`() {
        val armed = authority.arm(binding) as ArmingIssueResult.Armed
        val other = verifiedBinding(
            facts = verifiedFacts(runningPackage = "com.example.other"),
        )

        val check = authority.requireLive(armed.token, other)
        assertEquals("arm_target_mismatch", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `wrong scope is rejected`() {
        val armed = authority.arm(binding) as ArmingIssueResult.Armed
        val mismatched = binding.copy(scope = DestructiveScope.USER_SCOPED_WIPE)

        val check = authority.requireLive(armed.token, mismatched)
        assertEquals("arm_scope_mismatch", (check as ArmingCheck.Dead).reason)
    }

    @Test
    fun `cancellation destroys the token`() {
        val armed = authority.arm(binding) as ArmingIssueResult.Armed
        val cancelled = authority.disarm(armed.token)

        assertTrue(cancelled is ArmingCancelResult.Cancelled)
        assertEquals(
            "arm_not_issued_or_already_consumed",
            (authority.requireLive(armed.token, binding) as ArmingCheck.Dead).reason,
        )
    }

    @Test
    fun `replay of a disarmed token is rejected`() {
        val armed = authority.arm(binding) as ArmingIssueResult.Armed
        authority.disarm(armed.token)
        val replay = authority.disarm(armed.token)

        assertEquals(
            "arm_not_issued_or_already_consumed",
            (replay as ArmingCancelResult.Rejected).reason,
        )
    }

    @Test
    fun `stale arm still occupies the single live slot`() {
        val armed = authority.arm(binding) as ArmingIssueResult.Armed
        clock.now = 1_000L + DestructiveArmingAuthority.MAX_ARM_AGE_MILLIS + 1L
        assertTrue(authority.requireLive(armed.token, binding) is ArmingCheck.Dead)

        val second = authority.arm(binding)
        assertEquals("arm_already_live", (second as ArmingIssueResult.Rejected).reason)
    }
}
