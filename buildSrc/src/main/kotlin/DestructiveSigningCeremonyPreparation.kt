/**
 * Build-only signing-ceremony preparation contract.
 *
 * This is not key generation, certificate approval, production signing,
 * signed-candidate creation, trusted artifact enrollment, runtime
 * authorization, hardware-test approval, or a production distribution.
 *
 * Production runtime types must not import this file. The repository
 * default source is immutable and cannot reach READY. Main
 * [DestructiveSigningCeremonyPreparation.evaluate] never returns
 * READY, including for TEST_ONLY_SYNTHETIC input.
 */
enum class SigningCeremonySourceKind {
    REPOSITORY_DEFAULT,
    TEST_ONLY_SYNTHETIC,
}

enum class SigningCeremonyStatus {
    NOT_READY,
    READY,
}

enum class ApprovedCeremonyScope {
    DISPOSABLE_DEVICE_VALIDATION_CANDIDATE_SIGNING,
}

enum class RequiredSignerPolicy {
    SINGLE_CURRENT_SIGNER,
}

enum class RequiredSignatureSchemePolicy {
    V2_AND_V3_REQUIRED,
    V1_ONLY_FORBIDDEN,
}

enum class ExpectedIdentitySource {
    REPOSITORY_CONTRACT,
    OBSERVED_APK,
}

sealed class CeremonyEvidence<out T> {
    object Absent : CeremonyEvidence<Nothing>()
    object Unknown : CeremonyEvidence<Nothing>()
    object Malformed : CeremonyEvidence<Nothing>()
    data class Present<T>(val value: T) : CeremonyEvidence<T>()
}

sealed class ApprovalEvidence {
    object Absent : ApprovalEvidence()
    object Unknown : ApprovalEvidence()
    object Malformed : ApprovalEvidence()
    data class Recorded(val reference: String) : ApprovalEvidence()
}

enum class SigningCeremonyBlocker(val reportKey: String) {
    MISSING_CEREMONY_IDENTIFIER("missing_ceremony_identifier"),
    MISSING_OR_INVALID_APPROVED_SCOPE("missing_or_invalid_approved_scope"),
    MISSING_IMMUTABLE_CANDIDATE_IDENTITY("missing_immutable_candidate_identity"),
    MISSING_OR_DIRTY_CHECKOUT_PROVENANCE("missing_or_dirty_checkout_provenance"),
    CHECKOUT_PROVENANCE_DOES_NOT_PROVE_APK_ORIGIN("checkout_provenance_does_not_prove_apk_origin"),
    MISSING_OFFLINE_KEY_CUSTODY_APPROVAL("missing_offline_key_custody_approval"),
    MISSING_OPERATOR_APPROVAL("missing_operator_approval"),
    MISSING_INDEPENDENT_WITNESS_APPROVAL("missing_independent_witness_approval"),
    MISSING_RECOVERY_BACKUP_VERIFICATION("missing_recovery_backup_verification"),
    MISSING_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFICATION(
        "missing_branch_protection_required_check_verification",
    ),
    MISSING_PUBLIC_CERTIFICATE("missing_public_certificate"),
    MISSING_INDEPENDENTLY_SUPPLIED_EXPECTED_CERTIFICATE_FINGERPRINT(
        "missing_independently_supplied_expected_certificate_fingerprint",
    ),
    INVALID_SIGNER_POLICY("invalid_signer_policy"),
    INVALID_SIGNATURE_SCHEME_POLICY("invalid_signature_scheme_policy"),
    MISSING_POST_SIGNING_REINSPECTION_PLAN("missing_post_signing_reinspection_plan"),
    PRODUCTION_SIGNING_NOT_AUTHORIZED("production_signing_not_authorized"),
    SIGNED_VALIDATION_CANDIDATE_NOT_PRODUCED("signed_validation_candidate_not_produced"),
    UNKNOWN_OR_MALFORMED_STATE("unknown_or_malformed_state"),
    CONTRADICTORY_STATE("contradictory_state"),
    PARTIALLY_FILLED_STATE("partially_filled_state"),
}

