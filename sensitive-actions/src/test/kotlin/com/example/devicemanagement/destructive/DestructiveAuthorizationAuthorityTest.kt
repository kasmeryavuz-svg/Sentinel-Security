package com.example.devicemanagement.destructive

import com.example.devicemanagement.action.Approval
import com.example.devicemanagement.action.ApprovalAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Serializable

class DestructiveAuthorizationAuthorityTest {
    private val clock = MutableMonotonicClock(1_000L)
    private val arming = DestructiveArmingAuthority(clock)
    private val authority = DestructiveAuthorizationAuthority(arming, clock)
    private val binding = verifiedBinding()

    @Test
    fun `authorization requires a currently valid arm`() {
        val capability = DestructiveCapability.create()
        val consume = authority.consume(capability, binding)
        assertEquals(
            "capability_not_issued_or_already_consumed",
            (consume as DestructiveCapabilityConsumption.Rejected).reason,
        )

        val withoutArm = authority.authorize(DestructiveArmingToken.create(), binding)
        assertEquals(
            "arm_not_issued_or_already_consumed",
            (withoutArm as DestructiveAuthorizationResult.Rejected).reason,
        )
    }

    @Test
    fun `issued capability is bound and single use`() {
        val armed = arming.arm(binding) as ArmingIssueResult.Armed
        val authorized = authority.authorize(armed.token, binding)
            as DestructiveAuthorizationResult.Authorized

        val first = authority.consume(authorized.capability, binding)
        val replay = authority.consume(authorized.capability, binding)

        assertTrue(first is DestructiveCapabilityConsumption.Accepted)
        assertEquals(
            "capability_not_issued_or_already_consumed",
            (replay as DestructiveCapabilityConsumption.Rejected).reason,
        )
    }

    @Test
    fun `stale capability is rejected`() {
        val armed = arming.arm(binding) as ArmingIssueResult.Armed
        val authorized = authority.authorize(armed.token, binding)
            as DestructiveAuthorizationResult.Authorized
        clock.now = 1_000L + DestructiveAuthorizationAuthority.MAX_CAPABILITY_AGE_MILLIS + 1L

        val consume = authority.consume(authorized.capability, binding)
        assertEquals("capability_stale", (consume as DestructiveCapabilityConsumption.Rejected).reason)
    }

    @Test
    fun `negative monotonic delta fails closed`() {
        val armed = arming.arm(binding) as ArmingIssueResult.Armed
        val authorized = authority.authorize(armed.token, binding)
            as DestructiveAuthorizationResult.Authorized
        clock.now = 500L

        val consume = authority.consume(authorized.capability, binding)
        assertEquals(
            "capability_negative_monotonic_delta",
            (consume as DestructiveCapabilityConsumption.Rejected).reason,
        )
    }

    @Test
    fun `foreign forged and other-authority capabilities are rejected`() {
        val armed = arming.arm(binding) as ArmingIssueResult.Armed
        authority.authorize(armed.token, binding)
        val forged = DestructiveCapability.create()
        val foreignArming = DestructiveArmingAuthority(clock)
        val foreignBinding = verifiedBinding(
            correlationId = DestructiveCorrelationId.generate { "foreign" },
        )
        val foreignArmed = foreignArming.arm(foreignBinding) as ArmingIssueResult.Armed
        val foreignAuthority = DestructiveAuthorizationAuthority(foreignArming, clock)
        val foreign = foreignAuthority.authorize(foreignArmed.token, foreignBinding)
            as DestructiveAuthorizationResult.Authorized

        assertEquals(
            "capability_not_issued_or_already_consumed",
            (authority.consume(forged, binding) as DestructiveCapabilityConsumption.Rejected).reason,
        )
        assertEquals(
            "capability_not_issued_or_already_consumed",
            (
                authority.consume(foreign.capability, foreign.binding)
                    as DestructiveCapabilityConsumption.Rejected
                ).reason,
        )
    }

    @Test
    fun `wrong target or scope is rejected`() {
        val armed = arming.arm(binding) as ArmingIssueResult.Armed
        val authorized = authority.authorize(armed.token, binding)
            as DestructiveAuthorizationResult.Authorized
        val other = verifiedBinding(
            facts = verifiedFacts(runningPackage = "com.example.other"),
        )

        assertEquals(
            "capability_target_mismatch",
            (authority.consume(authorized.capability, other) as DestructiveCapabilityConsumption.Rejected).reason,
        )
    }

    @Test
    fun `disarmed arm cannot authorize`() {
        val armed = arming.arm(binding) as ArmingIssueResult.Armed
        arming.disarm(armed.token)
        val authorized = authority.authorize(armed.token, binding)
        assertEquals(
            "arm_not_issued_or_already_consumed",
            (authorized as DestructiveAuthorizationResult.Rejected).reason,
        )
    }

    @Test
    fun `reversible Approval is a different type and is not serializable`() {
        assertFalse(Approval::class.java == DestructiveCapability::class.java)
        assertFalse(Serializable::class.java.isAssignableFrom(DestructiveCapability::class.java))
        assertFalse(Serializable::class.java.isAssignableFrom(DestructiveArmingToken::class.java))
        assertFalse(
            DestructiveAuthorizationAuthority::class.java.methods.any { method ->
                method.parameterTypes.any { it == Approval::class.java }
            },
        )
        assertFalse(
            ApprovalAuthority::class.java.methods.any { method ->
                method.parameterTypes.any { it == DestructiveCapability::class.java }
            },
        )
    }
}
