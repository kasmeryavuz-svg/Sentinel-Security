import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

/**
 * Fail-closed independent-witness verification for one 19S local receipt
 * and the signed disposable-validation APK it names.
 *
 * This object never reads a private key, never generates a witness key,
 * never overwrites the 19S receipt, and never mints runtime trust.
 * Cryptographic signature validity is not independence and is not
 * approval.
 */
object IndependentWitnessVerification {
    const val TASK_PATH = ":app:verifyIndependentWitnessStatement"
    const val CONTRACT_TASK_PATH = ":app:checkIndependentWitnessVerificationContract"
    const val RECEIPT_PROPERTY = "sentinel.signedValidationReceipt"
    const val REPORT_RELATIVE_PATH = "local/independent-witness-verification.txt"
    const val SNAPSHOT_RELATIVE_PATH =
        "app/build/tmp/independent-witness-verification-snapshot"
    const val CONTRACT_REPORT_RELATIVE_PATH =
        "app/build/reports/independent-witness-verification-contract.txt"

    private val HEX_VALUE = Regex("\\b[0-9a-fA-F]{40,}\\b")

    data class Evaluation(
        val candidateReinspectionPassed: Boolean,
        val receiptMatchesCandidate: Boolean,
        val validationCertificateMatches: Boolean,
        val witnessStatementPresent: Boolean,
        val witnessSignatureVerified: Boolean,
        val witnessEvidenceMatchesCandidate: Boolean,
        val witnessIndependenceEstablished: Boolean,
        val independentWitnessApproval: Boolean,
    ) {
        fun render(): String {
            val rendered = buildString {
                appendLine("checkpoint=19T")
                appendLine(
                    "candidate_reinspection_passed=$candidateReinspectionPassed",
                )
                appendLine("receipt_matches_candidate=$receiptMatchesCandidate")
                appendLine(
                    "validation_certificate_matches=$validationCertificateMatches",
                )
                appendLine("witness_statement_present=$witnessStatementPresent")
                appendLine("witness_signature_verified=$witnessSignatureVerified")
                appendLine(
                    "witness_evidence_matches_candidate=$witnessEvidenceMatchesCandidate",
                )
                appendLine(
                    "witness_independence_established=$witnessIndependenceEstablished",
                )
                appendLine(
                    "independent_witness_approval=$independentWitnessApproval",
                )
                appendLine("authority=${ValidationOnlySigningGate.AUTHORITY}")
                appendLine("runtime_authorization=false")
                appendLine("trusted_expectation_minted=false")
                appendLine("production_distribution=false")
                appendLine("customer_device_authorized=false")
                appendLine("real_device_identity_recorded=false")
                appendLine("hardware_validation_approved=false")
                appendLine("hardware_test_performed=false")
                appendLine("verification_authorizes_hardware_test=false")
                appendLine("verification_authorizes_wipe=false")
                appendLine("local_receipt_is_independent_witness=false")
                appendLine("ci_is_independent_witness=false")
                appendLine("operator_is_independent_witness=false")
            }
            check(!HEX_VALUE.containsMatchIn(rendered)) {
                "19T verification report must not contain digest values"
            }
            return rendered
        }
    }

    fun evaluateMechanics(
        candidateReinspectionPassed: Boolean,
        receiptMatchesCandidate: Boolean,
        validationCertificateMatches: Boolean,
        witnessStatementPresent: Boolean,
        witnessSignatureVerified: Boolean,
        witnessEvidenceMatchesCandidate: Boolean,
        witnessIdentifier: String?,
    ): Evaluation {
        val independence = IndependentWitnessAuthorityContract.independenceEstablished(
            witnessIdentifier.orEmpty(),
        )
        val approval = IndependentWitnessAuthorityContract.approval(
            statementPresent = witnessStatementPresent,
            signatureVerified = witnessSignatureVerified,
            evidenceMatches = witnessEvidenceMatchesCandidate,
            independenceEstablished = independence,
        )
        return Evaluation(
            candidateReinspectionPassed = candidateReinspectionPassed,
            receiptMatchesCandidate = receiptMatchesCandidate,
            validationCertificateMatches = validationCertificateMatches,
            witnessStatementPresent = witnessStatementPresent,
            witnessSignatureVerified = witnessSignatureVerified,
            witnessEvidenceMatchesCandidate = witnessEvidenceMatchesCandidate,
            witnessIndependenceEstablished = independence,
            independentWitnessApproval = approval,
        )
    }

