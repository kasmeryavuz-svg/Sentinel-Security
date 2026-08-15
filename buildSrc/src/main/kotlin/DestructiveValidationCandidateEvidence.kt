import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/**
 * Build-only inspector for one explicitly supplied APK.
 *
 * The candidate is never trusted. This object does not mint a
 * DestructiveArtifactIdentityExpectation, does not write a digest into
 * production source, and does not authorize runtime destructive work.
 */
object DestructiveValidationCandidateEvidence {
    const val AUTHORITY = "UNTRUSTED_CANDIDATE_ONLY"
    const val REPORT_RELATIVE_PATH = "app/build/reports/destructive-validation-candidate.txt"
    const val CANDIDATE_APK_PROPERTY = "sentinel.destructiveValidationCandidateApk"
    const val GENERATE_TASK_PATH = ":app:generateDestructiveValidationCandidateEvidence"
    const val UNSIGNED_PROOF_TASK_PATH = ":app:checkUnsignedDestructiveValidationCandidateEvidence"

    enum class Signing {
        UNSIGNED,
        DEBUG_SIGNED,
        TEST_SIGNED,
        UNKNOWN,
        MULTIPLE_SIGNERS,
        MALFORMED,
        UNVERIFIABLE,
        PRODUCTION_SIGNED,
    }

    data class CandidateSigningInspection(
        val classification: Signing,
        val certificateSha256: String?,
        val signerCount: Int,
        val apksignerAvailable: Boolean,
        val apksignerExecuted: Boolean,
        val detail: String,
    )

    data class CandidateApkIdentity(
        val packageName: String?,
        val adminComponent: String?,
        val policies: List<String>?,
        val versionCode: String?,
        val versionName: String?,
        val minSdk: String?,
        val targetSdk: String?,
        val aapt2Available: Boolean,
        val detail: String,
    )

    data class GitProvenance(
        val revision: String,
        val worktree: String,
    )

    data class CandidateEvidenceReport(
        val authority: String = AUTHORITY,
        val runtimeAuthorization: Boolean = false,
        val trustedExpectationMinted: Boolean = false,
        val productionSigningEnabled: Boolean = false,
        val hardwareValidationApproved: Boolean = false,
        val candidateStatus: String,
        val signing: Signing,
        val apkSha256: String,
        val signingCertificateSha256: String?,
        val digestStable: Boolean,
        val fileAccepted: Boolean,
        val packageObserved: String?,
        val packageExpected: String,
        val adminObserved: String?,
        val adminExpected: String,
        val policiesObserved: List<String>?,
        val policiesExpected: List<String>,
        val versionCode: String?,
        val versionName: String?,
        val minSdk: String?,
        val targetSdk: String?,
        val expectedMinSdk: Int,
        val expectedTargetSdk: Int,
        val gitRevision: String,
        val worktree: String,
        val buildPurpose: String,
        val expectedCertificateConfigured: Boolean,
        val signerCount: Int,
        val apksignerAvailable: Boolean,
        val aapt2Available: Boolean,
        val ineligibilityReasons: List<String>,
    ) {
        fun render(): String {
            return buildString {
                appendLine("authority=$authority")
                appendLine("runtime_authorization=$runtimeAuthorization")
                appendLine("trusted_expectation_minted=$trustedExpectationMinted")
                appendLine("production_signing_enabled=$productionSigningEnabled")
                appendLine("hardware_validation_approved=$hardwareValidationApproved")
                appendLine("candidate_status=$candidateStatus")
                appendLine("signing=$signing")
                appendLine("apk_sha256=$apkSha256")
                appendLine(
                    "signing_certificate_sha256=${signingCertificateSha256 ?: "UNAVAILABLE"}",
                )
                appendLine("digest_stable=$digestStable")
                appendLine("file_accepted=$fileAccepted")
                appendLine("package_observed=${packageObserved ?: "UNAVAILABLE"}")
                appendLine("package_expected=$packageExpected")
                appendLine("package_matches=${packageObserved != null && packageObserved == packageExpected}")
                appendLine("admin_observed=${adminObserved ?: "UNAVAILABLE"}")
                appendLine("admin_expected=$adminExpected")
                appendLine("admin_matches=${adminObserved != null && adminObserved == adminExpected}")
                appendLine(
                    "policies_observed=${policiesObserved?.joinToString(",") ?: "UNAVAILABLE"}",
                )
                appendLine("policies_expected=${policiesExpected.joinToString(",")}")
                appendLine(
                    "policies_match=${policiesObserved != null && policiesObserved == policiesExpected}",
                )
                appendLine("version_code=${versionCode ?: "UNAVAILABLE"}")
                appendLine("version_name=${versionName ?: "UNAVAILABLE"}")
                appendLine("min_sdk=${minSdk ?: "UNAVAILABLE"}")
                appendLine("target_sdk=${targetSdk ?: "UNAVAILABLE"}")
                appendLine("expected_min_sdk=$expectedMinSdk")
                appendLine("expected_target_sdk=$expectedTargetSdk")
                appendLine("git_revision=$gitRevision")
                appendLine("worktree=$worktree")
                appendLine("build_purpose=$buildPurpose")
                appendLine("expected_certificate_configured=$expectedCertificateConfigured")
                appendLine("signer_count=$signerCount")
                appendLine("apksigner_available=$apksignerAvailable")
                appendLine("aapt2_available=$aapt2Available")
                appendLine(
                    "ineligibility_reasons=${ineligibilityReasons.joinToString(";")}",
                )
                appendLine("trusted_destructive_artifact_digest_recorded=false")
                appendLine("real_destructive_chain_runtime_available=false")
            }
        }

        fun statusLinesWithoutDigest(): String {
            return buildString {
                appendLine("candidate_status=$candidateStatus")
                appendLine("signing=$signing")
                appendLine("runtime_authorization=$runtimeAuthorization")
                appendLine("trusted_expectation_minted=$trustedExpectationMinted")
                appendLine("authority=$authority")
            }
        }
    }

