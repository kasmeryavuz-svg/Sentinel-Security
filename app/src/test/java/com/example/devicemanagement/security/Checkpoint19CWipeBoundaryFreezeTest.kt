package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19CWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 19C document separates readiness from assembly signing and hardware`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_19C_HARDWARE_VALIDATION_READINESS.md").readText()
        val checkpoint19B = File(docs, "WIPE_19B_DESTRUCTIVE_IMPLEMENTATION.md").readText()

        assertTrue(decision.contains("19C_READINESS_MODEL_NON_CIRCULAR = YES"))
        assertTrue(decision.contains("19C_REAL_CHAIN_ASSEMBLY_APPROVAL_REQUEST_READY = YES"))
        assertTrue(decision.contains("19C_HARDWARE_VALIDATION_PREPARATION_READY = NO"))
        assertTrue(decision.contains("REAL_DESTRUCTIVE_CHAIN_ASSEMBLED = false"))
        assertTrue(decision.contains("PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED = false"))
        assertTrue(decision.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(decision.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(decision.contains("REAL_DESTRUCTIVE_EXECUTOR_PRESENT = true"))
        assertTrue(decision.contains("DESTRUCTIVE_POLICY_WRAPPER_PRESENT = true"))
        assertTrue(decision.contains("DESTRUCTIVE_METADATA_PRESENT = true"))
        assertTrue(decision.contains("wipeDevice(0) exact-zero enforcement = true"))
        assertTrue(decision.contains("1. Implementation present"))
        assertTrue(decision.contains("2. Real-chain assembly approval"))
        assertTrue(decision.contains("3. Real-chain assembly"))
        assertTrue(decision.contains("4. Artifact / signing approval"))
        assertTrue(decision.contains("5. Artifact identity recording"))
        assertTrue(decision.contains("6. Destructive hardware-test approval"))
        assertTrue(decision.contains("7. Destructive hardware test"))
        assertTrue(decision.contains("8. GrapheneOS validation"))
        assertTrue(decision.contains("NO NEW DESTRUCTIVE SCOPE ADDED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertTrue(decision.contains("The readiness model is not circular"))
        assertTrue(decision.contains("PREPARATION_READY = YES"))
        assertTrue(decision.contains("HARDWARE_TEST_APPROVAL_GRANTED = NO"))
        assertTrue(decision.contains("Approval, execution, and result verification happen **after** preparation"))
        assertTrue(decision.contains("Later states that are **not** preparation blockers"))
        assertFalse(decision.contains("10. GrapheneOS wipe behavior is not verified."))
        assertTrue(checkpoint19B.contains("REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION = false"))
        assertFalse(decision.contains("REAL_DESTRUCTIVE_CHAIN_ASSEMBLED = true"))
        assertFalse(decision.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = true"))
        assertFalse(decision.contains("19C_HARDWARE_VALIDATION_PREPARATION_READY = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
    }

    @Test
    fun `app production sources still have no destructive DPM real-chain or 19C wiring`() {
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
        assertFalse(appSources.contains("Checkpoint19CDecision"))
        assertFalse(appSources.contains("Checkpoint19DDecision"))
        assertFalse(appSources.contains("Checkpoint19EDecision"))
        assertFalse(appSources.contains("ProductionDestructiveRealChainOrchestrator"))
        assertFalse(appSources.contains("assembleAlreadyBoundDeviceFactoryReset"))
        assertFalse(appSources.contains("Checkpoint19BDecision"))
        assertFalse(appSources.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(appSources.contains("AndroidDevicePolicyFactoryResetService"))
        assertFalse(appSources.contains("AuthorizedFactoryResetPort"))
        assertFalse(appSources.contains("ProductionDestructiveRealChain"))
        assertFalse(appSources.contains("<wipe-data>"))
        assertFalse(appSources.contains("issueFromTrustedConfirmationSource"))
        assertFalse(appSources.contains("issueFromTrustedValidationSource"))
    }

    @Test
    fun `19C freeze tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            requireNotNull(System.getProperty("repoRoot")),
            "app/src/test/java/com/example/devicemanagement/security/Checkpoint19CWipeBoundaryFreezeTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
