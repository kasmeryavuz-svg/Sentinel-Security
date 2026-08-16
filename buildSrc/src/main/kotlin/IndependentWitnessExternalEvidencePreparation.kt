import java.time.Instant

/**
 * Fail-closed evaluation of external witness-evidence verification
 * preparation.
 *
 * Mechanical evidence and review validity are not independence, not
 * enrollment, and not runtime authorization. 19V never enrolls a
 * witness and never mints wipe or hardware authority.
 */
object IndependentWitnessExternalEvidencePreparation {
    const val TASK_PATH = ":app:checkIndependentWitnessExternalEvidencePreparation"
    const val REPORT_RELATIVE_PATH =
        "app/build/reports/independent-witness-external-evidence-preparation.txt"
    const val STATUS_NOT_PRESENT = "NOT_PRESENT"
    const val STATUS_PRESENT_UNVERIFIED = "PRESENT_UNVERIFIED"
    const val STATUS_MECHANICS_VERIFIED = "MECHANICS_VERIFIED"
    private val HEX_VALUE = Regex("\\b[0-9a-fA-F]{40,}\\b")

    data class BindingExpectation(
        val witnessIdentifier: String,
        val repositoryRevision: String,
        val witnessVerificationKeySha256: String,
        val evidenceDigestSha256: String,
        val evaluationInstantUtc: Instant,
    )

    data class Evaluation(
        val externalEvidenceCount: Int,
        val reviewAttestationCount: Int,
        val externalEvidencePresent: Boolean,
        val externalEvidenceWellFormed: Boolean,
        val externalEvidenceSubjectMatchesWitness: Boolean,
        val externalEvidenceRepositoryBindingValid: Boolean,
        val externalEvidenceFresh: Boolean,
        val reviewAttestationPresent: Boolean,
        val reviewAttestationWellFormed: Boolean,
        val reviewerSeparateFromOperator: Boolean,
        val reviewerSeparateFromWitness: Boolean,
        val reviewBindingValid: Boolean,
        val externalIndependenceEvidenceVerified: Boolean,
    ) {
        val status: String
            get() = when {
                externalIndependenceEvidenceVerified -> STATUS_MECHANICS_VERIFIED
                externalEvidencePresent -> STATUS_PRESENT_UNVERIFIED
                else -> STATUS_NOT_PRESENT
            }

        fun render(): String {
            val established =
                IndependentWitnessAuthorityContract.establishedWitnessIdentifiers()
            val rendered = buildString {
                appendLine("checkpoint=19V")
                appendLine("external_witness_evidence_status=$status")
                appendLine("external_evidence_count=$externalEvidenceCount")
                appendLine("review_attestation_count=$reviewAttestationCount")
                appendLine("external_evidence_present=$externalEvidencePresent")
                appendLine("external_evidence_well_formed=$externalEvidenceWellFormed")
                appendLine(
                    "external_evidence_subject_matches_witness=" +
                        "$externalEvidenceSubjectMatchesWitness",
                )
                appendLine(
                    "external_evidence_repository_binding_valid=" +
                        "$externalEvidenceRepositoryBindingValid",
                )
                appendLine("external_evidence_fresh=$externalEvidenceFresh")
                appendLine("review_attestation_present=$reviewAttestationPresent")
                appendLine("review_attestation_well_formed=$reviewAttestationWellFormed")
                appendLine(
                    "reviewer_separate_from_operator=$reviewerSeparateFromOperator",
                )
                appendLine(
                    "reviewer_separate_from_witness=$reviewerSeparateFromWitness",
                )
                appendLine("review_binding_valid=$reviewBindingValid")
                appendLine(
                    "external_independence_evidence_verified=" +
                        "$externalIndependenceEvidenceVerified",
                )
                appendLine("established_witness_count=${established.size}")
                appendLine("witness_authority_enrolled=false")
                appendLine("witness_independence_established=false")
                appendLine("independent_witness_approval=false")
                appendLine("authority=${ValidationOnlySigningGate.AUTHORITY}")
                appendLine("runtime_authorization=false")
                appendLine("trusted_expectation_minted=false")
                appendLine("production_distribution=false")
                appendLine("customer_device_authorized=false")
                appendLine("real_device_identity_recorded=false")
                appendLine("hardware_validation_approved=false")
                appendLine("hardware_test_performed=false")
                appendLine("evidence_authorizes_hardware_test=false")
                appendLine("evidence_authorizes_wipe=false")
                appendLine("ci_is_independent_witness=false")
                appendLine("operator_is_independent_witness=false")
                appendLine("local_receipt_is_independent_witness=false")
            }
            check(!HEX_VALUE.containsMatchIn(rendered)) {
                "19V external-evidence-preparation report must not contain digest values"
            }
            check(established.isEmpty()) {
                "19V report must not observe an enrolled witness identifier"
            }
            return rendered
        }
    }

