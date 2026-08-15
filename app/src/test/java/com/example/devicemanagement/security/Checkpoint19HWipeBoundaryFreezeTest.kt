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

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
