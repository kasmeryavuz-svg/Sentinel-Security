/**
 * Test-only synthetic signing-ceremony fixtures.
 *
 * These values are not production source, do not generate a key or
 * signed APK, do not mint runtime trust, and must never be used by
 * `:app:checkDestructiveSigningCeremonyPreparation`.
 */
object SigningCeremonyPreparationTestFixtures {
    const val TEST_ONLY_CEREMONY_ID = "TEST_ONLY_CEREMONY_ID"
    const val TEST_ONLY_OPERATOR_REF = "TEST_ONLY_OPERATOR_APPROVAL_REF"
    const val TEST_ONLY_WITNESS_REF = "TEST_ONLY_WITNESS_APPROVAL_REF"
    const val TEST_ONLY_CUSTODY_REF = "TEST_ONLY_KEY_CUSTODY_REF"
    const val TEST_ONLY_RECOVERY_REF = "TEST_ONLY_RECOVERY_BACKUP_REF"
    const val TEST_ONLY_BRANCH_PROTECTION_REF = "TEST_ONLY_BRANCH_PROTECTION_REF"
    const val TEST_ONLY_UNSIGNED_SHA256 =
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val TEST_ONLY_SNAPSHOT_SHA256 =
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    const val TEST_ONLY_SIGNED_SHA256 =
        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
    const val TEST_ONLY_CERT_SHA256 =
        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    const val TEST_ONLY_REVISION = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    fun presentReadyOnlyInTests(
        evaluation: SigningCeremonyEvaluation,
    ): SigningCeremonyEvaluation {
        check(evaluation.sourceKind == SigningCeremonySourceKind.TEST_ONLY_SYNTHETIC) {
            "test-only READY presentation requires a TEST_ONLY_SYNTHETIC record"
        }
        check(evaluation.blockers.isEmpty()) {
            "test-only READY presentation requires an empty blocker set"
        }
        check(evaluation.status == SigningCeremonyStatus.NOT_READY) {
            "main evaluate() must have returned NOT_READY"
        }
        return evaluation.copy(status = SigningCeremonyStatus.READY)
    }

    fun fullyPopulatedReadyContract(): SigningCeremonyPreparationRecord {
        return SigningCeremonyPreparationRecord(
            sourceKind = SigningCeremonySourceKind.TEST_ONLY_SYNTHETIC,
            ceremonyIdentifier = CeremonyEvidence.Present(TEST_ONLY_CEREMONY_ID),
            approvedScope = CeremonyEvidence.Present(
                ApprovedCeremonyScope.DISPOSABLE_DEVICE_VALIDATION_CANDIDATE_SIGNING,
            ),
            unsignedCandidateSha256 = CeremonyEvidence.Present(TEST_ONLY_UNSIGNED_SHA256),
            immutableSnapshotSha256 = CeremonyEvidence.Present(TEST_ONLY_SNAPSHOT_SHA256),
            checkoutRevision = CeremonyEvidence.Present(TEST_ONLY_REVISION),
            worktreeClean = CeremonyEvidence.Present(true),
            checkoutRevisionProvesApkOrigin = CeremonyEvidence.Present(false),
            offlineKeyCustodyApproval = ApprovalEvidence.Recorded(TEST_ONLY_CUSTODY_REF),
            operatorApproval = ApprovalEvidence.Recorded(TEST_ONLY_OPERATOR_REF),
            witnessApproval = ApprovalEvidence.Recorded(TEST_ONLY_WITNESS_REF),
            recoveryBackupVerification = ApprovalEvidence.Recorded(TEST_ONLY_RECOVERY_REF),
            branchProtectionRequiredCheckVerification = ApprovalEvidence.Recorded(
                TEST_ONLY_BRANCH_PROTECTION_REF,
            ),
            requiredSignerPolicy = CeremonyEvidence.Present(
                RequiredSignerPolicy.SINGLE_CURRENT_SIGNER,
            ),
            currentSignerCount = CeremonyEvidence.Present(1),
            requiredSignatureSchemePolicy = CeremonyEvidence.Present(
                RequiredSignatureSchemePolicy.V2_AND_V3_REQUIRED,
            ),
            publicCertificateSha256 = CeremonyEvidence.Present(TEST_ONLY_CERT_SHA256),
            independentlySuppliedExpectedCertificateSha256 = CeremonyEvidence.Present(
                TEST_ONLY_CERT_SHA256,
            ),
            expectedIdentity = DestructiveValidationExpectedIdentity.repositoryContract(),
            expectedIdentitySource = ExpectedIdentitySource.REPOSITORY_CONTRACT,
            postSigningReinspectionRequired = CeremonyEvidence.Present(true),
            signedOutputRemainsValidationCandidate = true,
            signingEvidenceCannotMintRuntimeTrust = true,
            signedApkDigestCannotBecomeTrustedExpectation = true,
            productionSigningAuthorized = false,
            signedValidationCandidateProduced = true,
            signedCandidateSha256 = CeremonyEvidence.Present(TEST_ONLY_SIGNED_SHA256),
            offlineKeyGenerated = false,
            publicCertificateSupplied = true,
            expectedCertificateRecorded = true,
            productionArtifactSigned = false,
            runtimeAuthorization = false,
            trustedExpectationMinted = false,
            hardwareValidationApproved = false,
            ceremonyRecordFilled = true,
        )
    }
}
