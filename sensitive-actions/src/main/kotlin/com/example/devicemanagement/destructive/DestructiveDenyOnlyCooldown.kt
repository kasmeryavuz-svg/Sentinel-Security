package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource

/**
 * Tiny deny-only circuit breaker. The codec and in-process state machine
 * are implemented here. Persisted bytes may only deny. They never
 * authorize, arm, resume, execute, or become an attempt lease. After
 * process start, a well-formed marker starts a fresh full monotonic
 * cooldown. Same-UID arbitrary code remains application compromise and is
 * out of scope.
 *
 * TESTED PERSISTENCE SEMANTICS remain in this state machine. The purpose-
 * specific trusted RUNTIME PERSISTENCE IMPLEMENTATION is
 * TrustedRuntimeDenyOnlyCooldownMarkerStore plus a
 * DenyOnlyMarkerDurableMedium. The persisted marker may only deny.
 */
internal object DenyOnlyCooldownMarker {
    const val MAGIC = "SENTINEL_DENY_ONLY_COOLDOWN_REQUIRED_V1"

    fun encode(): ByteArray = MAGIC.toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): MarkerDecode {
        val text = try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Throwable) {
            return MarkerDecode.Malformed
        }
        return if (text == MAGIC) MarkerDecode.Present else MarkerDecode.Malformed
    }
}

internal sealed interface MarkerDecode {
    data object Present : MarkerDecode

    data object Malformed : MarkerDecode
}

internal sealed interface MarkerReadResult {
    data object Absent : MarkerReadResult

    data class Bytes(val value: ByteArray) : MarkerReadResult

    data object Unreadable : MarkerReadResult

    data object Unavailable : MarkerReadResult
}

internal sealed interface MarkerWriteResult {
    data object Written : MarkerWriteResult

    data object Failed : MarkerWriteResult
}

internal interface DenyOnlyCooldownMarkerStore {
    fun writeMarker(bytes: ByteArray): MarkerWriteResult

    fun readMarker(): MarkerReadResult
}

internal class SharedDenyOnlyMarkerState {
    var bytes: ByteArray? = null
}

internal class InMemoryDenyOnlyCooldownMarkerStore(
    private val state: SharedDenyOnlyMarkerState = SharedDenyOnlyMarkerState(),
) : DenyOnlyCooldownMarkerStore {
    var writeSucceeds: Boolean = true
    var readSucceeds: Boolean = true
    var unavailable: Boolean = false
    var readbackSucceeds: Boolean = true
    private var readsAfterSuccessfulWrite: Int = 0

    override fun writeMarker(bytes: ByteArray): MarkerWriteResult {
        if (unavailable || !writeSucceeds) {
            return MarkerWriteResult.Failed
        }
        state.bytes = bytes.copyOf()
        readsAfterSuccessfulWrite = 0
        return MarkerWriteResult.Written
    }

    override fun readMarker(): MarkerReadResult {
        if (unavailable) {
            return MarkerReadResult.Unavailable
        }
        if (!readSucceeds) {
            return MarkerReadResult.Unreadable
        }
        if (!readbackSucceeds && readsAfterSuccessfulWrite == 0 && state.bytes != null) {
            readsAfterSuccessfulWrite += 1
            return MarkerReadResult.Unreadable
        }
        readsAfterSuccessfulWrite += 1
        val current = state.bytes ?: return MarkerReadResult.Absent
        return MarkerReadResult.Bytes(current.copyOf())
    }

    fun loseMarker() {
        state.bytes = null
    }
}

