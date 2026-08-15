package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19GWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 19G document stays closed after observable purpose`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE.md").readText()

        assertTrue(decision.contains("CHECKPOINT_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE = YES"))
        assertTrue(decision.contains("19G_DISPOSABLE_VALIDATION_VARIANT_PRESENT = true"))
        assertTrue(decision.contains("19G_BUILD_PURPOSE_OBSERVABLE = true"))
        assertTrue(decision.contains("19G_CANDIDATE_ARTIFACT_ELIGIBLE = false"))
        assertTrue(decision.contains("19G_REAL_DEVICE_IDENTITY_RECORDED = false"))
        assertTrue(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(decision.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(decision.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(decision.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertFalse(decision.contains("19G_CANDIDATE_ARTIFACT_ELIGIBLE = true"))
        assertFalse(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
    }

    @Test
    fun `app production sources still have no 19G trigger or metadata reader`() {
        val appSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(appSources.contains("wipeData"))
        assertFalse(appSources.contains("wipeDevice"))
        assertFalse(appSources.contains("Checkpoint19GDecision"))
        assertFalse(appSources.contains("Checkpoint19HDecision"))
        assertFalse(appSources.contains("Checkpoint19JDecision"))
        assertFalse(appSources.contains("Checkpoint19FDecision"))
        assertFalse(appSources.contains("DESTRUCTIVE_VALIDATION_BUILD_PURPOSE"))
        assertFalse(appSources.contains("destructive-validation-disposable-purpose.txt"))
        assertFalse(appSources.contains("checkUnsignedDisposableValidationBuildPurposeEvidence"))
        assertFalse(appSources.contains("DestructiveValidationCandidateEvidence"))
    }

    @Test
    fun `independent CI still refuses uploads secrets and hardware access after 19G`() {
        val workflow = File(
            requireNotNull(System.getProperty("repoRoot")),
            ".github/workflows/checkpoint-19e-independent-ci.yml",
        ).readText()
        assertTrue(workflow.contains(":app:checkUnsignedDisposableValidationBuildPurposeEvidence"))
        assertTrue(workflow.contains("contents: read"))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(workflow.contains("\${{ secrets"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(Regex("\\bemulator\\b").containsMatchIn(workflow))
        assertFalse(Regex("\\badb\\b").containsMatchIn(workflow))
    }

    @Test
    fun `19G freeze tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            requireNotNull(System.getProperty("repoRoot")),
            "app/src/test/java/com/example/devicemanagement/security/Checkpoint19GWipeBoundaryFreezeTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
