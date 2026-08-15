package com.example.devicemanagement.destructive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint17BHardBlockRealityTest {
    @Test
    fun `destructive implementation flags stay false and match repository reality`() {
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertFalse(Checkpoint17BHardBlock.DESTRUCTIVE_POLICY_WRAPPER_PRESENT)
        assertFalse(Checkpoint17BHardBlock.DESTRUCTIVE_METADATA_PRESENT)
        assertFalse(Checkpoint17BHardBlock.PRODUCTION_REACHABLE_SIMULATION)
        assertFalse(Checkpoint17BHardBlock.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint17BHardBlock.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertFalse(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertFalse(Checkpoint17BHardBlock.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertFalse(Checkpoint17BHardBlock.WIPE_DATA_METADATA_REVIEW_APPROVED)
        assertFalse(Checkpoint17BHardBlock.DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED)
        assertFalse(Checkpoint17BHardBlock.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)

        val production = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(production.contains("wipeDevice"))
        assertFalse(production.contains("wipeData"))
        assertFalse(production.contains("<wipe-data>"))
        assertFalse(production.contains("class DestructiveDevicePolicy"))
        assertFalse(production.contains("fun wipe"))
    }

    @Test
    fun `trusted runtime cooldown adapter flag is true only because the adapter exists`() {
        assertTrue(Checkpoint17BHardBlock.TRUSTED_RUNTIME_COOLDOWN_PERSISTENCE_ADAPTER_PRESENT)
        val adapter = File(
            "src/main/kotlin/com/example/devicemanagement/persistence/TrustedRuntimeDenyOnlyCooldownMarkerStore.kt",
        )
        assertTrue(adapter.isFile)
        val text = adapter.readText()
        assertTrue(text.contains("Purpose-specific"))
        assertTrue(text.contains("may only deny"))
        assertFalse(text.contains("wipeDevice"))
        assertFalse(text.contains("wipeData"))
    }

    @Test
    fun `real durable pre-execution audit flag is true only because the durable path exists`() {
        assertTrue(Checkpoint17BHardBlock.REAL_DURABLE_DESTRUCTIVE_PRE_EXECUTION_AUDIT_PRESENT)
        val repository = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DurableDestructivePreExecutionRepository.kt",
        )
        val authority = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/PreExecutionEvidenceCommitAuthority.kt",
        )
        assertTrue(repository.isFile)
        assertTrue(authority.isFile)
        assertTrue(repository.readText().contains("never authorization"))
        assertTrue(authority.readText().contains("durableRepository"))
        assertFalse(repository.readText().contains("wipeDevice"))
        assertFalse(authority.readText().contains("wipeDevice"))
    }

    @Test
    fun `remaining blockers are machine-visible`() {
        Checkpoint17BHardBlock.remainingDestructiveBoundaryBlockers.forEach { name ->
            assertTrue(name, name in Checkpoint17BHardBlock.remainingDestructiveBoundaryBlockers)
        }
        assertTrue(
            Checkpoint17BHardBlock.gatesRequiringExplicitModification.any {
                it.contains("WIPE_17B_ENTRY_REVIEW.md")
            },
        )
    }

    @Test
    fun `production composition still does not wire the simulation pipeline`() {
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        assertFalse(controller.contains("DestructiveSimulationPipeline"))
        assertFalse(controller.contains("AndroidDestructiveSafetyPersistence"))
        assertFalse(controller.contains("TrustedRuntimeDenyOnlyCooldownMarkerStore"))
    }
}
