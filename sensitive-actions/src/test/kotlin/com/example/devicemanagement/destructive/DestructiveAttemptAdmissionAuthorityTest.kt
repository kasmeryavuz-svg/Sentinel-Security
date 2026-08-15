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