data class SigningCeremonyPreparationRecord(
    val sourceKind: SigningCeremonySourceKind,
    val ceremonyIdentifier: CeremonyEvidence<String>,
    val approvedScope: CeremonyEvidence<ApprovedCeremonyScope>,
    val unsignedCandidateSha256: CeremonyEvidence<String>,
    val immutableSnapshotSha256: CeremonyEvidence<String>,
    val checkoutRevision: CeremonyEvidence<String>,
    val worktreeClean: CeremonyEvidence<Boolean>,
    val checkoutRevisionProvesApkOrigin: CeremonyEvidence<Boolean>,
    val offlineKeyCustodyApproval: ApprovalEvidence,
    val operatorApproval: ApprovalEvidence,
    val witnessApproval: ApprovalEvidence,
    val recoveryBackupVerification: ApprovalEvidence,
    val branchProtectionRequiredCheckVerification: ApprovalEvidence,
    val requiredSignerPolicy: CeremonyEvidence<RequiredSignerPolicy>,
    val currentSignerCount: CeremonyEvidence<Int>,
    val requiredSignatureSchemePolicy: CeremonyEvidence<RequiredSignatureSchemePolicy>,
    val publicCertificateSha256: CeremonyEvidence<String>,
    val independentlySuppliedExpectedCertificateSha256: CeremonyEvidence<String>,
    val expectedIdentity: DestructiveValidationExpectedIdentity,
    val expectedIdentitySource: ExpectedIdentitySource,
    val postSigningReinspectionRequired: CeremonyEvidence<Boolean>,
    val signedOutputRemainsValidationCandidate: Boolean,
    val signingEvidenceCannotMintRuntimeTrust: Boolean,
    val signedApkDigestCannotBecomeTrustedExpectation: Boolean,
    val productionSigningAuthorized: Boolean,
    val signedValidationCandidateProduced: Boolean,
    val signedCandidateSha256: CeremonyEvidence<String>,
    val offlineKeyGenerated: Boolean,
    val publicCertificateSupplied: Boolean,
    val expectedCertificateRecorded: Boolean,
    val productionArtifactSigned: Boolean,
    val runtimeAuthorization: Boolean,
    val trustedExpectationMinted: Boolean,
    val hardwareValidationApproved: Boolean,
    val ceremonyRecordFilled: Boolean,
)

data class SigningCeremonyEvaluation(
    val status: SigningCeremonyStatus,
    val blockers: List<SigningCeremonyBlocker>,
    val sourceKind: SigningCeremonySourceKind,
    val offlineKeyGenerated: Boolean,
    val publicCertificateSupplied: Boolean,
    val expectedCertificateRecorded: Boolean,
    val operatorApprovalAvailable: Boolean,
    val witnessApprovalAvailable: Boolean,
    val keyCustodyApproved: Boolean,
    val recoveryBackupVerified: Boolean,
    val branchProtectionRequiredCheckVerified: Boolean,
    val productionArtifactSigned: Boolean,
    val runtimeAuthorization: Boolean,
    val trustedExpectationMinted: Boolean,
    val signedValidationCandidateProduced: Boolean,
    val expectedCertificateSha256Configured: Boolean,
    val checkoutRevisionProvesApkOrigin: Boolean,
    val trustedExpectationMintRefused: Boolean,
    val ceremonyRecordFilled: Boolean,
    val productionSigningAuthorized: Boolean,
) {
    fun render(): String {
        return buildString {
            appendLine("ceremony_status=${status.name}")
            appendLine("offline_key_generated=$offlineKeyGenerated")
            appendLine("public_certificate_supplied=$publicCertificateSupplied")
            appendLine("expected_certificate_recorded=$expectedCertificateRecorded")
            appendLine("operator_approval_available=$operatorApprovalAvailable")
            appendLine("witness_approval_available=$witnessApprovalAvailable")
            appendLine("key_custody_approved=$keyCustodyApproved")
            appendLine("recovery_backup_verified=$recoveryBackupVerified")
            appendLine(
                "branch_protection_required_check_verified=$branchProtectionRequiredCheckVerified",
            )
            appendLine("production_artifact_signed=$productionArtifactSigned")
            appendLine("runtime_authorization=$runtimeAuthorization")
            appendLine("trusted_expectation_minted=$trustedExpectationMinted")
            appendLine("signed_validation_candidate_produced=$signedValidationCandidateProduced")
            appendLine("expected_certificate_sha256_configured=$expectedCertificateSha256Configured")
            appendLine("production_signing_authorized=$productionSigningAuthorized")
            appendLine("ceremony_record_filled=$ceremonyRecordFilled")
            appendLine("checkout_revision_proves_apk_origin=$checkoutRevisionProvesApkOrigin")
            appendLine("trusted_expectation_mint_refused=$trustedExpectationMintRefused")
            appendLine("source_kind=${sourceKind.name}")
            appendLine("blockers=${blockers.joinToString(";") { it.reportKey }}")
        }
    }

    fun statusLinesWithoutDigest(): String {
        val rendered = render()
        check(!HEX_VALUE.containsMatchIn(rendered)) {
            "signing-ceremony preparation output must not contain digest values"
        }
        return rendered
    }

    private companion object {
        val HEX_VALUE = Regex("\\b[0-9a-fA-F]{40,}\\b")
    }
}