    class RejectedException(message: String) : IllegalStateException(message)

    fun inspectExplicitCandidate(
        apk: File,
        expected: DestructiveValidationExpectedIdentity =
            DestructiveValidationExpectedIdentity.repositoryContract(),
        androidSdkDir: File? = null,
        projectRoot: File? = null,
        afterInitialDigest: (() -> Unit)? = null,
        signingInspector: ((File) -> CandidateSigningInspection)? = null,
        identityInspector: ((File) -> CandidateApkIdentity)? = null,
        gitProvenance: GitProvenance? = null,
    ): CandidateEvidenceReport {
        acceptCandidateFile(apk)
        val firstDigest = sha256OfExactBytes(apk)
        afterInitialDigest?.invoke()
        val signing = (signingInspector ?: { file ->
            DestructiveValidationCandidateInspectors.inspectSigning(file, androidSdkDir)
        }).invoke(apk)
        val identity = (identityInspector ?: { file ->
            DestructiveValidationCandidateInspectors.inspectIdentity(file, androidSdkDir)
        }).invoke(apk)
        val secondDigest = sha256OfExactBytes(apk)
        if (firstDigest != secondDigest) {
            throw RejectedException(
                "APK bytes changed during inspection; refusing the candidate",
            )
        }
        val git = gitProvenance
            ?: DestructiveValidationCandidateInspectors.captureGit(projectRoot)
        return evaluate(
            apkSha256 = firstDigest,
            signing = signing,
            identity = identity,
            git = git,
            expected = expected,
        )
    }

    fun acceptCandidateFile(apk: File) {
        val path = apk.toPath()
        if (Files.isSymbolicLink(path)) {
            throw RejectedException("Candidate path is a symlink; APK files only")
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw RejectedException("Candidate APK is missing")
        }
        val attributes = try {
            Files.readAttributes(
                path,
                java.nio.file.attribute.BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
        } catch (failed: IOException) {
            throw RejectedException("Candidate APK is unreadable: ${failed.message}")
        }
        if (attributes.isSymbolicLink) {
            throw RejectedException("Candidate path is a symlink; APK files only")
        }
        if (!attributes.isRegularFile || Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw RejectedException("Candidate must be a regular APK file")
        }
        val name = apk.name.lowercase()
        when {
            name.endsWith(".aab") ->
                throw RejectedException("AAB input is rejected; APK files only")
            name.endsWith(".zip") ->
                throw RejectedException("ZIP input is rejected; APK files only")
            !name.endsWith(".apk") ->
                throw RejectedException("Candidate must be an APK file")
        }
        if (!Files.isReadable(path)) {
            throw RejectedException("Candidate APK is unreadable")
        }
        try {
            Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
                if (input.read() == -1 && attributes.size() < 0) {
                    throw RejectedException("Candidate APK is unreadable")
                }
            }
        } catch (failed: IOException) {
            throw RejectedException("Candidate APK is unreadable: ${failed.message}")
        }
    }

