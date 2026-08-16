import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndependentWitnessEnrollmentDecisionTest {
    @Test
    fun `real default repository decision remains BLOCKED and unenrolled`() {
        IndependentWitnessExternalEvidenceSource.assertEmpty()
        IndependentWitnessAuthorityEnrollmentSource.assertNotEnrolled()
        assertTrue(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty())
        assertTrue(IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isEmpty())
        assertTrue(
            IndependentWitnessExternalEvidenceSource.repositoryExternalWitnessEvidence().isEmpty(),
        )
        assertTrue(IndependentWitnessExternalEvidenceSource.repositoryWitnessReviews().isEmpty())
        val evaluation = IndependentWitnessEnrollmentDecision.evaluateRepositoryDefault()
        assertEquals(
            IndependentWitnessEnrollmentDecision.DECISION_BLOCKED,
            evaluation.decision,
        )
        assertFalse(evaluation.enrollmentCandidateMechanicsSatisfied)
        assertEquals(IndependentWitnessEnrollmentDecision.Inputs.none(), evaluation.inputs)
        assertEquals(
            IndependentWitnessEnrollmentDecision.Blocker.values().toList(),
            evaluation.blockers,
        )
        val rendered = evaluation.render()
        listOf(
            "checkpoint=19W",
            "witness_enrollment_decision=BLOCKED",
            "witness_statement_present=false",
            "witness_signature_verified=false",
            "witness_evidence_matches_candidate=false",
            "enrollment_record_present=false",
            "enrollment_record_well_formed=false",
            "witness_identifier_valid=false",
            "verification_key_fingerprint_valid=false",
            "operator_witness_separation_claimed=false",
            "enrollment_repository_binding_valid=false",
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
            "enrollment_candidate_mechanics_satisfied=false",
            "witness_authority_enrolled=false",
            "established_witness_count=0",
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
            "decision_authorizes_hardware_test=false",
            "decision_authorizes_wipe=false",
        ).forEach { line ->
            assertTrue(rendered.contains(line), line)
        }
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(rendered))
        assertFalse(IndependentWitnessEnrollmentDecision.repositoryAuthorityIsEnrolled())
    }

    @Test
    fun `missing-input permutations keep mechanics unsatisfied and decision BLOCKED`() {
        val complete = IndependentWitnessEnrollmentDecision.Inputs.syntheticCompleteMechanics()
        val permutations = listOf(
            complete.copy(witnessStatementPresent = false) to
                IndependentWitnessEnrollmentDecision.Blocker.MISSING_WITNESS_STATEMENT,
            complete.copy(witnessSignatureVerified = false) to
                IndependentWitnessEnrollmentDecision.Blocker.SIGNATURE_NOT_VERIFIED,
            complete.copy(witnessEvidenceMatchesCandidate = false) to
                IndependentWitnessEnrollmentDecision.Blocker.CANDIDATE_EVIDENCE_MISMATCH,
            complete.copy(enrollmentRecordPresent = false) to
                IndependentWitnessEnrollmentDecision.Blocker.MISSING_ENROLLMENT_RECORD,
            complete.copy(enrollmentRecordWellFormed = false) to
                IndependentWitnessEnrollmentDecision.Blocker.INVALID_ENROLLMENT_RECORD,
            complete.copy(witnessIdentifierValid = false) to
                IndependentWitnessEnrollmentDecision.Blocker.INVALID_ENROLLMENT_RECORD,
            complete.copy(verificationKeyFingerprintValid = false) to
                IndependentWitnessEnrollmentDecision.Blocker.INVALID_ENROLLMENT_RECORD,
            complete.copy(externalEvidencePresent = false) to
                IndependentWitnessEnrollmentDecision.Blocker.MISSING_EXTERNAL_EVIDENCE,
            complete.copy(externalEvidenceWellFormed = false) to
                IndependentWitnessEnrollmentDecision.Blocker.INVALID_EXTERNAL_EVIDENCE,
            complete.copy(externalEvidenceSubjectMatchesWitness = false) to
                IndependentWitnessEnrollmentDecision.Blocker.INVALID_EXTERNAL_EVIDENCE,
            complete.copy(externalEvidenceFresh = false) to
                IndependentWitnessEnrollmentDecision.Blocker.STALE_EXTERNAL_EVIDENCE,
            complete.copy(reviewAttestationPresent = false) to
                IndependentWitnessEnrollmentDecision.Blocker.MISSING_INDEPENDENT_REVIEW,
            complete.copy(reviewAttestationWellFormed = false) to
                IndependentWitnessEnrollmentDecision.Blocker.INVALID_REVIEW_BINDING,
            complete.copy(reviewBindingValid = false) to
                IndependentWitnessEnrollmentDecision.Blocker.INVALID_REVIEW_BINDING,
            complete.copy(enrollmentRepositoryBindingValid = false) to
                IndependentWitnessEnrollmentDecision.Blocker.REPOSITORY_REVISION_MISMATCH,
            complete.copy(externalEvidenceRepositoryBindingValid = false) to
                IndependentWitnessEnrollmentDecision.Blocker.REPOSITORY_REVISION_MISMATCH,
            complete.copy(externalIndependenceEvidenceVerified = false) to
                IndependentWitnessEnrollmentDecision.Blocker
                    .EXTERNAL_INDEPENDENCE_EVIDENCE_NOT_VERIFIED,
        )
        permutations.forEach { (inputs, expected) ->
            val evaluation = IndependentWitnessEnrollmentDecision.evaluate(inputs)
            assertFalse(evaluation.enrollmentCandidateMechanicsSatisfied, expected.name)
            assertEquals(
                IndependentWitnessEnrollmentDecision.DECISION_BLOCKED,
                evaluation.decision,
                expected.name,
            )
            assertTrue(expected in evaluation.blockers, expected.name)
            assertTrue(
                IndependentWitnessEnrollmentDecision.Blocker.NO_REPOSITORY_AUTHORITY_ENROLLMENT
                    in evaluation.blockers,
                expected.name,
            )
        }
    }

    @Test
    fun `identity separation and reserved aliases keep the decision BLOCKED`() {
        val complete = IndependentWitnessEnrollmentDecision.Inputs.syntheticCompleteMechanics()
        val evaluationOperator = IndependentWitnessEnrollmentDecision.evaluate(
            complete.copy(operatorWitnessSeparationClaimed = false),
        )
        assertTrue(
            IndependentWitnessEnrollmentDecision.Blocker.OPERATOR_WITNESS_NOT_SEPARATE
                in evaluationOperator.blockers,
        )
        val evaluationReviewerOperator = IndependentWitnessEnrollmentDecision.evaluate(
            complete.copy(reviewerSeparateFromOperator = false),
        )
        assertTrue(
            IndependentWitnessEnrollmentDecision.Blocker.REVIEWER_OPERATOR_NOT_SEPARATE
                in evaluationReviewerOperator.blockers,
        )
        val evaluationReviewerWitness = IndependentWitnessEnrollmentDecision.evaluate(
            complete.copy(reviewerSeparateFromWitness = false),
        )
        assertTrue(
            IndependentWitnessEnrollmentDecision.Blocker.REVIEWER_WITNESS_NOT_SEPARATE
                in evaluationReviewerWitness.blockers,
        )
        listOf(
            "ci",
            "github-actions",
            "cursor",
            "operator",
            "local-operator",
            "repository-owner",
            "19s-receipt",
            "19t-report",
        ).forEach { reserved ->
            assertTrue(
                IndependentWitnessAuthorityEnrollment.isReservedWitnessIdentifier(reserved),
                reserved,
            )
            val evaluation = IndependentWitnessEnrollmentDecision.evaluate(
                complete.copy(witnessIdentifierValid = false),
            )
            assertFalse(evaluation.enrollmentCandidateMechanicsSatisfied, reserved)
            assertTrue(
                IndependentWitnessEnrollmentDecision.Blocker.INVALID_ENROLLMENT_RECORD
                    in evaluation.blockers,
                reserved,
            )
            assertFalse(
                IndependentWitnessAuthorityContract.independenceEstablished(reserved),
                reserved,
            )
        }
    }

    @Test
    fun `synthetic complete mechanics still leave repository unenrolled`() {
        val evaluation = IndependentWitnessEnrollmentDecision.evaluate(
            IndependentWitnessEnrollmentDecision.Inputs.syntheticCompleteMechanics(),
        )
        assertTrue(evaluation.enrollmentCandidateMechanicsSatisfied)
        assertEquals(
            listOf(
                IndependentWitnessEnrollmentDecision.Blocker.NO_REPOSITORY_AUTHORITY_ENROLLMENT,
            ),
            evaluation.blockers,
        )
        assertEquals(
            IndependentWitnessEnrollmentDecision.DECISION_BLOCKED,
            evaluation.decision,
        )
        val rendered = evaluation.render()
        assertTrue(rendered.contains("enrollment_candidate_mechanics_satisfied=true"))
        assertTrue(rendered.contains("witness_enrollment_decision=BLOCKED"))
        assertTrue(rendered.contains("witness_authority_enrolled=false"))
        assertTrue(rendered.contains("independent_witness_approval=false"))
        assertTrue(rendered.contains("runtime_authorization=false"))
        assertTrue(rendered.contains("decision_authorizes_wipe=false"))
        assertTrue(rendered.contains("established_witness_count=0"))
        assertFalse(
            IndependentWitnessEnrollmentDecision.mechanicsSatisfiedImpliesEnrollment(true),
        )
        assertFalse(
            IndependentWitnessEnrollmentDecision.mechanicsSatisfiedImpliesApproval(true),
        )
        assertFalse(
            IndependentWitnessEnrollmentDecision.mechanicsSatisfiedImpliesRuntimeAuthorization(
                true,
            ),
        )
        assertTrue(IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isEmpty())
        assertTrue(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty())
        assertTrue(
            IndependentWitnessExternalEvidenceSource.repositoryExternalWitnessEvidence().isEmpty(),
        )
        assertTrue(IndependentWitnessExternalEvidenceSource.repositoryWitnessReviews().isEmpty())
        assertFalse(
            IndependentWitnessAuthorityContract.approval(
                statementPresent = true,
                signatureVerified = true,
                evidenceMatches = true,
                independenceEstablished = false,
            ),
        )
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
    fun `blocker ordering is stable unique and report rendering is deterministic`() {
        val default = IndependentWitnessEnrollmentDecision.evaluate(
            IndependentWitnessEnrollmentDecision.Inputs.none(),
        )
        val again = IndependentWitnessEnrollmentDecision.evaluate(
            IndependentWitnessEnrollmentDecision.Inputs.none(),
        )
        assertEquals(default.blockers, again.blockers)
        assertEquals(default.blockers.distinct(), default.blockers)
        assertEquals(
            IndependentWitnessEnrollmentDecision.Blocker.values().toList(),
            default.blockers,
        )
        assertEquals(default.render(), again.render())
        val stale = IndependentWitnessEnrollmentDecision.evaluate(
            IndependentWitnessEnrollmentDecision.Inputs.syntheticCompleteMechanics()
                .copy(externalEvidenceFresh = false),
        )
        assertEquals(
            listOf(
                IndependentWitnessEnrollmentDecision.Blocker.STALE_EXTERNAL_EVIDENCE,
                IndependentWitnessEnrollmentDecision.Blocker.NO_REPOSITORY_AUTHORITY_ENROLLMENT,
            ),
            stale.blockers,
        )
        assertEquals(stale.render(), IndependentWitnessEnrollmentDecision.evaluate(
            IndependentWitnessEnrollmentDecision.Inputs.syntheticCompleteMechanics()
                .copy(externalEvidenceFresh = false),
        ).render())
    }

    @Test
    fun `contract task stays CI-safe and absent from runtime`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        val task = File(
            "src/main/kotlin/CheckIndependentWitnessEnrollmentDecisionContractTask.kt",
        ).readText()
        val decision = File(
            "src/main/kotlin/IndependentWitnessEnrollmentDecision.kt",
        ).readText()
        val contract = File(
            "src/main/kotlin/IndependentWitnessAuthorityContract.kt",
        ).readText()
        val ignore = File("../.gitignore").readText()
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        val docs = File("../docs/WIPE_19W_INDEPENDENT_WITNESS_ENROLLMENT_DECISION.md").readText()
        assertTrue(appGradle.contains("checkIndependentWitnessEnrollmentDecisionContract"))
        assertFalse(task.contains("SENTINEL_VALIDATION_STORE"))
        assertFalse(task.contains("KeyPairGenerator"))
        assertFalse(task.contains("STORE_PASSWORD"))
        assertFalse(task.contains("gradleProperty"))
        assertFalse(decision.contains("System.getenv"))
        assertFalse(decision.contains("System.getProperty"))
        assertFalse(decision.contains("gradleProperty"))
        assertFalse(Regex("""\bvar\b""").containsMatchIn(decision))
        assertTrue(contract.contains("fun establishedWitnessIdentifiers(): Set<String> = emptySet()"))
        assertTrue(ignore.contains("local/independent-witness-enrollment-decision.txt"))
        assertTrue(workflow.contains(":app:checkIndependentWitnessEnrollmentDecisionContract"))
        assertTrue(workflow.contains("witness_enrollment_decision=BLOCKED"))
        assertTrue(workflow.contains("enrollment_candidate_mechanics_satisfied=false"))
        assertTrue(workflow.contains("decision_authorizes_wipe=false"))
        assertTrue(workflow.contains("witness_authority_enrolled=false"))
        assertFalse(workflow.contains("verifyIndependentWitnessStatement"))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(File("../local/independent-witness-enrollment-decision.txt").exists())
        assertTrue(docs.contains("CHECKPOINT_19W_INDEPENDENT_WITNESS_ENROLLMENT_DECISION = YES"))
        assertTrue(docs.contains("19W_WITNESS_ENROLLMENT_DECISION = BLOCKED"))
        assertTrue(docs.contains("DO NOT MERGE"))
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
                        text.contains("IndependentWitnessEnrollmentDecision"),
                        file.path,
                    )
                    assertFalse(
                        text.contains("checkIndependentWitnessEnrollmentDecisionContract"),
                        file.path,
                    )
                }
        }
    }
}
