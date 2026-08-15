package com.example.devicemanagement.destructive

import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium
import com.example.devicemanagement.persistence.DenyOnlyMarkerLoadResult
import com.example.devicemanagement.persistence.DenyOnlyMarkerPersistResult
import com.example.devicemanagement.persistence.ReconstructableDenyOnlyMarkerMedium
import com.example.devicemanagement.persistence.TrustedRuntimeDenyOnlyCooldownMarkerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.reflect.Modifier

class RuntimeDestructiveDurabilitySeparationTest {
    @Test
    fun `in-memory cooldown store cannot satisfy the runtime cooldown capability`() {
        val inMemory = InMemoryDenyOnlyCooldownMarkerStore()
        assertFalse(
            RuntimeDenyOnlyCooldownStore::class.java.isAssignableFrom(inMemory.javaClass),
        )
        assertFalse(
            inMemory.javaClass.isAssignableFrom(RuntimeDenyOnlyCooldownStore::class.java),
        )
        assertTrue(inMemory.javaClass != RuntimeDenyOnlyCooldownStore::class.java)
        assertTrue(onlyKotlinPrivateConstructor(RuntimeDenyOnlyCooldownStore::class.java))
    }

    @Test
    fun `in-memory pre-execution store cannot satisfy the runtime pre-execution capability`() {
        val inMemory = InMemoryDestructivePreExecutionDurableStore()
        assertFalse(
            RuntimeDestructivePreExecutionStore::class.java.isAssignableFrom(inMemory.javaClass),
        )
        assertFalse(
            inMemory.javaClass.isAssignableFrom(RuntimeDestructivePreExecutionStore::class.java),
        )
        assertNull(
            RuntimeDestructiveSafetyDurability.issueFromTrustedAndroidStores(
                ReconstructableDenyOnlyMarkerMedium(),
                inMemory,
            ),
        )
        assertFalse(
            RuntimeDestructiveSafetyDurability.isTrustedPreExecutionStore(inMemory),
        )
        assertTrue(onlyKotlinPrivateConstructor(RuntimeDestructivePreExecutionStore::class.java))
    }

    @Test
    fun `reconstructed test media cannot become runtime authority`() {
        val medium = ReconstructableDenyOnlyMarkerMedium()
        val adapter = TrustedRuntimeDenyOnlyCooldownMarkerStore(medium)
        assertFalse(
            RuntimeDenyOnlyCooldownStore::class.java.isAssignableFrom(adapter.javaClass),
        )
        assertFalse(RuntimeDestructiveSafetyDurability.isTrustedCooldownMedium(medium))
        assertNull(
            RuntimeDestructiveSafetyDurability.issueFromTrustedAndroidStores(
                medium,
                InMemoryDestructivePreExecutionDurableStore(),
            ),
        )
        assertTrue(DenyOnlyCooldownMarkerStore::class.java.isAssignableFrom(adapter.javaClass))
        assertFalse(
            RuntimeDestructiveSafetyDurability::class.java.isAssignableFrom(adapter.javaClass),
        )
    }

    @Test
    fun `generic caller-supplied persistence cannot mint runtime durability`() {
        val forgedMedium = ForgedDenyOnlyMarkerMedium()
        val forgedStore = ForgedPreExecutionStore()
        assertFalse(RuntimeDestructiveSafetyDurability.isTrustedCooldownMedium(forgedMedium))
        assertFalse(RuntimeDestructiveSafetyDurability.isTrustedPreExecutionStore(forgedStore))
        assertNull(
            RuntimeDestructiveSafetyDurability.issueFromTrustedAndroidStores(
                forgedMedium,
                forgedStore,
            ),
        )
        assertFalse(
            RuntimeDestructiveSafetyDurability.isTrustedPreExecutionStore(
                UnavailableDestructivePreExecutionDurableStore(),
            ),
        )
    }

    @Test
    fun `Android trusted runtime class names are the only accepted mint inputs`() {
        assertEquals(
            "com.example.devicemanagement.persistence.SqliteDenyOnlyMarkerStore",
            RuntimeDestructiveSafetyDurability.TRUSTED_COOLDOWN_MEDIUM_CLASS,
        )
        assertEquals(
            "com.example.devicemanagement.persistence.SqliteDestructivePreExecutionStore",
            RuntimeDestructiveSafetyDurability.TRUSTED_PRE_EXECUTION_STORE_CLASS,
        )
        val factory = listOf(
            File("../device-management/src/main/java/com/example/devicemanagement/persistence/SqliteDenyOnlyMarkerStore.kt"),
            File("device-management/src/main/java/com/example/devicemanagement/persistence/SqliteDenyOnlyMarkerStore.kt"),
        ).first { it.isFile }.readText()
        assertTrue(factory.contains("internal class SqliteDenyOnlyMarkerStore"))
        assertTrue(factory.contains("internal class SqliteDestructivePreExecutionStore"))
        assertTrue(factory.contains("fun issueRuntimeDurability"))
        assertTrue(factory.contains("RuntimeDestructiveSafetyDurability"))
        assertTrue(factory.contains("issueFromTrustedAndroidStores"))
        assertTrue(factory.contains("Not invoked by DeviceManagement composition"))
        assertFalse(factory.contains("wipeDevice"))
        assertFalse(factory.contains("wipeData"))
    }

