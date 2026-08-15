package com.example.devicemanagement.destructive

import com.example.devicemanagement.action.Approval
import com.example.devicemanagement.action.ApprovalAuthority
import com.example.devicemanagement.persistence.ReconstructableDenyOnlyMarkerMedium
import com.example.devicemanagement.persistence.TrustedRuntimeDenyOnlyCooldownMarkerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class FutureDestructiveExecutorContractTest {
    @Test
    fun `executor entrypoint accepts only the opaque bundle`() {
        val execute = FutureDestructiveExecutorContract::class.java.methods
            .filter { it.name == "execute" }
        assertEquals(1, execute.size)
        assertEquals(1, execute.single().parameterCount)
        assertEquals(FutureDestructiveExecutionBundle::class.java, execute.single().parameterTypes.single())
        assertEquals(
            FutureDestructiveHandoffAcknowledgement::class.java,
            execute.single().returnType,
        )
        assertTrue(
            FutureDestructiveExecutionBundle::class.java.declaredConstructors.all { constructor ->
                Modifier.isPrivate(constructor.modifiers)
            },
        )
        assertTrue(
            RealChainFinalLiveValidationPermit::class.java.declaredConstructors.all { constructor ->
                Modifier.isPrivate(constructor.modifiers)
            },
        )
        assertTrue(
            RuntimeDurablePreExecutionCommitProof::class.java.declaredConstructors.all { constructor ->
                Modifier.isPrivate(constructor.modifiers)
            },
        )
        assertTrue(
            FutureDestructiveExecutionBundle::class.java.declaredMethods.none { it.name == "create" },
        )
        assertTrue(
            RealChainFinalLiveValidationPermit::class.java.declaredMethods.none { it.name == "create" },
        )
        assertTrue(
            RuntimeDurablePreExecutionCommitProof::class.java.declaredMethods.none { it.name == "create" },
        )
        assertTrue(
            FutureDestructiveExecutionBundle::class.java.declaredClasses.none { nested ->
                nested.simpleName == "Companion" &&
                    nested.declaredMethods.any { it.name == "create" }
            },
        )
        assertTrue(
            RealChainFinalLiveValidationPermit::class.java.declaredClasses.none { nested ->
                nested.simpleName == "Companion" &&
                    nested.declaredMethods.any { it.name == "create" }
            },
        )
    }

    @Test
    fun `assembleAndHandoff rejects Boolean strings Approval and simulation proofs`() {
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        val parameters = handoff.parameterTypes.toList()
        assertFalse(java.lang.Boolean.TYPE in parameters)
        assertFalse(Boolean::class.java in parameters)
        assertFalse(String::class.java in parameters)
        assertFalse(Approval::class.java in parameters)
        assertFalse(PreExecutionEvidenceCommitProof::class.java in parameters)
        assertFalse(FinalExecutionPermit::class.java in parameters)
        assertFalse(DenyOnlyCooldownMarkerStore::class.java in parameters)
        assertFalse(DestructivePreExecutionDurableStore::class.java in parameters)
        assertFalse(DurableDestructivePreExecutionRepository::class.java in parameters)
        assertFalse(DestructiveOperatorChallenge::class.java in parameters)
        assertFalse(DestructiveHumanConfirmation::class.java in parameters)
        assertFalse(RuntimeDurablePreExecutionCommitProof::class.java in parameters)
        assertFalse(
            ApprovalAuthority::class.java.methods.any { method ->
                method.returnType == FutureDestructiveExecutionBundle::class.java
            },
        )
        assertFalse(
            SimulatedDestructiveExecutor::class.java.declaredMethods.any { method ->
                method.parameterTypes.any {
                    it == FutureDestructiveExecutionBundle::class.java ||
                        it == RuntimeDestructiveSafetyDurability::class.java ||
                        it == DestructiveHumanApproval::class.java
                }
            },
        )
    }

    @Test
    fun `boundary constructor requires runtime durability not generic persistence`() {
        val constructors = FutureDestructiveRealChainBoundary::class.java.declaredConstructors
        assertTrue(constructors.isNotEmpty())
        constructors.forEach { constructor ->
            assertTrue(RuntimeDestructiveSafetyDurability::class.java in constructor.parameterTypes)
            assertFalse(DenyOnlyCooldownMarkerStore::class.java in constructor.parameterTypes)
            assertFalse(DestructivePreExecutionDurableStore::class.java in constructor.parameterTypes)
            assertFalse(InMemoryDenyOnlyCooldownMarkerStore::class.java in constructor.parameterTypes)
            assertFalse(
                InMemoryDestructivePreExecutionDurableStore::class.java in constructor.parameterTypes,
            )
        }
        assertTrue(
            RuntimeDurablePreExecutionCommitAuthority::class.java.declaredConstructors.all { constructor ->
                RuntimeDestructiveSafetyDurability::class.java in constructor.parameterTypes &&
                    DestructivePreExecutionDurableStore::class.java !in constructor.parameterTypes
            },
        )
    }

    @Test
    fun `unwired append after consume cannot assemble or reach the executor`() {
        val fixture = RealChainBoundaryFixture.create()
        val recorder = RecordingFutureExecutor()
        val result = fixture.boundary.assembleAndHandoff(
            executor = recorder,
            binding = fixture.binding,
            attemptLease = fixture.authorized.attemptLease,
            capability = fixture.authorized.capability,
            armToken = fixture.authorized.armToken,
            artifactMatchProof = fixture.artifactProof,
            observedIdentity = fixture.identity,
            humanApproval = fixture.approval,
            wipeOptionPolicyProof = fixture.wipeProof,
        )
        assertTrue(result is FutureDestructiveHandoffResult.Failed)
        assertEquals(
            "runtime_pre_execution_issuer_unwired",
            (result as FutureDestructiveHandoffResult.Failed).reason,
        )
        assertEquals(0, recorder.authorizedInvocations)
        assertTrue(
            fixture.authorities.authorization.consume(
                fixture.authorized.capability,
                fixture.binding,
                fixture.authorized.attemptLease,
            ) is DestructiveCapabilityConsumption.Rejected,
        )
    }

    @Test
    fun `challenge confirmation Boolean and caller created approval cannot satisfy the boundary`() {
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        assertFalse(
            handoff.parameterTypes.any {
                it == DestructiveOperatorChallenge::class.java ||
                    it == DestructiveHumanConfirmation::class.java ||
                    it == java.lang.Boolean.TYPE
            },
        )
        val fixture = RealChainBoundaryFixture.create()
        val recorder = RecordingFutureExecutor()
        val result = fixture.boundary.assembleAndHandoff(
            executor = recorder,
            binding = fixture.binding,
            attemptLease = fixture.authorized.attemptLease,
            capability = fixture.authorized.capability,
            armToken = fixture.authorized.armToken,
            artifactMatchProof = fixture.artifactProof,
            observedIdentity = fixture.identity,
            humanApproval = DestructiveHumanApproval.create(),
            wipeOptionPolicyProof = fixture.wipeProof,
        )
        assertTrue(result is FutureDestructiveHandoffResult.Failed)
        assertEquals(0, recorder.authorizedInvocations)
        assertTrue(
            (result as FutureDestructiveHandoffResult.Failed).reason.contains("human_approval"),
        )
    }

    @Test
    fun `process restart destroys assembled positive materials`() {
        val first = RealChainBoundaryFixture.create()
        val reconstructed = RealChainBoundaryFixture.create()
        val recorder = RecordingFutureExecutor()
        val result = reconstructed.boundary.assembleAndHandoff(
            executor = recorder,
            binding = first.binding,
            attemptLease = first.authorized.attemptLease,
            capability = first.authorized.capability,
            armToken = first.authorized.armToken,
            artifactMatchProof = first.artifactProof,
            observedIdentity = first.identity,
            humanApproval = first.approval,
            wipeOptionPolicyProof = first.wipeProof,
        )
        assertTrue(result is FutureDestructiveHandoffResult.Failed)
        assertEquals(0, recorder.authorizedInvocations)
        assertTrue(
            reconstructed.humanApprovalAuthority.consume(
                first.approval,
                first.binding.correlationId,
                first.binding,
                first.binding.scope,
                first.identity,
                first.authorized.attemptLease,
            ) is DestructiveHumanApprovalCheck.Rejected,
        )
    }

    @Test
    fun `unwired runtime issuer never records durable evidence`() {
        val durability = reflectRuntimeDurabilityForRejectPathTests()
        val binding = verifiedBinding()
        val lease = DestructiveAttemptLease.create()
        val consumedProof = ConsumedDestructiveAuthorizationProof.create()
        assertNull(
            UnwiredRuntimeDurablePreExecutionCommitSource.commitAfterConsumedAuthorization(
                durability,
                consumedProof,
                binding,
                lease,
            ),
        )
        val authority = RuntimeDurablePreExecutionCommitAuthority(durability)
        assertTrue(
            authority.commitAfterConsumedAuthorization(
                consumedProof = consumedProof,
                expectedBinding = binding,
                expectedLease = lease,
                expectedArmToken = DestructiveArmingToken.create(),
                authorizationAuthority = DestructiveAuthorityBundle().authorization,
            ) is RuntimeDurablePreExecutionCommitResult.Failed,
        )
        assertTrue(
            authority.consume(
                reflectConstruct(RuntimeDurablePreExecutionCommitProof::class.java),
                binding,
                lease,
                consumedProof,
            ) is RuntimeDurablePreExecutionCheck.Rejected,
        )
        assertFalse(
            File("src/main/kotlin/com/example/devicemanagement/destructive/RuntimeDurablePreExecutionCommitProof.kt")
                .readText()
                .contains("insert("),
        )
    }

    @Test
    fun `handoff method is synchronous and has no async or persist gap`() {
        val source = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/FutureDestructiveExecutorContract.kt",
        ).readText()
        assertFalse(source.contains("suspend "))
        assertFalse(source.contains("Handler"))
        assertFalse(source.contains("WorkManager"))
        assertFalse(source.contains(".post("))
        assertFalse(source.contains("Coroutine"))
        assertFalse(source.contains("launch("))
        assertFalse(source.contains("async("))
        assertFalse(source.contains("ExecutorService"))
        assertFalse(source.contains("SharedPreferences"))
        assertFalse(source.contains("retry"))
        assertFalse(source.contains("BOOT_COMPLETED"))
        assertFalse(source.contains("wipeDevice"))
        assertFalse(source.contains("wipeData"))
        assertFalse(source.contains("import android.app.admin"))
        val consumeCapability = source.indexOf("val consumption = authorizationAuthority.consume")
        val runtimeCommit = source.indexOf("runtimePreExecutionAuthority.commitAfterConsumedAuthorization")
        val runtimeProof = source.indexOf("val runtime = runtimePreExecutionAuthority.consume")
        val liveFacts = source.indexOf("liveFactsSource.currentFacts")
        val permit = source.indexOf("val permit = mintFinalLiveValidationPermit")
        val assembleBundle = source.indexOf("val bundle = assembleBundleFromPermit")
        val execute = source.indexOf("executor.execute(bundle)")
        assertTrue(consumeCapability in 0 until runtimeCommit)
        assertTrue(runtimeCommit in 0 until runtimeProof)
        assertTrue(runtimeProof in 0 until liveFacts)
        assertTrue(liveFacts in 0 until permit)
        assertTrue(permit in 0 until assembleBundle)
        assertTrue(assembleBundle in 0 until execute)
        assertFalse(source.contains("runtimePreExecutionProof"))
        assertFalse(source.contains("fun create()"))
        assertFalse(
            FutureDestructiveRealChainBoundary::class.java.methods.any { method ->
                method.returnType == FutureDestructiveExecutionBundle::class.java ||
                    method.returnType == RealChainFinalLiveValidationPermit::class.java
            },
        )
    }

    @Test
    fun `production has no executor implementation and does not wire the boundary`() {
        val production = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val implementors = production.filter { file ->
            if (file.name == "FutureDestructiveExecutorContract.kt") {
                return@filter false
            }
            val text = file.readText()
            text.contains(": FutureDestructiveExecutorContract") ||
                text.contains("FutureDestructiveExecutorContract {")
        }
        assertTrue(implementors.isEmpty())
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        assertFalse(controller.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(controller.contains("assembleAndHandoff"))
        assertFalse(controller.contains("FutureDestructiveExecutorContract"))
        assertFalse(controller.contains("UnwiredFutureDestructiveExecutor"))
        assertFalse(
            FutureDestructiveExecutorContract::class.java.isAssignableFrom(
                UnwiredFutureDestructiveExecutor::class.java,
            ),
        )
    }

    @Test
    fun `forged bundle and permit cannot mint or invoke the executor`() {
        val recorder = RecordingFutureExecutor()
        val forgedBundle = reflectConstruct(FutureDestructiveExecutionBundle::class.java)
        val acknowledgement = recorder.execute(forgedBundle)
        assertTrue(acknowledgement is FutureDestructiveHandoffAcknowledgement.Refused)
        assertEquals(
            "forged_or_consumed_bundle",
            (acknowledgement as FutureDestructiveHandoffAcknowledgement.Refused).reason,
        )
        assertEquals(0, recorder.authorizedInvocations)
        val forgedPermit = reflectConstruct(RealChainFinalLiveValidationPermit::class.java)
        val assemble = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleBundleFromPermit" }
        assemble.isAccessible = true
        val fixture = RealChainBoundaryFixture.create()
        assertNull(assemble.invoke(fixture.boundary, forgedPermit))
        assertEquals(0, recorder.authorizedInvocations)
        val mint = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "mintFinalLiveValidationPermit" }
        assertTrue(Modifier.isPrivate(mint.modifiers))
        assertTrue(Modifier.isPrivate(assemble.modifiers))
    }

    @Test
    fun `pre-created or pre-banked runtime proof cannot satisfy the real-chain boundary`() {
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        assertFalse(RuntimeDurablePreExecutionCommitProof::class.java in handoff.parameterTypes)
        val fixture = RealChainBoundaryFixture.create()
        val durability = reflectRuntimeDurabilityForRejectPathTests()
        val authority = RuntimeDurablePreExecutionCommitAuthority(durability)
        val forgedProof = reflectConstruct(RuntimeDurablePreExecutionCommitProof::class.java)
        assertTrue(
            authority.consume(
                forgedProof,
                fixture.binding,
                fixture.authorized.attemptLease,
                ConsumedDestructiveAuthorizationProof.create(),
            ) is RuntimeDurablePreExecutionCheck.Rejected,
        )
        val beforeConsume = authority.commitAfterConsumedAuthorization(
            consumedProof = ConsumedDestructiveAuthorizationProof.create(),
            expectedBinding = fixture.binding,
            expectedLease = fixture.authorized.attemptLease,
            expectedArmToken = fixture.authorized.armToken,
            authorizationAuthority = fixture.authorities.authorization,
        )
        assertTrue(beforeConsume is RuntimeDurablePreExecutionCommitResult.Failed)
        val consumed = fixture.authorities.authorization.consume(
            fixture.authorized.capability,
            fixture.binding,
            fixture.authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val afterConsume = authority.commitAfterConsumedAuthorization(
            consumedProof = consumed.proof,
            expectedBinding = consumed.binding,
            expectedLease = consumed.attemptLease,
            expectedArmToken = consumed.armToken,
            authorizationAuthority = fixture.authorities.authorization,
        )
        assertTrue(afterConsume is RuntimeDurablePreExecutionCommitResult.Failed)
        assertEquals(
            "runtime_pre_execution_issuer_unwired",
            (afterConsume as RuntimeDurablePreExecutionCommitResult.Failed).reason,
        )
        val recorder = RecordingFutureExecutor()
        val result = fixture.boundary.assembleAndHandoff(
            executor = recorder,
            binding = fixture.binding,
            attemptLease = fixture.authorized.attemptLease,
            capability = fixture.authorized.capability,
            armToken = fixture.authorized.armToken,
            artifactMatchProof = fixture.artifactProof,
            observedIdentity = fixture.identity,
            humanApproval = fixture.approval,
            wipeOptionPolicyProof = fixture.wipeProof,
        )
        assertTrue(result is FutureDestructiveHandoffResult.Failed)
        assertEquals(0, recorder.authorizedInvocations)
    }

    private class RecordingFutureExecutor : FutureDestructiveExecutorContract() {
        var authorizedInvocations = 0

        override fun onAuthorizedHandoff(): FutureDestructiveHandoffAcknowledgement {
            authorizedInvocations += 1
            return FutureDestructiveHandoffAcknowledgement.Refused("test_stub_refused")
        }
    }
}