object RepositorySigningCeremonyPreparationSource {
    val record = SigningCeremonyPreparationRecord(
        sourceKind = SigningCeremonySourceKind.REPOSITORY_DEFAULT,
        ceremonyIdentifier = CeremonyEvidence.Absent,
        approvedScope = CeremonyEvidence.Absent,
        unsignedCandidateSha256 = CeremonyEvidence.Absent,
        immutableSnapshotSha256 = CeremonyEvidence.Absent,
        checkoutRevision = CeremonyEvidence.Absent,
        worktreeClean = CeremonyEvidence.Absent,
        checkoutRevisionProvesApkOrigin = CeremonyEvidence.Present(false),
        offlineKeyCustodyApproval = ApprovalEvidence.Absent,
        operatorApproval = ApprovalEvidence.Absent,
        witnessApproval = ApprovalEvidence.Absent,
        recoveryBackupVerification = ApprovalEvidence.Absent,
        branchProtectionRequiredCheckVerification = ApprovalEvidence.Absent,
        requiredSignerPolicy = CeremonyEvidence.Absent,
        currentSignerCount = CeremonyEvidence.Absent,
        requiredSignatureSchemePolicy = CeremonyEvidence.Absent,
        publicCertificateSha256 = CeremonyEvidence.Absent,
        independentlySuppliedExpectedCertificateSha256 = CeremonyEvidence.Absent,
        expectedIdentity = DestructiveValidationExpectedIdentity.repositoryContract(),
        expectedIdentitySource = ExpectedIdentitySource.REPOSITORY_CONTRACT,
        postSigningReinspectionRequired = CeremonyEvidence.Absent,
        signedOutputRemainsValidationCandidate = true,
        signingEvidenceCannotMintRuntimeTrust = true,
        signedApkDigestCannotBecomeTrustedExpectation = true,
        productionSigningAuthorized = false,
        signedValidationCandidateProduced = false,
        signedCandidateSha256 = CeremonyEvidence.Absent,
        offlineKeyGenerated = false,
        publicCertificateSupplied = false,
        expectedCertificateRecorded = false,
        productionArtifactSigned = false,
        runtimeAuthorization = false,
        trustedExpectationMinted = false,
        hardwareValidationApproved = false,
        ceremonyRecordFilled = false,
    )
}

object DestructiveSigningCeremonyPreparation {
    const val BLANK_RECORD_TEMPLATE_RELATIVE_PATH =
        "docs/templates/DESTRUCTIVE_SIGNING_CEREMONY_RECORD.template.txt"
    const val FILLED_RECORD_RELATIVE_PATH =
        "local/destructive-signing-ceremony-record.txt"
    const val PREPARATION_REPORT_RELATIVE_PATH =
        "app/build/reports/destructive-signing-ceremony-preparation.txt"

    private val VALID_TOKEN = Regex("^[A-Z][A-Z0-9_]{7,127}$")
    private val CLEAN_REVISION = Regex("^[0-9a-f]{40}$")
    private val FORBIDDEN_SHORTCUTS = setOf(
        "APPROVED",
        "YES",
        "TRUE",
        "READY",
        "OK",
        "AUTHORIZED",
    )

