import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Prove the real repository signing-ceremony preparation state is still
 * safely NOT_READY. This task never signs, never reads production secrets,
 * never selects a signed artifact, and never mints runtime trust.
 */
abstract class CheckDestructiveSigningCeremonyPreparationTask : DefaultTask() {
    @get:Input
    abstract val disposableValidationRemainsUnsigned: Property<Boolean>

    @get:Input
    abstract val productionSigningConfigurationActive: Property<Boolean>

    @get:Input
    abstract val productionDistributionRequested: Property<Boolean>

    @get:Input
    abstract val filledCeremonyRecordPath: Property<String>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @get:Internal
    abstract val temporaryDirectory: DirectoryProperty

    @TaskAction
    fun proveNotReady() {
        val temp = temporaryDirectory.get().asFile
        try {
            if (temp.exists()) {
                temp.deleteRecursively()
            }
            temp.mkdirs()
            check(!productionDistributionRequested.get()) {
                "Signing-ceremony preparation must not run while a production " +
                    "distribution is requested"
            }
            check(!productionSigningConfigurationActive.get()) {
                "Signing-ceremony preparation requires no active production-signing " +
                    "configuration"
            }
            check(disposableValidationRemainsUnsigned.get()) {
                "disposableValidation must remain unsigned"
            }
            check(
                DestructiveValidationExpectedIdentity.repositoryContract()
                    .expectedCertificateSha256 == null,
            ) {
                "expectedCertificateSha256 must remain null"
            }
            val filled = File(filledCeremonyRecordPath.get())
            check(!filled.exists()) {
                "Filled signing-ceremony record must not exist in this checkpoint"
            }
            val evaluation = DestructiveSigningCeremonyPreparation.evaluateRepositoryDefault()
            DestructiveSigningCeremonyPreparation.assertRepositoryDefaultStillNotReady(evaluation)
            check(
                DestructiveSigningCeremonyPreparation.refuseTrustedExpectationMint(null) == null,
            ) {
                "candidate digests cannot become trusted expectations"
            }
            val out = reportFile.get().asFile
            out.parentFile.mkdirs()
            val rendered = evaluation.statusLinesWithoutDigest()
            out.writeText(rendered)
            logger.lifecycle(rendered.trim())
        } finally {
            if (temp.exists()) {
                temp.deleteRecursively()
            }
        }
        check(!temp.exists()) {
            "signing-ceremony preparation temporary directory must be deleted"
        }
    }
}
