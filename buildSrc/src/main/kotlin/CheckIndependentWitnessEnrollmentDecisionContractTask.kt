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
 * Prove the real repository independent-witness enrollment decision
 * remains BLOCKED. This task never reads a signed APK, 19S receipt,
 * 19T report, 19U enrollment record, 19V evidence or review, witness
 * statement, certificate, or any private key.
 */
@DisableCachingByDefault(because = DestructiveProofTaskSemantics.REASON)
abstract class CheckIndependentWitnessEnrollmentDecisionContractTask : DefaultTask() {
    init {
        DestructiveProofTaskSemantics.neverReuseOutputs(this)
    }

    @get:Input
    abstract val filledDecisionPath: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val temporaryDirectory: DirectoryProperty

    @TaskAction
    fun proveBlocked() {
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
            val filled = File(filledDecisionPath.get())
            check(!filled.exists()) {
                "Filled witness enrollment decision must not exist in this checkpoint"
            }
            val evaluation =
                IndependentWitnessEnrollmentDecision.evaluateRepositoryDefault()
            check(
                evaluation.decision ==
                    IndependentWitnessEnrollmentDecision.DECISION_BLOCKED,
            )
            check(!evaluation.enrollmentCandidateMechanicsSatisfied)
            check(!IndependentWitnessEnrollmentDecision.repositoryAuthorityIsEnrolled())
            check(
                IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty(),
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
            "independent-witness enrollment-decision temporary directory must be deleted"
        }
    }
}
