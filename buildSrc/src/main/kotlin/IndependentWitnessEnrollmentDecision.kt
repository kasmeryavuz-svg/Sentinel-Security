/**
 * Fail-closed independent-witness enrollment **decision** contract.
 *
 * 19T, 19U, and 19V supply separate mechanical inputs. 19W combines them
 * into one read-only decision. Mechanical completeness is not enrollment,
 * not independent-witness approval, and not runtime or wipe authority.
 *
 * The repository authority sources remain empty, so the decision stays
 * BLOCKED even when a synthetic fixture satisfies every mechanical input.
 */
object IndependentWitnessEnrollmentDecision {
    const val CHECKPOINT = "19W"
    const val TASK_PATH = ":app:checkIndependentWitnessEnrollmentDecisionContract"
    const val REPORT_RELATIVE_PATH =
        "app/build/reports/independent-witness-enrollment-decision-contract.txt"
    const val LOCAL_DECISION_RELATIVE_PATH =
        "local/independent-witness-enrollment-decision.txt"
    const val DECISION_BLOCKED = "BLOCKED"
    const val DECISION_ENROLLED = "ENROLLED"
    private val HEX_VALUE = Regex("\\b[0-9a-fA-F]{40,}\\b")

    enum class Blocker {
        MISSING_WITNESS_STATEMENT,
        SIGNATURE_NOT_VERIFIED,
        CANDIDATE_EVIDENCE_MISMATCH,
        MISSING_ENROLLMENT_RECORD,
        INVALID_ENROLLMENT_RECORD,
        MISSING_EXTERNAL_EVIDENCE,
        INVALID_EXTERNAL_EVIDENCE,
        STALE_EXTERNAL_EVIDENCE,
        MISSING_INDEPENDENT_REVIEW,
        INVALID_REVIEW_BINDING,
        OPERATOR_WITNESS_NOT_SEPARATE,
        REVIEWER_OPERATOR_NOT_SEPARATE,
        REVIEWER_WITNESS_NOT_SEPARATE,
        REPOSITORY_REVISION_MISMATCH,
        EXTERNAL_INDEPENDENCE_EVIDENCE_NOT_VERIFIED,
        NO_REPOSITORY_AUTHORITY_ENROLLMENT,
        ;

        fun applies(inputs: Inputs, repositoryAuthorityEnrolled: Boolean): Boolean {
            return when (this) {
                MISSING_WITNESS_STATEMENT -> !inputs.witnessStatementPresent
                SIGNATURE_NOT_VERIFIED -> !inputs.witnessSignatureVerified
                CANDIDATE_EVIDENCE_MISMATCH -> !inputs.witnessEvidenceMatchesCandidate
                MISSING_ENROLLMENT_RECORD -> !inputs.enrollmentRecordPresent
                INVALID_ENROLLMENT_RECORD ->
                    !inputs.enrollmentRecordWellFormed ||
                        !inputs.witnessIdentifierValid ||
                        !inputs.verificationKeyFingerprintValid
                MISSING_EXTERNAL_EVIDENCE -> !inputs.externalEvidencePresent
                INVALID_EXTERNAL_EVIDENCE ->
                    !inputs.externalEvidenceWellFormed ||
                        !inputs.externalEvidenceSubjectMatchesWitness
                STALE_EXTERNAL_EVIDENCE -> !inputs.externalEvidenceFresh
                MISSING_INDEPENDENT_REVIEW -> !inputs.reviewAttestationPresent
                INVALID_REVIEW_BINDING ->
                    !inputs.reviewAttestationWellFormed || !inputs.reviewBindingValid
                OPERATOR_WITNESS_NOT_SEPARATE -> !inputs.operatorWitnessSeparationClaimed
                REVIEWER_OPERATOR_NOT_SEPARATE -> !inputs.reviewerSeparateFromOperator
                REVIEWER_WITNESS_NOT_SEPARATE -> !inputs.reviewerSeparateFromWitness
                REPOSITORY_REVISION_MISMATCH ->
                    !inputs.enrollmentRepositoryBindingValid ||
                        !inputs.externalEvidenceRepositoryBindingValid
                EXTERNAL_INDEPENDENCE_EVIDENCE_NOT_VERIFIED ->
                    !inputs.externalIndependenceEvidenceVerified
                NO_REPOSITORY_AUTHORITY_ENROLLMENT -> !repositoryAuthorityEnrolled
            }
        }
    }