    fun evaluateRepositoryDefault(): SigningCeremonyEvaluation {
        return evaluate(RepositorySigningCeremonyPreparationSource.record)
    }

    fun evaluate(record: SigningCeremonyPreparationRecord): SigningCeremonyEvaluation {
        val blockers = linkedSetOf<SigningCeremonyBlocker>()
        addFieldBlockers(record, blockers)
        addContradictionBlockers(record, blockers)
        if (isPartiallyFilled(record)) {
            blockers += SigningCeremonyBlocker.PARTIALLY_FILLED_STATE
        }
        if (record.sourceKind == SigningCeremonySourceKind.REPOSITORY_DEFAULT) {
            blockers += SigningCeremonyBlocker.PRODUCTION_SIGNING_NOT_AUTHORIZED
            blockers += SigningCeremonyBlocker.SIGNED_VALIDATION_CANDIDATE_NOT_PRODUCED
            blockers += SigningCeremonyBlocker.CHECKOUT_PROVENANCE_DOES_NOT_PROVE_APK_ORIGIN
        }
        val status = SigningCeremonyStatus.NOT_READY
        val publicCertValid = isValidFingerprint(record.publicCertificateSha256)
        val expectedCertValid = isValidFingerprint(
            record.independentlySuppliedExpectedCertificateSha256,
        )
        return SigningCeremonyEvaluation(
            status = status,
            blockers = blockers.toList(),
            sourceKind = record.sourceKind,
            offlineKeyGenerated = record.offlineKeyGenerated,
            publicCertificateSupplied = record.publicCertificateSupplied && publicCertValid,
            expectedCertificateRecorded = record.expectedCertificateRecorded && expectedCertValid,
            operatorApprovalAvailable = isRecordedApproval(record.operatorApproval),
            witnessApprovalAvailable = isRecordedApproval(record.witnessApproval),
            keyCustodyApproved = isRecordedApproval(record.offlineKeyCustodyApproval),
            recoveryBackupVerified = isRecordedApproval(record.recoveryBackupVerification),
            branchProtectionRequiredCheckVerified = isRecordedApproval(
                record.branchProtectionRequiredCheckVerification,
            ),
            productionArtifactSigned = record.productionArtifactSigned,
            runtimeAuthorization = record.runtimeAuthorization,
            trustedExpectationMinted = record.trustedExpectationMinted,
            signedValidationCandidateProduced = record.signedValidationCandidateProduced,
            expectedCertificateSha256Configured =
                !record.expectedIdentity.expectedCertificateSha256.isNullOrBlank(),
            checkoutRevisionProvesApkOrigin = originClaimed(record),
            trustedExpectationMintRefused = true,
            ceremonyRecordFilled = record.ceremonyRecordFilled,
            productionSigningAuthorized = record.productionSigningAuthorized,
        )
    }

    fun refuseTrustedExpectationMint(
        @Suppress("UNUSED_PARAMETER") digest: String?,
    ): Nothing? {
        return null
    }