    fun evaluate(
        evidenceRecords: List<IndependentWitnessExternalEvidence.Record>,
        reviewRecords: List<IndependentWitnessEnrollmentReview.Record>,
        expectation: BindingExpectation?,
    ): Evaluation {
        val evidencePresent = evidenceRecords.isNotEmpty()
        val evidence = evidenceRecords.singleOrNull()
        val evidenceWellFormed = evidence != null &&
            IndependentWitnessExternalEvidence.isStructurallyValid(evidence)
        val subjectMatches = evidenceWellFormed &&
            expectation != null &&
            IndependentWitnessAuthorityEnrollment.identifiersMatch(
                evidence!!.witnessIdentifier,
                expectation.witnessIdentifier,
            )
        val repositoryBindingValid = evidenceWellFormed &&
            expectation != null &&
            evidence!!.repositoryRevision ==
            expectation.repositoryRevision.trim().lowercase() &&
            evidence.witnessVerificationKeySha256 ==
            IndependentWitnessAuthorityEnrollment.requireSha256Hex(
                expectation.witnessVerificationKeySha256,
                "witness_verification_key_sha256",
            ) &&
            evidence.evidenceDigestSha256 ==
            IndependentWitnessAuthorityEnrollment.requireSha256Hex(
                expectation.evidenceDigestSha256,
                "evidence_digest_sha256",
            )
        val fresh = evidenceWellFormed &&
            expectation != null &&
            IndependentWitnessExternalEvidence.isFreshAt(
                evidence!!.evidenceTimestampUtc,
                evidence.evidenceExpiryUtc,
                expectation.evaluationInstantUtc,
            )
        val reviewPresent = reviewRecords.isNotEmpty()
        val review = reviewRecords.singleOrNull()
        val reviewWellFormed = review != null &&
            IndependentWitnessEnrollmentReview.isStructurallyValid(review)
        val reviewerSeparateOperator = reviewWellFormed &&
            IndependentWitnessEnrollmentReview.reviewerSeparatedFromOperator(review!!)
        val reviewerSeparateWitness = reviewWellFormed &&
            IndependentWitnessEnrollmentReview.reviewerSeparatedFromWitness(review!!)
        val operatorSeparateWitness = reviewWellFormed &&
            IndependentWitnessEnrollmentReview.operatorSeparatedFromWitness(review!!)
        val reviewBindingValid = evidenceWellFormed &&
            reviewWellFormed &&
            operatorSeparateWitness &&
            IndependentWitnessAuthorityEnrollment.identifiersMatch(
                review!!.evidenceIdentifier,
                evidence!!.evidenceIdentifier,
            ) &&
            IndependentWitnessAuthorityEnrollment.identifiersMatch(
                review.witnessIdentifier,
                evidence.witnessIdentifier,
            ) &&
            review.repositoryRevision == evidence.repositoryRevision &&
            !IndependentWitnessAuthorityEnrollment.identifiersMatch(
                review.reviewerIdentifier,
                evidence.evidenceIssuerIdentifier,
            )
        val verified = evidenceWellFormed &&
            subjectMatches &&
            repositoryBindingValid &&
            fresh &&
            reviewWellFormed &&
            reviewerSeparateOperator &&
            reviewerSeparateWitness &&
            reviewBindingValid &&
            evidence!!.reviewRequired &&
            review!!.decision ==
            IndependentWitnessEnrollmentReview.Decision.APPROVE_EVIDENCE_MECHANICS
        return Evaluation(
            externalEvidenceCount = evidenceRecords.size,
            reviewAttestationCount = reviewRecords.size,
            externalEvidencePresent = evidencePresent,
            externalEvidenceWellFormed = evidenceWellFormed,
            externalEvidenceSubjectMatchesWitness = subjectMatches,
            externalEvidenceRepositoryBindingValid = repositoryBindingValid,
            externalEvidenceFresh = fresh,
            reviewAttestationPresent = reviewPresent,
            reviewAttestationWellFormed = reviewWellFormed,
            reviewerSeparateFromOperator = reviewerSeparateOperator,
            reviewerSeparateFromWitness = reviewerSeparateWitness,
            reviewBindingValid = reviewBindingValid,
            externalIndependenceEvidenceVerified = verified,
        )
    }

    fun evaluateRepositoryDefault(): Evaluation {
        IndependentWitnessExternalEvidenceSource.assertEmpty()
        check(!IndependentWitnessAuthorityContract.ciIsIndependentWitness())
        check(!IndependentWitnessAuthorityContract.localOperatorIsIndependentWitness())
        check(!IndependentWitnessAuthorityContract.localReceiptIsIndependentWitness())
        val evaluation = evaluate(
            evidenceRecords =
                IndependentWitnessExternalEvidenceSource.repositoryExternalWitnessEvidence(),
            reviewRecords =
                IndependentWitnessExternalEvidenceSource.repositoryWitnessReviews(),
            expectation = null,
        )
        check(!evaluation.externalEvidencePresent)
        check(!evaluation.reviewAttestationPresent)
        check(!evaluation.externalIndependenceEvidenceVerified)
        check(evaluation.status == STATUS_NOT_PRESENT)
        check(
            !IndependentWitnessAuthorityContract.independenceEstablished("external-witness"),
        )
        check(
            !IndependentWitnessAuthorityContract.approval(
                statementPresent = true,
                signatureVerified = true,
                evidenceMatches = true,
                independenceEstablished = false,
            ),
        )
        return evaluation
    }

    fun verifiedEvidenceImpliesApproval(verified: Boolean): Boolean {
        return false && verified
    }

    fun verifiedEvidenceImpliesEnrollment(verified: Boolean): Boolean {
        return false && verified
    }
}