    data class Inputs(
        val witnessStatementPresent: Boolean,
        val witnessSignatureVerified: Boolean,
        val witnessEvidenceMatchesCandidate: Boolean,
        val enrollmentRecordPresent: Boolean,
        val enrollmentRecordWellFormed: Boolean,
        val witnessIdentifierValid: Boolean,
        val verificationKeyFingerprintValid: Boolean,
        val operatorWitnessSeparationClaimed: Boolean,
        val enrollmentRepositoryBindingValid: Boolean,
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
        fun enrollmentCandidateMechanicsSatisfied(): Boolean {
            return witnessStatementPresent &&
                witnessSignatureVerified &&
                witnessEvidenceMatchesCandidate &&
                enrollmentRecordPresent &&
                enrollmentRecordWellFormed &&
                witnessIdentifierValid &&
                verificationKeyFingerprintValid &&
                operatorWitnessSeparationClaimed &&
                enrollmentRepositoryBindingValid &&
                externalEvidencePresent &&
                externalEvidenceWellFormed &&
                externalEvidenceSubjectMatchesWitness &&
                externalEvidenceRepositoryBindingValid &&
                externalEvidenceFresh &&
                reviewAttestationPresent &&
                reviewAttestationWellFormed &&
                reviewerSeparateFromOperator &&
                reviewerSeparateFromWitness &&
                reviewBindingValid &&
                externalIndependenceEvidenceVerified
        }

        companion object {
            fun none(): Inputs {
                return Inputs(
                    witnessStatementPresent = false,
                    witnessSignatureVerified = false,
                    witnessEvidenceMatchesCandidate = false,
                    enrollmentRecordPresent = false,
                    enrollmentRecordWellFormed = false,
                    witnessIdentifierValid = false,
                    verificationKeyFingerprintValid = false,
                    operatorWitnessSeparationClaimed = false,
                    enrollmentRepositoryBindingValid = false,
                    externalEvidencePresent = false,
                    externalEvidenceWellFormed = false,
                    externalEvidenceSubjectMatchesWitness = false,
                    externalEvidenceRepositoryBindingValid = false,
                    externalEvidenceFresh = false,
                    reviewAttestationPresent = false,
                    reviewAttestationWellFormed = false,
                    reviewerSeparateFromOperator = false,
                    reviewerSeparateFromWitness = false,
                    reviewBindingValid = false,
                    externalIndependenceEvidenceVerified = false,
                )
            }

            fun syntheticCompleteMechanics(): Inputs {
                return none().copy(
                    witnessStatementPresent = true,
                    witnessSignatureVerified = true,
                    witnessEvidenceMatchesCandidate = true,
                    enrollmentRecordPresent = true,
                    enrollmentRecordWellFormed = true,
                    witnessIdentifierValid = true,
                    verificationKeyFingerprintValid = true,
                    operatorWitnessSeparationClaimed = true,
                    enrollmentRepositoryBindingValid = true,
                    externalEvidencePresent = true,
                    externalEvidenceWellFormed = true,
                    externalEvidenceSubjectMatchesWitness = true,
                    externalEvidenceRepositoryBindingValid = true,
                    externalEvidenceFresh = true,
                    reviewAttestationPresent = true,
                    reviewAttestationWellFormed = true,
                    reviewerSeparateFromOperator = true,
                    reviewerSeparateFromWitness = true,
                    reviewBindingValid = true,
                    externalIndependenceEvidenceVerified = true,
                )
            }
        }
    }