    fun assertRepositoryDefaultStillNotReady(evaluation: SigningCeremonyEvaluation) {
        check(evaluation.sourceKind == SigningCeremonySourceKind.REPOSITORY_DEFAULT) {
            "production ceremony proof must evaluate only the repository default source"
        }
        check(evaluation.status == SigningCeremonyStatus.NOT_READY) {
            "real signing-ceremony preparation must remain NOT_READY"
        }
        check(!evaluation.offlineKeyGenerated)
        check(!evaluation.publicCertificateSupplied)
        check(!evaluation.expectedCertificateRecorded)
        check(!evaluation.operatorApprovalAvailable)
        check(!evaluation.witnessApprovalAvailable)
        check(!evaluation.keyCustodyApproved)
        check(!evaluation.recoveryBackupVerified)
        check(!evaluation.branchProtectionRequiredCheckVerified)
        check(!evaluation.productionArtifactSigned)
        check(!evaluation.runtimeAuthorization)
        check(!evaluation.trustedExpectationMinted)
        check(!evaluation.signedValidationCandidateProduced)
        check(!evaluation.expectedCertificateSha256Configured)
        check(!evaluation.checkoutRevisionProvesApkOrigin)
        check(evaluation.trustedExpectationMintRefused)
        check(!evaluation.ceremonyRecordFilled)
        check(!evaluation.productionSigningAuthorized)
        val required = setOf(
            SigningCeremonyBlocker.MISSING_CEREMONY_IDENTIFIER,
            SigningCeremonyBlocker.MISSING_OR_INVALID_APPROVED_SCOPE,
            SigningCeremonyBlocker.MISSING_IMMUTABLE_CANDIDATE_IDENTITY,
            SigningCeremonyBlocker.MISSING_OR_DIRTY_CHECKOUT_PROVENANCE,
            SigningCeremonyBlocker.CHECKOUT_PROVENANCE_DOES_NOT_PROVE_APK_ORIGIN,
            SigningCeremonyBlocker.MISSING_OFFLINE_KEY_CUSTODY_APPROVAL,
            SigningCeremonyBlocker.MISSING_OPERATOR_APPROVAL,
            SigningCeremonyBlocker.MISSING_INDEPENDENT_WITNESS_APPROVAL,
            SigningCeremonyBlocker.MISSING_RECOVERY_BACKUP_VERIFICATION,
            SigningCeremonyBlocker.MISSING_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFICATION,
            SigningCeremonyBlocker.MISSING_PUBLIC_CERTIFICATE,
            SigningCeremonyBlocker.MISSING_INDEPENDENTLY_SUPPLIED_EXPECTED_CERTIFICATE_FINGERPRINT,
            SigningCeremonyBlocker.INVALID_SIGNER_POLICY,
            SigningCeremonyBlocker.INVALID_SIGNATURE_SCHEME_POLICY,
            SigningCeremonyBlocker.MISSING_POST_SIGNING_REINSPECTION_PLAN,
            SigningCeremonyBlocker.PRODUCTION_SIGNING_NOT_AUTHORIZED,
            SigningCeremonyBlocker.SIGNED_VALIDATION_CANDIDATE_NOT_PRODUCED,
        )
        check(evaluation.blockers.containsAll(required)) {
            "repository default evaluation is missing required blockers: " +
                (required - evaluation.blockers.toSet())
        }
        check(
            DestructiveValidationExpectedIdentity.repositoryContract()
                .expectedCertificateSha256 == null,
        ) {
            "repository expectedCertificateSha256 must remain null"
        }
        check(refuseTrustedExpectationMint(null) == null) {
            "signing-ceremony preparation must refuse trusted-expectation minting"
        }
    }

