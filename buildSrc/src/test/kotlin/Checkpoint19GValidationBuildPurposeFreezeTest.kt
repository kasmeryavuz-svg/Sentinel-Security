import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Checkpoint19GValidationBuildPurposeFreezeTest {
    @Test
    fun `dedicated variant and aapt2 purpose inspection stay build-only`() {
        val expected = File("src/main/kotlin/DestructiveValidationExpectedIdentity.kt").readText()
        val evidence = File("src/main/kotlin/DestructiveValidationCandidateEvidence.kt").readText()
        val inspectors = File("src/main/kotlin/DestructiveValidationCandidateInspectors.kt").readText()
        val tasks = File("src/main/kotlin/DestructiveValidationCandidateEvidenceTask.kt").readText()
        val appGradle = File("../app/build.gradle.kts").readText()
        val overlay = File("../app/src/disposableValidation/AndroidManifest.xml").readText()

        assertTrue(expected.contains("expectedCertificateSha256 = null"))
        assertTrue(expected.contains("DISPOSABLE_VALIDATION_BUILD_TYPE = \"disposableValidation\""))
        assertTrue(expected.contains("DESTRUCTIVE_VALIDATION_BUILD_PURPOSE"))
        assertFalse(expected.contains("SENTINEL_RELEASE_CERT_SHA256"))
        assertTrue(File("src/main/kotlin/DestructiveValidationBuildPurposeParser.kt").isFile)
        assertTrue(inspectors.contains("inspectObservedBuildPurpose"))
        assertTrue(inspectors.contains("dump\","))
        assertTrue(inspectors.contains("xmltree"))
        assertFalse(inspectors.contains("META-INF/sentinel-destructive-build-purpose"))
        assertFalse(inspectors.contains("SENTINEL_RELEASE_CERT_SHA256"))
        assertTrue(evidence.contains("assertDisposableValidationUnsignedIneligibleProof"))
        assertTrue(evidence.contains("findUnsignedDisposableValidationApk"))
        assertTrue(tasks.contains("CheckUnsignedDisposableValidationBuildPurposeEvidenceTask"))
        assertTrue(tasks.contains("supplied explicitly"))
        assertTrue(appGradle.contains("disposableValidation"))
        assertTrue(appGradle.contains("signingConfig = null"))
        assertTrue(appGradle.contains("checkUnsignedDisposableValidationBuildPurposeEvidence"))
        assertTrue(appGradle.contains("Never auto-selects a build output and never mints a trusted expectation."))
        assertTrue(overlay.contains("DISPOSABLE_DEVICE_VALIDATION"))
        assertEquals(
            1,
            Regex("""android:name="com.example.devicemanagement.DESTRUCTIVE_VALIDATION_BUILD_PURPOSE"""")
                .findAll(overlay)
                .count(),
        )
        val generateBlock = appGradle
            .substringAfter("generateDestructiveValidationCandidateEvidence")
            .substringBefore("androidComponents")
        assertTrue(generateBlock.contains("sentinel.destructiveValidationCandidateApk"))
        assertFalse(generateBlock.contains("disposableValidation"))
    }

    @Test
    fun `debug and release sources do not claim disposable-validation purpose`() {
        val metadataName = "com.example.devicemanagement.DESTRUCTIVE_VALIDATION_BUILD_PURPOSE"
        val mainManifest = File("../app/src/main/AndroidManifest.xml").readText()
        val debugDir = File("../app/src/debug")
        val releaseDir = File("../app/src/release")
        assertFalse(mainManifest.contains(metadataName))
        assertFalse(mainManifest.contains("DISPOSABLE_DEVICE_VALIDATION"))
        listOf(debugDir, releaseDir).filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val text = file.readText()
                    assertFalse(text.contains(metadataName), file.path)
                }
        }
    }

    @Test
    fun `production runtime modules never read the build-purpose metadata`() {
        val metadataName = "com.example.devicemanagement.DESTRUCTIVE_VALIDATION_BUILD_PURPOSE"
        val runtimeRoots = listOf(
            File("../app/src/main"),
            File("../device-management/src/main"),
            File("../device-management-api/src/main"),
            File("../device-management-facade/src/main"),
            File("../sensitive-actions/src/main"),
        )
        runtimeRoots.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    val text = file.readText()
                    assertFalse(text.contains(metadataName), file.path)
                    assertFalse(text.contains("DESTRUCTIVE_VALIDATION_BUILD_PURPOSE"), file.path)
                    assertFalse(text.contains("inspectObservedBuildPurpose"), file.path)
                    assertFalse(text.contains("DestructiveValidationBuildPurposeParser"), file.path)
                }
        }
    }

    @Test
    fun `workflow runs the 19G proof without uploads secrets or hardware`() {
        val workflowDir = File("../.github/workflows")
        val workflows = workflowDir.listFiles().orEmpty()
            .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        assertEquals(
            listOf("checkpoint-19e-independent-ci.yml"),
            workflows.map { it.name }.sorted(),
        )
        val text = workflows.single().readText()
        assertTrue(text.contains(":app:checkUnsignedDisposableValidationBuildPurposeEvidence"))
        assertTrue(text.contains(":app:checkUnsignedDestructiveValidationCandidateEvidence"))
        assertTrue(text.contains("build_purpose_observed=DISPOSABLE_DEVICE_VALIDATION"))
        assertTrue(text.contains("build_purpose_observed=UNAVAILABLE"))
        assertTrue(text.contains("expected_certificate_configured=false"))
        assertFalse(text.contains("upload-artifact"))
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
        assertFalse(Regex("""\bemulator\b""").containsMatchIn(text))
        assertFalse(Regex("""\badb\b""").containsMatchIn(text))
        val uses = Regex("""^\s+uses:\s*(\S+)""", RegexOption.MULTILINE)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(3, uses.size)
    }

    @Test
    fun `19G document stays closed and contains no trusted digest`() {
        val docs = File("../docs/WIPE_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE.md").readText()
        assertTrue(docs.contains("CHECKPOINT_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE = YES"))
        assertTrue(docs.contains("19G_DISPOSABLE_VALIDATION_VARIANT_PRESENT = true"))
        assertTrue(docs.contains("19G_BUILD_PURPOSE_OBSERVABLE = true"))
        assertTrue(docs.contains("19G_CANDIDATE_ARTIFACT_ELIGIBLE = false"))
        assertTrue(docs.contains("19G_REAL_DEVICE_IDENTITY_RECORDED = false"))
        assertTrue(docs.contains("19G_HARDWARE_VALIDATION_PREPARATION_READY = false"))
        assertTrue(docs.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("BUILD-PURPOSE OBSERVATION IS EVIDENCE, NOT TRUST"))
        assertTrue(docs.contains("CHECKOUT PROVENANCE STILL DOES NOT PROVE APK ORIGIN"))
        assertTrue(docs.contains("A MATCHING BUILD PURPOSE ALONE CANNOT MAKE A CANDIDATE ELIGIBLE"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertTrue(docs.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(docs.contains("NO HARDWARE WIPE PERFORMED"))
        assertFalse(docs.contains("19G_CANDIDATE_ARTIFACT_ELIGIBLE = true"))
        assertFalse(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