    fun sha256OfExactBytes(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun evaluate(
        apkSha256: String,
        signing: CandidateSigningInspection,
        identity: CandidateApkIdentity,
        git: GitProvenance,
        expected: DestructiveValidationExpectedIdentity,
    ): CandidateEvidenceReport {
        val reasons = mutableListOf<String>()
        if (apkSha256.isBlank() || !apkSha256.matches(SHA256_HEX)) {
            reasons += "apk_digest_unavailable"
        }
        if (!signing.apksignerAvailable || !signing.apksignerExecuted) {
            reasons += "apksigner_unavailable"
        }
        if (signing.classification != Signing.PRODUCTION_SIGNED) {
            reasons += "signing=${signing.classification}"
        }
        if (signing.signerCount != 1) {
            reasons += "signer_count=${signing.signerCount}"
        }
        if (signing.certificateSha256.isNullOrBlank()) {
            reasons += "signing_certificate_unavailable"
        }
        if (expected.expectedCertificateSha256.isNullOrBlank()) {
            reasons += "expected_certificate_unconfigured"
        } else if (
            signing.certificateSha256 != null &&
            signing.certificateSha256 != expected.expectedCertificateSha256
        ) {
            reasons += "certificate_mismatch"
        }
        if (identity.packageName.isNullOrBlank()) {
            reasons += "package_unavailable"
        } else if (identity.packageName != expected.packageName) {
            reasons += "package_mismatch"
        }
        if (identity.adminComponent.isNullOrBlank()) {
            reasons += "admin_unavailable"
        } else if (identity.adminComponent != expected.adminComponent) {
            reasons += "admin_mismatch"
        }
        if (identity.policies == null) {
            reasons += "policies_unavailable"
        } else if (identity.policies != expected.policies) {
            reasons += "policies_mismatch"
        }
        if (identity.versionCode.isNullOrBlank()) {
            reasons += "version_code_unavailable"
        }
        if (identity.versionName.isNullOrBlank()) {
            reasons += "version_name_unavailable"
        }
        if (identity.minSdk.isNullOrBlank()) {
            reasons += "min_sdk_unavailable"
        } else if (identity.minSdk != expected.minSdk.toString()) {
            reasons += "min_sdk_mismatch"
        }
        if (identity.targetSdk.isNullOrBlank()) {
            reasons += "target_sdk_unavailable"
        } else if (identity.targetSdk != expected.targetSdk.toString()) {
            reasons += "target_sdk_mismatch"
        }
        if (!identity.aapt2Available) {
            reasons += "aapt2_unavailable"
        }
        val eligible = reasons.isEmpty()
        return CandidateEvidenceReport(
            candidateStatus = if (eligible) "ELIGIBLE" else "INELIGIBLE",
            signing = signing.classification,
            apkSha256 = apkSha256,
            signingCertificateSha256 = signing.certificateSha256,
            digestStable = true,
            fileAccepted = true,
            packageObserved = identity.packageName,
            packageExpected = expected.packageName,
            adminObserved = identity.adminComponent,
            adminExpected = expected.adminComponent,
            policiesObserved = identity.policies,
            policiesExpected = expected.policies,
            versionCode = identity.versionCode,
            versionName = identity.versionName,
            minSdk = identity.minSdk,
            targetSdk = identity.targetSdk,
            expectedMinSdk = expected.minSdk,
            expectedTargetSdk = expected.targetSdk,
            gitRevision = git.revision,
            worktree = git.worktree,
            buildPurpose = expected.buildPurpose,
            expectedCertificateConfigured = !expected.expectedCertificateSha256.isNullOrBlank(),
            signerCount = signing.signerCount,
            apksignerAvailable = signing.apksignerAvailable,
            aapt2Available = identity.aapt2Available,
            ineligibilityReasons = reasons,
        )
    }

    fun assertUnsignedIneligibleProof(report: CandidateEvidenceReport) {
        check(report.authority == AUTHORITY) {
            "candidate authority must remain $AUTHORITY"
        }
        check(!report.runtimeAuthorization) {
            "candidate evidence must not become runtime authorization"
        }
        check(!report.trustedExpectationMinted) {
            "candidate evidence must not mint a trusted expectation"
        }
        check(!report.productionSigningEnabled) {
            "candidate evidence must not enable production signing"
        }
        check(!report.hardwareValidationApproved) {
            "candidate evidence must not approve hardware validation"
        }
        check(report.candidateStatus == "INELIGIBLE") {
            "unsigned candidate must remain INELIGIBLE"
        }
        check(report.signing == Signing.UNSIGNED) {
            "unsigned candidate signing must be UNSIGNED"
        }
        check(!report.expectedCertificateConfigured) {
            "candidate inspection must not configure an expected production certificate"
        }
    }

    fun findUnsignedReleaseApk(directory: File): File {
        val apks = directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .toList()
        val unsigned = apks.filter { "unsigned" in it.name.lowercase() }
        check(unsigned.size == 1 && unsigned.single().isFile) {
            "Unsigned candidate proof requires exactly one unsigned release APK; " +
                "found ${apks.map { it.name }}"
        }
        val apk = unsigned.single()
        acceptCandidateFile(apk)
        return apk
    }

    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
}
