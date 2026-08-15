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
    @get:Optional
    abstract val snapshotDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val supplied = candidateApkPath.orNull?.trim().orEmpty()
        check(supplied.isNotEmpty()) {
            "${DestructiveValidationCandidateEvidence.CANDIDATE_APK_PROPERTY} must be " +
                "supplied explicitly. This task never auto-selects a build output."
        }
        val apk = File(supplied)
        val report = DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
            apk = apk,
            androidSdkDir = androidSdkDirectory.orNull?.takeIf { it.isNotBlank() }?.let(::File),
            projectRoot = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let(::File),
            snapshotDirectory = snapshotDirectory.orNull?.asFile,
        )
        writeUntrustedReport(report)
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
    @get:Optional
    abstract val snapshotDirectory: DirectoryProperty

    @TaskAction
    fun proveUnsignedIneligible() {
        val apk = DestructiveValidationCandidateEvidence.findUnsignedReleaseApk(
            apkDirectory.get().asFile,
        )
        val report = DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
            apk = apk,
            androidSdkDir = androidSdkDirectory.orNull?.takeIf { it.isNotBlank() }?.let(::File),
            projectRoot = projectRootPath.orNull?.takeIf { it.isNotBlank() }?.let(::File),
            snapshotDirectory = snapshotDirectory.orNull?.asFile,
        )
        val out = reportFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(report.render())
        logger.lifecycle(report.statusLinesWithoutDigest().trim())
        DestructiveValidationCandidateEvidence.assertUnsignedIneligibleProof(report)
    }
}
