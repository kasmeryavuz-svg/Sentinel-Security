package com.example.devicemanagement.persistence

import com.example.devicemanagement.destructive.SharedDenyOnlyMarkerState

/**
 * Reconstructable purpose-specific medium for trusted-runtime adapter tests.
 *
 * Shared [SharedDenyOnlyMarkerState] survives adapter reconstruction the same
 * way a durable marker file survives process restart. This is not a general
 * filesystem API and is not an Android Context store.
 */
internal class ReconstructableDenyOnlyMarkerMedium(
    private val state: SharedDenyOnlyMarkerState = SharedDenyOnlyMarkerState(),
) : DenyOnlyMarkerDurableMedium {
    var persistSucceeds: Boolean = true
    var loadSucceeds: Boolean = true
    var unavailable: Boolean = false
    var failNextLoad: Boolean = false

    override fun persistEncodedMarker(encoded: ByteArray): DenyOnlyMarkerPersistResult {
        if (unavailable || !persistSucceeds) {
            return DenyOnlyMarkerPersistResult.FAILED
        }
        if (encoded.size > DenyOnlyCooldownStorageIdentity.MAX_PAYLOAD_BYTES) {
            return DenyOnlyMarkerPersistResult.FAILED
        }
        state.bytes = encoded.copyOf()
        return DenyOnlyMarkerPersistResult.WRITTEN
    }

    override fun loadEncodedMarker(): DenyOnlyMarkerLoadResult {
        if (unavailable) {
            return DenyOnlyMarkerLoadResult.Unavailable
        }
        if (!loadSucceeds) {
            return DenyOnlyMarkerLoadResult.Unreadable
        }
        if (failNextLoad) {
            failNextLoad = false
            return DenyOnlyMarkerLoadResult.Unreadable
        }
        val current = state.bytes ?: return DenyOnlyMarkerLoadResult.Absent
        return DenyOnlyMarkerLoadResult.Bytes(current)
    }

    fun loseMarker() {
        state.bytes = null
    }

    fun replaceWithCorruptPayload(payload: ByteArray) {
        state.bytes = payload.copyOf()
    }
}
