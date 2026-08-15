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

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
