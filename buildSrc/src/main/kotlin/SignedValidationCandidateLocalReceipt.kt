import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/**
 * Local-only receipt for one accepted validation-signed disposable APK.
 *
 * The receipt binds the observed APK bytes, public certificate, repository
 * identity contract, and a caller-supplied checkout claim. It remains
 * untrusted candidate evidence: it is not an independent witness, does not
 * prove APK origin, does not mint runtime trust, and authorizes no hardware
 * action or wipe.
 */
object SignedValidationCandidateLocalReceipt {
    const val TASK_PATH = ":app:recordSignedDisposableValidationCandidateReceipt"
    const val CANDIDATE_APK_PROPERTY = "sentinel.signedValidationCandidateApk"
    const val PUBLIC_CERTIFICATE_PROPERTY = "sentinel.validationPublicCertificate"
    const val SOURCE_HEAD_PROPERTY = "sentinel.signedValidationSourceHead"
    const val RECEIPT_RELATIVE_PATH =
        "local/signed-validation-candidate-receipt.txt"
    const val SNAPSHOT_RELATIVE_PATH =
        "app/build/tmp/signed-validation-receipt-snapshot"
    const val STATUS = "RECORDED_LOCAL_ONLY"

    private val SHA256 = Regex("[0-9a-f]{64}")
    private val GIT_REVISION = Regex("[0-9a-f]{40}")

    data class Receipt(
        val sourceHeadClaimed: String,
        val apkSha256: String,
        val validationCertificateSha256: String,
        val signerCount: Int,
        val v2Present: Boolean,
        val v3Present: Boolean,
    ) {
        fun render(): String {
            val identity = DestructiveValidationExpectedIdentity.repositoryContract()
            return buildString {
                appendLine("checkpoint=19S")
                appendLine("receipt_status=$STATUS")
                appendLine("authority=${ValidationOnlySigningGate.AUTHORITY}")
                appendLine("source_head_claimed=$sourceHeadClaimed")
                appendLine("source_head_proves_apk_origin=false")
                appendLine("apk_sha256=$apkSha256")
                appendLine("validation_certificate_sha256=$validationCertificateSha256")
                appendLine("artifact_digest_recorded_local_only=true")
                appendLine("signing=SIGNED_UNCLASSIFIED")
                appendLine("signer_count=$signerCount")
                appendLine("signer_count_reliable=true")
                appendLine("v2_present=$v2Present")
                appendLine("v3_present=$v3Present")
                appendLine("build_purpose=DISPOSABLE_DEVICE_VALIDATION")
                appendLine(
                    "package_name=${identity.packageName}",
                )
                appendLine(
                    "admin_component=${identity.adminComponent}",
                )
                appendLine(
                    "policies=${identity.policies.joinToString(",")}",
                )
                appendLine("min_sdk=${identity.minSdk}")
                appendLine("target_sdk=${identity.targetSdk}")
                appendLine("package_matches=true")
                appendLine("admin_matches=true")
                appendLine("policies_match=true")
                appendLine("min_sdk_matches=true")
                appendLine("target_sdk_matches=true")
                appendLine("validation_signing_performed_observed=true")
                appendLine("signed_validation_candidate_accepted=true")
                appendLine("local_receipt_is_independent_witness=false")
                appendLine("independent_witness_approval=false")
                appendLine("runtime_authorization=false")
                appendLine("trusted_expectation_minted=false")
                appendLine("production_distribution=false")
                appendLine("customer_device_authorized=false")
                appendLine("real_device_identity_recorded=false")
                appendLine("hardware_validation_approved=false")
                appendLine("hardware_test_performed=false")
                appendLine("receipt_authorizes_hardware_test=false")
                appendLine("receipt_authorizes_wipe=false")
            }
        }
    }

