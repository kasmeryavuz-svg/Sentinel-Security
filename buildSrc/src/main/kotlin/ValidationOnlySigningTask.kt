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
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Fail-closed inspection for an explicitly requested signed
 * disposableValidation APK. This task never mints trust, never writes
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
    abstract val temporaryDirectory: DirectoryProperty

    @TaskAction
    fun proveSignedCandidateStillUntrusted() {
        val temp = temporaryDirectory.get().asFile
        try {
            if (temp.exists()) {
                temp.deleteRecursively()
            }
            temp.mkdirs()
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
            val apk = findSignedDisposableValidationApk(apkDirectory.get().asFile)
            val sdk = androidSdkDirectory.orNull?.takeIf { it.isNotBlank() }?.let(::File)
            val signing = DestructiveValidationCandidateInspectors.inspectSigning(apk, sdk)
            val identity = DestructiveValidationCandidateInspectors.inspectIdentity(apk, sdk)
            val schemes = readSignatureSchemes(apk, sdk)
            val expected = ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(
                expectedCertificateSha256.orNull.orEmpty(),
            )
            val observedFingerprint = signing.certificateSha256?.let {
                ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(it)
            }
            val identityMatches = ValidationOnlySigningGate.identityMatchesRepositoryContract(
                packageName = identity.packageName,
                adminComponent = identity.adminComponent,
                policies = identity.policies,
                minSdk = identity.minSdk?.toIntOrNull(),
                targetSdk = identity.targetSdk?.toIntOrNull(),
            )
            val debugOrTest =
                signing.classification ==
                    DestructiveValidationCandidateEvidence.Signing.DEBUG_SIGNED ||
                    signing.classification ==
                    DestructiveValidationCandidateEvidence.Signing.TEST_SIGNED
            val verified =
                signing.apksignerExecuted &&
                    signing.classification ==
                    DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED
            val decision = ValidationOnlySigningGate.evaluateSignedCandidate(
                ValidationOnlySigningGate.ObservedSignedValidationApk(
                    signingVerified = verified,
                    signerCount = signing.signerCount,
                    signerCountReliable = signing.signerCountReliable,
                    v2Present = schemes.v2Present,
                    v3Present = schemes.v3Present,
                    schemesReliable = schemes.reliable,
                    buildPurpose = identity.buildPurposeObserved,
                    identityMatches = identityMatches,
                    debugOrTestCertificate = debugOrTest,
                    certificateFingerprintMatches =
                        expected != null && expected == observedFingerprint,
                ),
            )
            check(
                decision ==
                    ValidationOnlySigningGate.SignedCandidateDecision.ACCEPT_UNTRUSTED_CANDIDATE,
            ) {
                "signed disposableValidation candidate refused: $decision"
            }
            val rendered = ValidationOnlySigningGate.statusLinesWithoutDigest(
                signedCandidateAccepted = true,
            )
            val out = reportFile.get().asFile
            out.parentFile.mkdirs()
            out.writeText(rendered)
            logger.lifecycle(rendered.trim())
        } finally {
            if (temp.exists()) {
                temp.deleteRecursively()
            }
        }
        check(!temp.exists()) {
            "signed disposableValidation temporary directory must be deleted"
        }
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
            val apk = dedicated.single()
            check(!Files.isSymbolicLink(apk.toPath())) {
                "signed disposableValidation APK must not be a symlink"
            }
            return apk
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
            return try {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(60, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    return ValidationOnlySigningGate.ObservedSignatureSchemes(
                        v2Present = false,
                        v3Present = false,
                        reliable = false,
                    )
                }
                val output = process.inputStream.bufferedReader().use { it.readText() }
                ValidationOnlySigningGate.parseSignatureSchemes(output)
            } catch (_: Exception) {
                ValidationOnlySigningGate.ObservedSignatureSchemes(
                    v2Present = false,
                    v3Present = false,
                    reliable = false,
                )
            }
        }
    }
}