internal class RealChainBoundaryFixture(
    val boundary: FutureDestructiveRealChainBoundary,
    val authorities: DestructiveAuthorityBundle,
    val binding: DestructiveTargetBinding,
    val authorized: DestructiveAuthorizationResult.Authorized,
    val identity: DestructiveArtifactIdentity,
    val artifactProof: DestructiveArtifactIdentityMatchProof,
    val approval: DestructiveHumanApproval,
    val wipeProof: DestructiveWipeOptionPolicyProof,
    val humanApprovalAuthority: DestructiveHumanApprovalAuthority,
) {
    companion object {
        fun create(): RealChainBoundaryFixture {
            val authorities = DestructiveAuthorityBundle()
            val binding = verifiedBinding()
            val authorized = authorities.authorize(binding)
            val identity = requireNotNull(
                DestructiveArtifactIdentity.snapshot(
                    certificateSha256 = CERT,
                    artifactSha256 = ARTIFACT,
                    packageName = "com.example.devicemanagement",
                    adminComponent =
                        "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
                    buildPurpose = DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
                ),
            )
            val expected = requireNotNull(
                DestructiveArtifactIdentityExpectation.TrustedDestructiveArtifactExpectationMint
                    .issueFromTrustedValidationSource(
                    certificateSha256 = identity.certificateSha256,
                    artifactSha256 = identity.artifactSha256,
                    packageName = identity.packageName,
                    adminComponent = identity.adminComponent,
                    buildPurpose = identity.buildPurpose,
                ),
            )
            val artifactAuthority = DestructiveArtifactIdentityAuthority(expected, authorities.clock)
            val artifactProof = (
                artifactAuthority.admit(identity) as ArtifactIdentityAdmitResult.Admitted
                ).proof
            val humanApprovalAuthority = DestructiveHumanApprovalAuthority(authorities.clock)
            val confirmationAuthority = DestructiveHumanConfirmationAuthority()
            val challenge = humanApprovalAuthority.issueChallenge(
                correlationId = binding.correlationId,
                binding = binding,
                scope = binding.scope,
                artifactIdentity = identity,
                attemptLease = authorized.attemptLease,
            ) as DestructiveChallengeIssueResult.Issued
            val confirmation = (
                confirmationAuthority.confirm(
                    challenge = challenge.challenge,
                    correlationId = binding.correlationId,
                    binding = binding,
                    scope = binding.scope,
                    artifactIdentity = identity,
                    attemptLease = authorized.attemptLease,
                    nowMonotonicMillis = authorities.clock.now,
                ) as DestructiveHumanConfirmationResult.Confirmed
                ).confirmation
            val approval = (
                humanApprovalAuthority.redeem(
                    challenge = challenge.challenge,
                    confirmation = confirmation,
                    correlationId = binding.correlationId,
                    binding = binding,
                    scope = binding.scope,
                    artifactIdentity = identity,
                    attemptLease = authorized.attemptLease,
                ) as DestructiveHumanApprovalResult.Approved
                ).approval
            val wipeAuthority = DestructiveWipeOptionPolicyAuthority()
            val wipeProof = (
                wipeAuthority.verifyDefaultDeny(binding.scope, emptySet())
                    as WipeOptionPolicyVerifyResult.Verified
                ).proof
            val liveFacts = MutableDestructiveLiveFactsSource(verifiedFacts())
            val boundary = FutureDestructiveRealChainBoundary(
                durability = reflectRuntimeDurabilityForRejectPathTests(),
                authorizationAuthority = authorities.authorization,
                admissionAuthority = authorities.admission,
                armingAuthority = authorities.arming,
                artifactAuthority = artifactAuthority,
                humanApprovalAuthority = humanApprovalAuthority,
                wipePolicyAuthority = wipeAuthority,
                liveFactsSource = liveFacts,
                cooldown = authorities.cooldown,
                monotonicTimeSource = authorities.clock,
            )
            return RealChainBoundaryFixture(
                boundary = boundary,
                authorities = authorities,
                binding = binding,
                authorized = authorized,
                identity = identity,
                artifactProof = artifactProof,
                approval = approval,
                wipeProof = wipeProof,
                humanApprovalAuthority = humanApprovalAuthority,
            )
        }

        private const val CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val ARTIFACT = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}

internal fun reflectRuntimeDurabilityForRejectPathTests(): RuntimeDestructiveSafetyDurability {
    val cooldownAdapter = TrustedRuntimeDenyOnlyCooldownMarkerStore(ReconstructableDenyOnlyMarkerMedium())
    val cooldown = newInternalInstance(RuntimeDenyOnlyCooldownStore::class.java, cooldownAdapter)
    val preExecution = newInternalInstance(
        RuntimeDestructivePreExecutionStore::class.java,
        InMemoryDestructivePreExecutionDurableStore(),
    )
    return newInternalInstance(RuntimeDestructiveSafetyDurability::class.java, cooldown, preExecution)
}

internal fun <T> reflectConstruct(type: Class<T>): T {
    val constructor = type.declaredConstructors.first { candidate ->
        candidate.parameterTypes.all { it.name == "kotlin.jvm.internal.DefaultConstructorMarker" } ||
            candidate.parameterCount == 0
    }
    constructor.isAccessible = true
    val args = Array<Any?>(constructor.parameterCount) { null }
    @Suppress("UNCHECKED_CAST")
    return constructor.newInstance(*args) as T
}

private fun <T> newInternalInstance(type: Class<T>, vararg args: Any): T {
    val constructor = type.declaredConstructors.first { candidate ->
        val realParameters = candidate.parameterTypes.filter {
            it.name != "kotlin.jvm.internal.DefaultConstructorMarker"
        }
        realParameters.size == args.size &&
            realParameters.indices.all { index ->
                realParameters[index].isAssignableFrom(args[index].javaClass)
            }
    }
    constructor.isAccessible = true
    val invocation = if (constructor.parameterCount == args.size + 1) {
        args.toList() + null
    } else {
        args.toList()
    }
    @Suppress("UNCHECKED_CAST")
    return constructor.newInstance(*invocation.toTypedArray()) as T
}