    private fun addFieldBlockers(
        record: SigningCeremonyPreparationRecord,
        blockers: MutableSet<SigningCeremonyBlocker>,
    ) {
        when (val identifier = record.ceremonyIdentifier) {
            CeremonyEvidence.Absent ->
                blockers += SigningCeremonyBlocker.MISSING_CEREMONY_IDENTIFIER
            CeremonyEvidence.Unknown, CeremonyEvidence.Malformed ->
                blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
            is CeremonyEvidence.Present -> {
                if (!isValidToken(identifier.value)) {
                    blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
                }
            }
        }
        when (val scope = record.approvedScope) {
            CeremonyEvidence.Absent ->
                blockers += SigningCeremonyBlocker.MISSING_OR_INVALID_APPROVED_SCOPE
            CeremonyEvidence.Unknown, CeremonyEvidence.Malformed ->
                blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
            is CeremonyEvidence.Present -> {
                if (scope.value !=
                    ApprovedCeremonyScope.DISPOSABLE_DEVICE_VALIDATION_CANDIDATE_SIGNING
                ) {
                    blockers += SigningCeremonyBlocker.MISSING_OR_INVALID_APPROVED_SCOPE
                }
            }
        }
        if (!isValidFingerprint(record.unsignedCandidateSha256) ||
            !isValidFingerprint(record.immutableSnapshotSha256)
        ) {
            when {
                record.unsignedCandidateSha256 is CeremonyEvidence.Unknown ||
                    record.unsignedCandidateSha256 is CeremonyEvidence.Malformed ||
                    record.immutableSnapshotSha256 is CeremonyEvidence.Unknown ||
                    record.immutableSnapshotSha256 is CeremonyEvidence.Malformed ||
                    malformedFingerprint(record.unsignedCandidateSha256) ||
                    malformedFingerprint(record.immutableSnapshotSha256) ->
                    blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
                else ->
                    blockers += SigningCeremonyBlocker.MISSING_IMMUTABLE_CANDIDATE_IDENTITY
            }
        }
        addCheckoutBlockers(record, blockers)
        addApprovalBlocker(
            record.offlineKeyCustodyApproval,
            SigningCeremonyBlocker.MISSING_OFFLINE_KEY_CUSTODY_APPROVAL,
            blockers,
        )
        addApprovalBlocker(
            record.operatorApproval,
            SigningCeremonyBlocker.MISSING_OPERATOR_APPROVAL,
            blockers,
        )
        addApprovalBlocker(
            record.witnessApproval,
            SigningCeremonyBlocker.MISSING_INDEPENDENT_WITNESS_APPROVAL,
            blockers,
        )
        addApprovalBlocker(
            record.recoveryBackupVerification,
            SigningCeremonyBlocker.MISSING_RECOVERY_BACKUP_VERIFICATION,
            blockers,
        )
        addApprovalBlocker(
            record.branchProtectionRequiredCheckVerification,
            SigningCeremonyBlocker.MISSING_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFICATION,
            blockers,
        )
        addSignerPolicyBlockers(record, blockers)
        addSignatureSchemeBlockers(record, blockers)
        addCertificateBlockers(record, blockers)
        when (val plan = record.postSigningReinspectionRequired) {
            CeremonyEvidence.Absent ->
                blockers += SigningCeremonyBlocker.MISSING_POST_SIGNING_REINSPECTION_PLAN
            CeremonyEvidence.Unknown, CeremonyEvidence.Malformed ->
                blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
            is CeremonyEvidence.Present -> {
                if (!plan.value) {
                    blockers += SigningCeremonyBlocker.MISSING_POST_SIGNING_REINSPECTION_PLAN
                }
            }
        }
        if (!record.signedOutputRemainsValidationCandidate ||
            !record.signingEvidenceCannotMintRuntimeTrust ||
            !record.signedApkDigestCannotBecomeTrustedExpectation
        ) {
            blockers += SigningCeremonyBlocker.CONTRADICTORY_STATE
        }
        if (record.sourceKind != SigningCeremonySourceKind.REPOSITORY_DEFAULT &&
            !record.signedValidationCandidateProduced
        ) {
            blockers += SigningCeremonyBlocker.SIGNED_VALIDATION_CANDIDATE_NOT_PRODUCED
        }
    }

    private fun addCheckoutBlockers(
        record: SigningCeremonyPreparationRecord,
        blockers: MutableSet<SigningCeremonyBlocker>,
    ) {
        val revision = record.checkoutRevision
        val clean = record.worktreeClean
        val revisionValid = revision is CeremonyEvidence.Present &&
            CLEAN_REVISION.matches(revision.value)
        val worktreeKnownClean = clean is CeremonyEvidence.Present && clean.value
        if (revision is CeremonyEvidence.Unknown ||
            revision is CeremonyEvidence.Malformed ||
            clean is CeremonyEvidence.Unknown ||
            clean is CeremonyEvidence.Malformed ||
            (revision is CeremonyEvidence.Present && !CLEAN_REVISION.matches(revision.value))
        ) {
            blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
        }
        if (!revisionValid || !worktreeKnownClean) {
            blockers += SigningCeremonyBlocker.MISSING_OR_DIRTY_CHECKOUT_PROVENANCE
        }
        when (val origin = record.checkoutRevisionProvesApkOrigin) {
            CeremonyEvidence.Absent, CeremonyEvidence.Unknown, CeremonyEvidence.Malformed ->
                blockers += SigningCeremonyBlocker.CHECKOUT_PROVENANCE_DOES_NOT_PROVE_APK_ORIGIN
            is CeremonyEvidence.Present -> {
                if (origin.value) {
                    blockers += SigningCeremonyBlocker.CHECKOUT_PROVENANCE_DOES_NOT_PROVE_APK_ORIGIN
                    blockers += SigningCeremonyBlocker.CONTRADICTORY_STATE
                }
            }
        }
    }

