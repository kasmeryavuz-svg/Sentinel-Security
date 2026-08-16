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
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Fail-closed inspection for an explicitly requested signed
 * disposableValidation APK. Inspection reads only a task-private
 * immutable snapshot. This task never mints trust, never writes
 * secrets or digests into Git, and is not an independent witness.
 */
@DisableCachingByDefault(because = DestructiveProofTaskSemantics.REASON)
abstract class CheckSignedDisposableValidationTask : DefaultTask() {
    init {
        DestructiveProofTaskSemantics.neverReuseOutputs(this)
    }

    @get:Input
    abstract val validationSigningRequested: Property<Boolean>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val androidSdkDirectory: Property<String>

    @get:Input
    @get:Optional
    abstract val expectedCertificateSha256: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val snapshotDirectory: DirectoryProperty

    @TaskAction
    fun proveSignedCandidateStillUntrusted() {
        val snapshotDir = snapshotDirectory.get().asFile
        DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
            snapshotDirectory = snapshotDir,
            inspect = {
                check(validationSigningRequested.get()) {
                    "signed disposableValidation inspection runs only after an " +
                        "explicit assembleSignedDisposableValidation request"
                }
                check(ValidationOnlySigningGate.refuseTrustedExpectationMint(null) == null) {
                    "validation-only signing cannot mint a trusted expectation"
                }
                check(
                    DestructiveValidationExpectedIdentity.repositoryContract()
                        .expectedCertificateSha256 == null,
                ) {
                    "repository expectedCertificateSha256 must remain null"
                }
                val sourceApk = findSignedDisposableValidationApk(apkDirectory.get().asFile)
                val sdk = androidSdkDirectory.orNull?.takeIf { it.isNotBlank() }?.let(::File)
                ValidationOnlySignedCandidateEvidence.inspect(
                    apk = sourceApk,
                    snapshotDirectory = snapshotDir,
                    expectedCertificateSha256 = expectedCertificateSha256.orNull,
                    androidSdkDir = sdk,
                )
            },
            write = { result ->
                val rendered = ValidationOnlySigningGate.statusLinesWithoutDigest(
                    signedCandidateAccepted = result.decision ==
                        ValidationOnlySigningGate.SignedCandidateDecision
                            .ACCEPT_UNTRUSTED_CANDIDATE,
                ) + result.renderSafeDiagnostics()
                val out = reportFile.get().asFile
                out.parentFile.mkdirs()
                out.writeText(rendered)
                logger.lifecycle(rendered.trim())
            },
            assertProof = { result ->
                check(result.sameSnapshotForAllInspectors) {
                    "signed-candidate inspectors must share one immutable snapshot"
                }
                check(
                    result.decision ==
                        ValidationOnlySigningGate.SignedCandidateDecision
                            .ACCEPT_UNTRUSTED_CANDIDATE,
                ) {
                    "signed disposableValidation candidate refused: ${result.decision}; " +
                        result.renderSafeDiagnostics()
                            .lineSequence()
                            .filter { it.isNotBlank() }
                            .joinToString(";")
                }
            },
        )
    }

    companion object {
        fun findSignedDisposableValidationApk(directory: File): File {
            val apks = directory.walkTopDown()
                .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                .toList()
            val dedicated = apks.filter { apk ->
                val name = apk.name.lowercase()
                "disposablevalidation" in name && "unsigned" !in name
            }
            check(dedicated.size == 1 && dedicated.single().isFile) {
                "signed disposableValidation proof requires exactly one signed " +
                    "disposableValidation APK; found ${apks.map { it.name }}"
            }
            return dedicated.single()
        }

        internal fun readSignatureSchemes(
            apk: File,
            androidSdkDir: File?,
        ): ValidationOnlySigningGate.ObservedSignatureSchemes {
            val apksigner = DestructiveValidationCandidateInspectors.locateApksigner(androidSdkDir)
                ?: return ValidationOnlySigningGate.ObservedSignatureSchemes(
                    v2Present = false,
                    v3Present = false,
                    reliable = false,
                )
            val command = ApksignerLocator.commandLine(
                apksigner,
                "verify",
                "--verbose",
                apk.absolutePath,
            )
            val result = BoundedProcessRunner.run(command)
                ?: return ValidationOnlySigningGate.ObservedSignatureSchemes(
                    v2Present = false,
                    v3Present = false,
                    reliable = false,
                )
            if (result.exitCode != 0) {
                return ValidationOnlySigningGate.ObservedSignatureSchemes(
                    v2Present = false,
                    v3Present = false,
                    reliable = false,
                )
            }
            return ValidationOnlySigningGate.parseSignatureSchemes(result.output)
        }
    }
}
