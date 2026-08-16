import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndependentWitnessAuthorityEnrollmentPreparationTest {
    @Test
    fun `real default repository state remains NOT_ENROLLED`() {
        IndependentWitnessAuthorityEnrollmentSource.assertNotEnrolled()
        assertTrue(IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isEmpty())
        assertTrue(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty())
        assertFalse(IndependentWitnessAuthorityEnrollmentSource.repositoryAcceptsEnrollment())
        val evaluation =
            IndependentWitnessAuthorityEnrollmentPreparation.evaluateRepositoryDefault()
        assertFalse(evaluation.enrollmentRecordPresent)
        assertFalse(evaluation.enrollmentRecordWellFormed)
        assertFalse(evaluation.witnessIdentifierValid)
        assertFalse(evaluation.verificationKeyFingerprintValid)
        assertFalse(evaluation.operatorWitnessSeparationClaimed)
        assertFalse(evaluation.independenceEvidencePresent)
        assertFalse(evaluation.independentReviewPresent)
        assertFalse(evaluation.enrollmentRepositoryBindingValid)
        assertFalse(evaluation.witnessAuthorityEnrolled)
        assertEquals(
            IndependentWitnessAuthorityEnrollment.STATUS_NOT_ENROLLED,
            evaluation.status,
        )
        val rendered = evaluation.render()
        assertTrue(rendered.contains("checkpoint=19U"))
        assertTrue(rendered.contains("witness_authority_enrollment_status=NOT_ENROLLED"))
        assertTrue(rendered.contains("established_witness_count=0"))
        assertTrue(rendered.contains("repository_enrollment_present=false"))
        assertTrue(rendered.contains("witness_authority_enrolled=false"))
        assertTrue(rendered.contains("independent_witness_approval=false"))
        assertTrue(rendered.contains("enrollment_authorizes_wipe=false"))
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(rendered))
    }

    @Test
    fun `19T independence and approval remain false after 19U`() {
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
            "external-reviewer",
        ).forEach { identifier ->
            assertFalse(
                IndependentWitnessAuthorityContract.independenceEstablished(identifier),
                identifier,
            )
        }
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
            witnessIdentifier = "external-reviewer",
        )
        assertTrue(verification.witnessSignatureVerified)
        assertFalse(verification.witnessIndependenceEstablished)
        assertFalse(verification.independentWitnessApproval)
    }

    @Test
    fun `synthetic complete mechanics still leave repository unenrolled`() {
        val evidence = IndependentWitnessAuthorityEnrollment.ExternalEvidence(
            independenceEvidencePresent = true,
            independentReviewPresent = true,
            enrollmentRepositoryBindingValid = true,
        )
        val eligible = IndependentWitnessAuthorityEnrollmentPreparation.evaluate(
            record = validRecord(),
            evidence = evidence,
            repositoryAcceptsEnrollment = true,
        )
        assertTrue(eligible.enrollmentRecordPresent)
        assertTrue(eligible.enrollmentRecordWellFormed)
        assertTrue(eligible.witnessIdentifierValid)
        assertTrue(eligible.verificationKeyFingerprintValid)
        assertTrue(eligible.operatorWitnessSeparationClaimed)
        assertTrue(eligible.independenceEvidencePresent)
        assertTrue(eligible.independentReviewPresent)
        assertTrue(eligible.enrollmentRepositoryBindingValid)
        assertTrue(eligible.witnessAuthorityEnrolled)
        assertTrue(IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isEmpty())
        assertTrue(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty())

        val refused = IndependentWitnessAuthorityEnrollmentPreparation.evaluate(
            record = validRecord(),
            evidence = evidence,
            repositoryAcceptsEnrollment = false,
        )
        assertTrue(refused.enrollmentRecordWellFormed)
        assertFalse(refused.witnessAuthorityEnrolled)
    }

    @Test
    fun `classification and key ownership are not independence evidence`() {
        val recordOnly = IndependentWitnessAuthorityEnrollmentPreparation.evaluate(
            record = validRecord(),
            evidence = IndependentWitnessAuthorityEnrollment.ExternalEvidence.none(),
            repositoryAcceptsEnrollment = true,
        )
        assertTrue(recordOnly.enrollmentRecordWellFormed)
        assertFalse(recordOnly.independenceEvidencePresent)
        assertFalse(recordOnly.independentReviewPresent)
        assertFalse(recordOnly.witnessAuthorityEnrolled)
        assertFalse(
            IndependentWitnessAuthorityEnrollment.independenceFromSignatureValidity(true),
        )
        assertFalse(
            IndependentWitnessAuthorityEnrollment.independenceFromKeyFingerprint(
                validRecord().witnessVerificationKeySha256,
            ),
        )
    }

    @Test
    fun `reserved CI operator receipt and report identities cannot enroll`() {
        listOf("ci", "github-actions", "cursor", "local-operator", "19s-receipt", "19t-report")
            .forEach { identifier ->
                val evaluation = IndependentWitnessAuthorityEnrollmentPreparation.evaluate(
                    record = validRecord().copy(witnessIdentifier = identifier),
                    evidence = IndependentWitnessAuthorityEnrollment.ExternalEvidence(
                        independenceEvidencePresent = true,
                        independentReviewPresent = true,
                        enrollmentRepositoryBindingValid = true,
                    ),
                    repositoryAcceptsEnrollment = true,
                )
                assertFalse(evaluation.witnessIdentifierValid, identifier)
                assertFalse(evaluation.witnessAuthorityEnrolled, identifier)
            }
        val samePerson = IndependentWitnessAuthorityEnrollmentPreparation.evaluate(
            record = validRecord().copy(operatorIdentifier = "external-reviewer"),
            evidence = IndependentWitnessAuthorityEnrollment.ExternalEvidence(
                independenceEvidencePresent = true,
                independentReviewPresent = true,
                enrollmentRepositoryBindingValid = true,
            ),
            repositoryAcceptsEnrollment = true,
        )
        assertFalse(samePerson.operatorWitnessSeparationClaimed)
        assertFalse(samePerson.witnessAuthorityEnrolled)
    }

    @Test
    fun `contract task stays CI-safe and absent from runtime`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        val task = File(
            "src/main/kotlin/CheckIndependentWitnessAuthorityEnrollmentPreparationTask.kt",
        ).readText()
        val source = File(
            "src/main/kotlin/IndependentWitnessAuthorityEnrollmentSource.kt",
        ).readText()
        val contract = File(
            "src/main/kotlin/IndependentWitnessAuthorityContract.kt",
        ).readText()
        val ignore = File("../.gitignore").readText()
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        val docs = File("../docs/WIPE_19U_WITNESS_AUTHORITY_ENROLLMENT_PREPARATION.md").readText()
        val template = File(
            "../docs/templates/INDEPENDENT_WITNESS_AUTHORITY_ENROLLMENT.template.txt",
        ).readText()
        assertTrue(appGradle.contains("checkIndependentWitnessAuthorityEnrollmentPreparation"))
        assertFalse(task.contains("SENTINEL_VALIDATION_STORE"))
        assertFalse(task.contains("KeyPairGenerator"))
        assertFalse(task.contains("STORE_PASSWORD"))
        assertTrue(source.contains("fun repositoryEnrollments()"))
        assertTrue(source.contains("return emptyList()"))
        assertTrue(contract.contains("fun establishedWitnessIdentifiers(): Set<String> = emptySet()"))
        assertTrue(ignore.contains("local/independent-witness-authority-enrollment.txt"))
        assertTrue(workflow.contains(":app:checkIndependentWitnessAuthorityEnrollmentPreparation"))
        assertTrue(workflow.contains("witness_authority_enrolled=false"))
        assertTrue(workflow.contains("established_witness_count=0"))
        assertTrue(workflow.contains("enrollment_authorizes_wipe=false"))
        assertFalse(workflow.contains("verifyIndependentWitnessStatement"))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(File("../local/independent-witness-authority-enrollment.txt").exists())
        assertTrue(docs.contains("CHECKPOINT_19U_WITNESS_AUTHORITY_ENROLLMENT_PREPARATION = YES"))
        assertTrue(docs.contains("19U_WITNESS_AUTHORITY_ENROLLED = false"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertTrue(template.contains("independence_basis=<INDEPENDENCE_BASIS>"))
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(template))
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
                        text.contains("IndependentWitnessAuthorityEnrollment"),
                        file.path,
                    )
                    assertFalse(
                        text.contains("checkIndependentWitnessAuthorityEnrollmentPreparation"),
                        file.path,
                    )
                }
        }
    }

    private fun validRecord(): IndependentWitnessAuthorityEnrollment.Record {
        return IndependentWitnessAuthorityEnrollment.Record(
            witnessIdentifier = "external-reviewer",
            witnessDisplayName = "External Reviewer",
            witnessVerificationKeySha256 =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            witnessRole = IndependentWitnessAuthorityEnrollment.WITNESS_ROLE,
            independenceBasis =
                IndependentWitnessAuthorityEnrollment.IndependenceBasis.SEPARATE_NATURAL_PERSON,
            enrollmentTimestampUtc = "2026-08-16T19:00:00Z",
            enrollmentRepositoryRevision = "12".repeat(20),
            operatorIdentifier = "local-operator",
            reviewIdentifier = "review-desk",
        )
    }
}
