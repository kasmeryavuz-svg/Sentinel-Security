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
 * Prove the real repository witness-authority enrollment remains
 * NOT_ENROLLED. This task never reads a signed APK, 19S receipt,
 * witness statement, or any private key.
 */
@DisableCachingByDefault(because = DestructiveProofTaskSemantics.REASON)
abstract class CheckIndependentWitnessAuthorityEnrollmentPreparationTask : DefaultTask() {
    init {
        DestructiveProofTaskSemantics.neverReuseOutputs(this)
    }

    @get:Input
    abstract val filledEnrollmentRecordPath: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val temporaryDirectory: DirectoryProperty

    @TaskAction
    fun proveNotEnrolled() {
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
            val filled = File(filledEnrollmentRecordPath.get())
            check(!filled.exists()) {
                "Filled witness-authority enrollment record must not exist in this checkpoint"
            }
            val evaluation =
                IndependentWitnessAuthorityEnrollmentPreparation.evaluateRepositoryDefault()
            check(!evaluation.enrollmentRecordPresent)
            check(!evaluation.witnessAuthorityEnrolled)
            check(
                evaluation.status ==
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
            "witness-authority enrollment preparation temporary directory must be deleted"
        }
    }
}