internal class DestructiveDenyOnlyCooldown(
    private val store: DenyOnlyCooldownMarkerStore,
    private val monotonicTimeSource: MonotonicTimeSource,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
) {
    private var failClosed: Boolean
    private val restartCooldownUntilMonotonicMillis: Long?
    private val restartCooldownStartMonotonicMillis: Long?
    private var subsequentCooldownUntilMonotonicMillis: Long? = null
    private var subsequentCooldownStartMonotonicMillis: Long? = null

    init {
        when (val decoded = readDecoded()) {
            is DecodedMarker.Absent -> {
                failClosed = false
                restartCooldownUntilMonotonicMillis = null
                restartCooldownStartMonotonicMillis = null
            }
            is DecodedMarker.Present -> {
                failClosed = false
                val now = monotonicTimeSource.nowMillis()
                restartCooldownStartMonotonicMillis = now
                restartCooldownUntilMonotonicMillis = now + cooldownMillis
            }
            is DecodedMarker.Unusable -> {
                failClosed = true
                restartCooldownUntilMonotonicMillis = null
                restartCooldownStartMonotonicMillis = null
            }
        }
    }

    fun canAcceptNewRequest(): CooldownDecision {
        if (failClosed) {
            return CooldownDecision.Deny("cooldown_state_unusable")
        }
        val now = monotonicTimeSource.nowMillis()
        denyIfWindowActive(
            start = restartCooldownStartMonotonicMillis,
            until = restartCooldownUntilMonotonicMillis,
            now = now,
        )?.let { return it }
        denyIfWindowActive(
            start = subsequentCooldownStartMonotonicMillis,
            until = subsequentCooldownUntilMonotonicMillis,
            now = now,
        )?.let { return it }
        return CooldownDecision.NotDenied
    }

    fun recordAttempt(): CooldownRecordResult {
        if (failClosed) {
            return CooldownRecordResult.Failed("cooldown_state_unusable")
        }
        when (store.writeMarker(DenyOnlyCooldownMarker.encode())) {
            MarkerWriteResult.Failed -> {
                failClosed = true
                return CooldownRecordResult.Failed("cooldown_marker_write_failed")
            }
            MarkerWriteResult.Written -> Unit
        }
        when (val decoded = readDecoded()) {
            is DecodedMarker.Present -> Unit
            is DecodedMarker.Absent,
            is DecodedMarker.Unusable,
            -> {
                failClosed = true
                return CooldownRecordResult.Failed("cooldown_marker_readback_failed")
            }
        }
        val now = monotonicTimeSource.nowMillis()
        subsequentCooldownStartMonotonicMillis = now
        subsequentCooldownUntilMonotonicMillis = now + cooldownMillis
        return CooldownRecordResult.Recorded
    }

    fun assertUsable(): CooldownUsable {
        if (failClosed) {
            return CooldownUsable.Unusable("cooldown_state_unusable")
        }
        return when (readDecoded()) {
            is DecodedMarker.Unusable -> {
                failClosed = true
                CooldownUsable.Unusable("cooldown_state_unusable")
            }
            is DecodedMarker.Absent,
            is DecodedMarker.Present,
            -> CooldownUsable.Usable
        }
    }

    /**
     * Current-attempt check used after a live admission. Startup may treat
     * an absent marker as "no prior cooldown". Once this process wrote and
     * read back a marker for the live attempt, anything except Present is
     * fail-closed for that attempt.
     */
    fun assertCurrentAttemptMarkerPresent(): CooldownUsable {
        if (failClosed) {
            return CooldownUsable.Unusable("cooldown_state_unusable")
        }
        return when (readDecoded()) {
            is DecodedMarker.Present -> CooldownUsable.Usable
            is DecodedMarker.Absent -> {
                failClosed = true
                CooldownUsable.Unusable("cooldown_marker_missing_for_current_attempt")
            }
            is DecodedMarker.Unusable -> {
                failClosed = true
                CooldownUsable.Unusable("cooldown_state_unusable")
            }
        }
    }

    private fun denyIfWindowActive(
        start: Long?,
        until: Long?,
        now: Long,
    ): CooldownDecision.Deny? {
        if (start == null || until == null) {
            return null
        }
        if (now < start) {
            failClosed = true
            return CooldownDecision.Deny("cooldown_negative_monotonic_delta")
        }
        if (now < until) {
            return CooldownDecision.Deny("cooldown_active")
        }
        return null
    }

    private fun readDecoded(): DecodedMarker {
        return when (val read = store.readMarker()) {
            MarkerReadResult.Absent -> DecodedMarker.Absent
            MarkerReadResult.Unreadable,
            MarkerReadResult.Unavailable,
            -> DecodedMarker.Unusable
            is MarkerReadResult.Bytes -> when (DenyOnlyCooldownMarker.decode(read.value)) {
                MarkerDecode.Present -> DecodedMarker.Present
                MarkerDecode.Malformed -> DecodedMarker.Unusable
            }
        }
    }

    private sealed interface DecodedMarker {
        data object Absent : DecodedMarker

        data object Present : DecodedMarker

        data object Unusable : DecodedMarker
    }

    internal companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 15_000L
    }
}

internal sealed interface CooldownDecision {
    data object NotDenied : CooldownDecision

    data class Deny(val reason: String) : CooldownDecision
}

internal sealed interface CooldownRecordResult {
    data object Recorded : CooldownRecordResult

    data class Failed(val reason: String) : CooldownRecordResult
}

internal sealed interface CooldownUsable {
    data object Usable : CooldownUsable

    data class Unusable(val reason: String) : CooldownUsable
}
