import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Checkpoint19FValidationEvidenceFreezeTest {
    @Test
    fun `candidate tooling lives only in build and Gradle infrastructure`() {
        val evidence = File("src/main/kotlin/DestructiveValidationCandidateEvidence.kt").readText()
        val expected = File("src/main/kotlin/DestructiveValidationExpectedIdentity.kt").readText()
        val inspectors = File("src/main/kotlin/DestructiveValidationCandidateInspectors.kt").readText()
        val tasks = File("src/main/kotlin/DestructiveValidationCandidateEvidenceTask.kt").readText()
        val appGradle = File("../app/build.gradle.kts").readText()

        assertTrue(evidence.contains("UNTRUSTED_CANDIDATE_ONLY"))
        assertTrue(evidence.contains("SIGNED_UNCLASSIFIED"))
        assertFalse(evidence.contains("PRODUCTION_SIGNED"))
        assertTrue(evidence.contains("build_purpose_expected"))
        assertTrue(evidence.contains("build_purpose_observed"))
        assertTrue(evidence.contains("inspection_git_revision"))
        assertTrue(evidence.contains("inspection_revision_proves_apk_origin"))
        assertTrue(evidence.contains("immutable-candidate-snapshot.apk"))
        assertTrue(evidence.contains("NOFOLLOW_LINKS"))
        assertTrue(evidence.contains("sentinel.destructiveValidationCandidateApk"))
        assertTrue(evidence.contains(":app:generateDestructiveValidationCandidateEvidence"))
        assertTrue(evidence.contains(":app:checkUnsignedDestructiveValidationCandidateEvidence"))
        assertTrue(File("src/main/kotlin/DestructiveValidationApksignerSignerParser.kt").isFile)
        assertTrue(expected.contains("expectedCertificateSha256 = null"))
        assertFalse(expected.contains("SENTINEL_RELEASE_CERT_SHA256"))
        assertFalse(inspectors.contains("SENTINEL_RELEASE_STORE"))
        assertFalse(inspectors.contains("SENTINEL_RELEASE_CERT_SHA256"))
        assertFalse(inspectors.contains("PRODUCTION_SIGNED"))
        assertTrue(inspectors.contains("classifyOfficialApksignerOutput"))
        assertTrue(inspectors.contains("inspectObservedBuildPurpose"))
        assertTrue(tasks.contains("supplied explicitly"))
        assertTrue(tasks.contains("never auto-selects a build output"))
        assertTrue(tasks.contains("statusLinesWithoutDigest"))
        assertFalse(tasks.contains("apksigner sign"))
        assertFalse(tasks.contains("zipalign"))
        assertTrue(appGradle.contains("generateDestructiveValidationCandidateEvidence"))
        assertTrue(appGradle.contains("checkUnsignedDestructiveValidationCandidateEvidence"))
        assertTrue(appGradle.contains("sentinel.destructiveValidationCandidateApk"))
        assertTrue(appGradle.contains("destructive-validation-candidate-snapshot"))
        assertTrue(
            appGradle.contains("Never auto-selects a build output and never mints a trusted expectation."),
        )
        val generateBlock = appGradle
            .substringAfter("generateDestructiveValidationCandidateEvidence")
            .substringBefore("androidComponents")
        assertFalse(generateBlock.contains("SingleArtifact.APK"))
        assertFalse(generateBlock.contains("assembleRelease"))
    }

    @Test
    fun `production runtime modules have no candidate-report parser or digest trust`() {
        val runtimeRoots = listOf(
            File("../app/src/main"),
            File("../device-management/src/main"),
            File("../device-management-api/src/main"),
            File("../device-management-facade/src/main"),
            File("../sensitive-actions/src/main"),
        )
        val forbidden = listOf(
            "destructive-validation-candidate.txt",
            "generateDestructiveValidationCandidateEvidence",
            "DestructiveValidationCandidateEvidence",
            "sentinel.destructiveValidationCandidateApk",
            "apk_sha256=",
            "parseCandidate",
            "CandidateEvidenceReport",
        )
        runtimeRoots.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    val text = file.readText()
                    forbidden.forEach { token ->
                        assertFalse(text.contains(token), "${file.path}: $token")
                    }
                }
        }
        val decision = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19FDecision.kt",
        ).readText()
        assertTrue(decision.contains("ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT = true"))
        assertTrue(decision.contains("CANDIDATE_ARTIFACT_ELIGIBLE = false"))
        assertTrue(decision.contains("UNSIGNED_CANDIDATE_PROOF_PASSED = false") ||
            decision.contains("UNSIGNED_CANDIDATE_PROOF_PASSED = true"))
        assertTrue(decision.contains("REAL_DEVICE_IDENTITY_RECORDED = false"))
        assertTrue(decision.contains("HARDWARE_VALIDATION_PREPARATION_READY = false"))
        assertTrue(decision.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(decision.contains("CHECKPOINT_19F_USED_AS_RUNTIME_AUTHORIZATION = false"))
        assertFalse(decision.contains("wipeDevice"))
        assertFalse(decision.contains("wipeData"))
        assertFalse(HEX_SHA256.containsMatchIn(decision))
    }

    @Test
    fun `workflow extends 19E without uploads secrets hardware or production signing`() {
        val workflowDir = File("../.github/workflows")
        val workflows = workflowDir.listFiles().orEmpty()
            .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        assertEquals(
            listOf("checkpoint-19e-independent-ci.yml"),
            workflows.map { it.name }.sorted(),
        )
        val text = workflows.single().readText()
        val permissionsBlock = yamlBlockAfter(text, "permissions:")
        assertTrue(permissionsBlock.contains("contents: read"))
        assertFalse(Regex(""":\s*write\b""").containsMatchIn(permissionsBlock))
        assertTrue(text.contains(":app:checkUnsignedDestructiveValidationCandidateEvidence"))
        assertTrue(text.contains("candidate_status=INELIGIBLE"))
        assertTrue(text.contains("trusted_expectation_minted=false"))
        assertTrue(text.contains("runtime_authorization=false"))
        assertFalse(text.contains("checkProductionDistributionSigning"))
        assertFalse(text.contains("assembleProductionRelease"))
        assertFalse(text.contains("bundleProductionRelease"))
        assertFalse(text.contains("sentinel.destructiveValidationCandidateApk"))
        assertFalse(text.contains("\${{ secrets"))
        listOf(
            "SENTINEL_RELEASE_STORE_FILE:",
            "SENTINEL_RELEASE_STORE_PASSWORD:",
            "SENTINEL_RELEASE_KEY_ALIAS:",
            "SENTINEL_RELEASE_KEY_PASSWORD:",
            "SENTINEL_RELEASE_CERT_SHA256:",
        ).forEach { mapping ->
            assertFalse(text.contains(mapping), mapping)
        }
        assertFalse(text.contains("upload-artifact"))
        assertFalse(text.contains("actions/upload"))
        assertFalse(text.contains("apk_sha256"))
        assertFalse(Regex("""\bcat\b""").containsMatchIn(text))
        assertFalse(Regex("""\bemulator\b""").containsMatchIn(text))
        assertFalse(Regex("""\badb\b""").containsMatchIn(text))
        assertFalse(text.contains("set-device-owner"))
        assertFalse(text.contains("connectedAndroidTest"))
        val uses = Regex("""^\s+uses:\s*(\S+)""", RegexOption.MULTILINE)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(3, uses.size)
    }

    @Test
    fun `bytecode policy still forbids recovery from referencing Checkpoint 19F`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        assertTrue(
            source.contains("com/example/devicemanagement/destructive/Checkpoint19FDecision"),
        )
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(allowlistBlock.contains("wipeDevice(I)V"))
        assertTrue(allowlistBlock.contains("AndroidDevicePolicyFactoryResetService"))
        assertTrue(!allowlistBlock.contains("wipeData"))
    }

    @Test
    fun `contract template stays unfilled and keeps twelve states separate`() {
        val docs = File("../docs/WIPE_19F_DISPOSABLE_DEVICE_EVIDENCE_CONTRACT.md").readText()
        assertTrue(docs.contains("unfilled contract template only"))
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
        listOf(
            "operator_identity = UNRECORDED",
            "confirmation_utc_issuance_time = UNRECORDED",
            "confirmation_expiry = UNRECORDED",
            "one_attempt_identifier = UNRECORDED",
            "disposable_device_manufacturer = UNRECORDED",
            "disposable_device_model = UNRECORDED",
            "exact_device_serial = UNRECORDED",
            "grapheneos_version = UNRECORDED",
            "android_api_level = UNRECORDED",
            "os_build_fingerprint = UNRECORDED",
            "expected_package = UNRECORDED",
            "expected_device_admin_component = UNRECORDED",
            "device_owner_status = UNRECORDED",
            "active_admin_status = UNRECORDED",
            "profile_owner_absence = UNRECORDED",
            "exact_signed_apk_filename = UNRECORDED",
            "apk_sha256 = UNRECORDED",
            "signing_certificate_sha256 = UNRECORDED",
            "git_revision = UNRECORDED",
            "clean_dirty_build_provenance = UNRECORDED",
            "build_purpose = UNRECORDED",
            "flags_literal = UNRECORDED",
            "battery_percentage = UNRECORDED",
            "charging_state = UNRECORDED",
            "usb_state = UNRECORDED",
            "adb_state = UNRECORDED",
            "no_valuable_data_attestation = UNRECORDED",
            "recovery_reprovisioning_procedure = UNRECORDED",
            "artifact_frozen_acknowledgement = UNRECORDED",
            "factory_reset_consequence_acknowledgement = UNRECORDED",
            "hardware_validation_approval = UNRECORDED",
            "per_attempt_confirmation = UNRECORDED",
            "actual_execution_result = UNRECORDED",
            "post_reset_grapheneos_observations = UNRECORDED",
        ).forEach { field ->
            assertTrue(docs.contains(field), field)
        }
        assertTrue(docs.contains("19F_ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT = true"))
        assertTrue(docs.contains("19F_CANDIDATE_ARTIFACT_ELIGIBLE = false"))
        assertTrue(docs.contains("19F_REAL_DEVICE_IDENTITY_RECORDED = false"))
        assertTrue(docs.contains("19F_HARDWARE_VALIDATION_PREPARATION_READY = false"))
        assertTrue(docs.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertTrue(docs.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(docs.contains("NO HARDWARE WIPE PERFORMED"))
        assertTrue(docs.contains("build_purpose_expected"))
        assertTrue(docs.contains("build_purpose_observed"))
        assertTrue(docs.contains("SIGNED_UNCLASSIFIED"))
        assertTrue(docs.contains("future preparation blocker"))
        assertTrue(docs.contains("official apksigner signer indexes"))
        assertTrue(docs.contains("inspection/build-environment provenance"))
        assertTrue(docs.contains("task-private snapshot"))
        assertTrue(docs.contains("CHECKPOINT_19F_EVIDENCE_CORRECTNESS_REPAIRED = YES"))
        assertFalse(docs.contains("19F_CANDIDATE_ARTIFACT_ELIGIBLE = true"))
        assertFalse(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
    }

    private fun yamlBlockAfter(text: String, header: String): String {
        val start = text.indexOf("\n$header")
        assertTrue(start >= 0, header)
        val remainder = text.substring(start + 1 + header.length)
        val next = Regex("""\n[a-zA-Z]""").find(remainder) ?: return remainder
        return remainder.substring(0, next.range.first)
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
