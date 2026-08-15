package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19HWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 19H document stays closed after ceremony preparation`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_19H_SIGNING_CEREMONY_PREPARATION.md").readText()

        assertTrue(decision.contains("CHECKPOINT_19H_SIGNING_CEREMONY_PREPARATION = YES"))
        assertTrue(decision.contains("19H_SIGNING_CEREMONY_CONTRACT_PRESENT = true"))
        assertTrue(decision.contains("19H_SIGNING_CEREMONY_READY = false"))
        assertTrue(decision.contains("19H_SIGNED_VALIDATION_CANDIDATE_PRODUCED = false"))
        assertTrue(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(decision.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(decision.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(decision.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertFalse(decision.contains("19H_SIGNING_CEREMONY_READY = true"))
        assertFalse(decision.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
    }

    @Test
    fun `app production sources still have no 19H trigger or ceremony reader`() {
        val appSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(appSources.contains("wipeData"))
        assertFalse(appSources.contains("wipeDevice"))
        assertFalse(appSources.contains("Checkpoint19HDecision"))
        assertFalse(appSources.contains("Checkpoint19GDecision"))
        assertFalse(appSources.contains("DestructiveSigningCeremonyPreparation"))
        assertFalse(appSources.contains("checkDestructiveSigningCeremonyPreparation"))
        assertFalse(appSources.contains("destructive-signing-ceremony-preparation.txt"))
    }

    @Test
    fun `independent CI still refuses uploads secrets and hardware access after 19H`() {
        val workflow = File(
            requireNotNull(System.getProperty("repoRoot")),
            ".github/workflows/checkpoint-19e-independent-ci.yml",
        ).readText()
        assertTrue(workflow.contains(":app:checkDestructiveSigningCeremonyPreparation"))
        assertTrue(workflow.contains("contents: read"))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(workflow.contains("\${{ secrets"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(Regex("\\bemulator\\b").containsMatchIn(workflow))
        assertFalse(Regex("\\badb\\b").containsMatchIn(workflow))
    }

    @Test
    fun `19H freeze tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            requireNotNull(System.getProperty("repoRoot")),
            "app/src/test/java/com/example/devicemanagement/security/Checkpoint19HWipeBoundaryFreezeTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
