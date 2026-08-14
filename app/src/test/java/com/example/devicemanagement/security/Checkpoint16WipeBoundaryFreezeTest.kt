package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint16WipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 16 design documents exist and freeze the no-wipe contract`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val threatModel = File(docs, "WIPE_THREAT_MODEL.md").readText()
        val design = File(docs, "WIPE_DESIGN.md").readText()

        assertTrue(threatModel.contains("DENY / NO WIPE"))
        assertTrue(threatModel.contains("Malicious or untrusted trigger input"))
        assertTrue(threatModel.contains("Replay of a previous request"))
        assertTrue(threatModel.contains("Process death"))
        assertTrue(threatModel.contains("Clock manipulation"))
        assertTrue(threatModel.contains("Recovery-path abuse"))
        assertTrue(design.contains("NO REAL WIPE IS IMPLEMENTED"))
        assertTrue(design.contains("EXECUTION_COMMITTED"))
        assertTrue(design.contains("EXECUTION_INITIATED"))
        assertTrue(design.contains("DestructiveAuthorizationAuthority"))
        assertTrue(design.contains("DestructiveArmingAuthority"))
        assertTrue(design.contains("DestructiveFinalValidator"))
        assertTrue(design.contains("Unresolved items"))
        assertTrue(design.contains("Mandatory Checkpoint 17 entry criteria"))
        assertTrue(design.contains("setScreenCaptureDisabled"))
        assertTrue(design.contains("setCameraDisabled"))
        assertTrue(design.contains("setStatusBarDisabled"))
        assertTrue(design.contains("disable-camera"))
    }

    @Test
    fun `app production sources still have no destructive DPM invocation`() {
        val appSources = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(appSources.contains("wipeData"))
        assertFalse(appSources.contains("wipeDevice"))
        assertFalse(appSources.contains("DevicePolicyManager"))
        assertFalse(appSources.contains("MOCK_WIPE"))
        assertFalse(appSources.contains("mock_wipe"))
    }
}
