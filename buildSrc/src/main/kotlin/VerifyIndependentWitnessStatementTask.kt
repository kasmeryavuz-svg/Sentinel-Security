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
 * Re-inspects an explicitly supplied signed disposableValidation APK,
 * reads the existing 19S receipt, and verifies an optional externally
 * supplied witness statement. This task never reads a private key,
 * never overwrites the 19S receipt, and never grants approval.
 */
@DisableCachingByDefault(because = DestructiveProofTaskSemantics.REASON)
abstract class VerifyIndependentWitnessStatementTask : DefaultTask() {
    init {
        DestructiveProofTaskSemantics.neverReuseOutputs(this)
    }

    @get:Input
    abstract val candidateApkPath: Property<String>

    @get:Input
    abstract val publicCertificatePath: Property<String>

    @get:Input
    abstract val receiptPath: Property<String>

    @get:Input
    @get:Optional
    abstract val sourceHeadClaimed: Property<String>

    @get:Input
    @get:Optional
    abstract val witnessStatementPath: Property<String>

    @get:Input
    @get:Optional
    abstract val witnessVerificationKeyPath: Property<String>

    @get:Input
    @get:Optional
    abstract val androidSdkDirectory: Property<String>

    @get:Internal
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val snapshotDirectory: DirectoryProperty

    @TaskAction
    fun verifyIndependentWitness() {
        val apkPath = candidateApkPath.orNull?.trim().orEmpty()
        val certificatePath = publicCertificatePath.orNull?.trim().orEmpty()
        val receiptFilePath = receiptPath.orNull?.trim().orEmpty()
        check(apkPath.isNotEmpty()) {
            "${SignedValidationCandidateLocalReceipt.CANDIDATE_APK_PROPERTY} must be supplied"
        }
        check(certificatePath.isNotEmpty()) {
            "${SignedValidationCandidateLocalReceipt.PUBLIC_CERTIFICATE_PROPERTY} must be supplied"
        }
        check(receiptFilePath.isNotEmpty()) {
            "${IndependentWitnessVerification.RECEIPT_PROPERTY} must be supplied"
        }
        val snapshotDir = snapshotDirectory.get().asFile
        val receipt = File(receiptFilePath)
        val receiptBefore = if (receipt.isFile) receipt.readText() else null
        val evaluation =
            DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
                snapshotDirectory = snapshotDir,
                inspect = {
                    IndependentWitnessVerification.verify(
                        apk = File(apkPath),
                        receiptFile = receipt,
                        publicCertificate = File(certificatePath),
                        snapshotDirectory = snapshotDir,
                        androidSdkDir = androidSdkDirectory.orNull
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::File),
                        sourceHeadClaimed = sourceHeadClaimed.orNull
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() },
                        witnessStatementFile = witnessStatementPath.orNull
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let(::File),
                        witnessVerificationKeyFile = witnessVerificationKeyPath.orNull
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let(::File),
                    )
                },
                write = { observed ->
                    IndependentWitnessVerification.writeReport(
                        evaluation = observed,
                        destination = reportFile.get().asFile,
                    )
                },
            )
        check(receipt.isFile && receipt.readText() == receiptBefore) {
            "19T must not modify the 19S local receipt"
        }
        check(!evaluation.witnessIndependenceEstablished)
        check(!evaluation.independentWitnessApproval)
        logger.lifecycle(evaluation.render().trim())
    }
}