    fun verify(
        apk: File,
        receiptFile: File,
        publicCertificate: File,
        snapshotDirectory: File,
        androidSdkDir: File? = null,
        sourceHeadClaimed: String? = null,
        witnessStatementFile: File? = null,
        witnessVerificationKeyFile: File? = null,
        afterInitialDigest: (() -> Unit)? = null,
        afterSnapshotCreated: ((File) -> Unit)? = null,
        signingInspector: (
            (File) -> DestructiveValidationCandidateEvidence.CandidateSigningInspection
        )? = null,
        identityInspector: (
            (File) -> DestructiveValidationCandidateEvidence.CandidateApkIdentity
        )? = null,
        schemeInspector: (
            (File) -> ValidationOnlySigningGate.ObservedSignatureSchemes
        )? = null,
        cleanup: ((File) -> Unit)? = null,
    ): Evaluation {
        check(
            DestructiveValidationExpectedIdentity.repositoryContract()
                .expectedCertificateSha256 == null,
        ) {
            "repository expectedCertificateSha256 must remain null"
        }
        check(apk.name.contains("disposableValidation", ignoreCase = true)) {
            "19T verification requires an explicitly supplied disposableValidation APK"
        }
        check(!apk.name.contains("unsigned", ignoreCase = true)) {
            "19T verification requires a signed disposableValidation APK"
        }
        val certificateSha256 =
            SignedValidationCandidateLocalReceipt.publicCertificateSha256(publicCertificate)
        val receipt = SignedValidationCandidateLocalReceipt.parseFile(receiptFile)
        if (!sourceHeadClaimed.isNullOrBlank()) {
            val claimed = sourceHeadClaimed.trim().lowercase()
            check(claimed == receipt.sourceHeadClaimed) {
                "supplied source head does not match the 19S receipt"
            }
        }
        val inspected = ValidationOnlySignedCandidateEvidence.inspect(
            apk = apk,
            snapshotDirectory = snapshotDirectory,
            expectedCertificateSha256 = certificateSha256,
            androidSdkDir = androidSdkDir,
            afterInitialDigest = afterInitialDigest,
            afterSnapshotCreated = afterSnapshotCreated,
            signingInspector = signingInspector,
            identityInspector = identityInspector,
            schemeInspector = schemeInspector,
            cleanup = cleanup,
        )
        check(
            inspected.decision ==
                ValidationOnlySigningGate.SignedCandidateDecision.ACCEPT_UNTRUSTED_CANDIDATE,
        ) {
            "signed candidate re-inspection did not accept the untrusted validation APK"
        }
        check(inspected.sameSnapshotForAllInspectors) {
            "19T verification requires one immutable snapshot for every inspector"
        }
        check(
            inspected.signerCountReliable &&
                inspected.signerCount == 1 &&
                inspected.schemesReliable &&
                inspected.v2Present &&
                inspected.v3Present,
        ) {
            "19T verification requires one reliable V2/V3 signer"
        }
        check(
            inspected.packageMatches &&
                inspected.adminMatches &&
                inspected.policiesMatch &&
                inspected.minSdkMatches &&
                inspected.targetSdkMatches &&
                inspected.buildPurposeObserved ==
                DestructiveValidationExpectedIdentity.BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION &&
                inspected.buildPurposeStatus ==
                DestructiveValidationBuildPurposeParser.STATUS_OBSERVED,
        ) {
            "19T verification requires the complete repository identity contract"
        }
        val observedApkSha256 = inspected.apkSha256.trim().lowercase()
        val observedCertificate = inspected.signingCertificateSha256
            ?.trim()
            ?.lowercase()
            .orEmpty()
        check(observedApkSha256 == receipt.apkSha256) {
            "re-inspected APK digest does not match the 19S receipt"
        }
        check(
            observedCertificate == certificateSha256 &&
                certificateSha256 == receipt.validationCertificateSha256,
        ) {
            "validation public certificate does not match the receipt and observed signer"
        }
        val statementMaterial = resolveStatementMaterial(
            witnessStatementFile = witnessStatementFile,
            witnessVerificationKeyFile = witnessVerificationKeyFile,
        )
        var statementPresent = false
        var signatureVerified = false
        var evidenceMatches = false
        var witnessIdentifier: String? = null
        if (statementMaterial != null) {
            val parsed = IndependentWitnessStatement.parseFile(statementMaterial.statement)
            val publicKey = IndependentWitnessStatement.loadVerificationKey(
                statementMaterial.verificationKey,
            )
            statementPresent = true
            signatureVerified = IndependentWitnessStatement.verifySignature(
                statement = parsed.statement,
                signatureBase64 = parsed.signatureBase64,
                publicKey = publicKey,
            )
            check(signatureVerified) {
                "witness statement signature is not valid for the supplied verification key"
            }
            evidenceMatches = IndependentWitnessStatement.matchesCandidate(
                statement = parsed.statement,
                apkSha256 = observedApkSha256,
                validationCertificateSha256 = certificateSha256,
                sourceHeadClaimed = receipt.sourceHeadClaimed,
                identity = DestructiveValidationExpectedIdentity.repositoryContract(),
            )
            check(evidenceMatches) {
                "witness statement does not match independently re-observed candidate evidence"
            }
            witnessIdentifier = parsed.statement.witnessIdentifier
        }
        val evaluation = evaluateMechanics(
            candidateReinspectionPassed = true,
            receiptMatchesCandidate = true,
            validationCertificateMatches = true,
            witnessStatementPresent = statementPresent,
            witnessSignatureVerified = signatureVerified,
            witnessEvidenceMatchesCandidate = evidenceMatches,
            witnessIdentifier = witnessIdentifier,
        )
        check(!evaluation.witnessIndependenceEstablished) {
            "repository witness-authority contract must remain unenrolled"
        }
        check(!evaluation.independentWitnessApproval) {
            "independent_witness_approval must remain false without enrolled authority"
        }
        DestructiveValidationCandidateEvidence.assertSnapshotDeleted(snapshotDirectory)
        return evaluation
    }

