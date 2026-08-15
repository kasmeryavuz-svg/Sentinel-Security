package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint18WipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 18 decision document separates architecture from Android approval`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md").readText()
        val entryReview = File(docs, "WIPE_17B_ENTRY_REVIEW.md").readText()

        assertTrue(decision.contains("18_ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL = YES"))
        assertTrue(decision.contains("A. architecture readiness"))
        assertTrue(decision.contains("B. Android API implementation approval"))
        assertTrue(decision.contains("C. metadata approval"))
        assertTrue(decision.contains("D. production signing approval"))
        assertTrue(decision.contains("E. disposable hardware test approval"))
        assertTrue(decision.contains("targetSdk"))
        assertTrue(decision.contains("wipeDevice"))
        assertTrue(decision.contains("wipeData as Sentinel whole-device route"))
        assertTrue(decision.contains("<wipe-data>"))
        assertTrue(decision.contains("GrapheneOS"))
        assertTrue(decision.contains("NO REAL WIPE IMPLEMENTED"))
        assertTrue(decision.contains("NO WIPE-DATA METADATA ADDED"))
        assertTrue(decision.contains("NO DESTRUCTIVE HARDWARE TEST PERFORMED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertTrue(decision.contains("DESTRUCTIVE_EXECUTOR_CONTRACT_PRESENT = true"))
        assertTrue(decision.contains("REAL_CHAIN_RUNTIME_DURABILITY_REQUIRED = true"))
        assertTrue(decision.contains("REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false"))
        assertTrue(decision.contains("DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false"))
        assertTrue(entryReview.contains("17B_DESTRUCTIVE_BOUNDARY_READY = NO"))
    }

    @Test
    fun `app production sources still have no destructive DPM or real-chain wiring`() {
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
        assertFalse(appSources.contains("FutureDestructiveExecutorContract"))
        assertFalse(appSources.contains("SimulatedDestructiveExecutor"))
    }
}
