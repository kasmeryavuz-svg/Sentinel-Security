/**
 * Fail-closed evaluation of witness-authority enrollment preparation.
 *
 * Cryptographic identity and organizational independence are separate.
 * A well-formed record plus a valid key fingerprint still does not
 * enroll a witness. 19U never accepts repository enrollment.
 */
object IndependentWitnessAuthorityEnrollmentPreparation {
    const val TASK_PATH = ":app:checkIndependentWitnessAuthorityEnrollmentPreparation"
    const val REPORT_RELATIVE_PATH =
        "app/build/reports/independent-witness-authority-enrollment-preparation.txt"
    private val HEX_VALUE = Regex("\\b[0-9a-fA-F]{40,}\\b")

    data class Evaluation(
        val enrollmentRecordPresent: Boolean,
        val enrollmentRecordWellFormed: Boolean,
        val witnessIdentifierValid: Boolean,
        val verificationKeyFingerprintValid: Boolean,
        val operatorWitnessSeparationClaimed: Boolean,
        val independenceEvidencePresent: Boolean,
        val independentReviewPresent: Boolean,
        val enrollmentRepositoryBindingValid: Boolean,
        val witnessAuthorityEnrolled: Boolean,
    ) {
        val status: String
            get() = if (witnessAuthorityEnrolled) {
                "ENROLLED"
            } else {
                IndependentWitnessAuthorityEnrollment.STATUS_NOT_ENROLLED
            }

        fun render(): String {
            val established =
                IndependentWitnessAuthorityContract.establishedWitnessIdentifiers()
            val rendered = buildString {
                appendLine("checkpoint=19U")
                appendLine("witness_authority_enrollment_status=$status")
                appendLine("established_witness_count=${established.size}")
                appendLine("repository_enrollment_present=$enrollmentRecordPresent")
                appendLine("enrollment_record_present=$enrollmentRecordPresent")
                appendLine("enrollment_record_well_formed=$enrollmentRecordWellFormed")
                appendLine("witness_identifier_valid=$witnessIdentifierValid")
                appendLine(
                    "verification_key_fingerprint_valid=$verificationKeyFingerprintValid",
                )
                appendLine(
                    "operator_witness_separation_claimed=$operatorWitnessSeparationClaimed",
                )
                appendLine("independence_evidence_present=$independenceEvidencePresent")
                appendLine("independent_review_present=$independentReviewPresent")
                appendLine(
                    "enrollment_repository_binding_valid=$enrollmentRepositoryBindingValid",
                )
                appendLine("witness_authority_enrolled=$witnessAuthorityEnrolled")
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
                appendLine("enrollment_authorizes_hardware_test=false")
                appendLine("enrollment_authorizes_wipe=false")
                appendLine("ci_is_independent_witness=false")
                appendLine("operator_is_independent_witness=false")
                appendLine("local_receipt_is_independent_witness=false")
            }
            check(!HEX_VALUE.containsMatchIn(rendered)) {
                "19U enrollment-preparation report must not contain digest values"
            }
            check(established.isEmpty()) {
                "19U report must not observe an enrolled witness identifier"
            }
            return rendered
        }
    }

    fun evaluate(
        record: IndependentWitnessAuthorityEnrollment.Record?,
        evidence: IndependentWitnessAuthorityEnrollment.ExternalEvidence,
        repositoryAcceptsEnrollment: Boolean =
            IndependentWitnessAuthorityEnrollmentSource.repositoryAcceptsEnrollment(),
    ): Evaluation {
        if (record == null) {
            return Evaluation(
                enrollmentRecordPresent = false,
                enrollmentRecordWellFormed = false,
                witnessIdentifierValid = false,
                verificationKeyFingerprintValid = false,
                operatorWitnessSeparationClaimed = false,
                independenceEvidencePresent = false,
                independentReviewPresent = false,
                enrollmentRepositoryBindingValid = false,
                witnessAuthorityEnrolled = false,
            )
        }
        val identifierValid =
            record.witnessIdentifier.isNotBlank() &&
                !IndependentWitnessAuthorityEnrollment.isReservedWitnessIdentifier(
                    record.witnessIdentifier,
                )
        val fingerprintValid =
            IndependentWitnessAuthorityEnrollment.isValidFingerprint(
                record.witnessVerificationKeySha256,
            )
        val separated = !record.operatorIdentifier.equals(
            record.witnessIdentifier,
            ignoreCase = true,
        )
        val wellFormed = identifierValid &&
            fingerprintValid &&
            separated &&
            record.witnessRole == IndependentWitnessAuthorityEnrollment.WITNESS_ROLE &&
            record.reviewIdentifier.isNotBlank()
        val enrolled = wellFormed &&
            evidence.independenceEvidencePresent &&
            evidence.independentReviewPresent &&
            evidence.enrollmentRepositoryBindingValid &&
            repositoryAcceptsEnrollment
        return Evaluation(
            enrollmentRecordPresent = true,
            enrollmentRecordWellFormed = wellFormed,
            witnessIdentifierValid = identifierValid,
            verificationKeyFingerprintValid = fingerprintValid,
            operatorWitnessSeparationClaimed = separated,
            independenceEvidencePresent = evidence.independenceEvidencePresent,
            independentReviewPresent = evidence.independentReviewPresent,
            enrollmentRepositoryBindingValid = evidence.enrollmentRepositoryBindingValid,
            witnessAuthorityEnrolled = enrolled,
        )
    }

    fun evaluateRepositoryDefault(): Evaluation {
        IndependentWitnessAuthorityEnrollmentSource.assertNotEnrolled()
        check(!IndependentWitnessAuthorityContract.ciIsIndependentWitness())
        check(!IndependentWitnessAuthorityContract.localOperatorIsIndependentWitness())
        check(!IndependentWitnessAuthorityContract.localReceiptIsIndependentWitness())
        val evaluation = evaluate(
            record = IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments()
                .singleOrNull(),
            evidence = IndependentWitnessAuthorityEnrollment.ExternalEvidence.none(),
            repositoryAcceptsEnrollment = false,
        )
        check(!evaluation.witnessAuthorityEnrolled)
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
}
