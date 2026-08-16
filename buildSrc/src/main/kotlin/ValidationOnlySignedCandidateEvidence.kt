import java.io.File

/**
 * Snapshot-backed inspection for an explicitly requested signed
 * disposableValidation APK.
 *
 * This reuses the Checkpoint 19F/19J immutable-snapshot and cleanup
 * envelope. Inspectors never receive the mutable AGP source APK.
 * The snapshot digest is not written to the 19R report or Git.
 */
object ValidationOnlySignedCandidateEvidence {
    const val SNAPSHOT_RELATIVE_PATH =
        "app/build/tmp/signed-disposable-validation-snapshot"
    const val REPORT_RELATIVE_PATH =
        "app/build/reports/signed-disposable-validation.txt"

    data class Result(
        val decision: ValidationOnlySigningGate.SignedCandidateDecision,
        val signingSnapshotPath: String,
        val identitySnapshotPath: String,
        val schemeSnapshotPath: String,
        val buildPurposeObserved: String?,
        val buildPurposeStatus: String,
        val aapt2Available: Boolean,
        val identityDetail: String,
        val packageMatches: Boolean,
        val adminMatches: Boolean,
        val policiesMatch: Boolean,
        val minSdkMatches: Boolean,
        val targetSdkMatches: Boolean,
        val apkSha256: String,
        val signingCertificateSha256: String?,
        val signerCount: Int,
        val signerCountReliable: Boolean,
        val v2Present: Boolean,
        val v3Present: Boolean,
        val schemesReliable: Boolean,
    ) {
        val sameSnapshotForAllInspectors: Boolean
            get() = signingSnapshotPath == identitySnapshotPath &&
                identitySnapshotPath == schemeSnapshotPath &&
                File(signingSnapshotPath).name ==
                DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME

        fun renderSafeDiagnostics(): String {
            val safeObserved = when (buildPurposeObserved) {
                null -> "NONE"
                DestructiveValidationExpectedIdentity
                    .BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION ->
                    DestructiveValidationExpectedIdentity
                        .BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION
                else -> "OTHER"
            }
            val safeDetail = identityDetail
                .replace('\r', '_')
                .replace('\n', '_')
                .take(256)
            val rendered = buildString {
                appendLine("build_purpose_observed=$safeObserved")
                appendLine("build_purpose_status=$buildPurposeStatus")
                appendLine("aapt2_available=$aapt2Available")
                appendLine("identity_detail=$safeDetail")
                appendLine("package_matches=$packageMatches")
                appendLine("admin_matches=$adminMatches")
                appendLine("policies_match=$policiesMatch")
                appendLine("min_sdk_matches=$minSdkMatches")
                appendLine("target_sdk_matches=$targetSdkMatches")
            }
            check(!Regex("\\b[0-9a-fA-F]{40,}\\b").containsMatchIn(rendered)) {
                "signed-candidate diagnostics must not contain digest values"
            }
            return rendered
        }
    }

