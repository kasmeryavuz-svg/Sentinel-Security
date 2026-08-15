package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19HDecisionRealityTest {
    @Test
    fun `19H records ceremony preparation without later destructive states`() {
        assertTrue(Checkpoint19HDecision.SIGNING_CEREMONY_CONTRACT_PRESENT)
        assertFalse(Checkpoint19HDecision.SIGNING_CEREMONY_READY)
        assertFalse(Checkpoint19HDecision.OFFLINE_KEY_GENERATED)
        assertFalse(Checkpoint19HDecision.PUBLIC_CERTIFICATE_SUPPLIED)
        assertFalse(Checkpoint19HDecision.EXPECTED_CERTIFICATE_RECORDED)
        assertFalse(Checkpoint19HDecision.OPERATOR_APPROVAL_AVAILABLE)
        assertFalse(Checkpoint19HDecision.WITNESS_APPROVAL_AVAILABLE)
        assertFalse(Checkpoint19HDecision.KEY_CUSTODY_APPROVED)
        assertFalse(Checkpoint19HDecision.RECOVERY_BACKUP_VERIFIED)
        assertFalse(Checkpoint19HDecision.BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED)
        assertFalse(Checkpoint19HDecision.PRODUCTION_ARTIFACT_SIGNED)
        assertFalse(Checkpoint19HDecision.SIGNED_VALIDATION_CANDIDATE_PRODUCED)
        assertFalse(Checkpoint19HDecision.TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED)
        assertFalse(Checkpoint19HDecision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint19HDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertFalse(Checkpoint19HDecision.CHECKPOINT_19H_USED_AS_RUNTIME_AUTHORIZATION)
        assertFalse(Checkpoint19HDecision.CEREMONY_PREPARATION_MINTS_TRUSTED_EXPECTATION)
        assertEquals("NO", Checkpoint19HDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
        assertEquals("false", Checkpoint19HDecision.recordedFlags["19H_SIGNING_CEREMONY_READY"])
        assertEquals("true", Checkpoint19HDecision.recordedFlags["19H_SIGNING_CEREMONY_CONTRACT_PRESENT"])
        assertFalse(Checkpoint19GDecision.CANDIDATE_ARTIFACT_ELIGIBLE)
        assertFalse(Checkpoint19FDecision.CANDIDATE_ARTIFACT_ELIGIBLE)
        assertFalse(Checkpoint19EDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
    }

    @Test
    fun `19H is not runtime authorization and cannot mint trust`() {
        val methods = Checkpoint19HDecision::class.java.declaredMethods
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
            "Checkpoint19HDecision",
            "Checkpoint19JDecision",
            "DestructiveSigningCeremonyPreparation",
            "checkDestructiveSigningCeremonyPreparation",
            "SigningCeremonyPreparationRecord",
        ).forEach { token ->
            assertFalse(token, composition.contains(token))
            assertFalse(token, container.contains(token))
            assertFalse(token, registry.contains(token))
            assertFalse(token, artifactSource.contains(token))
        }
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
        assertNull(ProductionDestructiveTrustedArtifactExpectationSource().trustedExpectation())
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertTrue(sources.contains("Checkpoint19HDecision"))
        assertTrue(sources.contains("Checkpoint19JDecision"))
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("DestructiveSigningCeremonyPreparation"))
    }

    @Test
    fun `19H document keeps the ceremony not ready`() {
        val docs = File("../docs/WIPE_19H_SIGNING_CEREMONY_PREPARATION.md").readText()
        assertTrue(docs.contains("CHECKPOINT_19H_SIGNING_CEREMONY_PREPARATION = YES"))
        assertTrue(docs.contains("19H_SIGNING_CEREMONY_READY = false"))
        assertTrue(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertFalse(docs.contains("19H_SIGNING_CEREMONY_READY = true"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        assertFalse(HEX_SHA256.containsMatchIn(
            File("src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19HDecision.kt")
                .readText(),
        ))
    }

    @Test
    fun `workflow proves ceremony preparation without hardware or signing`() {
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        assertTrue(workflow.contains(":app:checkDestructiveSigningCeremonyPreparation"))
        assertTrue(workflow.contains("ceremony_status=NOT_READY"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(workflow.contains("assembleProductionRelease"))
        assertFalse(Regex("\\bemulator\\b").containsMatchIn(workflow))
        assertFalse(Regex("\\badb\\b").containsMatchIn(workflow))
        assertFalse(workflow.contains("apksigner sign"))
        assertFalse(workflow.contains("jarsigner"))
    }

    @Test
    fun `19H tests themselves do not invoke the platform whole-device call`() {
        val thisFile = File(
            "src/test/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19HDecisionRealityTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("android.app." + "admin"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
