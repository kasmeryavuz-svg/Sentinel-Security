package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Current app-module wipe-boundary and CI-refusal invariants.
 *
 * Checkpoint-specific historical documentation stays in thin 19G/19H/19J
 * freeze tests. This class must not be cloned per checkpoint.
 */
class CurrentProductionWipeBoundaryInvariantTest {
    @Test
    fun `app production sources have no wipe invocation or destructive trigger`() {
        val appSources = productionSources()
        assertFalse(appSources.contains("wipeData"))
        assertFalse(appSources.contains("wipeDevice"))
        assertFalse(appSources.contains("DevicePolicyManager"))
        assertFalse(appSources.contains("<wipe-data>"))
        assertFalse(appSources.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(appSources.contains("assembleAndHandoff"))
        assertFalse(appSources.contains("assembleAlreadyBoundDeviceFactoryReset"))
        assertFalse(appSources.contains("ProductionDestructiveRealChainOrchestrator"))
        assertFalse(appSources.contains("ProductionDestructiveHumanConfirmationSource"))
        assertFalse(appSources.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(appSources.contains("AndroidDevicePolicyFactoryResetService"))
        assertFalse(appSources.contains("AuthorizedFactoryResetPort"))
        assertFalse(appSources.contains("ProductionDestructiveRealChain"))
        assertFalse(appSources.contains("issueFromTrustedConfirmationSource"))
        assertFalse(appSources.contains("issueFromTrustedValidationSource"))
    }

    @Test
    fun `app production sources stay isolated from checkpoint and proof types`() {
        val appSources = productionSources()
        listOf(
            "Checkpoint19ADecision",
            "Checkpoint19BDecision",
            "Checkpoint19CDecision",
            "Checkpoint19DDecision",
            "Checkpoint19EDecision",
            "Checkpoint19FDecision",
            "Checkpoint19GDecision",
            "Checkpoint19HDecision",
            "Checkpoint19JDecision",
            "Checkpoint19PGovernanceObservation",
            "DestructiveValidationCandidateEvidence",
            "DestructiveSigningCeremonyPreparation",
            "ProductionDistributionSigningGate",
            "ValidationOnlySigningGate",
            "ValidationOnlySignedCandidateEvidence",
            "IndependentWitnessVerification",
            "IndependentWitnessStatement",
            "IndependentWitnessAuthorityContract",
            "IndependentWitnessAuthorityEnrollment",
            "IndependentWitnessAuthorityEnrollmentPreparation",
            "inspectWriteAndAssertCleanup",
            "DESTRUCTIVE_VALIDATION_BUILD_PURPOSE",
            "checkUnsignedDisposableValidationBuildPurposeEvidence",
            "checkDestructiveSigningCeremonyPreparation",
            "destructive-validation-disposable-purpose.txt",
            "destructive-validation-candidate.txt",
            "destructive-validation-explicit-candidate.txt",
            "destructive-signing-ceremony-preparation.txt",
            "sentinel.destructiveValidationCandidateApk",
            "apk_sha256=",
        ).forEach { token ->
            assertFalse(token, appSources.contains(token))
        }
    }

    @Test
    fun `independent CI refuses signing secrets uploads and hardware access`() {
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
        assertTrue(workflow.contains(":app:checkUnsignedDisposableValidationBuildPurposeEvidence"))
        assertTrue(workflow.contains(":app:checkDestructiveSigningCeremonyPreparation"))
        assertTrue(workflow.contains(":app:checkIndependentWitnessVerificationContract"))
        assertTrue(workflow.contains(":app:checkIndependentWitnessAuthorityEnrollmentPreparation"))
        assertTrue(workflow.contains("independent_witness_approval=false"))
        assertTrue(workflow.contains("witness_independence_established=false"))
        assertTrue(workflow.contains("witness_authority_enrolled=false"))
        assertTrue(workflow.contains("enrollment_authorizes_wipe=false"))
        assertFalse(workflow.contains("verifyIndependentWitnessStatement"))
        assertFalse(workflow.contains("recordSignedDisposableValidationCandidateReceipt"))
        assertTrue(workflow.contains("candidate_status=INELIGIBLE"))
        assertTrue(workflow.contains("trusted_expectation_minted=false"))
        assertTrue(workflow.contains("runtime_authorization=false"))
        assertTrue(workflow.contains("ceremony_status=NOT_READY"))
        assertTrue(workflow.contains("destructive-validation-unsigned-release-snapshot"))
        assertTrue(workflow.contains("signed-disposable-validation-snapshot"))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(workflow.contains("\${{ secrets"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_STORE_FILE:"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_STORE_PASSWORD:"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_KEY_ALIAS:"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_KEY_PASSWORD:"))
        assertFalse(workflow.contains("SENTINEL_RELEASE_CERT_SHA256:"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_STORE_FILE:"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_STORE_PASSWORD:"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_KEY_ALIAS:"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_KEY_PASSWORD:"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_CERT_SHA256:"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(workflow.contains("assembleProductionRelease"))
        assertFalse(workflow.contains("bundleProductionRelease"))
        assertFalse(workflow.contains("assembleSignedDisposableValidation"))
        assertFalse(workflow.contains("checkSignedDisposableValidation"))
        assertFalse(Regex("\\bemulator\\b").containsMatchIn(workflow))
        assertFalse(Regex("\\badb\\b").containsMatchIn(workflow))
        assertFalse(workflow.contains("set-device-owner"))
        assertFalse(workflow.contains("connectedAndroidTest"))
    }

    @Test
    fun `current invariant tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            requireNotNull(System.getProperty("repoRoot")),
            "app/src/test/java/com/example/devicemanagement/security/" +
                "CurrentProductionWipeBoundaryInvariantTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
        assertFalse(thisFile.contains("Checkpoint19P" + "WipeBoundaryFreezeTest"))
    }

    private fun productionSources(): String {
        return File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }
    }
}
