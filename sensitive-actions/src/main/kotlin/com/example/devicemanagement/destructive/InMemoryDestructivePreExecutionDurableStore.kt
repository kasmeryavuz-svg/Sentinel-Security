package com.example.devicemanagement.destructive

import java.util.concurrent.atomic.AtomicLong

internal class SharedDestructivePreExecutionDurableState {
    val rows = mutableListOf<PersistedDestructivePreExecutionRow>()
    val nextSequence = AtomicLong(1L)
    var failWrites: Boolean = false
    var failReads: Boolean = false
    var unavailable: Boolean = false
}

internal data class PersistedDestructivePreExecutionRow(
    val sequence: Long,
    val record: DestructivePreExecutionDurableRecord,
)

/**
 * Reconstructable durable pre-execution store for trusted-runtime tests.
 *
 * Shared [SharedDestructivePreExecutionDurableState] survives repository
 * reconstruction the same way a database file survives process restart.
 * Surviving rows are evidence only and cannot reconstruct authorization.
 */
internal class InMemoryDestructivePreExecutionDurableStore(
    private val state: SharedDestructivePreExecutionDurableState = SharedDestructivePreExecutionDurableState(),
) : DestructivePreExecutionDurableStore {
    override fun insert(record: DestructivePreExecutionDurableRecord): Long {
        if (state.unavailable || state.failWrites) {
            throw DestructivePreExecutionStoreException("destructive_pre_execution_write_failed")
        }
        val sequence = state.nextSequence.getAndIncrement()
        state.rows += PersistedDestructivePreExecutionRow(sequence = sequence, record = record)
        return sequence
    }

    override fun latest(limit: Int): DestructivePreExecutionDurableRead {
        failIfReadsBroken()
        return DestructivePreExecutionDurableRead(
            records = state.rows.sortedByDescending { it.sequence }.take(limit.coerceAtLeast(0)).map { it.record },
        )
    }

    override fun count(): Int {
        failIfReadsBroken()
        return state.rows.size
    }

    private fun failIfReadsBroken() {
        if (state.unavailable || state.failReads) {
            throw DestructivePreExecutionStoreException("destructive_pre_execution_read_failed")
        }
    }
}

class UnavailableDestructivePreExecutionDurableStore : DestructivePreExecutionDurableStore {
    override fun insert(record: DestructivePreExecutionDurableRecord): Long {
        throw DestructivePreExecutionStoreException("destructive_pre_execution_store_unavailable")
    }

    override fun latest(limit: Int): DestructivePreExecutionDurableRead {
        throw DestructivePreExecutionStoreException("destructive_pre_execution_store_unavailable")
    }

    override fun count(): Int {
        throw DestructivePreExecutionStoreException("destructive_pre_execution_store_unavailable")
    }
}
