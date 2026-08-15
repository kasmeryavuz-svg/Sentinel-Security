package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.Serializable
import java.lang.reflect.Modifier

class Checkpoint19DDecisionRealityTest {
    @Test
    fun `19D records assembly approval separately from runtime availability`() {
        assertEquals("YES", Checkpoint19DDecision.REAL_CHAIN_ASSEMBLY_IMPLEMENTATION_APPROVED)
        assertTrue(Checkpoint19DDecision.REAL_CHAIN_ASSEMBLY_PATH_PRESENT)
        assertFalse(Checkpoint19DDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertFalse(Checkpoint19DDecision.TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED)
        assertFalse(Checkpoint19DDecision.PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE)
        assertFalse(Checkpoint19DDecision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint19DDecision.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertFalse(Checkpoint19DDecision.DESTRUCTIVE_HARDWARE_TEST_PERFORMED)
        assertFalse(Checkpoint19DDecision.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertEquals("YES", Checkpoint19DDecision.REAL_CHAIN_STRUCTURAL_ASSEMBLY_COMPLETE)
        assertEquals("NO", Checkpoint19DDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
        assertNull(Checkpoint19DDecision.RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256)
        assertNull(Checkpoint19DDecision.RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256)
        assertNull(Checkpoint19DDecision.RECORDED_DISPOSABLE_DEVICE_SERIAL)
        assertNull(Checkpoint19DDecision.RECORDED_PER_ATTEMPT_CONFIRMATION)
        assertEquals(
            "167ef973eb06751ba56137c4b3fd57716da9e4b2",
            Checkpoint19DDecision.RECORDED_APPROVAL_GIT_REVISION,
        )
        assertTrue(
            Checkpoint19DDecision.RECORDED_APPROVAL_SENTENCE.contains(
                "Checkpoint 19D real-chain assembly",
            ),
        )
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
        assertNull(TrustedPerAttemptDestructiveConfirmationRecord.current())
        val confirmation = productionNullConfirmationSource().confirm(
            correlationId = DestructiveCorrelationId.generate { "19d-confirmation-probe" },
            binding = verifiedBinding(),
            scope = DestructiveScope.DEVICE_FACTORY_RESET,
            artifactIdentity = requireNotNull(disposableObservedIdentity()),
            challenge = DestructiveOperatorChallenge.create(ByteArray(32) { 1 }),
            attemptLease = DestructiveAttemptLease.create(),
            nowMonotonicMillis = 1_000L,
        )
        assertTrue(confirmation is DestructiveHumanConfirmationResult.Failed)
        assertEquals(
            "missing_per_attempt_human_confirmation",
            (confirmation as DestructiveHumanConfirmationResult.Failed).reason,
        )
    }

    @Test
    fun `historical 19C assembly flags remain a snapshot while 19D path is present`() {
        assertFalse(Checkpoint19CDecision.REAL_DESTRUCTIVE_CHAIN_ASSEMBLED)
        assertFalse(Checkpoint19CDecision.REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION)
        assertEquals("NO", Checkpoint19CDecision.REAL_CHAIN_ASSEMBLY_APPROVED)
        assertFalse(Checkpoint19BDecision.REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION)
        assertTrue(Checkpoint19DDecision.REAL_CHAIN_ASSEMBLY_PATH_PRESENT)
        assertFalse(Checkpoint19DDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
    }
}

class Checkpoint19DRealChainAssemblyTest {
    @Test
    fun `production orchestration exists and is origin-bound to assembleAndHandoff`() {
        val progression = ProductionDestructiveRealChainOrchestrator::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && it.name != "equals" && it.name != "hashCode" }
            .filter { it.name != "toString" }
        assertEquals(
            listOf("assembleAlreadyBoundDeviceFactoryReset"),
            progression.map { it.name },
        )
        val method = progression.single()
        assertEquals(
            ProductionBoundDeviceFactoryResetAttempt::class.java,
            method.parameterTypes.single(),
        )
        assertEquals(FutureDestructiveHandoffResult::class.java, method.returnType)
        assertFalse(DestructiveArtifactIdentityExpectation::class.java in method.parameterTypes)
        assertFalse(DestructiveHumanApproval::class.java in method.parameterTypes)
        assertFalse(FutureDestructiveExecutorContract::class.java in method.parameterTypes)
        assertFalse(AndroidFutureDestructiveExecutor::class.java in method.parameterTypes)
        assertFalse(Int::class.javaPrimitiveType in method.parameterTypes)
        assertFalse(java.lang.Boolean.TYPE in method.parameterTypes)
        assertFalse(String::class.java in method.parameterTypes)
        assertFalse(DestructiveOperatorChallenge::class.java in method.parameterTypes)
        assertFalse(DestructiveAttemptLease::class.java in method.parameterTypes)
        assertFalse(DestructiveHumanConfirmation::class.java in method.parameterTypes)
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        assertEquals(
            FutureDestructiveExecutorContract::class.java,
            handoff.parameterTypes.first(),
        )
        assertEquals(9, handoff.parameterCount)
        assertEquals(
            ProductionBoundDeviceFactoryResetAttempt::class.java,
            method.parameterTypes.single(),
        )
        val orchestratorSource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/" +
                "ProductionDestructiveRealChainOrchestrator.kt",
        ).readText()
        assertTrue(orchestratorSource.contains("assembleAndHandoff("))
        assertTrue(orchestratorSource.contains("executor = executor"))
        val signature = orchestratorSource.substringAfter("fun assembleAlreadyBoundDeviceFactoryReset(")
            .substringBefore("{")
        assertTrue(signature.contains("boundAttempt: ProductionBoundDeviceFactoryResetAttempt"))
        assertFalse(signature.contains("executor:"))
        assertFalse(signature.contains("expected:"))
        assertFalse(signature.contains("approval:"))
        val productionCallers = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot {
                it.path.endsWith("ProductionDestructiveRealChainOrchestrator.kt")
            }
            .joinToString("\n") { it.readText() }
        assertFalse(productionCallers.contains(".assembleAndHandoff("))
    }

    @Test
    fun `artifact and confirmation sources null block progression and cannot be injected`() {
        val retainer = retainedOrchestrator()
        val identity = requireNotNull(disposableObservedIdentity())
        val attempt = requireNotNull(
            ProductionBoundDeviceFactoryResetAttempt.bindAlreadyAuthorizedDeviceFactoryReset(
                binding = verifiedBinding(),
                observedIdentity = identity,
            ),
        )
        val result = retainer.orchestrator.assembleAlreadyBoundDeviceFactoryReset(attempt)
        assertTrue(result is FutureDestructiveHandoffResult.Failed)
        assertEquals(
            "missing_trusted_artifact_expectation",
            (result as FutureDestructiveHandoffResult.Failed).reason,
        )
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
        assertNull(UnwiredDestructiveArtifactIdentitySource.trustedExpectation())
        assertNull(TrustedPerAttemptDestructiveConfirmationRecord.current())
        assertTrue(
            ProductionDestructiveHumanConfirmationSource::class.java.declaredMethods
                .single { it.name == "confirm" }
                .parameterTypes
                .none {
                    it == DestructiveArtifactIdentityExpectation::class.java ||
                        it == DestructiveHumanApproval::class.java ||
                        it == java.lang.Boolean.TYPE
                },
        )
        assertTrue(
            DestructiveArtifactIdentityExpectation::class.java.declaredConstructors.none { constructor ->
                constructor.parameterTypes.contains(DestructiveArtifactIdentity::class.java)
            },
        )
        assertFalse(
            DestructiveArtifactIdentity::class.java.isAssignableFrom(
                DestructiveArtifactIdentityExpectation::class.java,
            ),
        )
        assertNull(
            ProductionBoundDeviceFactoryResetAttempt.bindAlreadyAuthorizedDeviceFactoryReset(
                binding = verifiedBinding().withScope(DestructiveScope.USER_SCOPED_WIPE),
                observedIdentity = identity,
            ),
        )
    }

    @Test
    fun `restart replay and durable state cannot reconstruct positive authority`() {
        val first = retainedOrchestrator()
        val identity = requireNotNull(disposableObservedIdentity())
        val attempt = requireNotNull(
            ProductionBoundDeviceFactoryResetAttempt.bindAlreadyAuthorizedDeviceFactoryReset(
                binding = verifiedBinding(),
                observedIdentity = identity,
            ),
        )
        val firstResult = first.orchestrator.assembleAlreadyBoundDeviceFactoryReset(attempt)
        val reconstructed = retainedOrchestrator()
        val replay = reconstructed.orchestrator.assembleAlreadyBoundDeviceFactoryReset(attempt)
        assertTrue(firstResult is FutureDestructiveHandoffResult.Failed)
        assertTrue(replay is FutureDestructiveHandoffResult.Failed)
        assertFalse(Serializable::class.java.isAssignableFrom(ProductionBoundDeviceFactoryResetAttempt::class.java))
        assertFalse(
            Serializable::class.java.isAssignableFrom(
                ProductionDestructiveRealChainOrchestrator::class.java,
            ),
        )
        assertFalse(Serializable::class.java.isAssignableFrom(DestructiveHumanApproval::class.java))
        assertFalse(Serializable::class.java.isAssignableFrom(FutureDestructiveExecutionBundle::class.java))
        assertFalse(
            ProductionDestructiveRealChainOrchestrator::class.java.declaredFields.any { field ->
                java.io.File::class.java.isAssignableFrom(field.type)
            },
        )
        val durabilitySource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/" +
                "RuntimeDestructiveSafetyDurability.kt",
        ).readText()
        assertTrue(durabilitySource.contains("cannot be sent across processes"))
    }

    @Test
    fun `no app UI registry or public facade trigger exists`() {
        val composition = File(
            "../device-management/src/main/java/com/example/devicemanagement/management/" +
                "DeviceManagementSensitiveActions.kt",
        ).readText()
        val facade = File(
            "../device-management-api/src/main/kotlin/com/example/devicemanagement/management/" +
                "DeviceManagementApi.kt",
        ).readText()
        val container = File(
            "../app/src/main/java/com/example/devicemanagement/app/AppContainer.kt",
        ).readText()
        val registry = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionRegistry.kt",
        ).readText()
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        listOf(
            "assembleAndHandoff",
            "assembleAlreadyBoundDeviceFactoryReset",
            "ProductionDestructiveRealChainOrchestrator",
            "Checkpoint19DDecision",
            "Checkpoint19EDecision",
            "Checkpoint19FDecision",
            "Checkpoint19GDecision",
            "Checkpoint19HDecision",
            "Checkpoint19JDecision",
            "issueFromTrustedConfirmationSource",
        ).forEach { token ->
            assertFalse(token, composition.contains(token))
            assertFalse(token, facade.contains(token))
            assertFalse(token, container.contains(token))
            assertFalse(token, registry.contains(token))
            assertFalse(token, controller.contains(token))
        }
        assertFalse(facade.contains("fun wipe"))
        assertTrue(registry.contains("MOCK_WIPE must never be registered in controlled mode"))
        assertTrue(composition.contains("ProductionDestructiveRealChain.retainForProduction"))
        val ui = File("../app/src/main/java/com/example/devicemanagement/ui")
        if (ui.isDirectory) {
            val uiSources = ui.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .joinToString("\n") { it.readText() }
            assertFalse(uiSources.contains("assembleAlreadyBoundDeviceFactoryReset"))
            assertFalse(uiSources.contains("assembleAndHandoff"))
        }
    }

    @Test
    fun `synchronous handoff order remains after durable append and live validation`() {
        val boundary = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/" +
                "FutureDestructiveExecutorContract.kt",
        ).readText()
        val consumeIndex = boundary.indexOf("authorizationAuthority.consume(")
        val commitIndex = boundary.indexOf("runtimePreExecutionAuthority.commitAfterConsumedAuthorization(")
        val liveIndex = boundary.indexOf("liveFactsSource.currentFacts()")
        val permitIndex = boundary.indexOf("val permit = mintFinalLiveValidationPermit()")
        val bundleIndex = boundary.indexOf("val bundle = assembleBundleFromPermit(permit)")
        val executeIndex = boundary.indexOf("executor.execute(bundle)")
        assertTrue(consumeIndex > 0)
        assertTrue(commitIndex > consumeIndex)
        assertTrue(liveIndex > commitIndex)
        assertTrue(permitIndex > liveIndex)
        assertTrue(bundleIndex > permitIndex)
        assertTrue(executeIndex > bundleIndex)
        assertFalse(boundary.contains("GlobalScope"))
        assertFalse(boundary.contains("Dispatchers."))
        assertFalse(boundary.contains("enqueue"))
        assertFalse(boundary.contains("WorkManager"))
        assertEquals(
            Checkpoint19DDecision.requiredRuntimeOrder,
            listOf(
                "consume capability",
                "durable PRE_EXECUTION_COMMITTED append",
                "fresh authoritative live validation",
                "single-use final permit",
                "single-use execution bundle",
                "immediate synchronous executor handoff",
                "AndroidFutureDestructiveExecutor",
                "AuthorizedFactoryResetPort",
                "AndroidDevicePolicyFactoryResetService",
                "platform whole-device call with literal flags 0",
            ),
        )
        Checkpoint19DDecision.runtimeFailClosedRequirements.forEach { name ->
            assertTrue(name, name in Checkpoint19DDecision.runtimeFailClosedRequirements)
        }
    }

