package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19JWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 19J document stays closed after the audit-findings repair`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_19J_AUDIT_FINDINGS_REPAIR.md").readText()

        assertTrue(decision.contains("CHECKPOINT_19J_AUDIT_FINDINGS_REPAIRED = YES"))
        assertTrue(decision.contains("19J_CANDIDATE_TASK_SNAPSHOT_PATHS_ISOLATED = true"))
        assertTrue(decision.contains("19J_SNAPSHOT_CLEANUP_ENFORCED = true"))
        assertTrue(decision.contains("19J_ORDINARY_RELEASE_REMAINS_UNSIGNED = true"))
        assertTrue(decision.contains("19J_PRODUCTION_SIGNING_PERFORMED = false"))
        assertTrue(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(decision.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(decision.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(decision.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertFalse(decision.contains("19J_PRODUCTION_SIGNING_PERFORMED = true"))
        assertFalse(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
    }

    @Test
    fun `app production sources still have no 19J trigger or signing-gate reader`() {
        val appSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(appSources.contains("wipeData"))
        assertFalse(appSources.contains("wipeDevice"))
        assertFalse(appSources.contains("Checkpoint19JDecision"))
        assertFalse(appSources.contains("ProductionDistributionSigningGate"))
        assertFalse(appSources.contains("inspectWriteAndAssertCleanup"))
        assertFalse(appSources.contains("destructive-validation-explicit-candidate.txt"))
    }

    @Test
    fun `independent CI still refuses uploads secrets and hardware access after 19J`() {
        val workflow = File(
            requireNotNull(System.getProperty("repoRoot")),
            ".github/workflows/checkpoint-19e-independent-ci.yml",
        ).readText()
        assertTrue(workflow.contains("destructive-validation-unsigned-release-snapshot"))
        assertTrue(workflow.contains("contents: read"))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(workflow.contains("\${{ secrets"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(Regex("\\bemulator\\b").containsMatchIn(workflow))
        assertFalse(Regex("\\badb\\b").containsMatchIn(workflow))
    }

    @Test
    fun `19J freeze tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            requireNotNull(System.getProperty("repoRoot")),
            "app/src/test/java/com/example/devicemanagement/security/Checkpoint19JWipeBoundaryFreezeTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
