import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Prove the real repository has no external witness evidence and no
 * independent review attestation. This task never reads a signed APK,
 * 19S receipt, 19T report, 19U enrollment record, witness statement, or
 * any private key.
 */
@DisableCachingByDefault(because = DestructiveProofTaskSemantics.REASON)
abstract class CheckIndependentWitnessExternalEvidencePreparationTask : DefaultTask() {
    init {
        DestructiveProofTaskSemantics.neverReuseOutputs(this)
    }

    @get:Input
    abstract val filledEvidencePath: Property<String>

    @get:Input
    abstract val filledReviewPath: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val temporaryDirectory: DirectoryProperty

    @TaskAction
    fun proveNotPresent() {
        val temp = temporaryDirectory.get().asFile
        try {
            if (temp.exists()) {
                temp.deleteRecursively()
            }
            temp.mkdirs()
            check(
                DestructiveValidationExpectedIdentity.repositoryContract()
                    .expectedCertificateSha256 == null,
            ) {
                "expectedCertificateSha256 must remain null"
            }
            val filledEvidence = File(filledEvidencePath.get())
            val filledReview = File(filledReviewPath.get())
            check(!filledEvidence.exists()) {
                "Filled external witness evidence must not exist in this checkpoint"
            }
            check(!filledReview.exists()) {
                "Filled witness enrollment review must not exist in this checkpoint"
            }
            val evaluation =
                IndependentWitnessExternalEvidencePreparation.evaluateRepositoryDefault()
            check(!evaluation.externalEvidencePresent)
            check(!evaluation.reviewAttestationPresent)
            check(!evaluation.externalIndependenceEvidenceVerified)
            check(
                evaluation.status ==
                    IndependentWitnessExternalEvidencePreparation.STATUS_NOT_PRESENT,
            )
            val enrollment =
                IndependentWitnessAuthorityEnrollmentPreparation.evaluateRepositoryDefault()
            check(!enrollment.witnessAuthorityEnrolled)
            check(
                enrollment.status ==
                    IndependentWitnessAuthorityEnrollment.STATUS_NOT_ENROLLED,
            )
            val out = reportFile.get().asFile
            out.parentFile.mkdirs()
            val rendered = evaluation.render()
            out.writeText(rendered)
            logger.lifecycle(rendered.trim())
        } finally {
            if (temp.exists()) {
                temp.deleteRecursively()
            }
        }
        check(!temp.exists()) {
            "external witness-evidence preparation temporary directory must be deleted"
        }
    }
}