    data class Evaluation(
        val inputs: Inputs,
        val enrollmentCandidateMechanicsSatisfied: Boolean,
        val blockers: List<Blocker>,
    ) {
        val decision: String
            get() = if (repositoryAuthorityIsEnrolled()) {
                DECISION_ENROLLED
            } else {
                DECISION_BLOCKED
            }

        fun render(): String {
            val established =
                IndependentWitnessAuthorityContract.establishedWitnessIdentifiers()
            val distinctBlockers = blockers.distinct()
            check(blockers == distinctBlockers) {
                "19W decision blockers must not contain duplicates"
            }
            check(blockers == blockers.sortedBy { it.ordinal }) {
                "19W decision blockers must use stable enum order"
            }
            val rendered = buildString {
                appendLine("checkpoint=$CHECKPOINT")
                appendLine("witness_enrollment_decision=$decision")
                appendLine("witness_statement_present=${inputs.witnessStatementPresent}")
                appendLine("witness_signature_verified=${inputs.witnessSignatureVerified}")
                appendLine(
                    "witness_evidence_matches_candidate=" +
                        "${inputs.witnessEvidenceMatchesCandidate}",
                )
                appendLine("enrollment_record_present=${inputs.enrollmentRecordPresent}")
                appendLine(
                    "enrollment_record_well_formed=${inputs.enrollmentRecordWellFormed}",
                )
                appendLine("witness_identifier_valid=${inputs.witnessIdentifierValid}")
                appendLine(
                    "verification_key_fingerprint_valid=" +
                        "${inputs.verificationKeyFingerprintValid}",
                )
                appendLine(
                    "operator_witness_separation_claimed=" +
                        "${inputs.operatorWitnessSeparationClaimed}",
                )
                appendLine(
                    "enrollment_repository_binding_valid=" +
                        "${inputs.enrollmentRepositoryBindingValid}",
                )
                appendLine("external_evidence_present=${inputs.externalEvidencePresent}")
                appendLine(
                    "external_evidence_well_formed=${inputs.externalEvidenceWellFormed}",
                )
                appendLine(
                    "external_evidence_subject_matches_witness=" +
                        "${inputs.externalEvidenceSubjectMatchesWitness}",
                )
                appendLine(
                    "external_evidence_repository_binding_valid=" +
                        "${inputs.externalEvidenceRepositoryBindingValid}",
                )
                appendLine("external_evidence_fresh=${inputs.externalEvidenceFresh}")
                appendLine("review_attestation_present=${inputs.reviewAttestationPresent}")
                appendLine(
                    "review_attestation_well_formed=${inputs.reviewAttestationWellFormed}",
                )
                appendLine(
                    "reviewer_separate_from_operator=" +
                        "${inputs.reviewerSeparateFromOperator}",
                )
                appendLine(
                    "reviewer_separate_from_witness=${inputs.reviewerSeparateFromWitness}",
                )
                appendLine("review_binding_valid=${inputs.reviewBindingValid}")
                appendLine(
                    "external_independence_evidence_verified=" +
                        "${inputs.externalIndependenceEvidenceVerified}",
                )
                appendLine(
                    "enrollment_candidate_mechanics_satisfied=" +
                        "$enrollmentCandidateMechanicsSatisfied",
                )
                appendLine("witness_authority_enrolled=false")
                appendLine("established_witness_count=${established.size}")
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
                appendLine("decision_authorizes_hardware_test=false")
                appendLine("decision_authorizes_wipe=false")
                appendLine("decision_blocker_count=${blockers.size}")
                appendLine("decision_blockers=${blockers.joinToString(",") { it.name }}")
                blockers.forEach { blocker ->
                    appendLine("blocker=${blocker.name}")
                }
                appendLine("ci_is_independent_witness=false")
                appendLine("operator_is_independent_witness=false")
                appendLine("local_receipt_is_independent_witness=false")
            }
            check(!HEX_VALUE.containsMatchIn(rendered)) {
                "19W enrollment-decision report must not contain digest values"
            }
            check(established.isEmpty()) {
                "19W report must not observe an enrolled witness identifier"
            }
            check(decision == DECISION_BLOCKED) {
                "19W must not report an enrolled witness-authority decision"
            }
            return rendered
        }
    }

    fun evaluate(inputs: Inputs): Evaluation {
        val mechanics = inputs.enrollmentCandidateMechanicsSatisfied()
        val blockers = Blocker.values().filter { blocker ->
            blocker.applies(inputs, repositoryAuthorityIsEnrolled())
        }
        check(blockers == blockers.distinct())
        check(Blocker.NO_REPOSITORY_AUTHORITY_ENROLLMENT in blockers) {
            "19W must keep NO_REPOSITORY_AUTHORITY_ENROLLMENT while sources are empty"
        }
        check(!mechanics || blockers == listOf(Blocker.NO_REPOSITORY_AUTHORITY_ENROLLMENT))
        return Evaluation(
            inputs = inputs,
            enrollmentCandidateMechanicsSatisfied = mechanics,
            blockers = blockers,
        )
    }