    @Test
    fun `17B ENFORCED flags are true only because the production path cannot skip them`() {
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED)
        val orchestrator = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/" +
                "ProductionDestructiveRealChainOrchestrator.kt",
        ).readText()
        val boundary = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/" +
                "FutureDestructiveExecutorContract.kt",
        ).readText()
        assertTrue(orchestrator.contains("emptySet()"))
        assertTrue(orchestrator.contains("verifyDefaultDeny("))
        assertTrue(orchestrator.contains("artifactExpectationSource.trustedExpectation()"))
        assertTrue(orchestrator.contains("confirmationSource.confirm("))
        val authorizeIndex = orchestrator.indexOf("assembled.authorize(")
        val admitIndex = orchestrator.indexOf("artifactAuthority.admit(")
        val challengeIndex = orchestrator.indexOf("issueChallenge(")
        val confirmIndex = orchestrator.indexOf("confirmationSource.confirm(")
        val redeemIndex = orchestrator.indexOf(".redeem(")
        assertTrue(authorizeIndex > 0)
        assertTrue(admitIndex > authorizeIndex)
        assertTrue(challengeIndex > admitIndex)
        assertTrue(confirmIndex > challengeIndex)
        assertTrue(redeemIndex > confirmIndex)
        assertTrue(boundary.contains("artifactAuthority.consume("))
        assertTrue(boundary.contains("humanApprovalAuthority.consume("))
        assertTrue(boundary.contains("wipePolicyAuthority.consume("))
        assertTrue(boundary.contains("commitAfterConsumedAuthorization("))
        assertTrue(boundary.contains("cooldown.assertCurrentAttemptMarkerPresent()"))
        assertFalse(orchestrator.contains("WIPE_SILENTLY"))
        assertFalse(orchestrator.contains("callerFlags"))
    }

    @Test
    fun `confirmation record stays null and has no public mint or boolean shortcut`() {
        assertNull(TrustedPerAttemptDestructiveConfirmationRecord.current())
        assertTrue(
            TrustedPerAttemptConfirmationFacts::class.java.declaredConstructors.all { constructor ->
                Modifier.isPrivate(constructor.modifiers)
            },
        )
        assertTrue(
            TrustedPerAttemptConfirmationFacts::class.java.declaredClasses.none { nested ->
                nested.declaredMethods.any {
                    it.name.startsWith("issue") || it.name.startsWith("mint") || it.name == "create"
                }
            },
        )
        assertTrue(
            ProductionDestructiveHumanConfirmationSource::class.java.declaredMethods
                .filter { it.name == "confirm" }
                .none { method ->
                    method.parameterTypes.contains(java.lang.Boolean.TYPE) ||
                        method.parameterTypes.contains(String::class.java) ||
                        method.parameterTypes.contains(DestructiveHumanApproval::class.java)
                },
        )
        val confirmationSource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/" +
                "DestructiveHumanApprovalAuthority.kt",
        ).readText()
        assertTrue(confirmationSource.contains("fun current(): TrustedPerAttemptConfirmationFacts? = null"))
        assertTrue(confirmationSource.contains("): DestructiveHumanConfirmation? = null"))
        assertTrue(confirmationSource.contains("challenge = challenge,"))
        assertTrue(confirmationSource.contains("attemptLease = attemptLease,"))
        assertFalse(confirmationSource.contains("challenge = trusted.challenge"))
        assertFalse(confirmationSource.contains("attemptLease = trusted.attemptLease"))
        assertFalse(confirmationSource.contains("Instant.ofEpochMilli(nowMonotonicMillis)"))
        assertFalse(
            ProductionDestructiveHumanConfirmationSource::class.java.declaredFields.any { field ->
                field.name == "INSTANCE"
            },
        )
        val confirm = ProductionDestructiveHumanConfirmationSource::class.java.declaredMethods
            .single { it.name == "confirm" }
        assertTrue(DestructiveOperatorChallenge::class.java in confirm.parameterTypes)
        assertTrue(DestructiveAttemptLease::class.java in confirm.parameterTypes)
        assertFalse(confirmationSource.contains("confirmed: Boolean"))
        assertFalse(confirmationSource.contains("fun confirm(confirmed"))
        assertFalse(confirmationSource.contains("= \"confirmed\""))
        val identity = requireNotNull(disposableObservedIdentity())
        val confirmation = productionNullConfirmationSource().confirm(
            correlationId = DestructiveCorrelationId.generate { "19d-no-shortcut" },
            binding = verifiedBinding(),
            scope = DestructiveScope.DEVICE_FACTORY_RESET,
            artifactIdentity = identity,
            challenge = DestructiveOperatorChallenge.create(ByteArray(32) { 1 }),
            attemptLease = DestructiveAttemptLease.create(),
            nowMonotonicMillis = 1_000L,
        )
        assertTrue(confirmation is DestructiveHumanConfirmationResult.Failed)
        assertEquals(
            "missing_per_attempt_human_confirmation",
            (confirmation as DestructiveHumanConfirmationResult.Failed).reason,
        )
    }

    @Test
    fun `decision document records assembly without claiming runtime availability`() {
        val docs = File("../docs/WIPE_19D_REAL_CHAIN_ASSEMBLY.md").readText()
        assertTrue(docs.contains("REAL_CHAIN_ASSEMBLY_IMPLEMENTATION_APPROVED = YES"))
        assertTrue(docs.contains("REAL_CHAIN_ASSEMBLY_PATH_PRESENT = true"))
        assertTrue(docs.contains("19D_REAL_CHAIN_STRUCTURAL_ASSEMBLY_COMPLETE = YES"))
        assertTrue(docs.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(docs.contains("19D_CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(docs.contains("PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false"))
        assertTrue(docs.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(docs.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(docs.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(docs.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(docs.contains("Checkpoint 19D implements **state 2 only**"))
        assertTrue(docs.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(docs.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertFalse(docs.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = true"))
        assertFalse(docs.contains("19D_CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        val decisionSource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19DDecision.kt",
        ).readText()
        assertFalse(HEX_SHA256.containsMatchIn(decisionSource))
        assertFalse(decisionSource.contains("wipeDevice"))
        assertFalse(decisionSource.contains("wipeData"))
        assertTrue(decisionSource.contains("REAL_CHAIN_STRUCTURAL_ASSEMBLY_COMPLETE = \"YES\""))
        assertTrue(decisionSource.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = \"NO\""))
    }

    @Test
    fun `manifests still have no factory-reset trigger action`() {
        val manifests = listOf(
            File("../app/src/main/AndroidManifest.xml"),
            File("../device-management/src/main/AndroidManifest.xml"),
        )
        manifests.forEach { file ->
            val text = file.readText()
            assertFalse(file.path, text.contains("assembleAlreadyBoundDeviceFactoryReset"))
            assertFalse(file.path, text.contains("assembleAndHandoff"))
            assertFalse(file.path, text.contains("FACTORY_RESET"))
            assertFalse(file.path, text.contains("BOOT_COMPLETED"))
            assertFalse(file.path, text.contains("wipeDevice"))
        }
    }

    private fun retainedOrchestrator(): ProductionDestructiveRetainer {
        return ProductionDestructiveRealChain.retainForProduction(
            factoryReset = object : AuthorizedFactoryResetPort {
                override fun performAuthorizedFactoryReset(): AuthorizedFactoryResetResult {
                    return AuthorizedFactoryResetResult.Refused("19d_must_not_execute")
                }
            },
            liveFacts = DestructiveLiveFactsSource { verifiedFacts() },
            clock = object : MonotonicTimeSource {
                override fun nowMillis(): Long = 1_000L
            },
            durability = null,
        )
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}

private fun productionNullConfirmationSource(): ProductionDestructiveHumanConfirmationSource {
    val artifact = ProductionDestructiveTrustedArtifactExpectationSource()
    return ProductionDestructiveHumanConfirmationSource(
        recordSource = ProductionDestructiveTrustedPerAttemptConfirmationRecordSource(),
        utcClock = ProductionDestructiveUtcClock(),
        approvedBuildRevision = ProductionDestructiveApprovedBuildRevisionSource(),
        liveFacts = DestructiveLiveFactsSource { verifiedFacts() },
        artifactExpectationSource = artifact,
    )
}

internal fun disposableObservedIdentity(): DestructiveArtifactIdentity? {
    return DestructiveArtifactIdentity.snapshot(
        certificateSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        artifactSha256 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        packageName = "com.example.devicemanagement",
        adminComponent =
            "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
        buildPurpose = DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
    )
}
