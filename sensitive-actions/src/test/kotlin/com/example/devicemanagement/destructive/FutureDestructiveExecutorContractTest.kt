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
        assertTrue(FutureDestructiveExecutionBundle::class.java.isInterface)
        assertTrue(RealChainFinalLiveValidationPermit::class.java.isInterface)
        val issuedBundle = issuedHandoffImplementation(FutureDestructiveExecutionBundle::class.java)
        val issuedPermit = issuedHandoffImplementation(RealChainFinalLiveValidationPermit::class.java)
        assertTrue(
            issuedBundle.declaredConstructors.any { constructor ->
                Modifier.isPrivate(constructor.modifiers) && constructor.parameterCount == 0
            },
        )
        assertTrue(
            issuedBundle.declaredConstructors.all { constructor ->
                Modifier.isPrivate(constructor.modifiers) ||
                    constructor.parameterTypes.singleOrNull()?.name ==
                    "kotlin.jvm.internal.DefaultConstructorMarker"
            },
        )
        assertTrue(
            issuedPermit.declaredConstructors.any { constructor ->
                Modifier.isPrivate(constructor.modifiers) && constructor.parameterCount == 0
            },
        )
        assertTrue(
            issuedPermit.declaredConstructors.all { constructor ->
                Modifier.isPrivate(constructor.modifiers) ||
                    constructor.parameterTypes.singleOrNull()?.name ==
                    "kotlin.jvm.internal.DefaultConstructorMarker"
            },
        )
        val issuedRuntimeProof = issuedRuntimePreExecutionProofImplementation()
        assertFalse(Modifier.isPublic(issuedRuntimeProof.modifiers))
        assertTrue(
            RuntimeDurablePreExecutionCommitProof::class.java.isAssignableFrom(issuedRuntimeProof),
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
    fun `paired append after consume reaches only the guarded executor handoff`() {
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
        assertTrue(result is FutureDestructiveHandoffResult.Acknowledged)
        assertEquals(1, recorder.authorizedInvocations)
        assertEquals(1, fixture.durableStore.count())
        val durable = fixture.durableStore.latest(1).records.single()
        assertEquals(fixture.binding.correlationId.value, durable.correlationId)
        assertEquals(
            DestructiveRuntimeActionNames.FUTURE_REAL_CHAIN_FACTORY_RESET,
            durable.actionName,
        )
        assertEquals(DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED, durable.phase)
        assertEquals(fixture.binding.runningPackage, durable.boundPackage)
        assertEquals(fixture.binding.expectedAdminComponent, durable.boundAdminComponent)
        assertEquals(DestructiveScope.DEVICE_FACTORY_RESET, durable.boundScope)
        assertTrue(
            fixture.authorities.authorization.consume(
                fixture.authorized.capability,
                fixture.binding,
                fixture.authorized.attemptLease,
            ) is DestructiveCapabilityConsumption.Rejected,
        )
    }

    @Test
    fun `append failure mints no permit bundle or executor handoff`() {
        val state = SharedDestructivePreExecutionDurableState().apply {
            failWrites = true
        }
        val store = InMemoryDestructivePreExecutionDurableStore(state)
        val fixture = RealChainBoundaryFixture.create(store)
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
            "runtime_pre_execution_append_failed",
            (result as FutureDestructiveHandoffResult.Failed).reason,
        )
        assertEquals(0, recorder.authorizedInvocations)
        assertEquals(0, store.count())
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
    fun `runtime authority appends exact binding then issues one single-use proof`() {
        val fixture = RealChainBoundaryFixture.create()
        val authority = RuntimeDurablePreExecutionCommitAuthority(
            durability = fixture.durability,
            presentationWallClockMillis = { 1234L },
            eventIdGenerator = { "runtime-event" },
        )
        val forgedConsumedProof = ConsumedDestructiveAuthorizationProof.create()
        assertTrue(
            authority.commitAfterConsumedAuthorization(
                consumedProof = forgedConsumedProof,
                expectedBinding = fixture.binding,
                expectedLease = fixture.authorized.attemptLease,
                expectedArmToken = fixture.authorized.armToken,
                authorizationAuthority = fixture.authorities.authorization,
            ) is RuntimeDurablePreExecutionCommitResult.Failed,
        )
        val consumed = fixture.authorities.authorization.consume(
            fixture.authorized.capability,
            fixture.binding,
            fixture.authorized.attemptLease,
        ) as DestructiveCapabilityConsumption.Accepted
        val committed = authority.commitAfterConsumedAuthorization(
            consumedProof = consumed.proof,
            expectedBinding = consumed.binding,
            expectedLease = consumed.attemptLease,
            expectedArmToken = consumed.armToken,
            authorizationAuthority = fixture.authorities.authorization,
        ) as RuntimeDurablePreExecutionCommitResult.Committed
        assertEquals(1, fixture.durableStore.count())
        val row = fixture.durableStore.latest(1).records.single()
        assertEquals("runtime-event", row.eventId)
        assertEquals(1234L, row.presentationWallClockMillis)
        assertEquals(fixture.binding.correlationId.value, row.correlationId)
        assertTrue(
            authority.consume(
                reflectConstruct(issuedRuntimePreExecutionProofImplementation())
                    as RuntimeDurablePreExecutionCommitProof,
                fixture.binding,
                fixture.authorized.attemptLease,
                consumed.proof,
            ) is RuntimeDurablePreExecutionCheck.Rejected,
        )
        assertTrue(
            authority.consume(
                committed.proof,
                fixture.binding,
                fixture.authorized.attemptLease,
                consumed.proof,
            ) is RuntimeDurablePreExecutionCheck.Accepted,
        )
        assertTrue(
            authority.consume(
                committed.proof,
                fixture.binding,
                fixture.authorized.attemptLease,
                consumed.proof,
            ) is RuntimeDurablePreExecutionCheck.Rejected,
        )
        val replay = authority.commitAfterConsumedAuthorization(
            consumedProof = consumed.proof,
            expectedBinding = consumed.binding,
            expectedLease = consumed.attemptLease,
            expectedArmToken = consumed.armToken,
            authorizationAuthority = fixture.authorities.authorization,
        )
        assertTrue(replay is RuntimeDurablePreExecutionCommitResult.Failed)
        assertEquals(
            "runtime_pre_execution_consumption_already_committed",
            (replay as RuntimeDurablePreExecutionCommitResult.Failed).reason,
        )
        assertEquals(1, fixture.durableStore.count())
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
        val permit = source.indexOf("val permit = mintFinalLiveValidationPermit()")
        val assembleBundle = source.indexOf("val bundle = assembleBundleFromPermit(permit)")
        val execute = source.indexOf("executor.execute(bundle)")
        assertTrue(consumeCapability in 0 until runtimeCommit)
        assertTrue(runtimeCommit in 0 until runtimeProof)
        assertTrue(runtimeProof in 0 until liveFacts)
        assertTrue(liveFacts in 0 until permit)
        assertTrue(permit in 0 until assembleBundle)
        assertTrue(assembleBundle in 0 until execute)
        assertFalse(source.contains("runtimePreExecutionProof"))
        assertFalse(source.contains("fun create()"))
        assertFalse(source.contains("object LiveValidationMint"))
        assertFalse(source.contains("object ExecutionBundleMint"))
        assertTrue(source.contains("private class IssuedRealChainFinalLiveValidationPermit"))
        assertTrue(source.contains("private class IssuedFutureDestructiveExecutionBundle"))
        assertFalse(
            FutureDestructiveRealChainBoundary::class.java.methods.any { method ->
                method.returnType == FutureDestructiveExecutionBundle::class.java ||
                    method.returnType == RealChainFinalLiveValidationPermit::class.java
            },
        )
    }

    @Test
    fun `production has exactly one executor implementor and does not wire it from UI`() {
        val production = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val implementors = production.filter { file ->
            if (file.name == "FutureDestructiveExecutorContract.kt") {
                return@filter false
            }
            val text = file.readText()
            text.contains(") : FutureDestructiveExecutorContract()")
        }
        assertTrue(implementors.single().name == "AndroidFutureDestructiveExecutor.kt")
        val executorSource = implementors.single().readText()
        assertFalse(executorSource.contains("wipeDevice"))
        assertFalse(executorSource.contains("wipeData"))
        assertFalse(executorSource.contains("import android.app.admin"))
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        assertFalse(controller.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(controller.contains("assembleAndHandoff"))
        assertFalse(controller.contains("FutureDestructiveExecutorContract"))
        assertFalse(controller.contains("UnwiredFutureDestructiveExecutor"))
        assertFalse(controller.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(controller.contains("AuthorizedFactoryResetPort"))
        assertFalse(
            FutureDestructiveExecutorContract::class.java.isAssignableFrom(
                UnwiredFutureDestructiveExecutor::class.java,
            ),
        )
    }

    @Test
    fun `same-module code cannot mint a valid permit or bundle`() {
        assertTrue(
            FutureDestructiveRealChainBoundary::class.java.methods.none { method ->
                method.name == "mintFinalLiveValidationPermit" ||
                    method.name == "assembleBundleFromPermit" ||
                    method.name == "create" ||
                    method.name == "registerIssuedPermit" ||
                    method.name == "registerIssuedBundle"
            },
        )
        val mint = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "mintFinalLiveValidationPermit" }
        val assemble = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleBundleFromPermit" }
        assertTrue(Modifier.isPrivate(mint.modifiers))
        assertTrue(Modifier.isPrivate(assemble.modifiers))
        assertTrue(
            FutureDestructiveExecutionBundle::class.java.declaredMethods.none { method ->
                method.name == "create" ||
                    method.name == "assembleBundleFromPermit" ||
                    method.name.startsWith("mint")
            },
        )
        assertTrue(
            RealChainFinalLiveValidationPermit::class.java.declaredMethods.none { method ->
                method.name == "create" || method.name.startsWith("mint")
            },
        )
        assertTrue(
            FutureDestructiveExecutionBundle::class.java.declaredClasses.none { nested ->
                nested.simpleName == "Companion" || nested.simpleName == "ExecutionBundleMint"
            },
        )
        assertTrue(
            RealChainFinalLiveValidationPermit::class.java.declaredClasses.none { nested ->
                nested.simpleName == "Companion" || nested.simpleName == "LiveValidationMint"
            },
        )
        assertTrue(
            runCatching {
                Class.forName(
                    "com.example.devicemanagement.destructive." +
                        "FutureDestructiveRealChainBoundary\$RealChainFinalLiveValidationPermit" +
                        "\$LiveValidationMint",
                )
            }.isFailure,
        )
        assertTrue(
            runCatching {
                Class.forName(
                    "com.example.devicemanagement.destructive." +
                        "FutureDestructiveRealChainBoundary\$FutureDestructiveExecutionBundle" +
                        "\$ExecutionBundleMint",
                )
            }.isFailure,
        )
        assertEquals(
            "IssuedFutureDestructiveExecutionBundle",
            issuedHandoffImplementation(FutureDestructiveExecutionBundle::class.java).simpleName,
        )
        assertEquals(
            "IssuedRealChainFinalLiveValidationPermit",
            issuedHandoffImplementation(RealChainFinalLiveValidationPermit::class.java).simpleName,
        )
    }

    @Test
    fun `forged bundle and permit cannot mint or invoke the executor`() {
        val recorder = RecordingFutureExecutor()
        val forgedBundle = reflectConstruct(
            issuedHandoffImplementation(FutureDestructiveExecutionBundle::class.java),
        ) as FutureDestructiveExecutionBundle
        val acknowledgement = recorder.execute(forgedBundle)
        assertTrue(acknowledgement is FutureDestructiveHandoffAcknowledgement.Refused)
        assertEquals(
            "forged_or_consumed_bundle",
            (acknowledgement as FutureDestructiveHandoffAcknowledgement.Refused).reason,
        )
        assertEquals(0, recorder.authorizedInvocations)
        val fixture = RealChainBoundaryFixture.create()
        val forgedPermit = reflectConstruct(
            issuedHandoffImplementation(RealChainFinalLiveValidationPermit::class.java),
        ) as RealChainFinalLiveValidationPermit
        val assemble = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleBundleFromPermit" }
        assemble.isAccessible = true
        assertNull(assemble.invoke(fixture.boundary, forgedPermit))
        assertEquals(0, recorder.authorizedInvocations)
        val second = recorder.execute(forgedBundle)
        assertTrue(second is FutureDestructiveHandoffAcknowledgement.Refused)
        assertEquals(0, recorder.authorizedInvocations)
    }

    @Test
    fun `pre-created or pre-banked runtime proof cannot satisfy the real-chain boundary`() {
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        assertFalse(RuntimeDurablePreExecutionCommitProof::class.java in handoff.parameterTypes)
        val fixture = RealChainBoundaryFixture.create()
        val durability = reflectRuntimeDurabilityForRejectPathTests()
        val authority = RuntimeDurablePreExecutionCommitAuthority(durability)
        val forgedProof = reflectConstruct(issuedRuntimePreExecutionProofImplementation())
            as RuntimeDurablePreExecutionCommitProof
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
        assertTrue(afterConsume is RuntimeDurablePreExecutionCommitResult.Committed)
        assertTrue(
            authority.consume(
                (afterConsume as RuntimeDurablePreExecutionCommitResult.Committed).proof,
                consumed.binding,
                consumed.attemptLease,
                consumed.proof,
            ) is RuntimeDurablePreExecutionCheck.Accepted,
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
    val durability: RuntimeDestructiveSafetyDurability,
    val durableStore: InMemoryDestructivePreExecutionDurableStore,
) {
    companion object {
        fun create(
            durableStore: InMemoryDestructivePreExecutionDurableStore =
                InMemoryDestructivePreExecutionDurableStore(),
        ): RealChainBoundaryFixture {
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
            val durability = reflectRuntimeDurabilityForRejectPathTests(durableStore)
            val boundary = FutureDestructiveRealChainBoundary(
                durability = durability,
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
                durability = durability,
                durableStore = durableStore,
            )
        }

        private const val CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val ARTIFACT = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}

internal fun reflectRuntimeDurabilityForRejectPathTests(
    durableStore: DestructivePreExecutionDurableStore =
        InMemoryDestructivePreExecutionDurableStore(),
): RuntimeDestructiveSafetyDurability {
    val cooldownAdapter = TrustedRuntimeDenyOnlyCooldownMarkerStore(ReconstructableDenyOnlyMarkerMedium())
    val cooldown = newInternalInstance(RuntimeDenyOnlyCooldownStore::class.java, cooldownAdapter)
    val preExecution = newInternalInstance(
        RuntimeDestructivePreExecutionStore::class.java,
        durableStore,
    )
    return newInternalInstance(RuntimeDestructiveSafetyDurability::class.java, cooldown, preExecution)
}

internal fun issuedRuntimePreExecutionProofImplementation(): Class<*> {
    return Class.forName(
        "com.example.devicemanagement.destructive.IssuedRuntimeDurablePreExecutionCommitProof",
    )
}

internal fun issuedHandoffImplementation(type: Class<*>): Class<*> {
    return when (type) {
        FutureDestructiveExecutionBundle::class.java ->
            Class.forName(
                "com.example.devicemanagement.destructive.IssuedFutureDestructiveExecutionBundle",
            )
        RealChainFinalLiveValidationPermit::class.java ->
            Class.forName(
                "com.example.devicemanagement.destructive.IssuedRealChainFinalLiveValidationPermit",
            )
        else -> error(type.name)
    }
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
