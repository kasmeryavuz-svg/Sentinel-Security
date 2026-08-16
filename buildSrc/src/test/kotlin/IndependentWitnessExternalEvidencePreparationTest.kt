import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndependentWitnessExternalEvidencePreparationTest {
    @Test
    fun `real default repository state remains NOT_PRESENT and unenrolled`() {
        IndependentWitnessExternalEvidenceSource.assertEmpty()
        assertTrue(
            IndependentWitnessExternalEvidenceSource.repositoryExternalWitnessEvidence()
                .isEmpty(),
        )
        assertTrue(IndependentWitnessExternalEvidenceSource.repositoryWitnessReviews().isEmpty())
        assertTrue(IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isEmpty())
        assertTrue(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty())
        val evaluation =
            IndependentWitnessExternalEvidencePreparation.evaluateRepositoryDefault()
        assertFalse(evaluation.externalEvidencePresent)
        assertFalse(evaluation.externalEvidenceWellFormed)
        assertFalse(evaluation.externalEvidenceSubjectMatchesWitness)
        assertFalse(evaluation.externalEvidenceRepositoryBindingValid)
        assertFalse(evaluation.externalEvidenceFresh)
        assertFalse(evaluation.reviewAttestationPresent)
        assertFalse(evaluation.reviewAttestationWellFormed)
        assertFalse(evaluation.reviewerSeparateFromOperator)
        assertFalse(evaluation.reviewerSeparateFromWitness)
        assertFalse(evaluation.reviewBindingValid)
        assertFalse(evaluation.externalIndependenceEvidenceVerified)
        assertEquals(0, evaluation.externalEvidenceCount)
        assertEquals(0, evaluation.reviewAttestationCount)
        assertEquals(
            IndependentWitnessExternalEvidencePreparation.STATUS_NOT_PRESENT,
            evaluation.status,
        )
        val rendered = evaluation.render()
        listOf(
            "checkpoint=19V",
            "external_witness_evidence_status=NOT_PRESENT",
            "external_evidence_count=0",
            "review_attestation_count=0",
            "external_evidence_present=false",
            "external_evidence_well_formed=false",
            "external_evidence_subject_matches_witness=false",
            "external_evidence_repository_binding_valid=false",
            "external_evidence_fresh=false",
            "review_attestation_present=false",
            "review_attestation_well_formed=false",
            "reviewer_separate_from_operator=false",
            "reviewer_separate_from_witness=false",
            "review_binding_valid=false",
            "external_independence_evidence_verified=false",
            "witness_authority_enrolled=false",
            "witness_independence_established=false",
            "independent_witness_approval=false",
            "authority=UNTRUSTED_CANDIDATE_ONLY",
            "runtime_authorization=false",
            "trusted_expectation_minted=false",
            "production_distribution=false",
            "customer_device_authorized=false",
            "real_device_identity_recorded=false",
            "hardware_validation_approved=false",
            "hardware_test_performed=false",
            "evidence_authorizes_hardware_test=false",
            "evidence_authorizes_wipe=false",
        ).forEach { line ->
            assertTrue(rendered.contains(line), line)
        }
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(rendered))
        val enrollment =
            IndependentWitnessAuthorityEnrollmentPreparation.evaluateRepositoryDefault()
        assertEquals(
            IndependentWitnessAuthorityEnrollment.STATUS_NOT_ENROLLED,
            enrollment.status,
        )
        assertFalse(enrollment.witnessAuthorityEnrolled)
    }

    @Test
    fun `synthetic complete mechanics still leave enrollment and approval false`() {
        val evaluation = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(validEvidence()),
            reviewRecords = listOf(validReview()),
            expectation = matchingExpectation(),
        )
        assertTrue(evaluation.externalEvidencePresent)
        assertTrue(evaluation.externalEvidenceWellFormed)
        assertTrue(evaluation.externalEvidenceSubjectMatchesWitness)
        assertTrue(evaluation.externalEvidenceRepositoryBindingValid)
        assertTrue(evaluation.externalEvidenceFresh)
        assertTrue(evaluation.reviewAttestationPresent)
        assertTrue(evaluation.reviewAttestationWellFormed)
        assertTrue(evaluation.reviewerSeparateFromOperator)
        assertTrue(evaluation.reviewerSeparateFromWitness)
        assertTrue(evaluation.reviewBindingValid)
        assertTrue(evaluation.externalIndependenceEvidenceVerified)
        assertEquals(
            IndependentWitnessExternalEvidencePreparation.STATUS_MECHANICS_VERIFIED,
            evaluation.status,
        )
        val rendered = evaluation.render()
        assertTrue(rendered.contains("external_independence_evidence_verified=true"))
        assertTrue(rendered.contains("witness_authority_enrolled=false"))
        assertTrue(rendered.contains("independent_witness_approval=false"))
        assertTrue(rendered.contains("runtime_authorization=false"))
        assertTrue(rendered.contains("evidence_authorizes_wipe=false"))
        assertFalse(
            IndependentWitnessExternalEvidencePreparation.verifiedEvidenceImpliesApproval(true),
        )
        assertFalse(
            IndependentWitnessExternalEvidencePreparation.verifiedEvidenceImpliesEnrollment(true),
        )
        assertTrue(
            IndependentWitnessExternalEvidenceSource.repositoryExternalWitnessEvidence().isEmpty(),
        )
        assertTrue(IndependentWitnessExternalEvidenceSource.repositoryWitnessReviews().isEmpty())
        assertTrue(IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isEmpty())
        assertTrue(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty())
        assertTrue(evaluation.externalIndependenceEvidenceVerified)
        assertFalse(
            IndependentWitnessAuthorityContract.independenceEstablished("external-witness"),
        )
        assertFalse(
            IndependentWitnessAuthorityContract.approval(
                statementPresent = true,
                signatureVerified = true,
                evidenceMatches = true,
                independenceEstablished = false,
            ),
        )
    }

    @Test
    fun `binding mismatches keep independence evidence unverified`() {
        val evidence = validEvidence()
        val review = validReview()
        val matched = matchingExpectation()
        val wrongWitness = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(review),
            expectation = matched.copy(witnessIdentifier = "other-witness"),
        )
        assertFalse(wrongWitness.externalEvidenceSubjectMatchesWitness)
        assertFalse(wrongWitness.externalIndependenceEvidenceVerified)

        val wrongEvidenceId = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(review.copy(evidenceIdentifier = "other-evidence")),
            expectation = matched,
        )
        assertTrue(wrongEvidenceId.reviewAttestationWellFormed)
        assertFalse(wrongEvidenceId.reviewBindingValid)
        assertFalse(wrongEvidenceId.externalIndependenceEvidenceVerified)

        val wrongReviewWitness = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(review.copy(witnessIdentifier = "other-witness")),
            expectation = matched,
        )
        assertFalse(wrongReviewWitness.reviewBindingValid)
        assertFalse(wrongReviewWitness.externalIndependenceEvidenceVerified)

        val wrongRevision = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(review),
            expectation = matched.copy(repositoryRevision = "ab".repeat(20)),
        )
        assertFalse(wrongRevision.externalEvidenceRepositoryBindingValid)
        assertFalse(wrongRevision.externalIndependenceEvidenceVerified)

        val wrongKey = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(review),
            expectation = matched.copy(witnessVerificationKeySha256 = "cc".repeat(32)),
        )
        assertFalse(wrongKey.externalEvidenceRepositoryBindingValid)
        assertFalse(wrongKey.externalIndependenceEvidenceVerified)

        val wrongDigest = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(review),
            expectation = matched.copy(evidenceDigestSha256 = "dd".repeat(32)),
        )
        assertFalse(wrongDigest.externalEvidenceRepositoryBindingValid)
        assertFalse(wrongDigest.externalIndependenceEvidenceVerified)

        val stale = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(review),
            expectation = matched.copy(evaluationInstantUtc = Instant.parse("2026-08-24T00:00:00Z")),
        )
        assertFalse(stale.externalEvidenceFresh)
        assertFalse(stale.externalIndependenceEvidenceVerified)

        val rejected = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(
                review.copy(decision = IndependentWitnessEnrollmentReview.Decision.REJECT),
            ),
            expectation = matched,
        )
        assertTrue(rejected.reviewAttestationWellFormed)
        assertTrue(rejected.reviewBindingValid)
        assertFalse(rejected.externalIndependenceEvidenceVerified)

        val reviewNotRequired = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence.copy(reviewRequired = false)),
            reviewRecords = listOf(review),
            expectation = matched,
        )
        assertTrue(reviewNotRequired.externalEvidenceWellFormed)
        assertFalse(reviewNotRequired.externalIndependenceEvidenceVerified)
    }

    @Test
    fun `overlapping identities keep review flags fail-closed`() {
        val evidence = validEvidence()
        val matched = matchingExpectation()
        val sameReviewerOperator = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(validReview().copy(reviewerIdentifier = "local-operator")),
            expectation = matched,
        )
        assertFalse(sameReviewerOperator.reviewAttestationWellFormed)
        assertFalse(sameReviewerOperator.reviewerSeparateFromOperator)
        assertFalse(sameReviewerOperator.externalIndependenceEvidenceVerified)

        val sameReviewerWitness = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(validReview().copy(reviewerIdentifier = "external-witness")),
            expectation = matched,
        )
        assertFalse(sameReviewerWitness.reviewAttestationWellFormed)
        assertFalse(sameReviewerWitness.reviewerSeparateFromWitness)
        assertFalse(sameReviewerWitness.externalIndependenceEvidenceVerified)

        val sameOperatorWitness = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(validReview().copy(operatorIdentifier = "external-witness")),
            expectation = matched,
        )
        assertFalse(sameOperatorWitness.reviewAttestationWellFormed)
        assertFalse(sameOperatorWitness.reviewBindingValid)
        assertFalse(sameOperatorWitness.externalIndependenceEvidenceVerified)

        val reviewerIsIssuer = IndependentWitnessExternalEvidencePreparation.evaluate(
            evidenceRecords = listOf(evidence),
            reviewRecords = listOf(validReview().copy(reviewerIdentifier = "external-attestor")),
            expectation = matched,
        )
        assertTrue(reviewerIsIssuer.reviewAttestationWellFormed)
        assertFalse(reviewerIsIssuer.reviewBindingValid)
        assertFalse(reviewerIsIssuer.externalIndependenceEvidenceVerified)
    }

    @Test
    fun `19T independence and 19U enrollment remain false after 19V`() {
        assertFalse(IndependentWitnessAuthorityContract.ciIsIndependentWitness())
        assertFalse(IndependentWitnessAuthorityContract.localOperatorIsIndependentWitness())
        assertFalse(IndependentWitnessAuthorityContract.localReceiptIsIndependentWitness())
        listOf(
            "ci",
            "github-actions",
            "cursor",
            "local-operator",
            "19s-receipt",
            "19t-report",
            "external-witness",
        ).forEach { identifier ->
            assertFalse(
                IndependentWitnessAuthorityContract.independenceEstablished(identifier),
                identifier,
            )
        }
        val verification = IndependentWitnessVerification.evaluateMechanics(
            candidateReinspectionPassed = true,
            receiptMatchesCandidate = true,
            validationCertificateMatches = true,
            witnessStatementPresent = true,
            witnessSignatureVerified = true,
            witnessEvidenceMatchesCandidate = true,
            witnessIdentifier = "external-witness",
        )
        assertTrue(verification.witnessSignatureVerified)
        assertFalse(verification.witnessIndependenceEstablished)
        assertFalse(verification.independentWitnessApproval)
    }

    @Test
    fun `contract task stays CI-safe and absent from runtime`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        val task = File(
            "src/main/kotlin/CheckIndependentWitnessExternalEvidencePreparationTask.kt",
        ).readText()
        val source = File(
            "src/main/kotlin/IndependentWitnessExternalEvidenceSource.kt",
        ).readText()
        val contract = File(
            "src/main/kotlin/IndependentWitnessAuthorityContract.kt",
        ).readText()
        val ignore = File("../.gitignore").readText()
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        val docs = File("../docs/WIPE_19V_EXTERNAL_WITNESS_EVIDENCE_PREPARATION.md").readText()
        val evidenceTemplate = File(
            "../docs/templates/INDEPENDENT_WITNESS_EXTERNAL_EVIDENCE.template.txt",
        ).readText()
        val reviewTemplate = File(
            "../docs/templates/INDEPENDENT_WITNESS_ENROLLMENT_REVIEW.template.txt",
        ).readText()
        assertTrue(appGradle.contains("checkIndependentWitnessExternalEvidencePreparation"))
        assertFalse(task.contains("SENTINEL_VALIDATION_STORE"))
        assertFalse(task.contains("KeyPairGenerator"))
        assertFalse(task.contains("STORE_PASSWORD"))
        assertFalse(task.contains("signed-validation-candidate-receipt"))
        assertFalse(task.contains("independent-witness-verification.txt"))
        assertTrue(source.contains("fun repositoryExternalWitnessEvidence()"))
        assertTrue(source.contains("fun repositoryWitnessReviews()"))
        assertTrue(source.contains("return emptyList()"))
        assertTrue(contract.contains("fun establishedWitnessIdentifiers(): Set<String> = emptySet()"))
        assertTrue(ignore.contains("local/independent-witness-external-evidence.txt"))
        assertTrue(ignore.contains("local/independent-witness-enrollment-review.txt"))
        assertTrue(workflow.contains(":app:checkIndependentWitnessExternalEvidencePreparation"))
        assertTrue(workflow.contains("external_witness_evidence_status=NOT_PRESENT"))
        assertTrue(workflow.contains("external_independence_evidence_verified=false"))
        assertTrue(workflow.contains("evidence_authorizes_wipe=false"))
        assertTrue(workflow.contains("witness_authority_enrolled=false"))
        assertFalse(workflow.contains("verifyIndependentWitnessStatement"))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(File("../local/independent-witness-external-evidence.txt").exists())
        assertFalse(File("../local/independent-witness-enrollment-review.txt").exists())
        assertTrue(docs.contains("CHECKPOINT_19V_EXTERNAL_WITNESS_EVIDENCE_PREPARATION = YES"))
        assertTrue(docs.contains("19V_EXTERNAL_INDEPENDENCE_EVIDENCE_VERIFIED = false"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertTrue(evidenceTemplate.contains("evidence_type=<EVIDENCE_TYPE>"))
        assertTrue(reviewTemplate.contains("decision=<REVIEW_DECISION>"))
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(evidenceTemplate))
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(reviewTemplate))
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(docs))
        val runtimeRoots = listOf(
            File("../app/src/main"),
            File("../device-management/src/main"),
            File("../device-management-api/src/main"),
            File("../device-management-facade/src/main"),
            File("../sensitive-actions/src/main"),
        )
        runtimeRoots.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    val text = file.readText()
                    assertFalse(
                        text.contains("IndependentWitnessExternalEvidence"),
                        file.path,
                    )
                    assertFalse(
                        text.contains("IndependentWitnessEnrollmentReview"),
                        file.path,
                    )
                    assertFalse(
                        text.contains("checkIndependentWitnessExternalEvidencePreparation"),
                        file.path,
                    )
                }
        }
    }

    private fun validEvidence(): IndependentWitnessExternalEvidence.Record {
        return IndependentWitnessExternalEvidenceTest.validEvidence()
    }

    private fun validReview(): IndependentWitnessEnrollmentReview.Record {
        return IndependentWitnessEnrollmentReviewTest.validReview()
    }

    private fun matchingExpectation():
        IndependentWitnessExternalEvidencePreparation.BindingExpectation {
        val evidence = validEvidence()
        return IndependentWitnessExternalEvidencePreparation.BindingExpectation(
            witnessIdentifier = evidence.witnessIdentifier,
            repositoryRevision = evidence.repositoryRevision,
            witnessVerificationKeySha256 = evidence.witnessVerificationKeySha256,
            evidenceDigestSha256 = evidence.evidenceDigestSha256,
            evaluationInstantUtc = IndependentWitnessExternalEvidenceTest.CLOCK,
        )
    }
}
