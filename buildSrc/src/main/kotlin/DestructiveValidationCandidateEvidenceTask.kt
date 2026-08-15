import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Shared task-owned inspect/write/cleanup envelope.
 *
 * Cleanup verification always runs after success, inspection failure, and
 * report-writing failure. Only the exact task-private snapshot directory is
 * checked. User-supplied candidate APKs are never deleted.
 */
object DestructiveValidationCandidateEvidenceTaskSupport {
    fun <T> inspectWriteAndAssertCleanup(
        snapshotDirectory: File,
        inspect: () -> T,
        write: (T) -> Unit,
        assertProof: (T) -> Unit = {},
    ): T {
        var inspectionFailure: Throwable? = null
        try {
            val result = inspect()
            write(result)
            assertProof(result)
            return result
        } catch (failed: Throwable) {
            inspectionFailure = failed
            throw failed
        } finally {
            try {
                DestructiveValidationCandidateEvidence.assertSnapshotDeleted(snapshotDirectory)
            } catch (cleanupFailed: Throwable) {
                val original = inspectionFailure
                if (original != null) {
                    original.addSuppressed(cleanupFailed)
                } else {
                    throw cleanupFailed
                }
            }
        }
    }
}

/**
 * Inspect one explicitly supplied APK as an untrusted candidate.
 * Never auto-selects assemble output and never mints a trusted expectation.
 */
abstract class GenerateDestructiveValidationCandidateEvidenceTask : DefaultTask() {
    @get:Input
    abstract val candidateApkPath: Property<String>

    @get:Input
    @get:Optional
    abstract val androidSdkDirectory: Property<String>

    @get:Input
    @get:Optional
    abstract val projectRootPath: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val snapshotDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val snapshotDir = snapshotDirectory.get().asFile
        DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
            snapshotDirectory = snapshotDir,
            inspect = {
                val supplied = candidateApkPath.orNull?.trim().orEmpty()
                check(supplied.isNotEmpty()) {
                    "${DestructiveValidationCandidateEvidence.CANDIDATE_APK_PROPERTY} must be " +
                        "supplied explicitly. This task never auto-selects a build output."
                }
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = File(supplied),
                    androidSdkDir = androidSdkDirectory.orNull?.takeIf { it.isNotBlank() }?.let(::File),
                    projectRoot = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let(::File),
                    snapshotDirectory = snapshotDir,
                )
            },
            write = ::writeUntrustedReport,
        )
    }

    private fun writeUntrustedReport(
        report: DestructiveValidationCandidateEvidence.CandidateEvidenceReport,
    ) {
        val out = reportFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(report.render())
        logger.lifecycle(report.statusLinesWithoutDigest().trim())
    }
}

/**
 * Prove the temporary unsigned release APK is an ineligible untrusted candidate.
 * This is not production signing and does not upload or trust the report.
 */
abstract class CheckUnsignedDestructiveValidationCandidateEvidenceTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val androidSdkDirectory: Property<String>

    @get:Input
    @get:Optional
    abstract val projectRootPath: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val snapshotDirectory: DirectoryProperty

    @TaskAction
    fun proveUnsignedIneligible() {
        val snapshotDir = snapshotDirectory.get().asFile
        DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
            snapshotDirectory = snapshotDir,
            inspect = {
                val apk = DestructiveValidationCandidateEvidence.findUnsignedReleaseApk(
                    apkDirectory.get().asFile,
                )
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = apk,
                    androidSdkDir = androidSdkDirectory.orNull?.takeIf { it.isNotBlank() }?.let(::File),
                    projectRoot = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let(::File),
                    snapshotDirectory = snapshotDir,
                )
            },
            write = { report ->
                val out = reportFile.get().asFile
                out.parentFile.mkdirs()
                out.writeText(report.render())
                logger.lifecycle(report.statusLinesWithoutDigest().trim())
            },
            assertProof = DestructiveValidationCandidateEvidence::assertUnsignedIneligibleProof,
        )
    }
}

/**
 * Prove the dedicated unsigned disposable-validation APK exposes an
 * independently observed build purpose and still remains ineligible.
 * This is not production signing and does not upload or trust the report.
 */
abstract class CheckUnsignedDisposableValidationBuildPurposeEvidenceTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val androidSdkDirectory: Property<String>

    @get:Input
    @get:Optional
    abstract val projectRootPath: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val snapshotDirectory: DirectoryProperty

    @TaskAction
    fun proveObservedPurposeStillIneligible() {
        val snapshotDir = snapshotDirectory.get().asFile
        DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
            snapshotDirectory = snapshotDir,
            inspect = {
                val apk = DestructiveValidationCandidateEvidence.findUnsignedDisposableValidationApk(
                    apkDirectory.get().asFile,
                )
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = apk,
                    androidSdkDir = androidSdkDirectory.orNull?.takeIf { it.isNotBlank() }?.let(::File),
                    projectRoot = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let(::File),
                    snapshotDirectory = snapshotDir,
                )
            },
            write = { report ->
                val out = reportFile.get().asFile
                out.parentFile.mkdirs()
                out.writeText(report.render())
                logger.lifecycle(report.statusLinesWithoutDigest().trim())
            },
            assertProof =
                DestructiveValidationCandidateEvidence::assertDisposableValidationUnsignedIneligibleProof,
        )
    }
}