    fun create(
        result: ValidationOnlySignedCandidateEvidence.Result,
        sourceHeadClaimed: String,
        independentlySuppliedPublicCertificateSha256: String,
    ): Receipt {
        val head = sourceHeadClaimed.trim().lowercase()
        check(GIT_REVISION.matches(head)) {
            "$SOURCE_HEAD_PROPERTY must be an exact 40-hex commit"
        }
        check(
            result.decision ==
                ValidationOnlySigningGate.SignedCandidateDecision.ACCEPT_UNTRUSTED_CANDIDATE,
        ) {
            "signed candidate was not accepted as untrusted validation evidence"
        }
        check(result.sameSnapshotForAllInspectors) {
            "receipt requires one immutable snapshot for every inspector"
        }
        check(
            result.buildPurposeObserved ==
                DestructiveValidationExpectedIdentity.BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION &&
                result.buildPurposeStatus == DestructiveValidationBuildPurposeParser.STATUS_OBSERVED,
        ) {
            "receipt requires independently observed disposable-validation purpose"
        }
        check(
            result.packageMatches &&
                result.adminMatches &&
                result.policiesMatch &&
                result.minSdkMatches &&
                result.targetSdkMatches,
        ) {
            "receipt requires the complete repository identity contract"
        }
        check(
            result.signerCountReliable &&
                result.signerCount == 1 &&
                result.schemesReliable &&
                result.v2Present &&
                result.v3Present,
        ) {
            "receipt requires one reliable V2/V3 signer"
        }
        val apkDigest = normalizeSha256(result.apkSha256)
            ?: error("candidate APK digest is not a valid SHA-256 value")
        val observedCertificate = normalizeSha256(result.signingCertificateSha256.orEmpty())
            ?: error("observed signing certificate is not a valid SHA-256 value")
        val suppliedCertificate = normalizeSha256(
            independentlySuppliedPublicCertificateSha256,
        ) ?: error("independently supplied public certificate digest is invalid")
        check(observedCertificate == suppliedCertificate) {
            "observed signer does not match the independently supplied public certificate"
        }
        return Receipt(
            sourceHeadClaimed = head,
            apkSha256 = apkDigest,
            validationCertificateSha256 = observedCertificate,
            signerCount = result.signerCount,
            v2Present = result.v2Present,
            v3Present = result.v3Present,
        )
    }

    fun publicCertificateSha256(certificate: File): String {
        rejectSymlinkPath(certificate)
        val before = Files.readAttributes(
            certificate.toPath(),
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        check(before.isRegularFile) {
            "validation public certificate must be a regular file"
        }
        val first = sha256(certificate)
        val middle = Files.readAttributes(
            certificate.toPath(),
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        val second = sha256(certificate)
        val after = Files.readAttributes(
            certificate.toPath(),
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        check(
            stableIdentity(before, middle) &&
                stableIdentity(middle, after) &&
                first == second,
        ) {
            "validation public certificate changed during hashing"
        }
        return first
    }

    fun writeOnce(receipt: Receipt, destination: File) {
        val parent = destination.parentFile
            ?: error("local receipt requires a parent directory")
        check(parent.exists() || parent.mkdirs()) {
            "could not create local receipt directory"
        }
        rejectSymlinkPath(parent)
        check(!Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            "local signed-candidate receipt already exists; refusing to overwrite it"
        }
        val rendered = receipt.render()
        val temporary = Files.createTempFile(
            parent.toPath(),
            ".signed-validation-candidate-receipt-",
            ".tmp",
        )
        try {
            Files.writeString(temporary, rendered)
            try {
                Files.move(
                    temporary,
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination.toPath())
            }
            check(
                Files.isRegularFile(destination.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                    destination.readText() == rendered,
            ) {
                "local signed-candidate receipt verification failed"
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun rejectSymlinkPath(file: File) {
        var cursor = file.toPath().toAbsolutePath().normalize()
        while (true) {
            check(!Files.isSymbolicLink(cursor)) {
                "local receipt input or parent must not be a symbolic link"
            }
            cursor = cursor.parent ?: break
        }
    }

    private fun stableIdentity(
        first: BasicFileAttributes,
        second: BasicFileAttributes,
    ): Boolean {
        return first.isRegularFile == second.isRegularFile &&
            first.fileKey() == second.fileKey() &&
            first.size() == second.size() &&
            first.lastModifiedTime() == second.lastModifiedTime() &&
            first.creationTime() == second.creationTime()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalizeSha256(value: String): String? {
        return value.trim().lowercase().takeIf(SHA256::matches)
    }
}
