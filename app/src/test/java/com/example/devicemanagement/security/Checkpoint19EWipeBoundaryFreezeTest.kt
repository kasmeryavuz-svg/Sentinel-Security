package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19EWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 19E document records CI without later destructive states`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_19E_INDEPENDENT_CI.md").readText()

        assertTrue(decision.contains("19E_INDEPENDENT_CI_WORKFLOW_PRESENT = true"))
        assertTrue(decision.contains("19E_GITHUB_CI_RUN_OBSERVED = false"))
        assertTrue(decision.contains("19E_BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED = false"))
        assertTrue(decision.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(decision.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(decision.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(decision.contains("1. CI workflow present"))
        assertTrue(decision.contains("2. Actual GitHub CI run observed"))
        assertTrue(decision.contains("3. Branch-protection required check configured"))
        assertTrue(decision.contains("4. Production signing enabled"))
        assertTrue(decision.contains("5. Hardware-validation preparation ready"))
        assertTrue(decision.contains("6. Hardware-test approval granted"))
        assertTrue(decision.contains("7. Hardware test performed"))
        assertTrue(decision.contains("8. GrapheneOS behavior verified"))
        assertTrue(decision.contains("must **never** be inferred"))
        assertTrue(decision.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(decision.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertFalse(decision.contains("19E_GITHUB_CI_RUN_OBSERVED = true"))
        assertFalse(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
    }

    @Test
    fun `app production sources still have no destructive DPM real-chain or 19E trigger`() {
        val appSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(appSources.contains("wipeData"))
        assertFalse(appSources.contains("wipeDevice"))
        assertFalse(appSources.contains("DevicePolicyManager"))
        assertFalse(appSources.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(appSources.contains("assembleAndHandoff"))
        assertFalse(appSources.contains("assembleAlreadyBoundDeviceFactoryReset"))
        assertFalse(appSources.contains("Checkpoint19EDecision"))
        assertFalse(appSources.contains("Checkpoint19DDecision"))
        assertFalse(appSources.contains("ProductionDestructiveRealChainOrchestrator"))
        assertFalse(appSources.contains("ProductionDestructiveHumanConfirmationSource"))
        assertFalse(appSources.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(appSources.contains("AndroidDevicePolicyFactoryResetService"))
        assertFalse(appSources.contains("AuthorizedFactoryResetPort"))
        assertFalse(appSources.contains("ProductionDestructiveRealChain"))
        assertFalse(appSources.contains("<wipe-data>"))
        assertFalse(appSources.contains("issueFromTrustedConfirmationSource"))
        assertFalse(appSources.contains("issueFromTrustedValidationSource"))
    }

    @Test
    fun `independent CI workflow does not upload artifacts or grant write permissions`() {
        val workflow = File(
            requireNotNull(System.getProperty("repoRoot")),
            ".github/workflows/checkpoint-19e-independent-ci.yml",
        ).readText()
        assertTrue(workflow.contains("pull_request:"))
        assertFalse(workflow.contains("pull_request_target"))
        assertTrue(workflow.contains("contents: read"))
        assertFalse(Regex(":\\s*write\\b").containsMatchIn(workflow.substringAfter("permissions:").substringBefore("concurrency:")))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(workflow.contains("\${{ secrets"))
        assertTrue(workflow.contains("assembleDebug"))
        assertTrue(workflow.contains("assembleRelease"))
        assertTrue(workflow.contains("signing=UNSIGNED"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(Regex("\\bemulator\\b").containsMatchIn(workflow))
        assertFalse(Regex("\\badb\\b").containsMatchIn(workflow))
    }

    @Test
    fun `19E freeze tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            requireNotNull(System.getProperty("repoRoot")),
            "app/src/test/java/com/example/devicemanagement/security/Checkpoint19EWipeBoundaryFreezeTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
