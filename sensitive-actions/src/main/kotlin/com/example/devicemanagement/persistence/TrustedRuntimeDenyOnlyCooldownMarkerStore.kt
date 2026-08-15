package com.example.devicemanagement.persistence

import com.example.devicemanagement.destructive.DenyOnlyCooldownMarker
import com.example.devicemanagement.destructive.DenyOnlyCooldownMarkerStore
import com.example.devicemanagement.destructive.MarkerReadResult
import com.example.devicemanagement.destructive.MarkerWriteResult

/**
 * Trusted runtime deny-only cooldown persistence adapter.
 *
 * Purpose-specific: it accepts only the exact encoded deny-only marker and
 * delegates to a [DenyOnlyMarkerDurableMedium] that can store only that blob.
 * The persisted marker may only deny. It cannot become an arm, lease,
 * capability, permit, or counted-attempt proof.
 *
 * This adapter is not a general filesystem or database capability. It has no
 * path, query, or arbitrary-write API. Production composition must not wire
 * it into a reachable destructive executor.
 */
internal class TrustedRuntimeDenyOnlyCooldownMarkerStore(
    private val medium: DenyOnlyMarkerDurableMedium,
) : DenyOnlyCooldownMarkerStore {
    override fun writeMarker(bytes: ByteArray): MarkerWriteResult {
        if (!bytes.contentEquals(DenyOnlyCooldownMarker.encode())) {
            return MarkerWriteResult.Failed
        }
        if (bytes.size > DenyOnlyCooldownStorageIdentity.MAX_PAYLOAD_BYTES) {
            return MarkerWriteResult.Failed
        }
        return when (medium.persistEncodedMarker(bytes.copyOf())) {
            DenyOnlyMarkerPersistResult.WRITTEN -> MarkerWriteResult.Written
            DenyOnlyMarkerPersistResult.FAILED -> MarkerWriteResult.Failed
        }
    }

    override fun readMarker(): MarkerReadResult {
        return when (val loaded = medium.loadEncodedMarker()) {
            DenyOnlyMarkerLoadResult.Absent -> MarkerReadResult.Absent
            DenyOnlyMarkerLoadResult.Unreadable -> MarkerReadResult.Unreadable
            DenyOnlyMarkerLoadResult.Unavailable -> MarkerReadResult.Unavailable
            is DenyOnlyMarkerLoadResult.Bytes -> MarkerReadResult.Bytes(loaded.value)
        }
    }
}
