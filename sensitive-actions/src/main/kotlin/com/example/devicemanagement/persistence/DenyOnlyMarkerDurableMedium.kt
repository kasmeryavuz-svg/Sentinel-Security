package com.example.devicemanagement.persistence

/**
 * Purpose-specific durable medium for the deny-only cooldown marker.
 *
 * This is not a general file, preferences, or database API. Callers cannot
 * choose a path, table, or key. The only operations are persist and load of
 * the single encoded deny-only marker blob. The blob is never authorization,
 * arming, a lease, a capability, or a permit.
 *
 * Implementations must fail closed on corrupt, malformed, unreadable, or
 * unavailable storage. Absence on a fresh install is [DenyOnlyMarkerLoadResult.Absent].
 */
interface DenyOnlyMarkerDurableMedium {
    fun persistEncodedMarker(encoded: ByteArray): DenyOnlyMarkerPersistResult

    fun loadEncodedMarker(): DenyOnlyMarkerLoadResult
}

enum class DenyOnlyMarkerPersistResult {
    WRITTEN,
    FAILED,
}

sealed interface DenyOnlyMarkerLoadResult {
    data object Absent : DenyOnlyMarkerLoadResult

    class Bytes(value: ByteArray) : DenyOnlyMarkerLoadResult {
        val value: ByteArray = value.copyOf()
    }

    data object Unreadable : DenyOnlyMarkerLoadResult

    data object Unavailable : DenyOnlyMarkerLoadResult
}

object DenyOnlyCooldownStorageIdentity {
    const val TABLE_NAME = "deny_only_marker"
    const val MAX_PAYLOAD_BYTES = 128
}
