package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19EDecisionRealityTest {
    @Test
    fun `19E records workflow presence separately from later states`() {
        assertTrue(Checkpoint19EDecision.INDEPENDENT_CI_WORKFLOW_PRESENT)
        assertFalse(Checkpoint19EDecision.GITHUB_CI_RUN_OBSERVED)
        assertFalse(Checkpoint19EDecision.BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED)
        assertFalse(Checkpoint19EDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertFalse(Checkpoint19EDecision.TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED)
        assertFalse(Checkpoint19EDecision.PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE)
        assertFalse(Checkpoint19EDecision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint19EDecision.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertFalse(Checkpoint19EDecision.DESTRUCTIVE_HARDWARE_TEST_PERFORMED)
        assertFalse(Checkpoint19EDecision.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertFalse(Checkpoint19EDecision.HARDWARE_VALIDATION_PREPARATION_READY)
        assertFalse(Checkpoint19EDecision.HARDWARE_TEST_APPROVAL_GRANTED)
        assertTrue(Checkpoint19EDecision.UNSIGNED_RELEASE_OUTPUT_IS_NOT_DISTRIBUTABLE)
        assertFalse(Checkpoint19EDecision.CHECKPOINT_19E_USED_AS_RUNTIME_AUTHORIZATION)
        assertEquals("NO", Checkpoint19EDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
        assertEquals("true", Checkpoint19EDecision.recordedFlags["19E_INDEPENDENT_CI_WORKFLOW_PRESENT"])
        assertEquals("false", Checkpoint19EDecision.recordedFlags["19E_GITHUB_CI_RUN_OBSERVED"])
        assertEquals("false", Checkpoint19EDecision.recordedFlags["19E_BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED"])
        assertEquals("false", Checkpoint19EDecision.recordedFlags["DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED"])
        assertEquals("NO", Checkpoint19EDecision.recordedFlags["CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET"])
        assertEquals(
            Checkpoint19EDecision.LOCAL_VALIDATION_PASSED.toString(),
            Checkpoint19EDecision.recordedFlags["19E_LOCAL_VALIDATION_PASSED"],
        )
        assertEquals(8, Checkpoint19EDecision.separateStatesThatMustNotBeInferred.size)
        assertFalse(Checkpoint19DDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertEquals("NO", Checkpoint19DDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
    }

    @Test
    fun `19E is not runtime authorization and has no production trigger`() {
        val methods = Checkpoint19EDecision::class.java.declaredMethods
            .filter { it.name !in setOf("equals", "hashCode", "toString") }
            .map { it.name }
        assertFalse(methods.contains("authorize"))
        assertFalse(methods.contains("approve"))
        assertFalse(methods.contains("wipe"))
        assertFalse(methods.contains("execute"))
        assertFalse(methods.contains("confirm"))
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
            "Checkpoint19EDecision",
            "Checkpoint19FDecision",
            "independent-safety-verification",
        ).forEach { token ->
            assertFalse(token, composition.contains(token))
            assertFalse(token, facade.contains(token))
            assertFalse(token, container.contains(token))
            assertFalse(token, registry.contains(token))
            assertFalse(token, controller.contains(token))
        }
        assertTrue(registry.contains("MOCK_WIPE must never be registered in controlled mode"))
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertTrue(sources.contains("Checkpoint19EDecision"))
        assertTrue(sources.contains("Checkpoint19FDecision"))
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("import android.app." + "admin.DevicePolicyManager"))
    }

    @Test
    fun `decision document keeps the eight states separate and closed after workflow presence`() {
        val docs = File("../docs/WIPE_19E_INDEPENDENT_CI.md").readText()
        assertTrue(docs.contains("19E_INDEPENDENT_CI_WORKFLOW_PRESENT = true"))
        assertTrue(docs.contains("19E_GITHUB_CI_RUN_OBSERVED = false"))
        assertTrue(docs.contains("19E_BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED = false"))
        assertTrue(docs.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(docs.contains("HARDWARE_VALIDATION_PREPARATION_READY = false"))
        assertTrue(docs.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(docs.contains("HARDWARE_TEST_APPROVAL_GRANTED = false"))
        assertTrue(docs.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(docs.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(docs.contains("1. CI workflow present"))
        assertTrue(docs.contains("2. Actual GitHub CI run observed"))
        assertTrue(docs.contains("3. Branch-protection required check configured"))
        assertTrue(docs.contains("4. Production signing enabled"))
        assertTrue(docs.contains("5. Hardware-validation preparation ready"))
        assertTrue(docs.contains("6. Hardware-test approval granted"))
        assertTrue(docs.contains("7. Hardware test performed"))
        assertTrue(docs.contains("8. GrapheneOS behavior verified"))
        assertTrue(docs.contains("They must **never** be inferred"))
        assertTrue(docs.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(docs.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertTrue(docs.contains("UNSIGNED"))
        assertFalse(docs.contains("19E_GITHUB_CI_RUN_OBSERVED = true"))
        assertFalse(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        val decisionSource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19EDecision.kt",
        ).readText()
        assertFalse(HEX_SHA256.containsMatchIn(decisionSource))
        Checkpoint19EDecision.requiredGradleVerificationTasks.forEach { task ->
            assertTrue(task, docs.contains(task))
        }
        assertEquals(
            Checkpoint19EDecision.LOCAL_VALIDATION_PASSED,
            docs.contains("19E_LOCAL_VALIDATION_PASSED = true") &&
                !docs.contains("19E_LOCAL_VALIDATION_PASSED = false"),
        )
        assertEquals(
            Checkpoint19EDecision.LOCAL_VALIDATION_PASSED.toString(),
            Checkpoint19EDecision.recordedFlags.getValue("19E_LOCAL_VALIDATION_PASSED"),
        )
    }

    @Test
    fun `ordinary debug and release tasks remain incapable of completing a factory reset`() {
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        assertTrue(workflow.contains("assembleDebug"))
        assertTrue(workflow.contains("assembleRelease"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(Checkpoint19EDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertEquals("NO", Checkpoint19EDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
        assertFalse(TrustedDestructiveArtifactValidationSource.trustedExpectation() != null)
        assertTrue(TrustedPerAttemptDestructiveConfirmationRecord.current() == null)
        val manifests = listOf(
            File("../app/src/main/AndroidManifest.xml"),
            File("../device-management/src/main/AndroidManifest.xml"),
        )
        manifests.forEach { file ->
            val text = file.readText()
            assertFalse(file.path, text.contains("assembleAlreadyBoundDeviceFactoryReset"))
            assertFalse(file.path, text.contains("Checkpoint19EDecision"))
            assertFalse(file.path, text.contains("Checkpoint19FDecision"))
            assertFalse(file.path, text.contains("BOOT_COMPLETED"))
        }
    }

    @Test
    fun `19E tests themselves do not invoke the platform whole-device call`() {
        val thisFile = File(
            "src/test/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19EDecisionRealityTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("android.app." + "admin"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
