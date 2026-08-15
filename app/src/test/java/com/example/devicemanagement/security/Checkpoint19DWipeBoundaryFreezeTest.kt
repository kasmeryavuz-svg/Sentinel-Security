package com.example.devicemanagement.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19DWipeBoundaryFreezeTest {
    @Test
    fun `Checkpoint 19D document records assembly without runtime availability`() {
        val docs = File(requireNotNull(System.getProperty("repoRoot")), "docs")
        val decision = File(docs, "WIPE_19D_REAL_CHAIN_ASSEMBLY.md").readText()
        val checkpoint19C = File(docs, "WIPE_19C_HARDWARE_VALIDATION_READINESS.md").readText()

        assertTrue(decision.contains("REAL_CHAIN_ASSEMBLY_IMPLEMENTATION_APPROVED = YES"))
        assertTrue(decision.contains("REAL_CHAIN_ASSEMBLY_PATH_PRESENT = true"))
        assertTrue(decision.contains("19D_REAL_CHAIN_STRUCTURAL_ASSEMBLY_COMPLETE = YES"))
        assertTrue(decision.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(decision.contains("19D_CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(decision.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(decision.contains("PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false"))
        assertTrue(decision.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(decision.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(decision.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(decision.contains("1. Implementation approval"))
        assertTrue(decision.contains("2. Structural assembly present"))
        assertTrue(decision.contains("3. Runtime destructive availability"))
        assertTrue(decision.contains("4. Trusted artifact / signing readiness"))
        assertTrue(decision.contains("5. Per-attempt hardware confirmation readiness"))
        assertTrue(decision.contains("6. Hardware-test approval"))
        assertTrue(decision.contains("7. Hardware test performed"))
        assertTrue(decision.contains("8. GrapheneOS result verification"))
        assertTrue(decision.contains("Checkpoint 19D implements **state 2 only**"))
        assertTrue(decision.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(decision.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(decision.contains("DO NOT MERGE"))
        assertTrue(checkpoint19C.contains("REAL_DESTRUCTIVE_CHAIN_ASSEMBLED = false"))
        assertFalse(decision.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = true"))
        assertFalse(decision.contains("19D_CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
    }

    @Test
    fun `app production sources still have no destructive DPM real-chain or 19D trigger`() {
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
        assertFalse(appSources.contains("assembleAlreadyBoundDeviceFactoryReset"))
        assertFalse(appSources.contains("Checkpoint19DDecision"))
        assertFalse(appSources.contains("Checkpoint19CDecision"))
        assertFalse(appSources.contains("ProductionDestructiveRealChainOrchestrator"))
        assertFalse(appSources.contains("ProductionDestructiveHumanConfirmationSource"))
        assertFalse(appSources.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(appSources.contains("AndroidDevicePolicyFactoryResetService"))
        assertFalse(appSources.contains("AuthorizedFactoryResetPort"))
        assertFalse(appSources.contains("ProductionDestructiveRealChain"))
        assertFalse(appSources.contains("<wipe-data>"))
        assertFalse(appSources.contains("issueFromTrustedConfirmationSource"))
        assertFalse(appSources.contains("issueFromTrustedValidationSource"))
    }

    @Test
    fun `19D freeze tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            requireNotNull(System.getProperty("repoRoot")),
            "app/src/test/java/com/example/devicemanagement/security/Checkpoint19DWipeBoundaryFreezeTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
