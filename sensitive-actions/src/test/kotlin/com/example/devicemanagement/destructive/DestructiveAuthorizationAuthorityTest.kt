package com.example.devicemanagement.destructive

import com.example.devicemanagement.action.Approval
import com.example.devicemanagement.action.ApprovalAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Serializable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DestructiveAuthorizationAuthorityTest {
    private val clock = MutableMonotonicClock(1_000L)
    private val bundle = DestructiveAuthorityBundle(clock)
    private val authority = bundle.authorization
    private val binding = verifiedBinding()

    @Test
    fun `authorization requires a currently valid arm and live lease`() {
        val capability = DestructiveCapability.create()
        val forgedLease = DestructiveAttemptLease.create()
        val consume = authority.consume(capability, binding, forgedLease)
        assertEquals(
            "capability_not_issued_or_already_consumed",
            (consume as DestructiveCapabilityConsumption.Rejected).reason,
        )

        val withoutArm = authority.authorize(DestructiveArmingToken.create(), binding, forgedLease)
        assertEquals(
            "attempt_lease_not_issued_or_already_consumed",
            (withoutArm as DestructiveAuthorizationResult.Rejected).reason,
        )
    }

    @Test
    fun `issued capability is bound and single use`() {
        val authorized = bundle.authorize(binding)

        val first = authority.consume(authorized.capability, binding, authorized.attemptLease)
        val replay = authority.consume(authorized.capability, binding, authorized.attemptLease)

        assertTrue(first is DestructiveCapabilityConsumption.Accepted)
        assertEquals(
            "capability_not_issued_or_already_consumed",
            (replay as DestructiveCapabilityConsumption.Rejected).reason,
        )
    }

    @Test
    fun `stale capability is rejected`() {
        val authorized = bundle.authorize(binding)
        clock.now = 1_000L + DestructiveAuthorizationAuthority.MAX_CAPABILITY_AGE_MILLIS + 1L

        val consume = authority.consume(authorized.capability, binding, authorized.attemptLease)
        assertEquals("capability_stale", (consume as DestructiveCapabilityConsumption.Rejected).reason)
    }

    @Test
    fun `negative monotonic delta fails closed`() {
        val authorized = bundle.authorize(binding)
        clock.now = 500L

        val consume = authority.consume(authorized.capability, binding, authorized.attemptLease)
        assertEquals(
            "capability_negative_monotonic_delta",
            (consume as DestructiveCapabilityConsumption.Rejected).reason,
        )
    }

    @Test
    fun `foreign forged and other-authority capabilities are rejected`() {
        bundle.authorize(binding)
        val forged = DestructiveCapability.create()
        val foreign = DestructiveAuthorityBundle(MutableMonotonicClock(1_000L))
        val foreignBinding = verifiedBinding(
            correlationId = DestructiveCorrelationId.generate { "foreign" },
        )
        val foreignAuthorized = foreign.authorize(foreignBinding)

        assertEquals(
            "capability_not_issued_or_already_consumed",
            (
                authority.consume(forged, binding, DestructiveAttemptLease.create())
                    as DestructiveCapabilityConsumption.Rejected
                ).reason,
        )
        assertEquals(
            "capability_not_issued_or_already_consumed",
            (
                authority.consume(
                    foreignAuthorized.capability,
                    foreignAuthorized.binding,
                    foreignAuthorized.attemptLease,
                ) as DestructiveCapabilityConsumption.Rejected
                ).reason,
        )
    }

    @Test
    fun `wrong target or scope is rejected`() {
        val authorized = bundle.authorize(binding)
        val other = verifiedBinding(
            facts = verifiedFacts(runningPackage = "com.example.other"),
        )

        assertEquals(
            "capability_target_mismatch",
            (
                authority.consume(authorized.capability, other, authorized.attemptLease)
                    as DestructiveCapabilityConsumption.Rejected
                ).reason,
        )
    }

    @Test
    fun `disarmed arm cannot authorize`() {
        val (lease, token) = bundle.arm(binding)
        bundle.arming.disarm(token)
        val authorized = authority.authorize(token, binding, lease)
        assertEquals(
            "arm_not_issued_or_already_consumed",
            (authorized as DestructiveAuthorizationResult.Rejected).reason,
        )
    }

    @Test
    fun `one arm mints at most one destructive authorization`() {
        val (lease, token) = bundle.arm(binding)
        val first = authority.authorize(token, binding, lease)
        val second = authority.authorize(token, binding, lease)

        assertTrue(first is DestructiveAuthorizationResult.Authorized)
        assertEquals(
            "arm_already_authorized",
            (second as DestructiveAuthorizationResult.Rejected).reason,
        )
        val consumed = authority.consume(
            (first as DestructiveAuthorizationResult.Authorized).capability,
            binding,
            lease,
        )
        assertTrue(consumed is DestructiveCapabilityConsumption.Accepted)
    }

    @Test
    fun `concurrent authorize on one arm issues at most one capability`() {
        val (lease, token) = bundle.arm(binding)
        val successes = ConcurrentLinkedQueue<DestructiveCapability>()
        val rejections = ConcurrentLinkedQueue<String>()
        val start = CountDownLatch(1)
        val workers = 8
        val done = CountDownLatch(workers)
        repeat(workers) {
            Thread {
                try {
                    start.await()
                    when (val result = authority.authorize(token, binding, lease)) {
                        is DestructiveAuthorizationResult.Authorized -> successes.add(result.capability)
                        is DestructiveAuthorizationResult.Rejected -> rejections.add(result.reason)
                    }
                } finally {
                    done.countDown()
                }
            }.start()
        }
        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, successes.size)
        assertEquals(workers - 1, rejections.size)
        assertTrue(rejections.all { it == "arm_already_authorized" })
        val proof = authority.consume(successes.single(), binding, lease)
        assertTrue(proof is DestructiveCapabilityConsumption.Accepted)
    }

    @Test
    fun `consumed authorization proof is required after consume and is single use`() {
        val authorized = bundle.authorize(binding)
        val consumed = authority.consume(authorized.capability, binding, authorized.attemptLease)
            as DestructiveCapabilityConsumption.Accepted
        val first = authority.requireConsumedFresh(
            proof = consumed.proof,
            expectedBinding = binding,
            expectedArmToken = authorized.armToken,
            expectedLease = authorized.attemptLease,
            nowMonotonicMillis = clock.nowMillis(),
        )
        val replay = authority.requireConsumedFresh(
            proof = consumed.proof,
            expectedBinding = binding,
            expectedArmToken = authorized.armToken,
            expectedLease = authorized.attemptLease,
            nowMonotonicMillis = clock.nowMillis(),
        )
        assertTrue(first is ConsumedAuthorizationCheck.Accepted)
        assertEquals(
            "consumed_authorization_not_issued_or_already_consumed",
            (replay as ConsumedAuthorizationCheck.Rejected).reason,
        )
        assertEquals(
            "consumed_authorization_not_issued_or_already_consumed",
            (
                authority.requireConsumedFresh(
                    proof = ConsumedDestructiveAuthorizationProof.create(),
                    expectedBinding = binding,
                    expectedArmToken = authorized.armToken,
                    expectedLease = authorized.attemptLease,
                    nowMonotonicMillis = clock.nowMillis(),
                ) as ConsumedAuthorizationCheck.Rejected
                ).reason,
        )
    }

    @Test
    fun `reversible Approval is a different type and is not serializable`() {
        assertFalse(Approval::class.java == DestructiveCapability::class.java)
        assertFalse(Serializable::class.java.isAssignableFrom(DestructiveCapability::class.java))
        assertFalse(Serializable::class.java.isAssignableFrom(DestructiveArmingToken::class.java))
        assertFalse(Serializable::class.java.isAssignableFrom(DestructiveAttemptLease::class.java))
        assertFalse(
            Serializable::class.java.isAssignableFrom(ConsumedDestructiveAuthorizationProof::class.java),
        )
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
