package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19FWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 19F contract stays unfilled and closed after tooling presence`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_19F_DISPOSABLE_DEVICE_EVIDENCE_CONTRACT.md").readText()

        assertTrue(decision.contains("19F_ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT = true"))
        assertTrue(decision.contains("19F_DISPOSABLE_DEVICE_CONTRACT_TEMPLATE_PRESENT = true"))
        assertTrue(decision.contains("19F_CANDIDATE_ARTIFACT_ELIGIBLE = false"))
        assertTrue(decision.contains("19F_REAL_DEVICE_IDENTITY_RECORDED = false"))
        assertTrue(decision.contains("19F_HARDWARE_VALIDATION_PREPARATION_READY = false"))
        assertTrue(decision.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(decision.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(decision.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(decision.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(decision.contains("1. Candidate evidence tooling present"))
        assertTrue(decision.contains("2. Candidate report generated"))
        assertTrue(decision.contains("3. Candidate eligible"))
        assertTrue(decision.contains("4. Production signing approved"))
        assertTrue(decision.contains("5. Production signing enabled"))
        assertTrue(decision.contains("6. Exact artifact frozen and trusted"))
        assertTrue(decision.contains("7. Disposable device identified"))
        assertTrue(decision.contains("8. Hardware-validation preparation ready"))
        assertTrue(decision.contains("9. Hardware-test approval granted"))
        assertTrue(decision.contains("10. Per-attempt confirmation available"))
        assertTrue(decision.contains("11. Hardware test performed"))
        assertTrue(decision.contains("12. GrapheneOS behavior verified"))
        assertTrue(decision.contains("must **never** be inferred"))
        assertTrue(decision.contains("exact_device_serial = UNRECORDED"))
        assertTrue(decision.contains("apk_sha256 = UNRECORDED"))
        assertTrue(decision.contains("signing_certificate_sha256 = UNRECORDED"))
        assertTrue(decision.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(decision.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertFalse(decision.contains("19F_CANDIDATE_ARTIFACT_ELIGIBLE = true"))
        assertFalse(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
    }

    @Test
    fun `app production sources still have no destructive DPM real-chain or 19F trigger`() {
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
        assertFalse(appSources.contains("Checkpoint19FDecision"))
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
        assertFalse(appSources.contains("destructive-validation-candidate.txt"))
        assertFalse(appSources.contains("DestructiveValidationCandidateEvidence"))
        assertFalse(appSources.contains("sentinel.destructiveValidationCandidateApk"))
        assertFalse(appSources.contains("apk_sha256="))
    }

    @Test
    fun `independent CI workflow still refuses uploads secrets and hardware access`() {
        val workflow = File(
            requireNotNull(System.getProperty("repoRoot")),
            ".github/workflows/checkpoint-19e-independent-ci.yml",
        ).readText()
        assertTrue(workflow.contains("pull_request:"))
        assertFalse(workflow.contains("pull_request_target"))
        assertTrue(workflow.contains("contents: read"))
        assertFalse(
            Regex(":\\s*write\\b").containsMatchIn(
                workflow.substringAfter("permissions:").substringBefore("concurrency:"),
            ),
        )
        assertTrue(workflow.contains(":app:checkUnsignedDestructiveValidationCandidateEvidence"))
        assertTrue(workflow.contains("candidate_status=INELIGIBLE"))
        assertTrue(workflow.contains("trusted_expectation_minted=false"))
        assertTrue(workflow.contains("runtime_authorization=false"))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(workflow.contains("\${{ secrets"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_STORE_FILE:"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_STORE_PASSWORD:"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_KEY_ALIAS:"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_KEY_PASSWORD:"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_CERT_SHA256:"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(workflow.contains("assembleProductionRelease"))
        assertFalse(workflow.contains("bundleProductionRelease"))
        assertFalse(Regex("\\bemulator\\b").containsMatchIn(workflow))
        assertFalse(Regex("\\badb\\b").containsMatchIn(workflow))
        assertFalse(workflow.contains("set-device-owner"))
        assertFalse(workflow.contains("connectedAndroidTest"))
    }

    @Test
    fun `19F freeze tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            requireNotNull(System.getProperty("repoRoot")),
            "app/src/test/java/com/example/devicemanagement/security/Checkpoint19FWipeBoundaryFreezeTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
