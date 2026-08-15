package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19AWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 19A document separates five states and does not record approval`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md").readText()
        val checkpoint18 = File(docs, "WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md").readText()

        assertTrue(decision.contains("19_DESTRUCTIVE_IMPLEMENTATION_APPROVAL_REQUEST_READY = YES"))
        assertTrue(decision.contains("1. Architecture readiness"))
        assertTrue(decision.contains("2. Approval request readiness"))
        assertTrue(decision.contains("3. Actual destructive approval"))
        assertTrue(decision.contains("4. Destructive implementation"))
        assertTrue(decision.contains("5. Destructive hardware validation"))
        assertTrue(decision.contains("DESTRUCTIVE_IMPLEMENTATION_APPROVED = NO"))
        assertTrue(checkpoint18.contains("18_ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL = YES"))
        assertTrue(decision.contains("factory-resetting the dedicated disposable Sentinel test device"))
        assertTrue(decision.contains("wipeDevice"))
        assertTrue(decision.contains("wipeData"))
        assertTrue(decision.contains("<wipe-data>"))
        assertTrue(decision.contains("USER_SCOPED_WIPE"))
        assertTrue(decision.contains("WIPE_SILENTLY"))
        assertTrue(decision.contains("WIPE_RESET_PROTECTION_DATA"))
        assertTrue(decision.contains("WIPE_EUICC"))
        assertTrue(decision.contains("Do not implement any of A–H"))
        assertTrue(decision.contains("REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false"))
        assertTrue(decision.contains("DESTRUCTIVE_POLICY_WRAPPER_PRESENT = false"))
        assertTrue(decision.contains("DESTRUCTIVE_METADATA_PRESENT = false"))
        assertTrue(decision.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false"))
        assertTrue(decision.contains("DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false"))
        assertTrue(decision.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(decision.contains("NO REAL WIPE IMPLEMENTED"))
        assertTrue(decision.contains("NO WIPE-DATA METADATA ADDED"))
        assertTrue(decision.contains("NO DESTRUCTIVE HARDWARE TEST PERFORMED"))
        assertTrue(decision.contains("NO DESTRUCTIVE APPROVAL RECORDED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertFalse(decision.contains("DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = true"))
        assertFalse(decision.contains("DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = true"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
    }

    @Test
    fun `app production sources still have no destructive DPM real-chain or 19A wiring`() {
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
        assertFalse(appSources.contains("Checkpoint19ADecision"))
        assertFalse(appSources.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(appSources.contains("AndroidDevicePolicyFactoryResetService"))
        assertFalse(appSources.contains("<wipe-data>"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
