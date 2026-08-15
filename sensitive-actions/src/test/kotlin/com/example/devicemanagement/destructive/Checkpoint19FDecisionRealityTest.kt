package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19FDecisionRealityTest {
    @Test
    fun `19F records tooling without later destructive states`() {
        assertTrue(Checkpoint19FDecision.ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT)
        assertTrue(Checkpoint19FDecision.DISPOSABLE_DEVICE_CONTRACT_TEMPLATE_PRESENT)
        assertFalse(Checkpoint19FDecision.CANDIDATE_ARTIFACT_ELIGIBLE)
        assertFalse(Checkpoint19FDecision.REAL_DEVICE_IDENTITY_RECORDED)
        assertFalse(Checkpoint19FDecision.HARDWARE_VALIDATION_PREPARATION_READY)
        assertFalse(Checkpoint19FDecision.TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED)
        assertFalse(Checkpoint19FDecision.PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE)
        assertFalse(Checkpoint19FDecision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint19FDecision.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertFalse(Checkpoint19FDecision.DESTRUCTIVE_HARDWARE_TEST_PERFORMED)
        assertFalse(Checkpoint19FDecision.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertFalse(Checkpoint19FDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertFalse(Checkpoint19FDecision.CHECKPOINT_19F_USED_AS_RUNTIME_AUTHORIZATION)
        assertFalse(Checkpoint19FDecision.CANDIDATE_REPORT_IS_RUNTIME_AUTHORIZATION)
        assertFalse(Checkpoint19FDecision.CANDIDATE_EVIDENCE_MINTS_TRUSTED_EXPECTATION)
        assertEquals("NO", Checkpoint19FDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
        assertEquals("true", Checkpoint19FDecision.recordedFlags["19F_ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT"])
        assertEquals("true", Checkpoint19FDecision.recordedFlags["19F_DISPOSABLE_DEVICE_CONTRACT_TEMPLATE_PRESENT"])
        assertEquals(
            Checkpoint19FDecision.UNSIGNED_CANDIDATE_PROOF_PASSED.toString(),
            Checkpoint19FDecision.recordedFlags["19F_UNSIGNED_CANDIDATE_PROOF_PASSED"],
        )
        assertEquals("false", Checkpoint19FDecision.recordedFlags["19F_CANDIDATE_ARTIFACT_ELIGIBLE"])
        assertEquals("false", Checkpoint19FDecision.recordedFlags["19F_REAL_DEVICE_IDENTITY_RECORDED"])
        assertEquals("false", Checkpoint19FDecision.recordedFlags["19F_HARDWARE_VALIDATION_PREPARATION_READY"])
        assertEquals("false", Checkpoint19FDecision.recordedFlags["TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED"])
        assertEquals("NO", Checkpoint19FDecision.recordedFlags["CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET"])
        assertEquals(12, Checkpoint19FDecision.separateStatesThatMustNotBeInferred.size)
        assertFalse(Checkpoint19EDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertFalse(Checkpoint19DDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertEquals("NO", Checkpoint19EDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
    }

    @Test
    fun `19F is not runtime authorization and cannot mint trust from candidate evidence`() {
        val methods = Checkpoint19FDecision::class.java.declaredMethods
            .filter { it.name !in setOf("equals", "hashCode", "toString") }
            .map { it.name }
        assertFalse(methods.contains("authorize"))
        assertFalse(methods.contains("approve"))
        assertFalse(methods.contains("wipe"))
        assertFalse(methods.contains("execute"))
        assertFalse(methods.contains("confirm"))
        assertFalse(methods.contains("parse"))
        assertFalse(methods.contains("trust"))
        val composition = File(
            "../device-management/src/main/java/com/example/devicemanagement/management/" +
                "DeviceManagementSensitiveActions.kt",
        ).readText()
        val facade = File(
            "../device-management-api/src/main/kotlin/com/example/devicemanagement/management/" +
                "DeviceManagementApi.kt",
        ).readText()
        val container = File(
            "../app/src/main/java/com/example/devicemanagement/app/AppContainer.kt",
        ).readText()
        val registry = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionRegistry.kt",
        ).readText()
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        val artifactSource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveArtifactIdentity.kt",
        ).readText()
        val productionSeams = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveTrustedProductionSeams.kt",
        ).readText()
        listOf(
            "Checkpoint19FDecision",
            "Checkpoint19GDecision",
            "Checkpoint19HDecision",
            "DestructiveValidationCandidateEvidence",
            "destructive-validation-candidate.txt",
            "sentinel.destructiveValidationCandidateApk",
            "generateDestructiveValidationCandidateEvidence",
        ).forEach { token ->
            assertFalse(token, composition.contains(token))
            assertFalse(token, facade.contains(token))
            assertFalse(token, container.contains(token))
            assertFalse(token, registry.contains(token))
            assertFalse(token, controller.contains(token))
            assertFalse(token, artifactSource.contains(token))
            assertFalse(token, productionSeams.contains(token))
        }
        assertTrue(registry.contains("MOCK_WIPE must never be registered in controlled mode"))
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
        assertNull(ProductionDestructiveTrustedArtifactExpectationSource().trustedExpectation())
        assertTrue(TrustedPerAttemptDestructiveConfirmationRecord.current() == null)
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertTrue(sources.contains("Checkpoint19FDecision"))
        assertTrue(sources.contains("Checkpoint19GDecision"))
        assertTrue(sources.contains("Checkpoint19HDecision"))
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("import android.app." + "admin.DevicePolicyManager"))
        assertFalse(sources.contains("apk_sha256="))
        assertFalse(sources.contains("parseCandidate"))
    }

    @Test
    fun `candidate report cannot populate production null sources`() {
        val report = File("../app/build/reports/destructive-validation-candidate.txt")
        if (report.isFile) {
            val text = report.readText()
            assertTrue(text.contains("authority=UNTRUSTED_CANDIDATE_ONLY") || text.isNotBlank())
        }
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
        assertNull(ProductionDestructiveTrustedArtifactExpectationSource().trustedExpectation())
        assertFalse(Checkpoint19FDecision.CANDIDATE_EVIDENCE_MINTS_TRUSTED_EXPECTATION)
        assertFalse(Checkpoint19FDecision.TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED)
        assertFalse(Checkpoint19FDecision.CANDIDATE_ARTIFACT_ELIGIBLE)
        val mint = DestructiveArtifactIdentityExpectation.TrustedDestructiveArtifactExpectationMint
        val methods = mint::class.java.declaredMethods.map { it.name }
        assertFalse(methods.contains("issueFromCandidateReport"))
        assertFalse(methods.contains("issueFromCandidateEvidence"))
    }

    @Test
    fun `decision document keeps the twelve states separate and the contract unfilled`() {
        val docs = File("../docs/WIPE_19F_DISPOSABLE_DEVICE_EVIDENCE_CONTRACT.md").readText()
        assertTrue(docs.contains("19F_ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT = true"))
        assertTrue(docs.contains("19F_DISPOSABLE_DEVICE_CONTRACT_TEMPLATE_PRESENT = true"))
        assertTrue(docs.contains("19F_CANDIDATE_ARTIFACT_ELIGIBLE = false"))
        assertTrue(docs.contains("19F_REAL_DEVICE_IDENTITY_RECORDED = false"))
        assertTrue(docs.contains("19F_HARDWARE_VALIDATION_PREPARATION_READY = false"))
        assertTrue(docs.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(docs.contains("PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false"))
        assertTrue(docs.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false"))
        assertTrue(docs.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false"))
        assertTrue(docs.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false"))
        assertTrue(docs.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false"))
        assertTrue(docs.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("1. Candidate evidence tooling present"))
        assertTrue(docs.contains("2. Candidate report generated"))
        assertTrue(docs.contains("3. Candidate eligible"))
        assertTrue(docs.contains("4. Production signing approved"))
        assertTrue(docs.contains("5. Production signing enabled"))
        assertTrue(docs.contains("6. Exact artifact frozen and trusted"))
        assertTrue(docs.contains("7. Disposable device identified"))
        assertTrue(docs.contains("8. Hardware-validation preparation ready"))
        assertTrue(docs.contains("9. Hardware-test approval granted"))
        assertTrue(docs.contains("10. Per-attempt confirmation available"))
        assertTrue(docs.contains("11. Hardware test performed"))
        assertTrue(docs.contains("12. GrapheneOS behavior verified"))
        assertTrue(docs.contains("must **never** be inferred"))
        assertTrue(docs.contains("exact_device_serial = UNRECORDED"))
        assertTrue(docs.contains("apk_sha256 = UNRECORDED"))
        assertTrue(docs.contains("signing_certificate_sha256 = UNRECORDED"))
        assertTrue(docs.contains("hardware_validation_approval = UNRECORDED"))
        assertTrue(docs.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(docs.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertFalse(docs.contains("19F_CANDIDATE_ARTIFACT_ELIGIBLE = true"))
        assertFalse(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        val decisionSource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19FDecision.kt",
        ).readText()
        assertFalse(HEX_SHA256.containsMatchIn(decisionSource))
        assertEquals(
            Checkpoint19FDecision.UNSIGNED_CANDIDATE_PROOF_PASSED,
            docs.contains("19F_UNSIGNED_CANDIDATE_PROOF_PASSED = true") &&
                !docs.contains("19F_UNSIGNED_CANDIDATE_PROOF_PASSED = false"),
        )
        assertEquals(
            Checkpoint19FDecision.UNSIGNED_CANDIDATE_PROOF_PASSED.toString(),
            Checkpoint19FDecision.recordedFlags.getValue("19F_UNSIGNED_CANDIDATE_PROOF_PASSED"),
        )
    }

    @Test
    fun `ordinary debug and release tasks remain incapable of completing a factory reset`() {
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        assertTrue(workflow.contains(":app:checkUnsignedDestructiveValidationCandidateEvidence"))
        assertFalse(workflow.contains("checkProductionDistributionSigning"))
        assertFalse(workflow.contains("assembleProductionRelease"))
        assertFalse(Checkpoint19FDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertEquals("NO", Checkpoint19FDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
        assertFalse(TrustedDestructiveArtifactValidationSource.trustedExpectation() != null)
        assertTrue(TrustedPerAttemptDestructiveConfirmationRecord.current() == null)
        val manifests = listOf(
            File("../app/src/main/AndroidManifest.xml"),
            File("../device-management/src/main/AndroidManifest.xml"),
        )
        manifests.forEach { file ->
            val text = file.readText()
            assertFalse(file.path, text.contains("assembleAlreadyBoundDeviceFactoryReset"))
            assertFalse(file.path, text.contains("Checkpoint19FDecision"))
            assertFalse(file.path, text.contains("BOOT_COMPLETED"))
        }
    }

    @Test
    fun `19F tests themselves do not invoke the platform whole-device call`() {
        val thisFile = File(
            "src/test/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19FDecisionRealityTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("android.app." + "admin"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
