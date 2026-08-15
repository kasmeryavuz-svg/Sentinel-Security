package com.example.devicemanagement.destructive

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint17AEntryCriteriaTest {
    @Test
    fun `17A preflight matrix covers every Checkpoint 17 criterion without completing wipe rows`() {
        val docs = File("../docs")
        val preflight = File(docs, "WIPE_17A_PREFLIGHT.md").readText()
        val design = File(docs, "WIPE_DESIGN.md").readText()

        listOf(
            "Threat model reviewed",
            "Target binding using only proven DPC facts",
            "Separate anti-replay authorization domain",
            "Non-executing process-local arming",
            "Executor order:",
            "Deny-only cooldown / circuit breaker",
            "Audit semantics:",
            "Lifecycle: no boot path",
            "Destructive API semantics verified on intended OS",
            "Intended path = verified DO",
            "Build-time DPM allowlist change explicitly reviewed",
            "DeviceAdmin `wipe-data` explicitly reviewed",
            "Simulation tests without destructive APIs",
            "Destructive tests on disposable hardware",
            "Test-artifact certificate verification",
            "Production distribution still requires Checkpoint 15",
            "Explicit human approval before destructive hardware test",
            "Controlled registry still excludes",
            "Reversible `ApprovalAuthority` isolated",
            "Checkpoint 13–15 guards still pass",
        ).forEach { requirement ->
            assertTrue("missing $requirement", preflight.contains(requirement))
        }
        assertTrue(preflight.contains("What remains blocked for 17B"))
        assertTrue(preflight.contains("Do **not** treat destructive API, metadata, or hardware-test rows as complete"))
        assertTrue(preflight.contains("TESTED PERSISTENCE SEMANTICS"))
        assertTrue(preflight.contains("RUNTIME PERSISTENCE IMPLEMENTATION"))
        assertTrue(preflight.contains("test-only reconstruction adapter"))
        assertTrue(preflight.contains("17B entry review"))
        assertTrue(design.contains("## 14. Checkpoint 17A status"))
        assertTrue(design.contains("NO REAL WIPE IS IMPLEMENTED"))
        assertTrue(design.contains("TESTED PERSISTENCE SEMANTICS"))
        assertTrue(design.contains("RUNTIME PERSISTENCE IMPLEMENTATION"))
        val entryReview = File(docs, "WIPE_17B_ENTRY_REVIEW.md").readText()
        assertTrue(entryReview.contains("17B_DESTRUCTIVE_BOUNDARY_READY"))
        assertTrue(entryReview.contains("NO REAL WIPE IMPLEMENTED"))
    }

    @Test
    fun `compileSdk targetSdk and minSdk remain the researched coordinates`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        assertTrue(appGradle.contains("compileSdk = 36"))
        assertTrue(appGradle.contains("minSdk = 26"))
        assertTrue(appGradle.contains("targetSdk = 36"))
    }
}
