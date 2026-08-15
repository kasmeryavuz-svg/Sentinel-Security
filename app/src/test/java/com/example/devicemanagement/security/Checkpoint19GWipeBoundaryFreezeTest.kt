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

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
