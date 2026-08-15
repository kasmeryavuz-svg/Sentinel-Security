package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19BWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 19B document records approval without hardware validation`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_19B_DESTRUCTIVE_IMPLEMENTATION.md").readText()
        val checkpoint19A = File(docs, "WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md").readText()

        assertTrue(decision.contains("DESTRUCTIVE_IMPLEMENTATION_APPROVED = YES"))
        assertTrue(decision.contains("DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = true"))
        assertTrue(decision.contains("DESTRUCTIVE_IMPLEMENTATION_PRESENT = true"))
        assertTrue(decision.contains("REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION = false"))
        assertTrue(decision.contains("wipeDevice(0)"))
        assertTrue(decision.contains("NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertTrue(checkpoint19A.contains("DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false"))
        assertFalse(decision.contains("DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = true"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
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
        assertFalse(appSources.contains("Checkpoint19BDecision"))
        assertFalse(appSources.contains("Checkpoint19CDecision"))
        assertFalse(appSources.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(appSources.contains("AndroidDevicePolicyFactoryResetService"))
        assertFalse(appSources.contains("AuthorizedFactoryResetPort"))
        assertFalse(appSources.contains("<wipe-data>"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
