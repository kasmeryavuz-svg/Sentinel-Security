package com.example.devicemanagement.persistence

import com.example.devicemanagement.destructive.CooldownDecision
import com.example.devicemanagement.destructive.CooldownRecordResult
import com.example.devicemanagement.destructive.CooldownUsable
import com.example.devicemanagement.destructive.CountedAttemptRecordResult
import com.example.devicemanagement.destructive.DenyOnlyCooldownMarker
import com.example.devicemanagement.destructive.DestructiveAttemptAdmissionAuthority
import com.example.devicemanagement.destructive.DestructiveAttemptLease
import com.example.devicemanagement.destructive.DestructiveCapability
import com.example.devicemanagement.destructive.DestructiveCorrelationId
import com.example.devicemanagement.destructive.DestructiveDenyOnlyCooldown
import com.example.devicemanagement.destructive.DestructiveScope
import com.example.devicemanagement.destructive.FinalExecutionPermit
import com.example.devicemanagement.destructive.MarkerReadResult
import com.example.devicemanagement.destructive.MarkerWriteResult
import com.example.devicemanagement.destructive.MutableMonotonicClock
import com.example.devicemanagement.destructive.SharedDenyOnlyMarkerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TrustedRuntimeDenyOnlyCooldownPersistenceTest {
    @Test
    fun `adapter rejects arbitrary payloads and never treats them as authorization`() {
        val medium = ReconstructableDenyOnlyMarkerMedium()
        val adapter = TrustedRuntimeDenyOnlyCooldownMarkerStore(medium)

        assertEquals(
            MarkerWriteResult.Failed,
            adapter.writeMarker("not-a-marker".toByteArray()),
        )
        assertEquals(MarkerReadResult.Absent, adapter.readMarker())
        assertFalse(adapter.javaClass == DestructiveAttemptLease::class.java)
        assertFalse(adapter.javaClass == DestructiveCapability::class.java)
        assertFalse(adapter.javaClass == FinalExecutionPermit::class.java)
    }

    @Test
    fun `write plus readback is required before a counted attempt can be admitted`() {
        val medium = ReconstructableDenyOnlyMarkerMedium()
        val clock = MutableMonotonicClock(1_000L)
        val cooldown = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(medium),
            clock,
        )
        val admission = DestructiveAttemptAdmissionAuthority(cooldown, clock)
        val correlation = DestructiveCorrelationId.generate { "adapter-correlation" }

        assertEquals(
            CountedAttemptRecordResult.Recorded::class.java,
            admission.recordCountedAttempt(correlation, DestructiveScope.DEVICE_FACTORY_RESET)::class.java,
        )
        val loaded = medium.loadEncodedMarker()
        assertTrue(loaded is DenyOnlyMarkerLoadResult.Bytes)
        assertTrue(
            (loaded as DenyOnlyMarkerLoadResult.Bytes).value.contentEquals(DenyOnlyCooldownMarker.encode()),
        )
    }

    @Test
    fun `readback failure after write fails closed and issues no counted-attempt proof`() {
        val medium = ReconstructableDenyOnlyMarkerMedium().apply { failNextLoad = true }
        val clock = MutableMonotonicClock(1_000L)
        val cooldown = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(medium),
            clock,
        )
        val admission = DestructiveAttemptAdmissionAuthority(cooldown, clock)

        val recorded = admission.recordCountedAttempt(
            DestructiveCorrelationId.generate { "readback-fail" },
            DestructiveScope.DEVICE_FACTORY_RESET,
        )
        assertTrue(recorded is CountedAttemptRecordResult.Failed)
        assertEquals(
            CooldownDecision.Deny("cooldown_state_unusable"),
            cooldown.canAcceptNewRequest(),
        )
    }

    @Test
    fun `corrupt malformed unreadable or unavailable media fail closed`() {
        val corrupt = SharedDenyOnlyMarkerState().also { it.bytes = "restored-garbage".toByteArray() }
        val corruptCooldown = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(ReconstructableDenyOnlyMarkerMedium(corrupt)),
            MutableMonotonicClock(1_000L),
        )
        assertEquals(
            CooldownDecision.Deny("cooldown_state_unusable"),
            corruptCooldown.canAcceptNewRequest(),
        )

        val unreadable = ReconstructableDenyOnlyMarkerMedium().apply { loadSucceeds = false }
        val unreadableCooldown = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(unreadable),
            MutableMonotonicClock(1_000L),
        )
        assertEquals(
            CooldownDecision.Deny("cooldown_state_unusable"),
            unreadableCooldown.canAcceptNewRequest(),
        )

        val unavailable = ReconstructableDenyOnlyMarkerMedium().apply { unavailable = true }
        val unavailableCooldown = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(unavailable),
            MutableMonotonicClock(1_000L),
        )
        assertEquals(
            CooldownDecision.Deny("cooldown_state_unusable"),
            unavailableCooldown.canAcceptNewRequest(),
        )
    }

    @Test
    fun `process restart with Present marker starts a fresh full monotonic cooldown`() {
        val state = SharedDenyOnlyMarkerState()
        val first = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(ReconstructableDenyOnlyMarkerMedium(state)),
            MutableMonotonicClock(4_000L),
        )
        assertEquals(CooldownRecordResult.Recorded, first.recordAttempt())

        val restartClock = MutableMonotonicClock(4_000L)
        val restarted = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(ReconstructableDenyOnlyMarkerMedium(state)),
            restartClock,
        )
        assertEquals(CooldownDecision.Deny("cooldown_active"), restarted.canAcceptNewRequest())
        restartClock.now = 4_000L + DestructiveDenyOnlyCooldown.DEFAULT_COOLDOWN_MILLIS
        assertEquals(CooldownDecision.NotDenied, restarted.canAcceptNewRequest())
    }

    @Test
    fun `absence on a fresh install is allowed`() {
        val cooldown = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(ReconstructableDenyOnlyMarkerMedium()),
            MutableMonotonicClock(1_000L),
        )
        assertEquals(CooldownDecision.NotDenied, cooldown.canAcceptNewRequest())
    }

    @Test
    fun `disappearance after current-attempt admission is fail closed`() {
        val medium = ReconstructableDenyOnlyMarkerMedium()
        val clock = MutableMonotonicClock(1_000L)
        val cooldown = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(medium),
            clock,
        )
        assertEquals(CooldownRecordResult.Recorded, cooldown.recordAttempt())
        medium.loseMarker()
        val usable = cooldown.assertCurrentAttemptMarkerPresent()
        assertTrue(usable is CooldownUsable.Unusable)
        assertEquals(
            "cooldown_marker_missing_for_current_attempt",
            (usable as CooldownUsable.Unusable).reason,
        )
    }

    @Test
    fun `backup-restored malformed marker cannot shorten cooldown into authorization`() {
        val restored = SharedDenyOnlyMarkerState().also {
            it.bytes = "SENTINEL_DENY_ONLY_COOLDOWN_REQUIRED_V1_TAMPERED".toByteArray()
        }
        val cooldown = DestructiveDenyOnlyCooldown(
            TrustedRuntimeDenyOnlyCooldownMarkerStore(ReconstructableDenyOnlyMarkerMedium(restored)),
            MutableMonotonicClock(1_000L),
        )
        assertEquals(
            CooldownDecision.Deny("cooldown_state_unusable"),
            cooldown.canAcceptNewRequest(),
        )
    }

    @Test
    fun `adapter source is purpose-specific and has no wall-clock remaining calculation`() {
        val source = File(
            "src/main/kotlin/com/example/devicemanagement/persistence/TrustedRuntimeDenyOnlyCooldownMarkerStore.kt",
        ).readText()
        assertTrue(source.contains("Purpose-specific"))
        assertTrue(source.contains("may only deny"))
        assertFalse(source.contains("currentTimeMillis"))
        assertFalse(source.contains("expiresAtEpoch"))
        assertFalse(source.contains("remainingCooldown"))
        assertFalse(source.contains("wipeData"))
        assertFalse(source.contains("wipeDevice"))
        assertFalse(source.contains("BOOT_COMPLETED"))
        assertFalse(source.contains("java.io.File"))
        assertFalse(source.contains("openOrCreateDatabase"))
    }

    @Test
    fun `medium interface is not a general storage capability`() {
        val methods = DenyOnlyMarkerDurableMedium::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue(methods.contains("persistEncodedMarker"))
        assertTrue(methods.contains("loadEncodedMarker"))
        assertFalse(methods.contains("open"))
        assertFalse(methods.contains("query"))
        assertFalse(methods.contains("delete"))
        assertFalse(methods.contains("putString"))
        assertFalse(methods.contains("getAll"))
    }
}
