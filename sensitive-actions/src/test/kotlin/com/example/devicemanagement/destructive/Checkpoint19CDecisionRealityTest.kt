package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19CDecisionRealityTest {
    @Test
    fun `readiness flags keep the chain unassembled and do not invent hashes`() {
        assertEquals("YES", Checkpoint19CDecision.ARCHITECTURE_READY_RECONFIRMED)
        assertEquals("YES", Checkpoint19CDecision.READINESS_MODEL_NON_CIRCULAR)
        assertEquals("YES", Checkpoint19CDecision.REAL_CHAIN_ASSEMBLY_APPROVAL_REQUEST_READY)
        assertEquals("NO", Checkpoint19CDecision.HARDWARE_VALIDATION_PREPARATION_READY)
        assertEquals("NO", Checkpoint19CDecision.REAL_CHAIN_ASSEMBLY_APPROVED)
        assertEquals("NO", Checkpoint19CDecision.ARTIFACT_SIGNING_APPROVAL_GRANTED)
        assertEquals("NO", Checkpoint19CDecision.HARDWARE_TEST_APPROVAL_GRANTED)
        assertTrue(Checkpoint19CDecision.DESTRUCTIVE_IMPLEMENTATION_PRESENT)
        assertFalse(Checkpoint19CDecision.REAL_DESTRUCTIVE_CHAIN_ASSEMBLED)
        assertFalse(Checkpoint19CDecision.REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION)
        assertFalse(Checkpoint19CDecision.PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED)
        assertFalse(Checkpoint19CDecision.TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED)
        assertFalse(Checkpoint19CDecision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint19CDecision.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertFalse(Checkpoint19CDecision.DESTRUCTIVE_HARDWARE_TEST_PERFORMED)
        assertFalse(Checkpoint19CDecision.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertFalse(Checkpoint19CDecision.DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT)
        assertFalse(Checkpoint19CDecision.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertFalse(Checkpoint19CDecision.PRODUCTION_REACHABLE_SIMULATION)
        assertNull(Checkpoint19CDecision.RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256)
        assertNull(Checkpoint19CDecision.RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256)
        assertNull(Checkpoint19CDecision.RECORDED_DISPOSABLE_DEVICE_SERIAL)
        assertNull(Checkpoint19CDecision.RECORDED_PER_ATTEMPT_CONFIRMATION)
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
        assertNull(UnwiredDestructiveArtifactIdentitySource.trustedExpectation())
        val retainer = ProductionDestructiveRealChain.retainForProduction(
            factoryReset = object : AuthorizedFactoryResetPort {
                override fun performAuthorizedFactoryReset(): AuthorizedFactoryResetResult {
                    return AuthorizedFactoryResetResult.Refused("19c_readiness_must_not_execute")
                }
            },
            liveFacts = DestructiveLiveFactsSource {
                error("live facts unused while chain unassembled")
            },
            clock = object : com.example.devicemanagement.integration.MonotonicTimeSource {
                override fun nowMillis(): Long = 0L
            },
            durability = null,
        )
        assertNull(retainer.boundary)
        assertTrue(retainer.executor is AndroidFutureDestructiveExecutor)
    }

    @Test
    fun `preserved 19B facts remain true without assembling the chain`() {
        assertTrue(Checkpoint19CDecision.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertTrue(Checkpoint19CDecision.DESTRUCTIVE_POLICY_WRAPPER_PRESENT)
        assertTrue(Checkpoint19CDecision.DESTRUCTIVE_METADATA_PRESENT)
        assertTrue(Checkpoint19CDecision.WIPE_ZERO_BYTECODE_ENFORCED)
        assertTrue(Checkpoint19CDecision.FACTORY_RESET_ORIGIN_EXACT)
        assertTrue(Checkpoint19CDecision.DEX_CONTROL_FLOW_ZERO_PROOF)
        assertTrue(Checkpoint19CDecision.WIPE_DATA_METADATA_REVIEW_APPROVED)
        assertTrue(Checkpoint19CDecision.DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED)
        assertTrue(Checkpoint19BDecision.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertTrue(Checkpoint19BDecision.DESTRUCTIVE_POLICY_WRAPPER_PRESENT)
        assertTrue(Checkpoint19BDecision.DESTRUCTIVE_METADATA_PRESENT)
        assertTrue(Checkpoint19BDecision.WIPE_ZERO_BYTECODE_ENFORCED)
        assertFalse(Checkpoint19BDecision.REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED)
        assertTrue(Checkpoint19CDecision.FUTURE_SCOPE_DEVICE_FACTORY_RESET_ONLY)
        assertTrue(Checkpoint19CDecision.FUTURE_EXTRA_FLAG_SET_MUST_BE_EMPTY)
        assertTrue(Checkpoint19CDecision.FUTURE_FLAGS_MUST_BE_LITERAL_ZERO)
    }

    @Test
    fun `contracts name the future assembly surface confirmation artifact and device checklist`() {
        assertTrue(
            Checkpoint19CDecision.futureRealChainAssemblySurface.contains(
                "FutureDestructiveRealChainBoundary.assembleAndHandoff",
            ),
        )
        assertTrue(
            Checkpoint19CDecision.futureRealChainAssemblySurface.contains(
                "DeviceManagementComposition.retainProductionDestructiveImplementation",
            ),
        )
        assertTrue(
            Checkpoint19CDecision.futureRealChainAssemblySurface.contains(
                "UnwiredDestructiveHumanConfirmationSource.confirm",
            ),
        )
        assertTrue(
            Checkpoint19CDecision.futureRealChainAssemblySurface.contains(
                "TrustedDestructiveArtifactValidationSource.trustedExpectation",
            ),
        )
        assertEquals(
            listOf(
                "consume capability",
                "durable PRE_EXECUTION_COMMITTED append",
                "fresh live validation",
                "single-use final permit",
                "single-use execution bundle",
                "immediate synchronous executor handoff",
                "AndroidFutureDestructiveExecutor",
                "AuthorizedFactoryResetPort",
                "AndroidDevicePolicyFactoryResetService",
                "platform whole-device call with literal flags 0",
            ),
            Checkpoint19CDecision.futureRequiredRuntimeOrder,
        )
        Checkpoint19CDecision.futureFailClosedRequirements.forEach { name ->
            assertTrue(name, name in Checkpoint19CDecision.futureFailClosedRequirements)
        }
        listOf(
            "operator_identity",
            "utc_timestamp",
            "exact_device_serial",
            "exact_package",
            "exact_device_admin_component",
            "exact_signing_certificate_sha256",
            "exact_apk_sha256",
            "exact_scope_DEVICE_FACTORY_RESET",
            "exact_flags_literal_0",
            "exact_build_revision",
            "one_attempt_only",
            "short_validity_window",
            "non_replayable_attempt_identifier",
        ).forEach { field ->
            assertTrue(field, field in Checkpoint19CDecision.futurePerAttemptConfirmationRequiredFields)
        }
        listOf(
            "exact_apk_used_for_installation_and_testing",
            "sha256_from_final_immutable_apk_bytes",
            "any_rebuild_invalidates_approval",
        ).forEach { step ->
            assertTrue(step, step in Checkpoint19CDecision.futureArtifactIdentityFlow)
        }
        listOf(
            "dedicated_disposable_android_device",
            "device_serial_explicitly_identified",
            "abort_criteria_defined",
        ).forEach { item ->
            assertTrue(item, item in Checkpoint19CDecision.disposableDeviceChecklist)
        }
        assertEquals(
            listOf(
                "REAL_DESTRUCTIVE_CHAIN_ASSEMBLED",
                "PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED",
                "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED",
                "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
                "DISPOSABLE_DEVICE_SERIAL_IDENTIFIED",
                "EXPECTED_OS_BUILD_RECORDED",
                "FACTORY_RESET_CONSEQUENCE_ACKNOWLEDGED",
                "DESTRUCTIVE_RECOVERY_PROCEDURE_PREPARED",
                "BATTERY_USB_ADB_STATE_RECORDED",
                "REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED",
                "REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED",
                "REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED",
                "REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED",
                "REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED",
            ),
            Checkpoint19CDecision.remainingHardwarePreparationBlockers,
        )
        assertEquals(
            listOf(
                "HARDWARE_TEST_APPROVAL_GRANTED",
                "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
                "DESTRUCTIVE_HARDWARE_TEST_PERFORMED",
                "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
            ),
            Checkpoint19CDecision.laterHardwareValidationStates,
        )
    }

    @Test
    fun `readiness model is not circular later states are not preparation blockers`() {
        assertEquals("YES", Checkpoint19CDecision.READINESS_MODEL_NON_CIRCULAR)
        assertEquals("NO", Checkpoint19CDecision.HARDWARE_VALIDATION_PREPARATION_READY)
        assertEquals("NO", Checkpoint19CDecision.HARDWARE_TEST_APPROVAL_GRANTED)
        assertFalse(Checkpoint19CDecision.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertFalse(Checkpoint19CDecision.DESTRUCTIVE_HARDWARE_TEST_PERFORMED)
        assertFalse(Checkpoint19CDecision.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertTrue(Checkpoint19CDecision.remainingHardwarePreparationBlockers.isNotEmpty())
        Checkpoint19CDecision.laterHardwareValidationStates.forEach { later ->
            assertFalse(
                later,
                later in Checkpoint19CDecision.remainingHardwarePreparationBlockers,
            )
        }
        assertTrue(
            Checkpoint19CDecision.remainingHardwarePreparationBlockers
                .intersect(Checkpoint19CDecision.laterHardwareValidationStates.toSet())
                .isEmpty(),
        )
        assertTrue(
            Checkpoint19CDecision.remainingHardwarePreparationBlockers.containsAll(
                listOf(
                    "REAL_DESTRUCTIVE_CHAIN_ASSEMBLED",
                    "PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED",
                    "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED",
                    "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
                    "DISPOSABLE_DEVICE_SERIAL_IDENTIFIED",
                    "EXPECTED_OS_BUILD_RECORDED",
                    "FACTORY_RESET_CONSEQUENCE_ACKNOWLEDGED",
                    "DESTRUCTIVE_RECOVERY_PROCEDURE_PREPARED",
                    "BATTERY_USB_ADB_STATE_RECORDED",
                    "REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED",
                    "REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED",
                    "REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED",
                    "REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED",
                    "REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED",
                ),
            ),
        )
    }

    @Test
    fun `decision document separates states and refuses assembly signing and hardware`() {
        val docs = File("../docs/WIPE_19C_HARDWARE_VALIDATION_READINESS.md").readText()
        assertTrue(docs.contains("19C_READINESS_MODEL_NON_CIRCULAR = YES"))
        assertTrue(docs.contains("19C_REAL_CHAIN_ASSEMBLY_APPROVAL_REQUEST_READY = YES"))
        assertTrue(docs.contains("19C_HARDWARE_VALIDATION_PREPARATION_READY = NO"))
        assertTrue(docs.contains("REAL_DESTRUCTIVE_CHAIN_ASSEMBLED = false"))
        assertTrue(docs.contains("PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED = false"))
        assertTrue(docs.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(docs.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(docs.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(docs.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(docs.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(docs.contains("REAL_DESTRUCTIVE_EXECUTOR_PRESENT = true"))
        assertTrue(docs.contains("DESTRUCTIVE_POLICY_WRAPPER_PRESENT = true"))
        assertTrue(docs.contains("DESTRUCTIVE_METADATA_PRESENT = true"))
        assertTrue(docs.contains("wipeDevice(0) exact-zero enforcement = true"))
        assertTrue(docs.contains("1. Implementation present"))
        assertTrue(docs.contains("2. Real-chain assembly approval"))
        assertTrue(docs.contains("3. Real-chain assembly"))
        assertTrue(docs.contains("4. Artifact / signing approval"))
        assertTrue(docs.contains("5. Artifact identity recording"))
        assertTrue(docs.contains("6. Destructive hardware-test approval"))
        assertTrue(docs.contains("7. Destructive hardware test"))
        assertTrue(docs.contains("8. GrapheneOS validation"))
        assertTrue(docs.contains("Do **not** assemble it in this checkpoint."))
        assertTrue(docs.contains("This checkpoint does not mint or record a real confirmation."))
        assertTrue(docs.contains("Do not invent hashes."))
        assertTrue(docs.contains("Do **not** run the hardware test in this checkpoint."))
        assertTrue(docs.contains("NO NEW DESTRUCTIVE SCOPE ADDED"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertTrue(docs.contains("The readiness model is not circular"))
        assertTrue(docs.contains("PREPARATION_READY = YES"))
        assertTrue(docs.contains("HARDWARE_TEST_APPROVAL_GRANTED = NO"))
        assertTrue(docs.contains("Approval, execution, and result verification happen **after** preparation"))
        assertTrue(docs.contains("Later states that are **not** preparation blockers"))
        assertFalse(docs.contains("10. GrapheneOS wipe behavior is not verified."))
        assertFalse(docs.contains("REAL_DESTRUCTIVE_CHAIN_ASSEMBLED = true"))
        assertFalse(docs.contains("PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED = true"))
        assertFalse(docs.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = true"))
        assertFalse(docs.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = true"))
        assertFalse(docs.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = true"))
        assertFalse(docs.contains("19C_HARDWARE_VALIDATION_PREPARATION_READY = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        val decisionSource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19CDecision.kt",
        ).readText()
        assertFalse(HEX_SHA256.containsMatchIn(decisionSource))
        assertFalse(decisionSource.contains("wipeDevice"))
        assertFalse(decisionSource.contains("wipeData"))
    }

    @Test
    fun `Checkpoint 19C does not assemble wire confirm enable signing or add a wipe command`() {
        val composition = File(
            "../device-management/src/main/java/com/example/devicemanagement/management/DeviceManagementSensitiveActions.kt",
        ).readText()
        assertTrue(composition.contains("ProductionDestructiveRealChain.retainForProduction"))
        assertFalse(composition.contains("assembleAndHandoff"))
        assertFalse(composition.contains("issueFromTrustedConfirmationSource"))
        assertFalse(composition.contains("issueFromTrustedValidationSource"))
        assertFalse(composition.contains("Checkpoint19CDecision"))
        assertFalse(composition.contains("assembleAlreadyBoundDeviceFactoryReset"))
        assertFalse(composition.contains("DestructiveHumanConfirmationAuthority"))
        val retainer = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/ProductionDestructiveRealChain.kt",
        ).readText()
        assertTrue(retainer.contains("trustedExpectation()"))
        assertTrue(retainer.contains("?: return null"))
        assertFalse(retainer.contains("assembleAndHandoff("))
        val confirmation = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveHumanApprovalAuthority.kt",
        ).readText()
        assertTrue(confirmation.contains("fun confirm("))
        assertTrue(confirmation.contains("): DestructiveHumanConfirmation? = null"))
        val artifact = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveArtifactIdentity.kt",
        ).readText()
        assertTrue(artifact.contains("fun trustedExpectation(): DestructiveArtifactIdentityExpectation? = null"))
        val registry = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionRegistry.kt",
        ).readText()
        assertTrue(registry.contains("DISABLE_SCREEN_CAPTURE"))
        assertTrue(registry.contains("ENABLE_STATUS_BAR"))
        assertTrue(registry.contains("MOCK_WIPE must never be registered in controlled mode"))
        assertFalse(registry.contains("assembleAndHandoff"))
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        assertFalse(controller.contains("assembleAndHandoff"))
        assertFalse(controller.contains("Checkpoint19CDecision"))
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertTrue(sources.contains("Checkpoint19CDecision"))
        assertTrue(sources.contains("Checkpoint19DDecision"))
        assertTrue(sources.contains("Checkpoint19EDecision"))
        assertTrue(sources.contains("Checkpoint19FDecision"))
        assertTrue(sources.contains("Checkpoint19GDecision"))
        assertTrue(sources.contains("Checkpoint19HDecision"))
        assertTrue(sources.contains("Checkpoint19JDecision"))
    }

    @Test
    fun `19C tests themselves do not invoke the platform whole-device call`() {
        val testRoot = File("src/test/kotlin/com/example/devicemanagement/destructive")
        val nineteenC = testRoot.listFiles().orEmpty()
            .filter { it.name.startsWith("Checkpoint19C") }
            .joinToString("\n") { it.readText() }
        assertTrue(nineteenC.contains("Checkpoint19CDecision"))
        assertFalse(nineteenC.contains("manager." + "wipeDevice"))
        assertFalse(nineteenC.contains("android.app." + "admin"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
