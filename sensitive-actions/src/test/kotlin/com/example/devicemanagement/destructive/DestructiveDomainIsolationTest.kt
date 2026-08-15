package com.example.devicemanagement.destructive

import com.example.devicemanagement.action.ActionExecutor
import com.example.devicemanagement.action.Approval
import com.example.devicemanagement.action.ApprovalAuthority
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class DestructiveDomainIsolationTest {
    @Test
    fun `destructive types cannot be serialized or parceled`() {
        val types = listOf(
            DestructiveArmingToken::class.java,
            DestructiveCapability::class.java,
            DestructiveAttemptLease::class.java,
            ConsumedDestructiveAuthorizationProof::class.java,
            CountedAttemptProof::class.java,
            PreExecutionEvidenceCommitProof::class.java,
            FinalExecutionPermit::class.java,
            DestructiveArmingAuthority::class.java,
            DestructiveAuthorizationAuthority::class.java,
            DestructiveAttemptAdmissionAuthority::class.java,
            DestructiveFinalExecutionGate::class.java,
            PreExecutionEvidenceCommitAuthority::class.java,
            DurableDestructivePreExecutionRepository::class.java,
            FrozenAdminSet::class.java,
            SimulatedDestructiveExecutor::class.java,
            RuntimeDenyOnlyCooldownStore::class.java,
            RuntimeDestructivePreExecutionStore::class.java,
            RuntimeDestructiveSafetyDurability::class.java,
            DestructiveArtifactIdentity::class.java,
            DestructiveArtifactIdentityMatchProof::class.java,
            DestructiveArtifactIdentityExpectation::class.java,
            DestructiveHumanApproval::class.java,
            DestructiveHumanConfirmation::class.java,
            DestructiveOperatorChallenge::class.java,
            DestructiveChallengeIdentity::class.java,
            DestructiveHumanApprovalAuthority::class.java,
            DestructiveHumanConfirmationAuthority::class.java,
            FutureDestructiveExecutionBundle::class.java,
            FutureDestructiveRealChainBoundary::class.java,
            RuntimeDurablePreExecutionCommitProof::class.java,
            RealChainFinalLiveValidationPermit::class.java,
            DestructiveWipeOptionPolicyProof::class.java,
        )
        types.forEach { type ->
            assertFalse(Serializable::class.java.isAssignableFrom(type))
            assertFalse(type.interfaces.any { it.name == "android.os.Parcelable" })
        }
        val capability = DestructiveCapability.create()
        val failed = try {
            ObjectOutputStream(ByteArrayOutputStream()).use { it.writeObject(capability) }
            false
        } catch (_: Exception) {
            true
        }
        assertTrue(failed)
    }

    @Test
    fun `reversible executor cannot consume a destructive capability`() {
        assertFalse(
            ActionExecutor::class.java.methods.any { method ->
                method.parameterTypes.any { it == DestructiveCapability::class.java }
            },
        )
        assertFalse(
            ActionExecutor::class.java.methods.any { method ->
                method.parameterTypes.any { it == FinalExecutionPermit::class.java }
            },
        )
        assertFalse(Approval::class.java == DestructiveCapability::class.java)
        assertFalse(
            ApprovalAuthority::class.java.methods.any { method ->
                method.returnType == DestructiveCapability::class.java
            },
        )
    }

    @Test
    fun `destructive sources do not reference Android policy manager or reversible authority`() {
        val sources = java.io.File("src/main/kotlin/com/example/devicemanagement/destructive")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertFalse(sources.contains("DevicePolicyManager"))
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("com.example.devicemanagement.action.ApprovalAuthority"))
        assertFalse(sources.contains("ActionExecutor"))
        assertFalse(sources.contains("VerifiedPolicyMutation"))
        assertFalse(sources.contains("SafeMockWipeAction"))
        assertFalse(sources.contains("BOOT_COMPLETED"))
        assertFalse(sources.contains("Parcelable"))
        assertFalse(sources.contains("Serializable"))
        assertFalse(sources.contains("java.io.File"))
        assertFalse(sources.contains("FileOutputStream"))
        assertTrue(sources.contains("DESTRUCTIVE ACTION WOULD EXECUTE"))
        assertTrue(sources.contains("checkpoint_17a_simulation_only"))
        assertFalse(sources.contains("fun issue(binding: DestructiveTargetBinding)"))
        assertFalse(sources.contains("FinalValidation.Passed"))
        assertFalse(sources.contains("FinalExecutionPermitConsumer"))
        assertTrue(sources.contains("PreExecutionEvidenceCommitProof"))
        assertTrue(sources.contains("CountedAttemptProof"))
        assertTrue(sources.contains("DurableDestructivePreExecutionRepository"))
        assertTrue(sources.contains("RuntimeDestructiveSafetyDurability"))
        assertTrue(sources.contains("DestructiveArtifactIdentity"))
        assertTrue(sources.contains("TrustedDestructiveArtifactExpectationMint"))
        assertTrue(sources.contains("issueFromTrustedValidationSource"))
        assertTrue(sources.contains("RuntimeDestructiveSafetyDurabilityMint"))
        assertFalse(sources.contains("TrustedDestructiveArtifactExpectationFactory"))
        assertTrue(sources.contains("DestructiveHumanApproval"))
        assertTrue(sources.contains("DestructiveHumanConfirmationAuthority"))
        assertTrue(sources.contains("issueFromTrustedConfirmationSource"))
        assertTrue(sources.contains("DestructiveWipeOptionPolicy"))
        assertTrue(sources.contains("FutureDestructiveExecutorContract"))
        assertTrue(sources.contains("FutureDestructiveRealChainBoundary"))
        assertTrue(sources.contains("assembleAndHandoff"))
        assertTrue(sources.contains("RuntimeDurablePreExecutionCommitProof"))
        assertTrue(sources.contains("RealChainFinalLiveValidationPermit"))
        assertTrue(sources.contains("DestructiveWipeOptionPolicyProof"))
        assertFalse(sources.contains("fromTrustedSnapshot"))
        assertFalse(sources.contains("java.nio.file.Files"))
    }

    @Test
    fun `evidence writer cannot arm authorize or invoke the sink`() {
        val methods = DestructiveSimulationEvidenceWriter::class.java.methods.map { it.name }
        assertTrue(methods.contains("append"))
        assertFalse(methods.contains("arm"))
        assertFalse(methods.contains("authorize"))
        assertFalse(methods.contains("execute"))
        assertFalse(methods.contains("invoke"))
        assertFalse(
            DestructiveSimulationEvidenceWriter::class.java.methods.any { method ->
                method.returnType == DestructiveCapability::class.java ||
                    method.returnType == FinalExecutionPermit::class.java ||
                    method.returnType == PreExecutionEvidenceCommitProof::class.java ||
                    method.returnType == CountedAttemptProof::class.java
            },
        )
    }

    @Test
    fun `production composition factory does not wire the simulation pipeline`() {
        val controller = java.io.File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        assertFalse(controller.contains("DestructiveSimulationPipeline"))
        assertFalse(controller.contains("DestructiveArmingAuthority"))
        assertFalse(controller.contains("DestructiveAttemptAdmissionAuthority"))
        assertFalse(controller.contains("DestructiveFinalExecutionGate"))
        assertFalse(controller.contains("SimulatedDestructiveExecutor"))
        assertFalse(controller.contains("Checkpoint17ASimulationSink"))
        assertFalse(controller.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(controller.contains("assembleAndHandoff"))
        assertFalse(controller.contains("FutureDestructiveExecutorContract"))
    }
}
