package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19GDecisionRealityTest {
    @Test
    fun `19G records observable purpose without later destructive states`() {
        assertTrue(Checkpoint19GDecision.DISPOSABLE_VALIDATION_VARIANT_PRESENT)
        assertTrue(Checkpoint19GDecision.BUILD_PURPOSE_OBSERVABLE)
        assertFalse(Checkpoint19GDecision.CANDIDATE_ARTIFACT_ELIGIBLE)
        assertFalse(Checkpoint19GDecision.REAL_DEVICE_IDENTITY_RECORDED)
        assertFalse(Checkpoint19GDecision.HARDWARE_VALIDATION_PREPARATION_READY)
        assertFalse(Checkpoint19GDecision.TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED)
        assertFalse(Checkpoint19GDecision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint19GDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertFalse(Checkpoint19GDecision.CHECKPOINT_19G_USED_AS_RUNTIME_AUTHORIZATION)
        assertFalse(Checkpoint19GDecision.OBSERVED_BUILD_PURPOSE_IS_RUNTIME_AUTHORIZATION)
        assertFalse(Checkpoint19GDecision.CANDIDATE_EVIDENCE_MINTS_TRUSTED_EXPECTATION)
        assertEquals("NO", Checkpoint19GDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
        assertEquals("false", Checkpoint19GDecision.recordedFlags["19G_CANDIDATE_ARTIFACT_ELIGIBLE"])
        assertEquals("true", Checkpoint19GDecision.recordedFlags["19G_BUILD_PURPOSE_OBSERVABLE"])
        assertFalse(Checkpoint19FDecision.CANDIDATE_ARTIFACT_ELIGIBLE)
        assertFalse(Checkpoint19EDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
    }

    @Test
    fun `19G is not runtime authorization and cannot mint trust`() {
        val methods = Checkpoint19GDecision::class.java.declaredMethods
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
            "Checkpoint19GDecision",
            "DESTRUCTIVE_VALIDATION_BUILD_PURPOSE",
            "checkUnsignedDisposableValidationBuildPurposeEvidence",
            "disposableValidation",
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
        assertTrue(sources.contains("Checkpoint19GDecision"))
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("DESTRUCTIVE_VALIDATION_BUILD_PURPOSE"))
    }

    @Test
    fun `19G document keeps the candidate ineligible`() {
        val docs = File("../docs/WIPE_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE.md").readText()
        assertTrue(docs.contains("CHECKPOINT_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE = YES"))
        assertTrue(docs.contains("19G_CANDIDATE_ARTIFACT_ELIGIBLE = false"))
        assertTrue(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertFalse(docs.contains("19G_CANDIDATE_ARTIFACT_ELIGIBLE = true"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        assertFalse(HEX_SHA256.containsMatchIn(
            File("src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19GDecision.kt")
                .readText(),
        ))
    }

    @Test
    fun `workflow proves the dedicated unsigned APK without hardware access`() {
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        assertTrue(workflow.contains(":app:checkUnsignedDisposableValidationBuildPurposeEvidence"))
        assertTrue(workflow.contains("build_purpose_observed=DISPOSABLE_DEVICE_VALIDATION"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(workflow.contains("assembleProductionRelease"))
        assertFalse(Regex("\\bemulator\\b").containsMatchIn(workflow))
        assertFalse(Regex("\\badb\\b").containsMatchIn(workflow))
    }

    @Test
    fun `19G tests themselves do not invoke the platform whole-device call`() {
        val thisFile = File(
            "src/test/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19GDecisionRealityTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("android.app." + "admin"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
