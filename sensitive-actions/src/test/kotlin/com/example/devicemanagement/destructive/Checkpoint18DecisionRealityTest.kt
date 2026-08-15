package com.example.devicemanagement.destructive

import com.example.devicemanagement.action.Approval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.Serializable

class Checkpoint18DecisionRealityTest {
    @Test
    fun `structural required flags are true only because the contract requires those types`() {
        assertTrue(Checkpoint18Decision.DESTRUCTIVE_EXECUTOR_CONTRACT_PRESENT)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_UNFORGEABLE_HANDOFF_PRESENT)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_RUNTIME_DURABILITY_REQUIRED)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_ARTIFACT_IDENTITY_REQUIRED)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_HUMAN_APPROVAL_REQUIRED)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_WIPE_OPTION_POLICY_REQUIRED)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_FINAL_LIVE_VALIDATION_REQUIRED)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_PRE_EXECUTION_APPEND_AFTER_CONSUME_REQUIRED)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_RUNTIME_DURABLE_APPEND_PAIRED)
        assertEquals("YES", Checkpoint18Decision.ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL)

        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        val parameters = handoff.parameterTypes.toList()
        assertTrue(FutureDestructiveExecutorContract::class.java in parameters)
        assertTrue(DestructiveTargetBinding::class.java in parameters)
        assertTrue(DestructiveAttemptLease::class.java in parameters)
        assertTrue(DestructiveCapability::class.java in parameters)
        assertTrue(DestructiveArtifactIdentityMatchProof::class.java in parameters)
        assertTrue(DestructiveHumanApproval::class.java in parameters)
        assertFalse(RuntimeDurablePreExecutionCommitProof::class.java in parameters)
        assertTrue(DestructiveWipeOptionPolicyProof::class.java in parameters)
        assertTrue(
            FutureDestructiveRealChainBoundary::class.java.declaredConstructors.any { constructor ->
                RuntimeDestructiveSafetyDurability::class.java in constructor.parameterTypes
            },
        )
        assertEquals(
            FutureDestructiveExecutionBundle::class.java,
            FutureDestructiveExecutorContract::class.java.methods
                .single { it.name == "execute" }
                .parameterTypes
                .single(),
        )
    }

    @Test
    fun `implementation and recorded-approval flags stay false`() {
        assertTrue(Checkpoint18Decision.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertTrue(Checkpoint18Decision.DESTRUCTIVE_POLICY_WRAPPER_PRESENT)
        assertTrue(Checkpoint18Decision.DESTRUCTIVE_METADATA_PRESENT)
        assertFalse(Checkpoint18Decision.PRODUCTION_REACHABLE_SIMULATION)
        assertFalse(Checkpoint18Decision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint18Decision.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertTrue(Checkpoint18Decision.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertFalse(Checkpoint18Decision.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertTrue(Checkpoint18Decision.WIPE_DATA_METADATA_REVIEW_APPROVED)
        assertTrue(Checkpoint18Decision.DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED)
        assertFalse(Checkpoint18Decision.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertFalse(Checkpoint17BHardBlock.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
    }

    @Test
    fun `remaining separate-approval blockers stay machine-visible and false`() {
        Checkpoint18Decision.remainingSeparateApprovalBlockers.forEach { name ->
            assertTrue(name, name in Checkpoint18Decision.remainingSeparateApprovalBlockers)
        }
        assertTrue(
            Checkpoint18Decision.gatesRequiringExplicitModification.any {
                it.contains("WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md")
            },
        )
        val docs = File("../docs/WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md").readText()
        assertTrue(docs.contains("18_ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL = YES"))
        assertFalse(
            "REAL_CHAIN_RUNTIME_DURABLE_APPEND_PAIRED" in
                Checkpoint18Decision.remainingSeparateApprovalBlockers,
        )
        assertTrue(docs.contains("NO REAL WIPE IMPLEMENTED"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertTrue(docs.contains("A. architecture readiness"))
        assertTrue(docs.contains("B. Android API implementation approval"))
        assertTrue(docs.contains("C. metadata approval"))
        assertTrue(docs.contains("D. production signing approval"))
        assertTrue(docs.contains("E. disposable hardware test approval"))
    }

    @Test
    fun `production sources still contain no destructive Android API tokens`() {
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("import android.app.admin.DevicePolicyManager"))
        assertFalse(sources.contains("<wipe-data>"))
        assertFalse(sources.contains("class DestructiveDevicePolicy"))
        assertFalse(sources.contains("fun wipe"))
        assertTrue(sources.contains("FutureDestructiveExecutorContract"))
        assertTrue(sources.contains("RuntimeDurablePreExecutionCommitProof"))
        assertTrue(sources.contains("RealChainFinalLiveValidationPermit"))
        assertTrue(sources.contains("DestructiveWipeOptionPolicyProof"))
        listOf(
            File("../app/src/main"),
            File("../device-management-api/src/main"),
        ).filter { it.isDirectory }.forEach { root ->
            val extra = root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .joinToString("\n") { it.readText() }
            assertFalse(root.path, extra.contains("wipeDevice"))
            assertFalse(root.path, extra.contains("wipeData"))
            assertFalse(root.path, extra.contains("<wipe-data>"))
            assertFalse(root.path, extra.contains("assembleAndHandoff"))
        }
        File("../device-management/src/main").walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java" || it.extension == "xml") }
            .forEach { file ->
                val extra = file.readText()
                when {
                    file.name == "AndroidDevicePolicyFactoryResetService.kt" -> {
                        assertTrue(file.path, extra.contains("wipeDevice(0)"))
                        assertFalse(file.path, extra.contains("wipeData"))
                    }
                    file.name == "device_admin_receiver.xml" -> {
                        assertTrue(file.path, extra.contains("wipe-data"))
                    }
                    else -> {
                        assertFalse(file.path, extra.contains("wipeDevice"))
                        assertFalse(file.path, extra.contains("wipeData"))
                        assertFalse(file.path, extra.contains("assembleAndHandoff"))
                    }
                }
            }
    }

    @Test
    fun `contract types remain process-local and non-serializable`() {
        val types = listOf(
            FutureDestructiveExecutionBundle::class.java,
            RealChainFinalLiveValidationPermit::class.java,
            RuntimeDurablePreExecutionCommitProof::class.java,
            DestructiveWipeOptionPolicyProof::class.java,
            FutureDestructiveRealChainBoundary::class.java,
            RuntimeDurablePreExecutionCommitAuthority::class.java,
        )
        types.forEach { type ->
            assertFalse(Serializable::class.java.isAssignableFrom(type))
            assertFalse(type.interfaces.any { it.name == "android.os.Parcelable" })
        }
        assertFalse(Approval::class.java.isAssignableFrom(DestructiveHumanApproval::class.java))
        assertFalse(
            FutureDestructiveExecutorContract::class.java.isAssignableFrom(
                SimulatedDestructiveExecutor::class.java,
            ),
        )
    }
}
