package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint17AWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 17A documents exist and keep the no-wipe contract`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val preflight = File(docs, "WIPE_17A_PREFLIGHT.md").readText()
        val platform = File(docs, "WIPE_PLATFORM_PREFLIGHT.md").readText()

        assertTrue(preflight.contains("NO REAL WIPE IS IMPLEMENTED"))
        assertTrue(preflight.contains("NO WIPE-DATA METADATA WAS ADDED"))
        assertTrue(preflight.contains("NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED"))
        assertTrue(preflight.contains("Entry-criteria matrix"))
        assertTrue(preflight.contains("17A versus 17B"))
        assertTrue(preflight.contains("setScreenCaptureDisabled"))
        assertTrue(preflight.contains("disable-camera"))
        assertTrue(preflight.contains("checkpoint17BForbiddenDpmMethodNames"))
        val entryReview = File(docs, "WIPE_17B_ENTRY_REVIEW.md").readText()
        assertTrue(entryReview.contains("17B_DESTRUCTIVE_BOUNDARY_READY = NO"))
        assertTrue(entryReview.contains("NO REAL WIPE IMPLEMENTED"))
        assertTrue(entryReview.contains("DO NOT MERGE"))
        assertTrue(entryReview.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED = false"))
        assertTrue(entryReview.contains("REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED = false"))
        assertTrue(entryReview.contains("REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED = false"))
        assertTrue(entryReview.contains("REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED = false"))
        assertTrue(entryReview.contains("DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false"))
        assertTrue(platform.contains("VERIFIED_ANDROID"))
        assertTrue(platform.contains("VERIFIED_GRAPHENEOS"))
        assertTrue(platform.contains("REPO_PROVEN"))
        assertTrue(platform.contains("UNRESOLVED_REQUIRES_DEVICE_TEST"))
        assertTrue(platform.contains("wipeDevice"))
        assertTrue(platform.contains("USES_POLICY_WIPE_DATA"))
        assertFalse(platform.contains("forum posts are authoritative"))
    }

    @Test
    fun `app production sources still have no destructive DPM invocation or 17A authorities`() {
        val appSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(appSources.contains("wipeData"))
        assertFalse(appSources.contains("wipeDevice"))
        assertFalse(appSources.contains("DevicePolicyManager"))
        assertFalse(appSources.contains("DestructiveArmingAuthority"))
        assertFalse(appSources.contains("DestructiveAttemptAdmissionAuthority"))
        assertFalse(appSources.contains("SimulatedDestructiveExecutor"))
        assertFalse(appSources.contains("FinalExecutionPermit"))
        assertFalse(appSources.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(appSources.contains("assembleAndHandoff"))
        assertFalse(appSources.contains("wipeDevice"))
    }
}