    private fun addApprovalBlocker(
        evidence: ApprovalEvidence,
        missing: SigningCeremonyBlocker,
        blockers: MutableSet<SigningCeremonyBlocker>,
    ) {
        when (evidence) {
            ApprovalEvidence.Absent -> blockers += missing
            ApprovalEvidence.Unknown, ApprovalEvidence.Malformed ->
                blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
            is ApprovalEvidence.Recorded -> {
                if (!isValidToken(evidence.reference)) {
                    blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
                }
            }
        }
    }

    private fun addSignerPolicyBlockers(
        record: SigningCeremonyPreparationRecord,
        blockers: MutableSet<SigningCeremonyBlocker>,
    ) {
        val policy = record.requiredSignerPolicy
        val count = record.currentSignerCount
        val policyValid = policy is CeremonyEvidence.Present &&
            policy.value == RequiredSignerPolicy.SINGLE_CURRENT_SIGNER
        val countValid = count is CeremonyEvidence.Present && count.value == 1
        if (policy is CeremonyEvidence.Unknown ||
            policy is CeremonyEvidence.Malformed ||
            count is CeremonyEvidence.Unknown ||
            count is CeremonyEvidence.Malformed
        ) {
            blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
        }
        if (!policyValid || !countValid) {
            blockers += SigningCeremonyBlocker.INVALID_SIGNER_POLICY
        }
    }

    private fun addSignatureSchemeBlockers(
        record: SigningCeremonyPreparationRecord,
        blockers: MutableSet<SigningCeremonyBlocker>,
    ) {
        when (val policy = record.requiredSignatureSchemePolicy) {
            CeremonyEvidence.Absent ->
                blockers += SigningCeremonyBlocker.INVALID_SIGNATURE_SCHEME_POLICY
            CeremonyEvidence.Unknown, CeremonyEvidence.Malformed ->
                blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
            is CeremonyEvidence.Present -> {
                if (policy.value != RequiredSignatureSchemePolicy.V2_AND_V3_REQUIRED) {
                    blockers += SigningCeremonyBlocker.INVALID_SIGNATURE_SCHEME_POLICY
                }
            }
        }
    }

    private fun addCertificateBlockers(
        record: SigningCeremonyPreparationRecord,
        blockers: MutableSet<SigningCeremonyBlocker>,
    ) {
        val publicValid = isValidFingerprint(record.publicCertificateSha256)
        val expectedValid = isValidFingerprint(
            record.independentlySuppliedExpectedCertificateSha256,
        )
        if (malformedFingerprint(record.publicCertificateSha256) ||
            record.publicCertificateSha256 is CeremonyEvidence.Unknown ||
            record.publicCertificateSha256 is CeremonyEvidence.Malformed
        ) {
            blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
        }
        if (malformedFingerprint(record.independentlySuppliedExpectedCertificateSha256) ||
            record.independentlySuppliedExpectedCertificateSha256 is CeremonyEvidence.Unknown ||
            record.independentlySuppliedExpectedCertificateSha256 is CeremonyEvidence.Malformed
        ) {
            blockers += SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE
        }
        if (!publicValid) {
            blockers += SigningCeremonyBlocker.MISSING_PUBLIC_CERTIFICATE
        }
        if (!expectedValid) {
            blockers +=
                SigningCeremonyBlocker.MISSING_INDEPENDENTLY_SUPPLIED_EXPECTED_CERTIFICATE_FINGERPRINT
        }
        if (publicValid && expectedValid) {
            val publicValue = normalizePresent(record.publicCertificateSha256)
            val expectedValue = normalizePresent(
                record.independentlySuppliedExpectedCertificateSha256,
            )
            if (publicValue != expectedValue) {
                blockers += SigningCeremonyBlocker.CONTRADICTORY_STATE
            }
        }
        if (record.publicCertificateSupplied != publicValid ||
            record.expectedCertificateRecorded != expectedValid
        ) {
            blockers += SigningCeremonyBlocker.CONTRADICTORY_STATE
        }
    }

