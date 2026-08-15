package com.example.devicemanagement.destructive

import com.example.devicemanagement.action.ActionExecutor
import com.example.devicemanagement.action.Approval
import com.example.devicemanagement.action.ApprovalAuthority
import com.example.devicemanagement.persistence.DenyOnlyMarkerDurableMedium
import com.example.devicemanagement.persistence.TrustedRuntimeDenyOnlyCooldownMarkerStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.Serializable

class Checkpoint17BAuthorityGraphTest {
    @Test
    fun `no raw permit minting or fake permit consumer type exists`() {
        assertFalse(
            DestructiveFinalExecutionGate::class.java.methods.any { it.name == "issue" },
        )
        assertFalse(
            Checkpoint17ASimulationSink::class.java.constructors.any { constructor ->
                constructor.parameterTypes.any { it.name.contains("PermitConsumer") }
            },
        )
        val sources = File("src/main/kotlin/com/example/devicemanagement/destructive")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(sources.contains("FinalExecutionPermitConsumer"))
        assertFalse(sources.contains("fun issue(binding: DestructiveTargetBinding)"))
        assertFalse(sources.contains("fun allowed(): Boolean"))
        assertFalse(sources.contains("FinalValidation.Passed"))
    }

    @Test
    fun `fake evidence proof and marker-only admission cannot reach the sink`() {
        val composition = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        val consumed = composition.authorizationAuthority.consume(
            authorized.capability,
            binding,
            authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val fakeProof = PreExecutionEvidenceCommitProof.create()
        val gated = composition.gate.validateAndIssue(
            binding = consumed.binding,
            armToken = consumed.armToken,
            attemptLease = consumed.attemptLease,
            consumptionProof = consumed.proof,
            preExecutionProof = fakeProof,
        )
        assertTrue(gated is FinalExecutionGateResult.Failed)
        assertTrue(composition.sink.invoke(FinalExecutionPermit.create(), binding) is SimulationSinkResult.Denied)
        assertEqualsZeroInvocations(composition)
    }

    @Test
    fun `capability arm and lease objects are not reusable across reconstructed authorities`() {
        val first = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = first.admitBindAuthorize(binding)
        val reconstructed = DestructiveSimulationComposition.create()
        assertTrue(
            reconstructed.authorizationAuthority.consume(
                authorized.capability,
                binding,
                authorized.attemptLease,
            ) is DestructiveCapabilityConsumption.Rejected,
        )
        assertTrue(
            reconstructed.armingAuthority.requireLive(
                authorized.armToken,
                binding,
                authorized.attemptLease,
            ) is ArmingCheck.Dead,
        )
        assertTrue(
            reconstructed.admissionAuthority.requireLive(authorized.attemptLease, binding)
                is AttemptLeaseCheck.Dead,
        )
        assertEqualsZeroInvocations(reconstructed)
    }

    @Test
    fun `recovery and production controller cannot host destructive authorities`() {
        val recovery = File("src/main/kotlin/com/example/devicemanagement/recovery")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        listOf(
            "TrustedRuntimeDenyOnlyCooldownMarkerStore",
            "DurableDestructivePreExecutionRepository",
            "DestructivePreExecutionDurableStore",
            "RuntimeDenyOnlyCooldownStore",
            "RuntimeDestructivePreExecutionStore",
            "RuntimeDestructiveSafetyDurability",
            "DestructiveArtifactIdentityAuthority",
            "TrustedDestructiveArtifactExpectationMint",
            "issueFromTrustedValidationSource",
            "RuntimeDestructiveSafetyDurabilityMint",
            "DestructiveHumanApprovalAuthority",
            "DestructiveHumanConfirmationAuthority",
            "issueFromTrustedConfirmationSource",
            "DestructiveWipeOptionPolicy",
            "SimulatedDestructiveExecutor",
            "FinalExecutionPermit",
            "PreExecutionEvidenceCommitProof",
            "FutureDestructiveExecutorContract",
            "FutureDestructiveRealChainBoundary",
            "assembleAndHandoff",
            "RuntimeDurablePreExecutionCommitProof",
            "RealChainFinalLiveValidationPermit",
            "DestructiveWipeOptionPolicyProof",
            "Checkpoint19BDecision",
            "Checkpoint19CDecision",
            "Checkpoint19DDecision",
            "ProductionDestructiveRealChainOrchestrator",
            "assembleAlreadyBoundDeviceFactoryReset",
            "ProductionDestructiveHumanConfirmationSource",
            "ProductionDestructiveUtcClock",
            "AuthorizedFactoryResetPort",
            "ProductionDestructiveRealChain",
            "AndroidFutureDestructiveExecutor",
        ).forEach { token ->
            assertFalse(recovery.contains(token))
            assertFalse(controller.contains(token))
        }
    }

    @Test
    fun `reversible approval domain cannot consume destructive authority`() {
        assertFalse(
            ActionExecutor::class.java.methods.any { method ->
                method.parameterTypes.any {
                    it == DestructiveCapability::class.java ||
                        it == FinalExecutionPermit::class.java ||
                        it == PreExecutionEvidenceCommitProof::class.java
                }
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
    fun `positive authority types remain process-local and non-serializable`() {
        val types = listOf(
            DestructiveArmingToken::class.java,
            DestructiveCapability::class.java,
            DestructiveAttemptLease::class.java,
            ConsumedDestructiveAuthorizationProof::class.java,
            CountedAttemptProof::class.java,
            PreExecutionEvidenceCommitProof::class.java,
            FinalExecutionPermit::class.java,
            TrustedRuntimeDenyOnlyCooldownMarkerStore::class.java,
            RuntimeDenyOnlyCooldownStore::class.java,
            RuntimeDestructivePreExecutionStore::class.java,
            RuntimeDestructiveSafetyDurability::class.java,
            DestructiveArtifactIdentityMatchProof::class.java,
            DestructiveHumanApproval::class.java,
            DestructiveHumanConfirmation::class.java,
            DestructiveOperatorChallenge::class.java,
            DestructiveChallengeIdentity::class.java,
            FutureDestructiveExecutionBundle::class.java,
            RuntimeDurablePreExecutionCommitProof::class.java,
            RealChainFinalLiveValidationPermit::class.java,
            DestructiveWipeOptionPolicyProof::class.java,
        )
        types.forEach { type ->
            assertFalse(Serializable::class.java.isAssignableFrom(type))
            assertFalse(type.interfaces.any { it.name == "android.os.Parcelable" })
        }
        assertFalse(Serializable::class.java.isAssignableFrom(DenyOnlyMarkerDurableMedium::class.java))
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
        assertFalse(sources.contains("BOOT_COMPLETED"))
    }

    private fun assertEqualsZeroInvocations(composition: DestructiveSimulationComposition) {
        assertTrue(composition.sink.invocationCount() == 0)
    }
}
