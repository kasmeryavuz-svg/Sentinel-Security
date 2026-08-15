import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestructiveSigningCeremonyPreparationTest {
    @Test
    fun `repository default contract is NOT_READY with every required item absent`() {
        val evaluation = DestructiveSigningCeremonyPreparation.evaluateRepositoryDefault()
        DestructiveSigningCeremonyPreparation.assertRepositoryDefaultStillNotReady(evaluation)
        assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status)
        assertEquals(SigningCeremonySourceKind.REPOSITORY_DEFAULT, evaluation.sourceKind)
        assertFalse(evaluation.offlineKeyGenerated)
        assertFalse(evaluation.publicCertificateSupplied)
        assertFalse(evaluation.expectedCertificateRecorded)
        assertFalse(evaluation.operatorApprovalAvailable)
        assertFalse(evaluation.witnessApprovalAvailable)
        assertFalse(evaluation.keyCustodyApproved)
        assertFalse(evaluation.recoveryBackupVerified)
        assertFalse(evaluation.branchProtectionRequiredCheckVerified)
        assertFalse(evaluation.productionArtifactSigned)
        assertFalse(evaluation.runtimeAuthorization)
        assertFalse(evaluation.trustedExpectationMinted)
        assertFalse(evaluation.signedValidationCandidateProduced)
        assertFalse(evaluation.expectedCertificateSha256Configured)
        assertNull(DestructiveValidationExpectedIdentity.repositoryContract().expectedCertificateSha256)
        assertFalse(evaluation.render().contains("ceremony_status=READY"))
        assertFalse(HEX_SHA256.containsMatchIn(evaluation.statusLinesWithoutDigest()))
    }

    @Test
    fun `test-only fully populated contract can return READY without changing production flags`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        val evaluation = DestructiveSigningCeremonyPreparation.evaluate(ready)
        assertEquals(SigningCeremonyStatus.READY, evaluation.status)
        assertEquals(SigningCeremonySourceKind.TEST_ONLY_SYNTHETIC, evaluation.sourceKind)
        assertTrue(evaluation.blockers.isEmpty())
        assertEquals(
            SigningCeremonyStatus.NOT_READY,
            DestructiveSigningCeremonyPreparation.evaluateRepositoryDefault().status,
        )
        assertNull(DestructiveValidationExpectedIdentity.repositoryContract().expectedCertificateSha256)
        assertNull(
            DestructiveSigningCeremonyPreparation.refuseTrustedExpectationMint(
                SigningCeremonyPreparationTestFixtures.TEST_ONLY_SIGNED_SHA256,
            ),
        )
    }

    @Test
    fun `each missing field produces its specific blocker`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        assertBlocker(
            ready.copy(ceremonyIdentifier = CeremonyEvidence.Absent),
            SigningCeremonyBlocker.MISSING_CEREMONY_IDENTIFIER,
        )
        assertBlocker(
            ready.copy(approvedScope = CeremonyEvidence.Absent),
            SigningCeremonyBlocker.MISSING_OR_INVALID_APPROVED_SCOPE,
        )
        assertBlocker(
            ready.copy(unsignedCandidateSha256 = CeremonyEvidence.Absent),
            SigningCeremonyBlocker.MISSING_IMMUTABLE_CANDIDATE_IDENTITY,
        )
        assertBlocker(
            ready.copy(checkoutRevision = CeremonyEvidence.Absent, worktreeClean = CeremonyEvidence.Absent),
            SigningCeremonyBlocker.MISSING_OR_DIRTY_CHECKOUT_PROVENANCE,
        )
        assertBlocker(
            ready.copy(offlineKeyCustodyApproval = ApprovalEvidence.Absent),
            SigningCeremonyBlocker.MISSING_OFFLINE_KEY_CUSTODY_APPROVAL,
        )
        assertBlocker(
            ready.copy(operatorApproval = ApprovalEvidence.Absent),
            SigningCeremonyBlocker.MISSING_OPERATOR_APPROVAL,
        )
        assertBlocker(
            ready.copy(witnessApproval = ApprovalEvidence.Absent),
            SigningCeremonyBlocker.MISSING_INDEPENDENT_WITNESS_APPROVAL,
        )
        assertBlocker(
            ready.copy(recoveryBackupVerification = ApprovalEvidence.Absent),
            SigningCeremonyBlocker.MISSING_RECOVERY_BACKUP_VERIFICATION,
        )
        assertBlocker(
            ready.copy(branchProtectionRequiredCheckVerification = ApprovalEvidence.Absent),
            SigningCeremonyBlocker.MISSING_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFICATION,
        )
        assertBlocker(
            ready.copy(publicCertificateSha256 = CeremonyEvidence.Absent, publicCertificateSupplied = false),
            SigningCeremonyBlocker.MISSING_PUBLIC_CERTIFICATE,
        )
        assertBlocker(
            ready.copy(
                independentlySuppliedExpectedCertificateSha256 = CeremonyEvidence.Absent,
                expectedCertificateRecorded = false,
            ),
            SigningCeremonyBlocker.MISSING_INDEPENDENTLY_SUPPLIED_EXPECTED_CERTIFICATE_FINGERPRINT,
        )
        assertBlocker(
            ready.copy(postSigningReinspectionRequired = CeremonyEvidence.Absent),
            SigningCeremonyBlocker.MISSING_POST_SIGNING_REINSPECTION_PLAN,
        )
        assertBlocker(
            ready.copy(
                signedValidationCandidateProduced = false,
                signedCandidateSha256 = CeremonyEvidence.Absent,
            ),
            SigningCeremonyBlocker.SIGNED_VALIDATION_CANDIDATE_NOT_PRODUCED,
        )
    }

    @Test
    fun `partial records remain NOT_READY`() {
        val partial = RepositorySigningCeremonyPreparationSource.record.copy(
            sourceKind = SigningCeremonySourceKind.TEST_ONLY_SYNTHETIC,
            ceremonyIdentifier = CeremonyEvidence.Present(
                SigningCeremonyPreparationTestFixtures.TEST_ONLY_CEREMONY_ID,
            ),
        )
        val evaluation = DestructiveSigningCeremonyPreparation.evaluate(partial)
        assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status)
        assertTrue(evaluation.blockers.contains(SigningCeremonyBlocker.PARTIALLY_FILLED_STATE))
        assertTrue(evaluation.blockers.contains(SigningCeremonyBlocker.MISSING_OPERATOR_APPROVAL))
    }

    @Test
    fun `contradictory records remain NOT_READY`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        val cases = listOf(
            ready.copy(runtimeAuthorization = true),
            ready.copy(trustedExpectationMinted = true),
            ready.copy(productionSigningAuthorized = true),
            ready.copy(productionArtifactSigned = true),
            ready.copy(hardwareValidationApproved = true),
            ready.copy(offlineKeyGenerated = true),
            ready.copy(checkoutRevisionProvesApkOrigin = CeremonyEvidence.Present(true)),
            ready.copy(expectedIdentitySource = ExpectedIdentitySource.OBSERVED_APK),
            ready.copy(signedOutputRemainsValidationCandidate = false),
            ready.copy(publicCertificateSupplied = false),
            ready.copy(
                independentlySuppliedExpectedCertificateSha256 = CeremonyEvidence.Present(
                    SigningCeremonyPreparationTestFixtures.TEST_ONLY_UNSIGNED_SHA256,
                ),
            ),
        )
        cases.forEach { record ->
            val evaluation = DestructiveSigningCeremonyPreparation.evaluate(record)
            assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status, record.toString())
            assertTrue(
                evaluation.blockers.contains(SigningCeremonyBlocker.CONTRADICTORY_STATE) ||
                    evaluation.blockers.contains(
                        SigningCeremonyBlocker.CHECKOUT_PROVENANCE_DOES_NOT_PROVE_APK_ORIGIN,
                    ) ||
                    evaluation.blockers.contains(
                        SigningCeremonyBlocker.PRODUCTION_SIGNING_NOT_AUTHORIZED,
                    ),
                evaluation.blockers.toString(),
            )
        }
    }

    @Test
    fun `invalid certificate fingerprints fail closed`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        val invalid = ready.copy(
            publicCertificateSha256 = CeremonyEvidence.Present("not-a-fingerprint"),
            independentlySuppliedExpectedCertificateSha256 = CeremonyEvidence.Present("also-bad"),
            publicCertificateSupplied = false,
            expectedCertificateRecorded = false,
        )
        val evaluation = DestructiveSigningCeremonyPreparation.evaluate(invalid)
        assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status)
        assertTrue(evaluation.blockers.contains(SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE))
        assertTrue(evaluation.blockers.contains(SigningCeremonyBlocker.MISSING_PUBLIC_CERTIFICATE))
        assertTrue(
            evaluation.blockers.contains(
                SigningCeremonyBlocker.MISSING_INDEPENDENTLY_SUPPLIED_EXPECTED_CERTIFICATE_FINGERPRINT,
            ),
        )
    }

    @Test
    fun `multiple-signer policy fails closed`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        val evaluation = DestructiveSigningCeremonyPreparation.evaluate(
            ready.copy(currentSignerCount = CeremonyEvidence.Present(2)),
        )
        assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status)
        assertTrue(evaluation.blockers.contains(SigningCeremonyBlocker.INVALID_SIGNER_POLICY))
    }

    @Test
    fun `weak or absent signature-scheme policy fails closed`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        val weak = DestructiveSigningCeremonyPreparation.evaluate(
            ready.copy(
                requiredSignatureSchemePolicy = CeremonyEvidence.Present(
                    RequiredSignatureSchemePolicy.V1_ONLY_FORBIDDEN,
                ),
            ),
        )
        val absent = DestructiveSigningCeremonyPreparation.evaluate(
            ready.copy(requiredSignatureSchemePolicy = CeremonyEvidence.Absent),
        )
        assertEquals(SigningCeremonyStatus.NOT_READY, weak.status)
        assertEquals(SigningCeremonyStatus.NOT_READY, absent.status)
        assertTrue(weak.blockers.contains(SigningCeremonyBlocker.INVALID_SIGNATURE_SCHEME_POLICY))
        assertTrue(absent.blockers.contains(SigningCeremonyBlocker.INVALID_SIGNATURE_SCHEME_POLICY))
    }

    @Test
    fun `dirty or unavailable Git provenance fails closed`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        val dirty = DestructiveSigningCeremonyPreparation.evaluate(
            ready.copy(worktreeClean = CeremonyEvidence.Present(false)),
        )
        val unavailable = DestructiveSigningCeremonyPreparation.evaluate(
            ready.copy(checkoutRevision = CeremonyEvidence.Absent),
        )
        val malformed = DestructiveSigningCeremonyPreparation.evaluate(
            ready.copy(checkoutRevision = CeremonyEvidence.Present("not-a-revision")),
        )
        assertEquals(SigningCeremonyStatus.NOT_READY, dirty.status)
        assertEquals(SigningCeremonyStatus.NOT_READY, unavailable.status)
        assertEquals(SigningCeremonyStatus.NOT_READY, malformed.status)
        assertTrue(dirty.blockers.contains(SigningCeremonyBlocker.MISSING_OR_DIRTY_CHECKOUT_PROVENANCE))
        assertTrue(unavailable.blockers.contains(SigningCeremonyBlocker.MISSING_OR_DIRTY_CHECKOUT_PROVENANCE))
        assertTrue(malformed.blockers.contains(SigningCeremonyBlocker.MISSING_OR_DIRTY_CHECKOUT_PROVENANCE))
    }

    @Test
    fun `checkout provenance is never presented as APK origin`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        val claimed = DestructiveSigningCeremonyPreparation.evaluate(
            ready.copy(checkoutRevisionProvesApkOrigin = CeremonyEvidence.Present(true)),
        )
        val repository = DestructiveSigningCeremonyPreparation.evaluateRepositoryDefault()
        assertTrue(claimed.blockers.contains(SigningCeremonyBlocker.CHECKOUT_PROVENANCE_DOES_NOT_PROVE_APK_ORIGIN))
        assertTrue(repository.blockers.contains(SigningCeremonyBlocker.CHECKOUT_PROVENANCE_DOES_NOT_PROVE_APK_ORIGIN))
        assertFalse(repository.checkoutRevisionProvesApkOrigin)
        assertFalse(claimed.status == SigningCeremonyStatus.READY)
    }

    @Test
    fun `observed APK values are never copied into expected values`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        val copied = DestructiveSigningCeremonyPreparation.evaluate(
            ready.copy(expectedIdentitySource = ExpectedIdentitySource.OBSERVED_APK),
        )
        assertEquals(SigningCeremonyStatus.NOT_READY, copied.status)
        assertTrue(copied.blockers.contains(SigningCeremonyBlocker.CONTRADICTORY_STATE))
        val expected = DestructiveValidationExpectedIdentity.repositoryContract()
        assertEquals("com.example.devicemanagement", expected.packageName)
        assertNull(expected.expectedCertificateSha256)
    }

    @Test
    fun `candidate digests cannot become trusted expectations`() {
        val digest = SigningCeremonyPreparationTestFixtures.TEST_ONLY_SIGNED_SHA256
        assertNull(DestructiveSigningCeremonyPreparation.refuseTrustedExpectationMint(digest))
        val minted = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
            .copy(trustedExpectationMinted = true)
        val evaluation = DestructiveSigningCeremonyPreparation.evaluate(minted)
        assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status)
        assertTrue(evaluation.blockers.contains(SigningCeremonyBlocker.CONTRADICTORY_STATE))
        assertTrue(evaluation.trustedExpectationMintRefused)
    }

    @Test
    fun `approval string shortcuts fail closed`() {
        val ready = SigningCeremonyPreparationTestFixtures.fullyPopulatedReadyContract()
        val evaluation = DestructiveSigningCeremonyPreparation.evaluate(
            ready.copy(operatorApproval = ApprovalEvidence.Recorded("approved")),
        )
        assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status)
        assertTrue(evaluation.blockers.contains(SigningCeremonyBlocker.UNKNOWN_OR_MALFORMED_STATE))
        assertFalse(evaluation.operatorApprovalAvailable)
    }

    @Test
    fun `repository default source cannot be mutated to READY`() {
        val mutated = RepositorySigningCeremonyPreparationSource.record.copy(
            ceremonyIdentifier = CeremonyEvidence.Present(
                SigningCeremonyPreparationTestFixtures.TEST_ONLY_CEREMONY_ID,
            ),
        )
        val evaluation = DestructiveSigningCeremonyPreparation.evaluate(mutated)
        assertEquals(SigningCeremonySourceKind.REPOSITORY_DEFAULT, evaluation.sourceKind)
        assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status)
        assertNotEquals(SigningCeremonyStatus.READY, evaluation.status)
    }

    @Test
    fun `production task source never references the test-only fixture`() {
        val task = java.io.File("src/main/kotlin/DestructiveSigningCeremonyPreparationTask.kt").readText()
        val contract = java.io.File("src/main/kotlin/DestructiveSigningCeremonyPreparation.kt").readText()
        val gradle = java.io.File("../app/build.gradle.kts").readText()
        assertFalse(task.contains("SigningCeremonyPreparationTestFixtures"))
        assertFalse(contract.contains("SigningCeremonyPreparationTestFixtures"))
        assertFalse(gradle.contains("SigningCeremonyPreparationTestFixtures"))
        assertTrue(task.contains("evaluateRepositoryDefault"))
        assertFalse(task.contains("evaluate(SigningCeremonyPreparationTestFixtures"))
    }

    private fun assertBlocker(
        record: SigningCeremonyPreparationRecord,
        blocker: SigningCeremonyBlocker,
    ) {
        val evaluation = DestructiveSigningCeremonyPreparation.evaluate(record)
        assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status, blocker.name)
        assertTrue(evaluation.blockers.contains(blocker), evaluation.blockers.toString())
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
