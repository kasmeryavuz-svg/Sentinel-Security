import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Records one local-only receipt for an explicitly supplied validation APK
 * and public certificate. This task never reads a keystore or password,
 * never signs, never uploads, and never mints runtime trust.
 */
@DisableCachingByDefault(because = DestructiveProofTaskSemantics.REASON)
abstract class RecordSignedDisposableValidationCandidateReceiptTask : DefaultTask() {
    init {
        DestructiveProofTaskSemantics.neverReuseOutputs(this)
    }

    @get:Input
    abstract val candidateApkPath: Property<String>

    @get:Input
    abstract val publicCertificatePath: Property<String>

    @get:Input
    abstract val sourceHeadClaimed: Property<String>

    @get:Input
    @get:Optional
    abstract val androidSdkDirectory: Property<String>

    // This one-shot local record must never be treated as a disposable Gradle
    // output: Gradle output cleanup must not bypass writeOnce()'s refusal.
    @get:Internal
    abstract val receiptFile: RegularFileProperty

    @get:Internal
    abstract val snapshotDirectory: DirectoryProperty

    @TaskAction
    fun recordLocalReceipt() {
        val apkPath = candidateApkPath.orNull?.trim().orEmpty()
        val certificatePath = publicCertificatePath.orNull?.trim().orEmpty()
        val sourceHead = sourceHeadClaimed.orNull?.trim().orEmpty()
        check(apkPath.isNotEmpty()) {
            "${SignedValidationCandidateLocalReceipt.CANDIDATE_APK_PROPERTY} must be supplied"
        }
        check(certificatePath.isNotEmpty()) {
            "${SignedValidationCandidateLocalReceipt.PUBLIC_CERTIFICATE_PROPERTY} must be supplied"
        }
        check(sourceHead.isNotEmpty()) {
            "${SignedValidationCandidateLocalReceipt.SOURCE_HEAD_PROPERTY} must be supplied"
        }
        check(
            DestructiveValidationExpectedIdentity.repositoryContract()
                .expectedCertificateSha256 == null,
        ) {
            "repository expectedCertificateSha256 must remain null"
        }
        val apk = File(apkPath)
        check(
            apk.name.contains("disposableValidation", ignoreCase = true) &&
                !apk.name.contains("unsigned", ignoreCase = true),
        ) {
            "local receipt requires an explicitly supplied signed disposableValidation APK"
        }
        val certificate = File(certificatePath)
        val certificateSha256 =
            SignedValidationCandidateLocalReceipt.publicCertificateSha256(certificate)
        val snapshotDir = snapshotDirectory.get().asFile
        try {
            val result = ValidationOnlySignedCandidateEvidence.inspect(
                apk = apk,
                snapshotDirectory = snapshotDir,
                expectedCertificateSha256 = certificateSha256,
                androidSdkDir = androidSdkDirectory.orNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File),
            )
            val receipt = SignedValidationCandidateLocalReceipt.create(
                result = result,
                sourceHeadClaimed = sourceHead,
                independentlySuppliedPublicCertificateSha256 = certificateSha256,
            )
            SignedValidationCandidateLocalReceipt.writeOnce(
                receipt = receipt,
                destination = receiptFile.get().asFile,
            )
            logger.lifecycle(
                buildString {
                    appendLine("receipt_status=${SignedValidationCandidateLocalReceipt.STATUS}")
                    appendLine("authority=${ValidationOnlySigningGate.AUTHORITY}")
                    appendLine("signed_validation_candidate_accepted=true")
                    appendLine("artifact_digest_recorded_local_only=true")
                    appendLine("independent_witness_approval=false")
                    appendLine("runtime_authorization=false")
                    appendLine("trusted_expectation_minted=false")
                    appendLine("hardware_validation_approved=false")
                    appendLine("receipt_authorizes_wipe=false")
                }.trim(),
            )
        } finally {
            DestructiveValidationCandidateEvidence.assertSnapshotDeleted(snapshotDir)
        }
    }
}