    fun evaluateRepositoryDefault(): Evaluation {
        IndependentWitnessExternalEvidenceSource.assertEmpty()
        IndependentWitnessAuthorityEnrollmentSource.assertNotEnrolled()
        check(!IndependentWitnessAuthorityContract.ciIsIndependentWitness())
        check(!IndependentWitnessAuthorityContract.localOperatorIsIndependentWitness())
        check(!IndependentWitnessAuthorityContract.localReceiptIsIndependentWitness())
        val statement = IndependentWitnessVerification.contractEvaluation()
        val enrollment =
            IndependentWitnessAuthorityEnrollmentPreparation.evaluateRepositoryDefault()
        val evidence =
            IndependentWitnessExternalEvidencePreparation.evaluateRepositoryDefault()
        val evaluation = evaluate(inputsFrom(statement, enrollment, evidence))
        check(evaluation.decision == DECISION_BLOCKED)
        check(!evaluation.enrollmentCandidateMechanicsSatisfied)
        check(!repositoryAuthorityIsEnrolled())
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

    fun inputsFrom(
        statement: IndependentWitnessVerification.Evaluation,
        enrollment: IndependentWitnessAuthorityEnrollmentPreparation.Evaluation,
        evidence: IndependentWitnessExternalEvidencePreparation.Evaluation,
    ): Inputs {
        return Inputs(
            witnessStatementPresent = statement.witnessStatementPresent,
            witnessSignatureVerified = statement.witnessSignatureVerified,
            witnessEvidenceMatchesCandidate = statement.witnessEvidenceMatchesCandidate,
            enrollmentRecordPresent = enrollment.enrollmentRecordPresent,
            enrollmentRecordWellFormed = enrollment.enrollmentRecordWellFormed,
            witnessIdentifierValid = enrollment.witnessIdentifierValid,
            verificationKeyFingerprintValid = enrollment.verificationKeyFingerprintValid,
            operatorWitnessSeparationClaimed = enrollment.operatorWitnessSeparationClaimed,
            enrollmentRepositoryBindingValid = enrollment.enrollmentRepositoryBindingValid,
            externalEvidencePresent = evidence.externalEvidencePresent,
            externalEvidenceWellFormed = evidence.externalEvidenceWellFormed,
            externalEvidenceSubjectMatchesWitness =
                evidence.externalEvidenceSubjectMatchesWitness,
            externalEvidenceRepositoryBindingValid =
                evidence.externalEvidenceRepositoryBindingValid,
            externalEvidenceFresh = evidence.externalEvidenceFresh,
            reviewAttestationPresent = evidence.reviewAttestationPresent,
            reviewAttestationWellFormed = evidence.reviewAttestationWellFormed,
            reviewerSeparateFromOperator = evidence.reviewerSeparateFromOperator,
            reviewerSeparateFromWitness = evidence.reviewerSeparateFromWitness,
            reviewBindingValid = evidence.reviewBindingValid,
            externalIndependenceEvidenceVerified =
                evidence.externalIndependenceEvidenceVerified,
        )
    }

    fun repositoryAuthorityIsEnrolled(): Boolean {
        return IndependentWitnessAuthorityEnrollmentSource.repositoryAcceptsEnrollment() &&
            IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isNotEmpty() &&
            IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isNotEmpty()
    }

    fun mechanicsSatisfiedImpliesEnrollment(mechanicsSatisfied: Boolean): Boolean {
        return false && mechanicsSatisfied
    }

    fun mechanicsSatisfiedImpliesApproval(mechanicsSatisfied: Boolean): Boolean {
        return false && mechanicsSatisfied
    }

    fun mechanicsSatisfiedImpliesRuntimeAuthorization(mechanicsSatisfied: Boolean): Boolean {
        return false && mechanicsSatisfied
    }
}