    fun inspect(
        apk: File,
        snapshotDirectory: File,
        expectedCertificateSha256: String?,
        androidSdkDir: File? = null,
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
        gitProvenance: DestructiveValidationCandidateEvidence.GitProvenance? =
            DestructiveValidationCandidateEvidence.GitProvenance(
                revision = "UNAVAILABLE",
                worktree = "UNAVAILABLE",
            ),
    ): Result {
        var signingSnapshotPath: String? = null
        var identitySnapshotPath: String? = null
        var schemeSnapshotPath: String? = null
        var signing: DestructiveValidationCandidateEvidence.CandidateSigningInspection? = null
        var identity: DestructiveValidationCandidateEvidence.CandidateApkIdentity? = null
        var schemes: ValidationOnlySigningGate.ObservedSignatureSchemes? = null
        val candidateReport = DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
            apk = apk,
            snapshotDirectory = snapshotDirectory,
            afterInitialDigest = afterInitialDigest,
            afterSnapshotCreated = afterSnapshotCreated,
            signingInspector = { snapshot ->
                signingSnapshotPath = snapshot.absolutePath
                schemeSnapshotPath = snapshot.absolutePath
                signing = (
                    signingInspector ?: { file ->
                        DestructiveValidationCandidateInspectors.inspectSigning(
                            file,
                            androidSdkDir,
                        )
                    }
                    ).invoke(snapshot)
                schemes = (
                    schemeInspector ?: { file ->
                        CheckSignedDisposableValidationTask.readSignatureSchemes(
                            file,
                            androidSdkDir,
                        )
                    }
                    ).invoke(snapshot)
                signing!!
            },
            identityInspector = { snapshot ->
                identitySnapshotPath = snapshot.absolutePath
                identity = (
                    identityInspector ?: { file ->
                        DestructiveValidationCandidateInspectors.inspectIdentity(
                            file,
                            androidSdkDir,
                        )
                    }
                    ).invoke(snapshot)
                identity!!
            },
            gitProvenance = gitProvenance,
            cleanup = cleanup,
        )
        val signingPath = checkNotNull(signingSnapshotPath)
        val identityPath = checkNotNull(identitySnapshotPath)
        val schemePath = checkNotNull(schemeSnapshotPath)
        check(signingPath == identityPath && identityPath == schemePath) {
            "signed-candidate inspectors must read the same immutable snapshot"
        }
        check(
            File(signingPath).name ==
                DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME,
        ) {
            "signed-candidate inspectors must read the task-owned snapshot file"
        }
        val observedSigning = checkNotNull(signing)
        val observedIdentity = checkNotNull(identity)
        val observedSchemes = checkNotNull(schemes)
        val identityContract = DestructiveValidationExpectedIdentity.repositoryContract()
        val expected = ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(
            expectedCertificateSha256.orEmpty(),
        )
        val observedFingerprint = observedSigning.certificateSha256?.let { fingerprint ->
            ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(fingerprint)
        }
        val debugOrTest =
            observedSigning.classification ==
                DestructiveValidationCandidateEvidence.Signing.DEBUG_SIGNED ||
                observedSigning.classification ==
                DestructiveValidationCandidateEvidence.Signing.TEST_SIGNED
        val verified =
            observedSigning.apksignerExecuted &&
                observedSigning.classification ==
                DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED
        val decision = ValidationOnlySigningGate.evaluateSignedCandidate(
            ValidationOnlySigningGate.ObservedSignedValidationApk(
                signingVerified = verified,
                signerCount = observedSigning.signerCount,
                signerCountReliable = observedSigning.signerCountReliable,
                v2Present = observedSchemes.v2Present,
                v3Present = observedSchemes.v3Present,
                schemesReliable = observedSchemes.reliable,
                buildPurpose = observedIdentity.buildPurposeObserved,
                identityMatches = ValidationOnlySigningGate.identityMatchesRepositoryContract(
                    packageName = observedIdentity.packageName,
                    adminComponent = observedIdentity.adminComponent,
                    policies = observedIdentity.policies,
                    minSdk = observedIdentity.minSdk?.toIntOrNull(),
                    targetSdk = observedIdentity.targetSdk?.toIntOrNull(),
                ),
                debugOrTestCertificate = debugOrTest,
                certificateFingerprintMatches =
                    expected != null && expected == observedFingerprint,
            ),
        )
        return Result(
            decision = decision,
            signingSnapshotPath = signingPath,
            identitySnapshotPath = identityPath,
            schemeSnapshotPath = schemePath,
            buildPurposeObserved = observedIdentity.buildPurposeObserved,
            buildPurposeStatus = observedIdentity.buildPurposeStatus,
            aapt2Available = observedIdentity.aapt2Available,
            identityDetail = observedIdentity.detail,
            packageMatches = observedIdentity.packageName == identityContract.packageName,
            adminMatches = observedIdentity.adminComponent == identityContract.adminComponent,
            policiesMatch =
                observedIdentity.policies?.toSet() == identityContract.policies.toSet(),
            minSdkMatches =
                observedIdentity.minSdk?.toIntOrNull() == identityContract.minSdk,
            targetSdkMatches =
                observedIdentity.targetSdk?.toIntOrNull() == identityContract.targetSdk,
            apkSha256 = candidateReport.apkSha256,
            signingCertificateSha256 = candidateReport.signingCertificateSha256,
            signerCount = observedSigning.signerCount,
            signerCountReliable = observedSigning.signerCountReliable,
            v2Present = observedSchemes.v2Present,
            v3Present = observedSchemes.v3Present,
            schemesReliable = observedSchemes.reliable,
        )
    }
}