    private fun addContradictionBlockers(
        record: SigningCeremonyPreparationRecord,
        blockers: MutableSet<SigningCeremonyBlocker>,
    ) {
        if (record.runtimeAuthorization ||
            record.trustedExpectationMinted ||
            record.productionSigningAuthorized ||
            record.productionArtifactSigned ||
            record.hardwareValidationApproved ||
            record.offlineKeyGenerated ||
            record.expectedIdentitySource == ExpectedIdentitySource.OBSERVED_APK
        ) {
            blockers += SigningCeremonyBlocker.CONTRADICTORY_STATE
        }
        if (record.productionSigningAuthorized) {
            blockers += SigningCeremonyBlocker.PRODUCTION_SIGNING_NOT_AUTHORIZED
        }
        if (record.signedValidationCandidateProduced !=
            isValidFingerprint(record.signedCandidateSha256)
        ) {
            blockers += SigningCeremonyBlocker.CONTRADICTORY_STATE
        }
        if (record.ceremonyRecordFilled &&
            record.sourceKind == SigningCeremonySourceKind.REPOSITORY_DEFAULT
        ) {
            blockers += SigningCeremonyBlocker.CONTRADICTORY_STATE
        }
        if (!record.expectedIdentity.expectedCertificateSha256.isNullOrBlank()) {
            blockers += SigningCeremonyBlocker.CONTRADICTORY_STATE
        }
    }

    private fun isPartiallyFilled(record: SigningCeremonyPreparationRecord): Boolean {
        val fillable = listOf(
            record.ceremonyIdentifier !is CeremonyEvidence.Absent,
            record.approvedScope !is CeremonyEvidence.Absent,
            record.unsignedCandidateSha256 !is CeremonyEvidence.Absent,
            record.immutableSnapshotSha256 !is CeremonyEvidence.Absent,
            record.checkoutRevision !is CeremonyEvidence.Absent,
            record.worktreeClean !is CeremonyEvidence.Absent,
            record.offlineKeyCustodyApproval !is ApprovalEvidence.Absent,
            record.operatorApproval !is ApprovalEvidence.Absent,
            record.witnessApproval !is ApprovalEvidence.Absent,
            record.recoveryBackupVerification !is ApprovalEvidence.Absent,
            record.branchProtectionRequiredCheckVerification !is ApprovalEvidence.Absent,
            record.requiredSignerPolicy !is CeremonyEvidence.Absent,
            record.currentSignerCount !is CeremonyEvidence.Absent,
            record.requiredSignatureSchemePolicy !is CeremonyEvidence.Absent,
            record.publicCertificateSha256 !is CeremonyEvidence.Absent,
            record.independentlySuppliedExpectedCertificateSha256 !is CeremonyEvidence.Absent,
            record.postSigningReinspectionRequired !is CeremonyEvidence.Absent,
            record.signedCandidateSha256 !is CeremonyEvidence.Absent,
        )
        return fillable.any { it } && fillable.any { !it }
    }

    private fun isValidToken(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.uppercase() in FORBIDDEN_SHORTCUTS) {
            return false
        }
        return VALID_TOKEN.matches(trimmed)
    }

    private fun isValidFingerprint(evidence: CeremonyEvidence<String>): Boolean {
        return normalizePresent(evidence) != null
    }

    private fun malformedFingerprint(evidence: CeremonyEvidence<String>): Boolean {
        return evidence is CeremonyEvidence.Present &&
            ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(evidence.value) == null
    }

    private fun normalizePresent(evidence: CeremonyEvidence<String>): String? {
        val present = evidence as? CeremonyEvidence.Present ?: return null
        return ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(present.value)
    }

    private fun isRecordedApproval(evidence: ApprovalEvidence): Boolean {
        return evidence is ApprovalEvidence.Recorded && isValidToken(evidence.reference)
    }

    private fun originClaimed(record: SigningCeremonyPreparationRecord): Boolean {
        val origin = record.checkoutRevisionProvesApkOrigin
        return origin is CeremonyEvidence.Present && origin.value
    }
}
