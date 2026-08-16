import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Prove the real repository independent-witness contract remains
 * fail-closed. This task never reads a signed APK, 19S receipt, witness
 * statement, or any private key.
 */
@DisableCachingByDefault(because = DestructiveProofTaskSemantics.REASON)
abstract class CheckIndependentWitnessVerificationContractTask : DefaultTask() {
    init {
        DestructiveProofTaskSemantics.neverReuseOutputs(this)
    }

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val temporaryDirectory: DirectoryProperty

    @TaskAction
    fun proveFailClosed() {
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
            val evaluation = IndependentWitnessVerification.contractEvaluation()
            check(!evaluation.witnessStatementPresent)
            check(!evaluation.witnessSignatureVerified)
            check(!evaluation.witnessEvidenceMatchesCandidate)
            check(!evaluation.witnessIndependenceEstablished)
            check(!evaluation.independentWitnessApproval)
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
            "independent-witness contract temporary directory must be deleted"
        }
    }
}
