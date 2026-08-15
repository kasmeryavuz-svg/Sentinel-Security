package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19JDecisionRealityTest {
    @Test
    fun `19J records infrastructure repair without later destructive states`() {
        assertEquals("YES", Checkpoint19JDecision.AUDIT_FINDINGS_REPAIRED)
        assertTrue(Checkpoint19JDecision.CANDIDATE_TASK_SNAPSHOT_PATHS_ISOLATED)
        assertTrue(Checkpoint19JDecision.CANDIDATE_TASK_REPORT_PATHS_ISOLATED)
        assertTrue(Checkpoint19JDecision.SNAPSHOT_CLEANUP_ENFORCED)
        assertTrue(Checkpoint19JDecision.ORDINARY_RELEASE_REMAINS_UNSIGNED)
        assertTrue(Checkpoint19JDecision.PRODUCTION_SIGNING_REQUIRES_EXPLICIT_DISTRIBUTION_REQUEST)
        assertFalse(Checkpoint19JDecision.PRODUCTION_SIGNING_PERFORMED)
        assertFalse(Checkpoint19JDecision.SIGNED_VALIDATION_CANDIDATE_PRODUCED)
        assertFalse(Checkpoint19JDecision.TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED)
        assertFalse(Checkpoint19JDecision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint19JDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertFalse(Checkpoint19JDecision.CHECKPOINT_19J_USED_AS_RUNTIME_AUTHORIZATION)
        assertFalse(Checkpoint19JDecision.REPAIR_MINTS_TRUSTED_EXPECTATION)
        assertEquals("NO", Checkpoint19JDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
        assertEquals("true", Checkpoint19JDecision.recordedFlags["19J_CANDIDATE_TASK_SNAPSHOT_PATHS_ISOLATED"])
        assertEquals("false", Checkpoint19JDecision.recordedFlags["19J_PRODUCTION_SIGNING_PERFORMED"])
        assertFalse(Checkpoint19HDecision.SIGNING_CEREMONY_READY)
        assertFalse(Checkpoint19GDecision.CANDIDATE_ARTIFACT_ELIGIBLE)
        assertFalse(Checkpoint19FDecision.CANDIDATE_ARTIFACT_ELIGIBLE)
    }

    @Test
    fun `19J is not runtime authorization and cannot mint trust`() {
        val methods = Checkpoint19JDecision::class.java.declaredMethods
            .filter { it.name !in setOf("equals", "hashCode", "toString") }
            .map { it.name }
        assertFalse(methods.contains("authorize"))
        assertFalse(methods.contains("approve"))
        assertFalse(methods.contains("wipe"))
        assertFalse(methods.contains("execute"))
        assertFalse(methods.contains("parse"))
        assertFalse(methods.contains("trust"))
        val composition = File(
            "../device-management/src/main/java/com/example/devicemanagement/management/" +
                "DeviceManagementSensitiveActions.kt",
        ).readText()
        val container = File(
            "../app/src/main/java/com/example/devicemanagement/app/AppContainer.kt",
        ).readText()
        val registry = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionRegistry.kt",
        ).readText()
        val artifactSource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveArtifactIdentity.kt",
        ).readText()
        listOf(
            "Checkpoint19JDecision",
            "ProductionDistributionSigningGate",
            "inspectWriteAndAssertCleanup",
        ).forEach { token ->
            assertFalse(token, composition.contains(token))
            assertFalse(token, container.contains(token))
            assertFalse(token, registry.contains(token))
            assertFalse(token, artifactSource.contains(token))
        }
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
        assertNull(ProductionDestructiveTrustedArtifactExpectationSource().trustedExpectation())
        assertTrue(TrustedPerAttemptDestructiveConfirmationRecord.current() == null)
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertTrue(sources.contains("Checkpoint19JDecision"))
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("ProductionDistributionSigningGate"))
    }

    @Test
    fun `19J document keeps the repair closed`() {
        val docs = File("../docs/WIPE_19J_AUDIT_FINDINGS_REPAIR.md").readText()
        assertTrue(docs.contains("CHECKPOINT_19J_AUDIT_FINDINGS_REPAIRED = YES"))
        assertTrue(docs.contains("19J_CANDIDATE_TASK_SNAPSHOT_PATHS_ISOLATED = true"))
        assertTrue(docs.contains("19J_SNAPSHOT_CLEANUP_ENFORCED = true"))
        assertTrue(docs.contains("19J_ORDINARY_RELEASE_REMAINS_UNSIGNED = true"))
        assertTrue(docs.contains("19J_PRODUCTION_SIGNING_REQUIRES_EXPLICIT_DISTRIBUTION_REQUEST = true"))
        assertTrue(docs.contains("19J_PRODUCTION_SIGNING_PERFORMED = false"))
        assertTrue(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertFalse(docs.contains("19J_PRODUCTION_SIGNING_PERFORMED = true"))
        assertFalse(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        assertFalse(
            HEX_SHA256.containsMatchIn(
                File("src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19JDecision.kt")
                    .readText(),
            ),
        )
    }

    @Test
    fun `workflow proves isolated snapshots without hardware or signing`() {
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        assertTrue(workflow.contains("destructive-validation-explicit-candidate-snapshot"))
        assertTrue(workflow.contains("destructive-validation-unsigned-release-snapshot"))
        assertTrue(workflow.contains("destructive-validation-disposable-purpose-snapshot"))
        assertTrue(workflow.contains("ceremony_status=NOT_READY"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(workflow.contains("assembleProductionRelease"))
        assertFalse(Regex("\\bemulator\\b").containsMatchIn(workflow))
        assertFalse(Regex("\\badb\\b").containsMatchIn(workflow))
        assertFalse(workflow.contains("apksigner sign"))
        assertFalse(workflow.contains("jarsigner"))
    }

    @Test
    fun `19J tests themselves do not invoke the platform whole-device call`() {
        val thisFile = File(
            "src/test/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19JDecisionRealityTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("android.app." + "admin"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
