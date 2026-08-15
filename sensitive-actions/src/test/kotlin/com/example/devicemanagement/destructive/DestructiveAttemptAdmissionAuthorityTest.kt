package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveAttemptAdmissionAuthorityTest {
    @Test
    fun `lease is issued only after cooldown write and readback succeed`() {
        val bundle = DestructiveAuthorityBundle()
        val binding = verifiedBinding()
        val admitted = bundle.admission.admit(binding.correlationId, binding.scope)
        assertTrue(admitted is AttemptAdmissionResult.Admitted)
        val bound = bundle.admission.bindTarget(
            (admitted as AttemptAdmissionResult.Admitted).lease,
            binding,
        )
        assertTrue(bound is AttemptBindResult.Bound)
        assertTrue(bundle.admission.requireLive(admitted.lease, binding) is AttemptLeaseCheck.Live)
    }

    @Test
    fun `marker write failure issues no lease`() {
        val store = InMemoryDenyOnlyCooldownMarkerStore().apply { writeSucceeds = false }
        val bundle = DestructiveAuthorityBundle(store = store)
        val binding = verifiedBinding()
        val admitted = bundle.admission.admit(binding.correlationId, binding.scope)
        assertEquals(
            "cooldown_marker_write_failed",
            (admitted as AttemptAdmissionResult.Rejected).reason,
        )
        val armed = bundle.arming.arm(binding, DestructiveAttemptLease.create())
        assertTrue(armed is ArmingIssueResult.Rejected)
    }

    @Test
    fun `marker readback failure issues no lease`() {
        val store = InMemoryDenyOnlyCooldownMarkerStore().apply { readbackSucceeds = false }
        val bundle = DestructiveAuthorityBundle(store = store)
        val binding = verifiedBinding()
        val admitted = bundle.admission.admit(binding.correlationId, binding.scope)
        assertEquals(
            "cooldown_marker_readback_failed",
            (admitted as AttemptAdmissionResult.Rejected).reason,
        )
    }

    @Test
    fun `forged lease cannot arm authorize or prove admission`() {
        val bundle = DestructiveAuthorityBundle()
        val binding = verifiedBinding()
        val forged = DestructiveAttemptLease.create()
        assertEquals(
            "attempt_lease_not_issued_or_already_consumed",
            (bundle.admission.requireLive(forged, binding) as AttemptLeaseCheck.Dead).reason,
        )
        assertEquals(
            "attempt_lease_not_issued_or_already_consumed",
            (bundle.arming.arm(binding, forged) as ArmingIssueResult.Rejected).reason,
        )
        assertEquals(
            "attempt_lease_not_issued_or_already_consumed",
            (
                bundle.authorization.authorize(DestructiveArmingToken.create(), binding, forged)
                    as DestructiveAuthorizationResult.Rejected
                ).reason,
        )
    }

    @Test
    fun `restart destroys the lease and persisted marker only denies`() {
        val state = SharedDenyOnlyMarkerState()
        val first = DestructiveAuthorityBundle(store = InMemoryDenyOnlyCooldownMarkerStore(state))
        val binding = verifiedBinding()
        val lease = first.admitAndBind(binding)
        assertTrue(first.admission.requireLive(lease, binding) is AttemptLeaseCheck.Live)

        val reconstructed = DestructiveAuthorityBundle(
            clock = MutableMonotonicClock(1_000L),
            store = InMemoryDenyOnlyCooldownMarkerStore(state),
        )
        assertEquals(
            "attempt_lease_not_issued_or_already_consumed",
            (reconstructed.admission.requireLive(lease, binding) as AttemptLeaseCheck.Dead).reason,
        )
        assertEquals(
            CooldownDecision.Deny("cooldown_active"),
            reconstructed.cooldown.canAcceptNewRequest(),
        )
        assertEquals(
            "cooldown_active",
            (
                reconstructed.admission.admit(binding.correlationId, binding.scope)
                    as AttemptAdmissionResult.Rejected
                ).reason,
        )
    }

    @Test
    fun `expired lease cannot later create a fresh arm`() {
        val bundle = DestructiveAuthorityBundle()
        val binding = verifiedBinding()
        val lease = bundle.admitAndBind(binding)
        bundle.clock.now = 1_000L + DestructiveAttemptAdmissionAuthority.MAX_LEASE_AGE_MILLIS + 1L
        val armed = bundle.arming.arm(binding, lease)
        assertEquals("attempt_lease_stale", (armed as ArmingIssueResult.Rejected).reason)
    }

    @Test
    fun `negative monotonic delta on a live lease is rejected`() {
        val bundle = DestructiveAuthorityBundle()
        val binding = verifiedBinding()
        val lease = bundle.admitAndBind(binding)
        bundle.clock.now = 999L
        assertEquals(
            "attempt_lease_negative_monotonic_delta",
            (bundle.admission.requireLive(lease, binding) as AttemptLeaseCheck.Dead).reason,
        )
        assertEquals(
            "attempt_lease_negative_monotonic_delta",
            (bundle.admission.bindTarget(lease, binding) as AttemptBindResult.Rejected).reason,
        )
        assertEquals(
            "attempt_lease_negative_monotonic_delta",
            (bundle.arming.arm(binding, lease) as ArmingIssueResult.Rejected).reason,
        )
    }

    @Test
    fun `second admit is rejected while a non-terminal lease is live`() {
        val bundle = DestructiveAuthorityBundle()
        val binding = verifiedBinding()
        val first = bundle.admission.admit(binding.correlationId, binding.scope)
        assertTrue(first is AttemptAdmissionResult.Admitted)
        val second = bundle.admission.admit(
            DestructiveCorrelationId.generate { "second-lease" },
            binding.scope,
        )
        assertEquals(
            "attempt_lease_already_live",
            (second as AttemptAdmissionResult.Rejected).reason,
        )
        assertTrue(bundle.admission.hasNonTerminalLease())
    }

    @Test
    fun `issueLease without a fresh counted-attempt proof fails after cooldown expiry`() {
        val bundle = DestructiveAuthorityBundle()
        val binding = verifiedBinding()
        val recorded = bundle.admission.recordCountedAttempt(binding.correlationId, binding.scope)
        val firstProof = (recorded as CountedAttemptRecordResult.Recorded).proof
        val first = bundle.admission.issueLease(
            proof = firstProof,
            correlationId = binding.correlationId,
            requestedScope = binding.scope,
        )
        assertTrue(first is AttemptAdmissionResult.Admitted)
        bundle.cleanup.close((first as AttemptAdmissionResult.Admitted).lease)
        assertTrue(!bundle.admission.hasNonTerminalLease())

        bundle.clock.now = 1_000L + DestructiveDenyOnlyCooldown.DEFAULT_COOLDOWN_MILLIS
        assertEquals(CooldownDecision.NotDenied, bundle.cooldown.canAcceptNewRequest())
        assertEquals(CooldownUsable.Usable, bundle.cooldown.assertCurrentAttemptMarkerPresent())

        val leftover = bundle.admission.issueLease(
            proof = firstProof,
            correlationId = binding.correlationId,
            requestedScope = binding.scope,
        )
        assertEquals(
            "counted_attempt_not_issued_or_already_consumed",
            (leftover as AttemptAdmissionResult.Rejected).reason,
        )
        val withoutFreshProof = bundle.admission.issueLease(
            proof = CountedAttemptProof.create(),
            correlationId = binding.correlationId,
            requestedScope = binding.scope,
        )
        assertEquals(
            "counted_attempt_not_issued_or_already_consumed",
            (withoutFreshProof as AttemptAdmissionResult.Rejected).reason,
        )
        assertTrue(!bundle.admission.hasNonTerminalLease())

        val fresh = bundle.admission.recordCountedAttempt(
            DestructiveCorrelationId.generate { "fresh-after-cooldown" },
            binding.scope,
        )
        val freshLease = bundle.admission.issueLease(
            proof = (fresh as CountedAttemptRecordResult.Recorded).proof,
            correlationId = DestructiveCorrelationId.generate { "fresh-after-cooldown" },
            requestedScope = binding.scope,
        )
        assertTrue(freshLease is AttemptAdmissionResult.Admitted)
    }

    @Test
    fun `fresh counted-attempt proof issues exactly one lease`() {
        val bundle = DestructiveAuthorityBundle()
        val binding = verifiedBinding()
        val recorded = bundle.admission.recordCountedAttempt(binding.correlationId, binding.scope)
        assertTrue(recorded is CountedAttemptRecordResult.Recorded)
        val proof = (recorded as CountedAttemptRecordResult.Recorded).proof

        val first = bundle.admission.issueLease(
            proof = proof,
            correlationId = binding.correlationId,
            requestedScope = binding.scope,
        )
        assertTrue(first is AttemptAdmissionResult.Admitted)

        val replay = bundle.admission.issueLease(
            proof = proof,
            correlationId = binding.correlationId,
            requestedScope = binding.scope,
        )
        assertEquals(
            "counted_attempt_not_issued_or_already_consumed",
            (replay as AttemptAdmissionResult.Rejected).reason,
        )
        assertTrue(bundle.admission.hasNonTerminalLease())
    }

    @Test
    fun `foreign counted-attempt proof cannot issue a lease`() {
        val first = DestructiveAuthorityBundle()
        val second = DestructiveAuthorityBundle()
        val binding = verifiedBinding()
        val recorded = first.admission.recordCountedAttempt(binding.correlationId, binding.scope)
        val proof = (recorded as CountedAttemptRecordResult.Recorded).proof

        val foreign = second.admission.issueLease(
            proof = proof,
            correlationId = binding.correlationId,
            requestedScope = binding.scope,
        )
        assertEquals(
            "counted_attempt_not_issued_or_already_consumed",
            (foreign as AttemptAdmissionResult.Rejected).reason,
        )
        assertTrue(!second.admission.hasNonTerminalLease())
    }

    @Test
    fun `malformed counted attempt is discarded and never becomes a lease`() {
        val bundle = DestructiveAuthorityBundle()
        val correlationId = DestructiveCorrelationId.generate { "malformed" }
        val recorded = bundle.admission.recordCountedAttempt(correlationId, null)
        assertTrue(recorded is CountedAttemptRecordResult.Recorded)
        val proof = (recorded as CountedAttemptRecordResult.Recorded).proof
        bundle.admission.discardCountedAttempt(proof)

        val issued = bundle.admission.issueLease(
            proof = proof,
            correlationId = correlationId,
            requestedScope = DestructiveScope.DEVICE_FACTORY_RESET,
        )
        assertEquals(
            "counted_attempt_not_issued_or_already_consumed",
            (issued as AttemptAdmissionResult.Rejected).reason,
        )
        assertTrue(!bundle.admission.hasNonTerminalLease())
    }

    @Test
    fun `issueLease has no marker-only overload`() {
        val methods = DestructiveAttemptAdmissionAuthority::class.java.declaredMethods
            .filter { it.name == "issueLease" }
        assertTrue(methods.isNotEmpty())
        methods.forEach { method ->
            assertTrue(CountedAttemptProof::class.java in method.parameterTypes)
        }
        assertFalse(
            methods.any { method ->
                method.parameterTypes.contentEquals(
                    arrayOf(DestructiveCorrelationId::class.java, DestructiveScope::class.java),
                )
            },
        )
    }

    @Test
    fun `persisted marker is never treated as a lease`() {
        val source = java.io.File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveDenyOnlyCooldown.kt",
        ).readText()
        assertTrue(source.contains("never become an attempt lease") || source.contains("become an attempt lease"))
        assertTrue(!source.contains("DestructiveAttemptLease"))
        val admission = java.io.File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveAttemptAdmissionAuthority.kt",
        ).readText()
        assertTrue(admission.contains("Never serialized or persisted"))
        assertTrue(admission.contains("deny-only marker is never a lease"))
    }
}
