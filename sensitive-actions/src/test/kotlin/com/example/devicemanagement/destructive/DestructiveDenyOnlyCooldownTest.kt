package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DestructiveDenyOnlyCooldownTest {
    @Test
    fun `no marker does not deny a first request`() {
        val clock = MutableMonotonicClock(1_000L)
        val cooldown = DestructiveDenyOnlyCooldown(InMemoryDenyOnlyCooldownMarkerStore(), clock)
        assertEquals(CooldownDecision.NotDenied, cooldown.canAcceptNewRequest())
    }

    @Test
    fun `valid marker starts a fresh full monotonic cooldown after construction`() {
        val state = SharedDenyOnlyMarkerState()
        val writer = InMemoryDenyOnlyCooldownMarkerStore(state)
        writer.writeMarker(DenyOnlyCooldownMarker.encode())
        val clock = MutableMonotonicClock(5_000L)
        val cooldown = DestructiveDenyOnlyCooldown(InMemoryDenyOnlyCooldownMarkerStore(state), clock)

        assertEquals(
            CooldownDecision.Deny("cooldown_active"),
            cooldown.canAcceptNewRequest(),
        )
        clock.now = 5_000L + DestructiveDenyOnlyCooldown.DEFAULT_COOLDOWN_MILLIS
        assertEquals(CooldownDecision.NotDenied, cooldown.canAcceptNewRequest())
    }

    @Test
    fun `restart reconstructs deny-only cooldown from the surviving marker`() {
        val state = SharedDenyOnlyMarkerState()
        val clock = MutableMonotonicClock(1_000L)
        val first = DestructiveDenyOnlyCooldown(InMemoryDenyOnlyCooldownMarkerStore(state), clock)
        assertEquals(CooldownRecordResult.Recorded, first.recordAttempt())

        val restarted = DestructiveDenyOnlyCooldown(
            InMemoryDenyOnlyCooldownMarkerStore(state),
            MutableMonotonicClock(1_000L),
        )
        assertEquals(
            CooldownDecision.Deny("cooldown_active"),
            restarted.canAcceptNewRequest(),
        )
    }

    @Test
    fun `repeated restart cannot shorten cooldown`() {
        val state = SharedDenyOnlyMarkerState()
        InMemoryDenyOnlyCooldownMarkerStore(state).writeMarker(DenyOnlyCooldownMarker.encode())
        repeat(3) {
            val clock = MutableMonotonicClock(10_000L)
            val cooldown = DestructiveDenyOnlyCooldown(InMemoryDenyOnlyCooldownMarkerStore(state), clock)
            assertEquals(
                CooldownDecision.Deny("cooldown_active"),
                cooldown.canAcceptNewRequest(),
            )
        }
    }

    @Test
    fun `reboot-equivalent reconstruction uses a fresh full window`() {
        val file = File.createTempFile("deny-only-cooldown", ".marker")
        file.writeBytes(DenyOnlyCooldownMarker.encode())
        val store = TestOnlyDenyOnlyCooldownReconstructionAdapter(file)
        val clock = MutableMonotonicClock(2_000L)
        val cooldown = DestructiveDenyOnlyCooldown(store, clock)
        assertEquals(CooldownDecision.Deny("cooldown_active"), cooldown.canAcceptNewRequest())
        clock.now = 2_000L + DestructiveDenyOnlyCooldown.DEFAULT_COOLDOWN_MILLIS
        assertEquals(CooldownDecision.NotDenied, cooldown.canAcceptNewRequest())
        file.delete()
    }

    @Test
    fun `malformed state fails closed`() {
        val state = SharedDenyOnlyMarkerState()
        state.bytes = "not-a-marker".toByteArray()
        val cooldown = DestructiveDenyOnlyCooldown(
            InMemoryDenyOnlyCooldownMarkerStore(state),
            MutableMonotonicClock(1_000L),
        )
        assertEquals(
            CooldownDecision.Deny("cooldown_state_unusable"),
            cooldown.canAcceptNewRequest(),
        )
    }

    @Test
    fun `storage unavailable fails closed`() {
        val store = InMemoryDenyOnlyCooldownMarkerStore().apply { unavailable = true }
        val cooldown = DestructiveDenyOnlyCooldown(store, MutableMonotonicClock(1_000L))
        assertEquals(
            CooldownDecision.Deny("cooldown_state_unusable"),
            cooldown.canAcceptNewRequest(),
        )
    }

    @Test
    fun `marker write failure fails closed`() {
        val store = InMemoryDenyOnlyCooldownMarkerStore().apply { writeSucceeds = false }
        val cooldown = DestructiveDenyOnlyCooldown(store, MutableMonotonicClock(1_000L))
        assertEquals(
            CooldownRecordResult.Failed("cooldown_marker_write_failed"),
            cooldown.recordAttempt(),
        )
        assertEquals(
            CooldownDecision.Deny("cooldown_state_unusable"),
            cooldown.canAcceptNewRequest(),
        )
    }

    @Test
    fun `marker readback failure fails closed`() {
        val store = InMemoryDenyOnlyCooldownMarkerStore().apply { readbackSucceeds = false }
        val cooldown = DestructiveDenyOnlyCooldown(store, MutableMonotonicClock(1_000L))
        assertEquals(
            CooldownRecordResult.Failed("cooldown_marker_readback_failed"),
            cooldown.recordAttempt(),
        )
        assertEquals(
            CooldownDecision.Deny("cooldown_state_unusable"),
            cooldown.canAcceptNewRequest(),
        )
    }

    @Test
    fun `wall clock manipulation has no authority effect`() {
        val state = SharedDenyOnlyMarkerState()
        InMemoryDenyOnlyCooldownMarkerStore(state).writeMarker(DenyOnlyCooldownMarker.encode())
        val monotonic = MutableMonotonicClock(1_000L)
        val cooldown = DestructiveDenyOnlyCooldown(InMemoryDenyOnlyCooldownMarkerStore(state), monotonic)
        val wallClocks = listOf(0L, Long.MAX_VALUE, -1L, 42L)
        wallClocks.forEach { _ ->
            assertEquals(
                CooldownDecision.Deny("cooldown_active"),
                cooldown.canAcceptNewRequest(),
            )
        }
        monotonic.now = 1_000L + DestructiveDenyOnlyCooldown.DEFAULT_COOLDOWN_MILLIS
        wallClocks.forEach { _ ->
            assertEquals(CooldownDecision.NotDenied, cooldown.canAcceptNewRequest())
        }
    }

    @Test
    fun `negative monotonic delta after restart fails closed`() {
        val state = SharedDenyOnlyMarkerState()
        InMemoryDenyOnlyCooldownMarkerStore(state).writeMarker(DenyOnlyCooldownMarker.encode())
        val clock = MutableMonotonicClock(8_000L)
        val cooldown = DestructiveDenyOnlyCooldown(InMemoryDenyOnlyCooldownMarkerStore(state), clock)
        clock.now = 7_000L
        assertEquals(
            CooldownDecision.Deny("cooldown_negative_monotonic_delta"),
            cooldown.canAcceptNewRequest(),
        )
    }

    @Test
    fun `persisted marker never authorizes`() {
        val source = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveDenyOnlyCooldown.kt",
        ).readText()
        assertTrue(source.contains("may only deny"))
        assertTrue(!source.contains("currentTimeMillis"))
        assertTrue(!source.contains("expiresAtEpoch"))
        assertTrue(!source.contains("Approval"))
        assertTrue(!source.contains("DestructiveCapability"))
        assertTrue(!source.contains("FinalExecutionPermit"))
    }
}

/**
 * Test-only reconstruction adapter. Exercises deny-only marker
 * TESTED PERSISTENCE SEMANTICS (write / readback / reboot-equivalent
 * reconstruction). This is not a trusted RUNTIME PERSISTENCE
 * IMPLEMENTATION and must not be moved into main sources.
 */
internal class TestOnlyDenyOnlyCooldownReconstructionAdapter(
    private val file: File,
) : DenyOnlyCooldownMarkerStore {
    override fun writeMarker(bytes: ByteArray): MarkerWriteResult {
        return try {
            file.writeBytes(bytes)
            MarkerWriteResult.Written
        } catch (_: Throwable) {
            MarkerWriteResult.Failed
        }
    }

    override fun readMarker(): MarkerReadResult {
        return try {
            if (!file.isFile) {
                MarkerReadResult.Absent
            } else {
                MarkerReadResult.Bytes(file.readBytes())
            }
        } catch (_: Throwable) {
            MarkerReadResult.Unreadable
        }
    }
}
