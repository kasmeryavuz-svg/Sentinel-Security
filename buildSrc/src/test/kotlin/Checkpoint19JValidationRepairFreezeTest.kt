import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Checkpoint19JValidationRepairFreezeTest {
    @Test
    fun `candidate-evidence task snapshot and report paths stay isolated`() {
        DestructiveValidationCandidateEvidence.assertCandidateEvidenceTasksIsolated()
        val tasks = DestructiveValidationCandidateEvidence.isolatedCandidateEvidenceTasks
        assertEquals(3, tasks.size)
        assertEquals(
            setOf(
                DestructiveValidationCandidateEvidence.EXPLICIT_CANDIDATE_SNAPSHOT_RELATIVE_PATH,
                DestructiveValidationCandidateEvidence.UNSIGNED_RELEASE_SNAPSHOT_RELATIVE_PATH,
                DestructiveValidationCandidateEvidence.DISPOSABLE_PURPOSE_SNAPSHOT_RELATIVE_PATH,
            ),
            tasks.map { it.snapshotRelativePath }.toSet(),
        )
        assertEquals(
            setOf(
                DestructiveValidationCandidateEvidence.EXPLICIT_CANDIDATE_REPORT_RELATIVE_PATH,
                DestructiveValidationCandidateEvidence.REPORT_RELATIVE_PATH,
                DestructiveValidationCandidateEvidence.DISPOSABLE_PURPOSE_REPORT_RELATIVE_PATH,
            ),
            tasks.map { it.reportRelativePath }.toSet(),
        )
        val generate = tasks.single {
            it.taskPath == DestructiveValidationCandidateEvidence.GENERATE_TASK_PATH
        }
        assertEquals(
            DestructiveValidationCandidateEvidence.CandidateApkSelection.EXPLICIT_CANDIDATE_PROPERTY,
            generate.apkSelection,
        )
        val unsigned = tasks.single {
            it.taskPath == DestructiveValidationCandidateEvidence.UNSIGNED_PROOF_TASK_PATH
        }
        assertEquals(
            DestructiveValidationCandidateEvidence.CandidateApkSelection.AGP_UNSIGNED_RELEASE_ARTIFACT,
            unsigned.apkSelection,
        )
        assertEquals(
            "app/build/reports/destructive-validation-candidate.txt",
            unsigned.reportRelativePath,
        )
        val disposable = tasks.single {
            it.taskPath ==
                DestructiveValidationCandidateEvidence.DISPOSABLE_VALIDATION_PROOF_TASK_PATH
        }
        assertEquals(
            DestructiveValidationCandidateEvidence.CandidateApkSelection
                .AGP_UNSIGNED_DISPOSABLE_VALIDATION_ARTIFACT,
            disposable.apkSelection,
        )
    }

    @Test
    fun `gradle wires isolated paths and does not reuse the shared snapshot directory`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        val tasks = File("src/main/kotlin/DestructiveValidationCandidateEvidenceTask.kt").readText()
        assertTrue(appGradle.contains("destructive-validation-explicit-candidate-snapshot"))
        assertTrue(appGradle.contains("destructive-validation-explicit-candidate.txt"))
        assertTrue(appGradle.contains("destructive-validation-unsigned-release-snapshot"))
        assertTrue(appGradle.contains("reports/destructive-validation-candidate.txt"))
        assertTrue(appGradle.contains("destructive-validation-disposable-purpose-snapshot"))
        assertTrue(appGradle.contains("destructive-validation-disposable-purpose.txt"))
        assertFalse(appGradle.contains("tmp/destructive-validation-candidate-snapshot"))
        assertTrue(appGradle.contains("assertCandidateEvidenceTasksIsolated"))
        assertTrue(appGradle.contains("sentinel.destructiveValidationCandidateApk"))
        val generateBlock = appGradle
            .substringAfter("generateDestructiveValidationCandidateEvidence")
            .substringBefore("androidComponents")
        assertTrue(generateBlock.contains("sentinel.destructiveValidationCandidateApk"))
        assertFalse(generateBlock.contains("SingleArtifact.APK"))
        assertFalse(generateBlock.contains("assembleRelease"))
        assertTrue(tasks.contains("inspectWriteAndAssertCleanup"))
        assertTrue(tasks.contains("assertSnapshotDeleted"))
        assertFalse(tasks.contains("deleteSnapshotQuietly"))
        assertFalse(tasks.contains("apksigner sign"))
    }

    @Test
    fun `workflow still proves unsigned ineligible ceremony and isolated snapshot cleanup`() {
        val workflowDir = File("../.github/workflows")
        val workflows = workflowDir.listFiles().orEmpty()
            .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        assertEquals(
            listOf("checkpoint-19e-independent-ci.yml"),
            workflows.map { it.name }.sorted(),
        )
        val text = workflows.single().readText()
        assertTrue(text.contains("candidate_status=INELIGIBLE"))
        assertTrue(text.contains("signing=UNSIGNED"))
        assertTrue(text.contains("build_purpose_observed=DISPOSABLE_DEVICE_VALIDATION"))
        assertTrue(text.contains("ceremony_status=NOT_READY"))
        assertTrue(text.contains("destructive-validation-explicit-candidate-snapshot"))
        assertTrue(text.contains("destructive-validation-unsigned-release-snapshot"))
        assertTrue(text.contains("destructive-validation-disposable-purpose-snapshot"))
        assertFalse(text.contains("checkProductionDistributionSigning"))
        assertFalse(text.contains("assembleProductionRelease"))
        assertFalse(text.contains("bundleProductionRelease"))
        assertFalse(text.contains("assembleSignedDisposableValidation"))
        assertFalse(text.contains("upload-artifact"))
        assertFalse(text.contains("\${{ secrets"))
        assertFalse(text.contains("apksigner sign"))
        assertFalse(text.contains("keytool"))
        assertFalse(Regex("""\bemulator\b""").containsMatchIn(text))
        assertFalse(Regex("""\badb\b""").containsMatchIn(text))
        val uses = Regex("""^\s+uses:\s*(\S+)""", RegexOption.MULTILINE)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(3, uses.size)
    }

    @Test
    fun `bytecode policy still forbids recovery from referencing Checkpoint 19J`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        assertTrue(
            source.contains("com/example/devicemanagement/destructive/Checkpoint19JDecision") &&
                source.contains(
                    "com/example/devicemanagement/destructive/Checkpoint19PGovernanceObservation",
                ),
        )
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(allowlistBlock.contains("wipeDevice(I)V"))
        assertTrue(allowlistBlock.contains("AndroidDevicePolicyFactoryResetService"))
        assertTrue(!allowlistBlock.contains("wipeData"))
    }
}