    fun writeReport(evaluation: Evaluation, destination: File) {
        val parent = destination.parentFile
            ?: error("19T verification report requires a parent directory")
        check(parent.exists() || parent.mkdirs()) {
            "could not create 19T verification report directory"
        }
        IndependentWitnessStatement.rejectSymlinkPath(parent)
        if (Files.exists(destination.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            IndependentWitnessStatement.rejectSymlinkPath(destination)
            check(
                Files.isRegularFile(destination.toPath(), LinkOption.NOFOLLOW_LINKS),
            ) {
                "19T verification report must be a regular file when replaced"
            }
        }
        val rendered = evaluation.render()
        val temporary = Files.createTempFile(
            parent.toPath(),
            ".independent-witness-verification-",
            ".tmp",
        )
        try {
            Files.writeString(temporary, rendered)
            try {
                Files.move(
                    temporary,
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary,
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            check(
                Files.isRegularFile(destination.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                    destination.readText() == rendered,
            ) {
                "19T verification report verification failed"
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun contractEvaluation(): Evaluation {
        check(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty()) {
            "no independently established witness authority may be enrolled"
        }
        check(!IndependentWitnessAuthorityContract.ciIsIndependentWitness())
        check(!IndependentWitnessAuthorityContract.localOperatorIsIndependentWitness())
        check(!IndependentWitnessAuthorityContract.localReceiptIsIndependentWitness())
        return evaluateMechanics(
            candidateReinspectionPassed = false,
            receiptMatchesCandidate = false,
            validationCertificateMatches = false,
            witnessStatementPresent = false,
            witnessSignatureVerified = false,
            witnessEvidenceMatchesCandidate = false,
            witnessIdentifier = null,
        )
    }

    private data class StatementMaterial(
        val statement: File,
        val verificationKey: File,
    )

    private fun resolveStatementMaterial(
        witnessStatementFile: File?,
        witnessVerificationKeyFile: File?,
    ): StatementMaterial? {
        val statementPath = witnessStatementFile
            ?.takeIf { it.path.isNotBlank() }
        val keyPath = witnessVerificationKeyFile
            ?.takeIf { it.path.isNotBlank() }
        if (statementPath == null && keyPath == null) {
            return null
        }
        check(statementPath != null && keyPath != null) {
            "witness statement and verification key must be supplied together"
        }
        check(statementPath.isFile) { "witness statement is missing" }
        check(keyPath.isFile) { "witness verification key is missing" }
        return StatementMaterial(statementPath, keyPath)
    }
}