    @Test
    fun `app UI recovery and production composition cannot obtain runtime durability`() {
        val recovery = File("src/main/kotlin/com/example/devicemanagement/recovery")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        listOf(
            "RuntimeDenyOnlyCooldownStore",
            "RuntimeDestructivePreExecutionStore",
            "RuntimeDestructiveSafetyDurability",
            "issueFromTrustedAndroidStores",
            "issueRuntimeDurability",
            "AndroidDestructiveSafetyPersistence",
        ).forEach { token ->
            assertFalse(recovery.contains(token))
            assertFalse(controller.contains(token))
        }
    }

    @Test
    fun `runtime durability capability is not serializable or persistable`() {
        val types = listOf(
            RuntimeDenyOnlyCooldownStore::class.java,
            RuntimeDestructivePreExecutionStore::class.java,
            RuntimeDestructiveSafetyDurability::class.java,
        )
        types.forEach { type ->
            assertFalse(Serializable::class.java.isAssignableFrom(type))
            assertFalse(type.interfaces.any { it.name == "android.os.Parcelable" })
            assertTrue(onlyKotlinPrivateConstructor(type))
            assertFalse(type.declaredFields.any { it.name == "serialVersionUID" })
            assertFalse(type.declaredMethods.any { it.name == "writeObject" || it.name == "writeReplace" })
        }
        val failed = try {
            ObjectOutputStream(ByteArrayOutputStream()).use {
                it.writeObject(RuntimeDestructiveSafetyDurability)
            }
            false
        } catch (_: Exception) {
            true
        }
        assertTrue(failed)
    }

    @Test
    fun `simulation continues to work without becoming production reachable or runtime durable`() {
        val composition = DestructiveSimulationComposition.create()
        assertEquals(
            DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE,
            composition.pipeline.submit(validRequest()).outcome,
        )
        assertEquals(1, composition.sink.invocationCount())
        assertFalse(Checkpoint17BHardBlock.PRODUCTION_REACHABLE_SIMULATION)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED)
        assertNotNull(composition.markerStore)
        assertFalse(
            RuntimeDenyOnlyCooldownStore::class.java.isAssignableFrom(
                composition.markerStore!!.javaClass,
            ),
        )
        assertFalse(
            RuntimeDestructivePreExecutionStore::class.java.isAssignableFrom(
                composition.durableStore.javaClass,
            ),
        )
        assertTrue(
            SimulatedDestructiveExecutor::class.java.declaredConstructors.none { constructor ->
                constructor.parameterTypes.any {
                    it == RuntimeDestructiveSafetyDurability::class.java ||
                        it == RuntimeDenyOnlyCooldownStore::class.java ||
                        it == RuntimeDestructivePreExecutionStore::class.java
                }
            },
        )
        assertTrue(
            PreExecutionEvidenceCommitAuthority::class.java.declaredConstructors.none { constructor ->
                constructor.parameterTypes.any {
                    it == RuntimeDestructiveSafetyDurability::class.java ||
                        it == RuntimeDestructivePreExecutionStore::class.java
                }
            },
        )
        assertTrue(
            DestructiveDenyOnlyCooldown::class.java.declaredConstructors.none { constructor ->
                constructor.parameterTypes.any { it == RuntimeDenyOnlyCooldownStore::class.java }
            },
        )
    }

    private fun onlyKotlinPrivateConstructor(type: Class<*>): Boolean {
        val publicConstructors = type.constructors
        return publicConstructors.isNotEmpty() &&
            publicConstructors.all { constructor ->
                constructor.parameterTypes.any { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" }
            } &&
            type.declaredConstructors.any { Modifier.isPrivate(it.modifiers) }
    }

    private class ForgedDenyOnlyMarkerMedium : DenyOnlyMarkerDurableMedium {
        override fun persistEncodedMarker(encoded: ByteArray): DenyOnlyMarkerPersistResult {
            return DenyOnlyMarkerPersistResult.WRITTEN
        }

        override fun loadEncodedMarker(): DenyOnlyMarkerLoadResult {
            return DenyOnlyMarkerLoadResult.Absent
        }
    }

    private class ForgedPreExecutionStore : DestructivePreExecutionDurableStore {
        override fun insert(record: DestructivePreExecutionDurableRecord): Long = 1L

        override fun latest(limit: Int): DestructivePreExecutionDurableRead {
            return DestructivePreExecutionDurableRead(records = emptyList())
        }

        override fun count(): Int = 0
    }
}
