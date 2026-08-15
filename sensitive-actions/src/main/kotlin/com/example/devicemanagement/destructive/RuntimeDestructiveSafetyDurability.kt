package com.example.devicemanagement.destructive

import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium
import com.example.devicemanagement.persistence.ReconstructableDenyOnlyMarkerMedium
import com.example.devicemanagement.persistence.TrustedRuntimeDenyOnlyCooldownMarkerStore

/**
 * Opaque runtime-durable deny-only cooldown capability.
 *
 * This is not an interface. In-memory and reconstructable test stores cannot
 * implement it or be assigned to it. The only mint path is
 * [RuntimeDestructiveSafetyDurabilityMint.issueFromTrustedAndroidStores],
 * which accepts only the trusted Android SQLite medium.
 *
 * A future real destructive chain must require this type (or the paired
 * [RuntimeDestructiveSafetyDurability]) and must not accept
 * [DenyOnlyCooldownMarkerStore]. Simulation continues to use the generic
 * store and cannot obtain this capability.
 */
class RuntimeDenyOnlyCooldownStore private constructor(
    private val adapter: TrustedRuntimeDenyOnlyCooldownMarkerStore,
) {
    internal fun markerStore(): DenyOnlyCooldownMarkerStore = adapter

    companion object {
        internal fun mintFromTrustedAndroidMedium(
            medium: DenyOnlyMarkerDurableMedium,
        ): RuntimeDenyOnlyCooldownStore? {
            if (!RuntimeDestructiveSafetyDurability.isTrustedCooldownMedium(medium)) {
                return null
            }
            return RuntimeDenyOnlyCooldownStore(
                TrustedRuntimeDenyOnlyCooldownMarkerStore(medium),
            )
        }
    }
}

/**
 * Opaque runtime-durable pre-execution evidence capability.
 *
 * This is not an interface. In-memory and unavailable test stores cannot
 * implement it or be assigned to it. The only mint path is
 * [RuntimeDestructiveSafetyDurabilityMint.issueFromTrustedAndroidStores],
 * which accepts only the trusted Android SQLite store.
 *
 * A future real destructive chain must require this type (or the paired
 * [RuntimeDestructiveSafetyDurability]) and must not accept
 * [DestructivePreExecutionDurableStore]. Simulation continues to use the
 * generic store and cannot obtain this capability.
 */
class RuntimeDestructivePreExecutionStore private constructor(
    private val store: DestructivePreExecutionDurableStore,
) {
    internal fun durableStore(): DestructivePreExecutionDurableStore = store

    companion object {
        internal fun mintFromTrustedAndroidStore(
            store: DestructivePreExecutionDurableStore,
        ): RuntimeDestructivePreExecutionStore? {
            if (!RuntimeDestructiveSafetyDurability.isTrustedPreExecutionStore(store)) {
                return null
            }
            return RuntimeDestructivePreExecutionStore(store)
        }
    }
}

/**
 * Paired runtime-durable destructive safety capability.
 *
 * This is the persistence prerequisite a future real destructive chain must
 * require. It can be produced only from the exact Android trusted SQLite
 * implementations. Test, simulation, reconstructable, unavailable, and any
 * other caller-supplied persistence cannot satisfy it.
 *
 * Issuing this object does not arm, authorize, or execute. There is still
 * no real destructive executor that consumes it, so the Checkpoint 17B
 * ENFORCED flags remain false.
 *
 * The capability is process-local. It is not written to storage and cannot
 * be sent across processes.
 */
class RuntimeDestructiveSafetyDurability private constructor(
    val cooldown: RuntimeDenyOnlyCooldownStore,
    val preExecution: RuntimeDestructivePreExecutionStore,
) {
    companion object {
        const val TRUSTED_COOLDOWN_MEDIUM_CLASS =
            "com.example.devicemanagement.persistence.SqliteDenyOnlyMarkerStore"
        const val TRUSTED_PRE_EXECUTION_STORE_CLASS =
            "com.example.devicemanagement.persistence.SqliteDestructivePreExecutionStore"

        fun isTrustedCooldownMedium(medium: DenyOnlyMarkerDurableMedium): Boolean {
            return medium::class.java.name == TRUSTED_COOLDOWN_MEDIUM_CLASS &&
                medium !is ReconstructableDenyOnlyMarkerMedium
        }

        fun isTrustedPreExecutionStore(store: DestructivePreExecutionDurableStore): Boolean {
            return store::class.java.name == TRUSTED_PRE_EXECUTION_STORE_CLASS &&
                store !is InMemoryDestructivePreExecutionDurableStore &&
                store !is UnavailableDestructivePreExecutionDurableStore
        }
    }

    /**
     * Sole mint path for the runtime-durable pair. Dedicated Kotlin object
     * with one JVM owner
     * (`RuntimeDestructiveSafetyDurability$RuntimeDestructiveSafetyDurabilityMint`).
     * Not a companion. Production bytecode allows this call only from
     * AndroidDestructiveSafetyPersistence.
     */
    object RuntimeDestructiveSafetyDurabilityMint {
        fun issueFromTrustedAndroidStores(
            cooldownMedium: DenyOnlyMarkerDurableMedium,
            preExecutionStore: DestructivePreExecutionDurableStore,
        ): RuntimeDestructiveSafetyDurability? {
            val cooldown = RuntimeDenyOnlyCooldownStore.mintFromTrustedAndroidMedium(cooldownMedium)
                ?: return null
            val preExecution = RuntimeDestructivePreExecutionStore.mintFromTrustedAndroidStore(
                preExecutionStore,
            ) ?: return null
            return RuntimeDestructiveSafetyDurability(
                cooldown = cooldown,
                preExecution = preExecution,
            )
        }
    }
}
