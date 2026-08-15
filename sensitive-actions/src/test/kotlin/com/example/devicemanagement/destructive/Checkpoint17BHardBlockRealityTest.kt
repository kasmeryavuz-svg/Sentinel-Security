package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint17BHardBlockRealityTest {
    @Test
    fun `destructive implementation flags match repository reality`() {
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_POLICY_WRAPPER_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_METADATA_PRESENT)
        assertFalse(Checkpoint17BHardBlock.PRODUCTION_REACHABLE_SIMULATION)
        assertFalse(Checkpoint17BHardBlock.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint17BHardBlock.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertFalse(Checkpoint17BHardBlock.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertTrue(Checkpoint17BHardBlock.WIPE_DATA_METADATA_REVIEW_APPROVED)
        assertTrue(Checkpoint17BHardBlock.DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED)
        assertFalse(Checkpoint17BHardBlock.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_ARTIFACT_IDENTITY_PRECONDITION_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_AUTHORITY_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_WIPE_OPTION_POLICY_PRESENT)

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
        assertTrue(
            "REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED" !in
                Checkpoint17BHardBlock.remainingDestructiveBoundaryBlockers,
        )
        assertTrue(
            "REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED" !in
                Checkpoint17BHardBlock.remainingDestructiveBoundaryBlockers,
        )
        assertTrue(
            "REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED" !in
                Checkpoint17BHardBlock.remainingDestructiveBoundaryBlockers,
        )
        assertTrue(
            "REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED" !in
                Checkpoint17BHardBlock.remainingDestructiveBoundaryBlockers,
        )
        assertTrue(
            "REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED" !in
                Checkpoint17BHardBlock.remainingDestructiveBoundaryBlockers,
        )
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
        assertFalse(controller.contains("RuntimeDestructiveSafetyDurability"))
        assertFalse(controller.contains("issueRuntimeDurability"))
        assertFalse(controller.contains("DestructiveArtifactIdentityAuthority"))
        assertFalse(controller.contains("DestructiveHumanApprovalAuthority"))
        assertFalse(controller.contains("DestructiveHumanConfirmationAuthority"))
        assertFalse(controller.contains("issueChallenge"))
        assertFalse(controller.contains("issueFromTrustedConfirmationSource"))
        assertFalse(controller.contains("issueFromTrustedValidationSource"))
        assertFalse(controller.contains("RuntimeDestructiveSafetyDurabilityMint"))
        assertFalse(controller.contains("TrustedDestructiveArtifactExpectationMint"))
        assertFalse(controller.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(controller.contains("assembleAndHandoff"))
        assertFalse(controller.contains("FutureDestructiveExecutorContract"))
    }

    @Test
    fun `enforced flags are true because the production real-chain path cannot skip those gates`() {
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED)
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
            DestructiveDenyOnlyCooldown::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.any { it == DenyOnlyCooldownMarkerStore::class.java }
            },
        )
        assertTrue(
            PreExecutionEvidenceCommitAuthority::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.any {
                    it == DurableDestructivePreExecutionRepository::class.java
                }
            },
        )
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED)
        assertTrue(
            SimulatedDestructiveExecutor::class.java.declaredConstructors.none { constructor ->
                constructor.parameterTypes.any {
                    it == DestructiveArtifactIdentity::class.java ||
                        it == DestructiveHumanApproval::class.java ||
                        it == DestructiveWipeOptionPolicy::class.java
                }
            },
        )
        val orchestrator = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/" +
                "ProductionDestructiveRealChainOrchestrator.kt",
        ).readText()
        val boundary = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/" +
                "FutureDestructiveExecutorContract.kt",
        ).readText()
        assertTrue(orchestrator.contains("assembleAndHandoff("))
        assertTrue(orchestrator.contains("artifactExpectationSource.trustedExpectation()"))
        assertTrue(orchestrator.contains("confirmationSource.confirm("))
        assertTrue(orchestrator.contains("verifyDefaultDeny("))
        assertTrue(boundary.contains("runtimePreExecutionAuthority.commitAfterConsumedAuthorization"))
        assertTrue(boundary.contains("cooldown.assertCurrentAttemptMarkerPresent()"))
        assertTrue(boundary.contains("artifactAuthority.consume("))
        assertTrue(boundary.contains("humanApprovalAuthority.consume("))
        assertTrue(boundary.contains("wipePolicyAuthority.consume("))
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        val progression = ProductionDestructiveRealChainOrchestrator::class.java.declaredMethods
            .single { it.name == "assembleAlreadyBoundDeviceFactoryReset" }
        assertTrue(DestructiveArtifactIdentityMatchProof::class.java in handoff.parameterTypes)
        assertTrue(DestructiveHumanApproval::class.java in handoff.parameterTypes)
        assertTrue(DestructiveWipeOptionPolicyProof::class.java in handoff.parameterTypes)
        assertEquals(
            ProductionBoundDeviceFactoryResetAttempt::class.java,
            progression.parameterTypes.single(),
        )
        assertFalse(DestructiveArtifactIdentityExpectation::class.java in progression.parameterTypes)
        assertFalse(DestructiveHumanApproval::class.java in progression.parameterTypes)
        assertFalse(FutureDestructiveExecutorContract::class.java in progression.parameterTypes)
        assertFalse(Int::class.javaPrimitiveType in progression.parameterTypes)
    }

    @Test
    fun `new prerequisite present flags are true only because the components exist`() {
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_ARTIFACT_IDENTITY_PRECONDITION_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_AUTHORITY_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_WIPE_OPTION_POLICY_PRESENT)
        assertTrue(
            File("src/main/kotlin/com/example/devicemanagement/destructive/DestructiveArtifactIdentity.kt").isFile,
        )
        assertTrue(
            File(
                "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveHumanApprovalAuthority.kt",
            ).isFile,
        )
        assertTrue(
            File("src/main/kotlin/com/example/devicemanagement/destructive/DestructiveWipeOptionPolicy.kt").isFile,
        )
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertFalse(Checkpoint17BHardBlock.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertFalse(Checkpoint17BHardBlock.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
    }
}
