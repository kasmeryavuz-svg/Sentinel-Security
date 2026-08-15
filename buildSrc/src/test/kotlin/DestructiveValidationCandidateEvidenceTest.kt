import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestructiveValidationCandidateEvidenceTest {
    @Test
    fun `repository contract never infers expected identity or a certificate`() {
        val expected = DestructiveValidationExpectedIdentity.repositoryContract()
        assertEquals("com.example.devicemanagement", expected.packageName)
        assertEquals(
            "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
            expected.adminComponent,
        )
        assertEquals(listOf("disable-camera", "wipe-data"), expected.policies)
        assertEquals(26, expected.minSdk)
        assertEquals(36, expected.targetSdk)
        assertEquals(null, expected.expectedCertificateSha256)
        assertEquals("DISPOSABLE_DEVICE_VALIDATION", expected.buildPurpose)
    }

    @Test
    fun `unsigned matching identity remains ineligible and untrusted`() {
        val report = inspect(
            signing = unsignedSigning(),
            identity = matchingIdentity(),
        )
        assertEquals("INELIGIBLE", report.candidateStatus)
        assertEquals(DestructiveValidationCandidateEvidence.Signing.UNSIGNED, report.signing)
        assertFalse(report.runtimeAuthorization)
        assertFalse(report.trustedExpectationMinted)
        assertFalse(report.productionSigningEnabled)
        assertFalse(report.hardwareValidationApproved)
        assertFalse(report.expectedCertificateConfigured)
        assertNull(report.buildPurposeObserved)
        assertFalse(report.inspectionRevisionProvesApkOrigin)
        val rendered = report.render()
        assertTrue(rendered.contains("authority=UNTRUSTED_CANDIDATE_ONLY"))
        assertTrue(rendered.contains("runtime_authorization=false"))
        assertTrue(rendered.contains("trusted_expectation_minted=false"))
        assertTrue(rendered.contains("production_signing_enabled=false"))
        assertTrue(rendered.contains("hardware_validation_approved=false"))
        assertTrue(rendered.contains("candidate_status=INELIGIBLE"))
        assertTrue(rendered.contains("signing=UNSIGNED"))
        assertTrue(rendered.contains("build_purpose_expected=DISPOSABLE_DEVICE_VALIDATION"))
        assertTrue(rendered.contains("build_purpose_observed=UNAVAILABLE"))
        assertTrue(rendered.contains("build_purpose_matches=false"))
        assertTrue(rendered.contains("build_purpose_status=UNAVAILABLE"))
        assertFalse(rendered.contains("\nbuild_purpose=DISPOSABLE_DEVICE_VALIDATION"))
        assertTrue(rendered.contains("inspection_git_revision=UNAVAILABLE"))
        assertTrue(rendered.contains("inspection_worktree=UNAVAILABLE"))
        assertTrue(rendered.contains("inspection_revision_proves_apk_origin=false"))
        assertTrue(report.ineligibilityReasons.contains("build_purpose_unavailable"))
        assertTrue(report.ineligibilityReasons.contains("inspection_revision_unavailable"))
        assertTrue(report.ineligibilityReasons.contains("inspection_worktree_unavailable"))
        DestructiveValidationCandidateEvidence.assertUnsignedIneligibleProof(report)
    }

    @Test
    fun `debug test unknown malformed unverifiable and multi-signer artifacts stay ineligible`() {
        listOf(
            DestructiveValidationCandidateEvidence.Signing.DEBUG_SIGNED,
            DestructiveValidationCandidateEvidence.Signing.TEST_SIGNED,
            DestructiveValidationCandidateEvidence.Signing.UNKNOWN,
            DestructiveValidationCandidateEvidence.Signing.MALFORMED,
            DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE,
            DestructiveValidationCandidateEvidence.Signing.MULTIPLE_SIGNERS,
        ).forEach { classification ->
            val signerCount =
                if (classification == DestructiveValidationCandidateEvidence.Signing.MULTIPLE_SIGNERS) {
                    2
                } else {
                    1
                }
            val report = inspect(
                signing = DestructiveValidationCandidateEvidence.CandidateSigningInspection(
                    classification = classification,
                    certificateSha256 = if (signerCount == 1) "ab".repeat(32) else null,
                    signerCount = signerCount,
                    apksignerAvailable = true,
                    apksignerExecuted = true,
                    detail = "fixture",
                ),
                identity = matchingIdentity(),
            )
            assertEquals("INELIGIBLE", report.candidateStatus, classification.name)
            assertEquals(classification, report.signing)
            assertFalse(report.trustedExpectationMinted)
            assertFalse(report.runtimeAuthorization)
        }
    }

    @Test
    fun `two current signers with the same certificate digest stay ineligible`() {
        val report = inspect(
            signing = DestructiveValidationCandidateEvidence.CandidateSigningInspection(
                classification = DestructiveValidationCandidateEvidence.Signing.MULTIPLE_SIGNERS,
                certificateSha256 = null,
                signerCount = 2,
                signerCountReliable = true,
                lineagePresent = false,
                apksignerAvailable = true,
                apksignerExecuted = true,
                detail = "two current signer indexes share one digest",
            ),
            identity = matchingIdentity(),
        )
        assertEquals("INELIGIBLE", report.candidateStatus)
        assertEquals(DestructiveValidationCandidateEvidence.Signing.MULTIPLE_SIGNERS, report.signing)
        assertTrue(report.ineligibilityReasons.contains("signing=MULTIPLE_SIGNERS"))
        assertFalse(report.trustedExpectationMinted)
    }

    @Test
    fun `unreliable signer count is never treated as one signer`() {
        val report = inspect(
            signing = DestructiveValidationCandidateEvidence.CandidateSigningInspection(
                classification = DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE,
                certificateSha256 = "ab".repeat(32),
                signerCount = -1,
                signerCountReliable = false,
                apksignerAvailable = true,
                apksignerExecuted = true,
                detail = "unreliable",
            ),
            identity = matchingIdentity(),
        )
        assertEquals("INELIGIBLE", report.candidateStatus)
        assertEquals(DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE, report.signing)
        assertTrue(report.ineligibilityReasons.contains("signer_count_unreliable"))
        assertFalse(report.trustedExpectationMinted)
    }

    @Test
    fun `wrong package admin and policies fail closed`() {
        val wrongPackage = inspect(
            signing = unknownSigned(),
            identity = matchingIdentity().copy(packageName = "com.example.other"),
        )
        assertEquals("INELIGIBLE", wrongPackage.candidateStatus)
        assertTrue(wrongPackage.ineligibilityReasons.contains("package_mismatch"))

        val wrongAdmin = inspect(
            signing = unknownSigned(),
            identity = matchingIdentity().copy(
                adminComponent = "com.example.devicemanagement/com.example.other.Admin",
            ),
        )
        assertEquals("INELIGIBLE", wrongAdmin.candidateStatus)
        assertTrue(wrongAdmin.ineligibilityReasons.contains("admin_mismatch"))

        val wrongPolicies = inspect(
            signing = unknownSigned(),
            identity = matchingIdentity().copy(
                policies = listOf("disable-camera", "wipe-data", "force-lock"),
            ),
        )
        assertEquals("INELIGIBLE", wrongPolicies.candidateStatus)
        assertTrue(wrongPolicies.ineligibilityReasons.contains("policies_mismatch"))
    }

    @Test
    fun `verified unclassified signature without a configured expected certificate stays ineligible`() {
        val report = inspect(
            signing = signedUnclassified(),
            identity = matchingIdentity(),
        )
        assertEquals("INELIGIBLE", report.candidateStatus)
        assertEquals(
            DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED,
            report.signing,
        )
        assertTrue(report.ineligibilityReasons.contains("expected_certificate_unconfigured"))
        assertTrue(report.ineligibilityReasons.contains("build_purpose_unavailable"))
        assertFalse(report.trustedExpectationMinted)
        assertFalse(report.expectedCertificateConfigured)
        assertFalse(report.runtimeAuthorization)
    }

    @Test
    fun `dirty or unavailable inspection provenance makes the candidate ineligible`() {
        val dirty = evaluateFullyMatchingFixture(
            git = DestructiveValidationCandidateEvidence.GitProvenance(
                revision = "a".repeat(40),
                worktree = "DIRTY",
            ),
        )
        assertEquals("INELIGIBLE", dirty.candidateStatus)
        assertTrue(dirty.ineligibilityReasons.contains("inspection_worktree_dirty"))

        val unavailableRevision = evaluateFullyMatchingFixture(
            git = DestructiveValidationCandidateEvidence.GitProvenance(
                revision = "UNAVAILABLE",
                worktree = "CLEAN",
            ),
        )
        assertEquals("INELIGIBLE", unavailableRevision.candidateStatus)
        assertTrue(unavailableRevision.ineligibilityReasons.contains("inspection_revision_unavailable"))

        val unavailableWorktree = evaluateFullyMatchingFixture(
            git = DestructiveValidationCandidateEvidence.GitProvenance(
                revision = "a".repeat(40),
                worktree = "UNAVAILABLE",
            ),
        )
        assertEquals("INELIGIBLE", unavailableWorktree.candidateStatus)
        assertTrue(unavailableWorktree.ineligibilityReasons.contains("inspection_worktree_unavailable"))
    }

    @Test
    fun `missing or mismatched observed build purpose makes the candidate ineligible`() {
        val missing = evaluateFullyMatchingFixture(
            identity = matchingIdentity().copy(buildPurposeObserved = null),
        )
        assertEquals("INELIGIBLE", missing.candidateStatus)
        assertTrue(missing.ineligibilityReasons.contains("build_purpose_unavailable"))
        assertEquals("UNAVAILABLE", missing.render().lineSequence().first { it.startsWith("build_purpose_observed=") }.substringAfter("="))

        val mismatched = evaluateFullyMatchingFixture(
            identity = matchingIdentity().copy(buildPurposeObserved = "SOMETHING_ELSE"),
        )
        assertEquals("INELIGIBLE", mismatched.candidateStatus)
        assertTrue(mismatched.ineligibilityReasons.contains("build_purpose_mismatch"))
        assertTrue(mismatched.render().contains("build_purpose_matches=false"))
        assertEquals("SOMETHING_ELSE", mismatched.buildPurposeObserved)
        assertEquals(
            "DISPOSABLE_DEVICE_VALIDATION",
            mismatched.buildPurposeExpected,
        )

        val duplicate = evaluateFullyMatchingFixture(
            identity = matchingIdentity().copy(
                buildPurposeObserved = null,
                buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_DUPLICATE,
            ),
        )
        assertEquals("INELIGIBLE", duplicate.candidateStatus)
        assertTrue(duplicate.ineligibilityReasons.contains("build_purpose_duplicate"))
        assertNull(duplicate.buildPurposeObserved)

        val malformed = evaluateFullyMatchingFixture(
            identity = matchingIdentity().copy(
                buildPurposeObserved = null,
                buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_MALFORMED,
            ),
        )
        assertEquals("INELIGIBLE", malformed.candidateStatus)
        assertTrue(malformed.ineligibilityReasons.contains("build_purpose_malformed"))

        val uninspectable = evaluateFullyMatchingFixture(
            identity = matchingIdentity().copy(
                buildPurposeObserved = null,
                buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_UNINSPECTABLE,
            ),
        )
        assertEquals("INELIGIBLE", uninspectable.candidateStatus)
        assertTrue(uninspectable.ineligibilityReasons.contains("build_purpose_uninspectable"))
    }

    @Test
    fun `test-only fully matching fixture can reach build-only eligible without minting trust`() {
        val report = evaluateFullyMatchingFixture()
        assertEquals("ELIGIBLE", report.candidateStatus)
        assertEquals(
            DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED,
            report.signing,
        )
        assertEquals(DestructiveValidationCandidateEvidence.AUTHORITY, report.authority)
        assertFalse(report.runtimeAuthorization)
        assertFalse(report.trustedExpectationMinted)
        assertFalse(report.productionSigningEnabled)
        assertFalse(report.hardwareValidationApproved)
        assertTrue(report.expectedCertificateConfigured)
        assertTrue(report.lineagePresent)
        assertTrue(report.signerCountReliable)
        assertEquals(1, report.signerCount)
        assertEquals("DISPOSABLE_DEVICE_VALIDATION", report.buildPurposeObserved)
        assertEquals(DestructiveValidationBuildPurposeParser.STATUS_OBSERVED, report.buildPurposeStatus)
        assertTrue(report.render().contains("build_purpose_matches=true"))
        assertTrue(report.render().contains("build_purpose_status=OBSERVED"))
        assertFalse(report.inspectionRevisionProvesApkOrigin)
        assertEquals("CHECKOUT", report.inspectionRevisionSource)
        assertEquals("UNAVAILABLE", report.artifactEmbeddedRevision)
        assertTrue(report.ineligibilityReasons.isEmpty())
        val rendered = report.render()
        assertTrue(rendered.contains("authority=UNTRUSTED_CANDIDATE_ONLY"))
        assertTrue(rendered.contains("runtime_authorization=false"))
        assertTrue(rendered.contains("trusted_expectation_minted=false"))
        assertTrue(rendered.contains("inspection_revision_proves_apk_origin=false"))
        assertFalse(rendered.contains("signing=PRODUCTION_SIGNED"))
        assertFailsWith<IllegalStateException> {
            DestructiveValidationCandidateEvidence.assertUnsignedIneligibleProof(report)
        }
    }

    @Test
    fun `inspection checkout revision is never claimed as apk origin even when a fixture says so`() {
        val report = evaluateFullyMatchingFixture(
            git = DestructiveValidationCandidateEvidence.GitProvenance(
                revision = "a".repeat(40),
                worktree = "CLEAN",
                revisionSource = "CHECKOUT",
                artifactEmbeddedRevision = "UNAVAILABLE",
                revisionProvesApkOrigin = true,
            ),
        )
        assertEquals("ELIGIBLE", report.candidateStatus)
        assertFalse(report.inspectionRevisionProvesApkOrigin)
        assertTrue(report.render().contains("inspection_revision_proves_apk_origin=false"))
    }

    @Test
    fun `AAB ZIP directory missing symlink and non-apk inputs are rejected`() {
        val root = Files.createTempDirectory("candidate-reject").toFile()
        try {
            val aab = File(root, "app.aab").apply { writeText("not-an-apk") }
            val zip = File(root, "app.zip").apply { writeText("zip") }
            val dir = File(root, "dir").apply { mkdirs() }
            val missing = File(root, "missing.apk")
            val txt = File(root, "app.txt").apply { writeText("nope") }
            val symlink = File(root, "link.apk")
            val target = File(root, "target.apk").apply { writeText("apk") }
            Files.createSymbolicLink(symlink.toPath(), target.toPath())

            assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.acceptCandidateFile(aab)
            }
            assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.acceptCandidateFile(zip)
            }
            assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.acceptCandidateFile(dir)
            }
            assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.acceptCandidateFile(missing)
            }
            assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.acceptCandidateFile(txt)
            }
            assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.acceptCandidateFile(symlink)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `persistent source mutation during inspection is rejected and snapshot is deleted`() {
        val root = Files.createTempDirectory("candidate-changed").toFile()
        val snapshotDir = File(root, "snapshot")
        try {
            val apk = writeZipApk(File(root, "app-release-unsigned.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterInitialDigest = { apk.appendBytes(byteArrayOf(0x01)) },
                    signingInspector = { unsignedSigning() },
                    identityInspector = { matchingIdentity() },
                    gitProvenance = unavailableGit(),
                )
            }
            assertTrue(thrown.message.orEmpty().contains("changed during"))
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `swap-and-restore of the source during inspection is rejected`() {
        val root = Files.createTempDirectory("candidate-swap").toFile()
        val snapshotDir = File(root, "snapshot")
        try {
            val apk = writeZipApk(File(root, "app-release-unsigned.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterInitialDigest = {
                        val bytes = apk.readBytes()
                        check(apk.delete())
                        apk.writeBytes(bytes)
                        apk.setLastModified(apk.lastModified() + 5_000L)
                    },
                    signingInspector = { unsignedSigning() },
                    identityInspector = { matchingIdentity() },
                    gitProvenance = unavailableGit(),
                )
            }
            assertTrue(thrown.message.orEmpty().contains("changed during"))
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `parent-directory symlink is rejected`() {
        val root = Files.createTempDirectory("candidate-parent-link").toFile()
        try {
            val realDir = File(root, "real").apply { mkdirs() }
            writeZipApk(File(realDir, "app-release-unsigned.apk"))
            val linkDir = File(root, "link")
            Files.createSymbolicLink(linkDir.toPath(), realDir.toPath())
            val viaLink = File(linkDir, "app-release-unsigned.apk")
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = viaLink,
                    signingInspector = { unsignedSigning() },
                    identityInspector = { matchingIdentity() },
                    gitProvenance = unavailableGit(),
                )
            }
            assertTrue(thrown.message.orEmpty().contains("symbolic link"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `source replacement during inspection is rejected`() {
        val root = Files.createTempDirectory("candidate-replaced").toFile()
        val snapshotDir = File(root, "snapshot")
        try {
            val apk = writeZipApk(File(root, "app-release-unsigned.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterInitialDigest = {
                        apk.delete()
                        writeZipApk(apk, extraEntry = "replaced" to "different-bytes")
                    },
                    signingInspector = { unsignedSigning() },
                    identityInspector = { matchingIdentity() },
                    gitProvenance = unavailableGit(),
                )
            }
            assertTrue(thrown.message.orEmpty().contains("changed during"))
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `stable snapshot is inspected then deleted after success`() {
        val root = Files.createTempDirectory("candidate-stable").toFile()
        val snapshotDir = File(root, "snapshot")
        try {
            val apk = writeZipApk(File(root, "app-release-unsigned.apk"))
            val originalDigest = DestructiveValidationCandidateEvidence.sha256OfExactBytes(apk)
            var inspectedSigning: File? = null
            var inspectedIdentity: File? = null
            var snapshotDuringInspect: File? = null
            val report = DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                apk = apk,
                snapshotDirectory = snapshotDir,
                afterSnapshotCreated = { snapshot ->
                    snapshotDuringInspect = snapshot
                    assertTrue(snapshot.isFile)
                    assertEquals(
                        DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME,
                        snapshot.name,
                    )
                    assertEquals(
                        originalDigest,
                        DestructiveValidationCandidateEvidence.sha256OfExactBytes(snapshot),
                    )
                },
                signingInspector = { file ->
                    inspectedSigning = file
                    unsignedSigning()
                },
                identityInspector = { file ->
                    inspectedIdentity = file
                    matchingIdentity()
                },
                gitProvenance = unavailableGit(),
            )
            assertEquals(originalDigest, report.apkSha256)
            assertEquals(
                DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME,
                inspectedSigning?.name,
            )
            assertEquals(
                DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME,
                inspectedIdentity?.name,
            )
            assertTrue(inspectedSigning!!.absolutePath.contains("snapshot"))
            assertFalse(inspectedSigning!!.absolutePath == apk.absolutePath)
            assertEquals(snapshotDuringInspect!!.absolutePath, inspectedSigning!!.absolutePath)
            assertEquals("INELIGIBLE", report.candidateStatus)
            assertEquals(
                originalDigest,
                DestructiveValidationCandidateEvidence.sha256OfExactBytes(apk),
            )
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `snapshot mutation during inspection is rejected and cleaned up`() {
        val root = Files.createTempDirectory("candidate-snapshot-mutated").toFile()
        val snapshotDir = File(root, "snapshot")
        try {
            val apk = writeZipApk(File(root, "app-release-unsigned.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterSnapshotCreated = { snapshot ->
                        snapshot.setWritable(true)
                        snapshot.appendBytes(byteArrayOf(0x02))
                    },
                    signingInspector = { unsignedSigning() },
                    identityInspector = { matchingIdentity() },
                    gitProvenance = unavailableGit(),
                )
            }
            assertTrue(thrown.message.orEmpty().contains("Snapshot bytes changed"))
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
            assertTrue(apk.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `malformed apk bytes fail closed as ineligible`() {
        val root = Files.createTempDirectory("candidate-malformed").toFile()
        try {
            val apk = File(root, "broken.apk").apply { writeBytes(byteArrayOf(0x00, 0x01, 0x02)) }
            val report = DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                apk = apk,
                androidSdkDir = File(root, "no-sdk"),
                gitProvenance = unavailableGit(),
            )
            assertEquals("INELIGIBLE", report.candidateStatus)
            assertEquals(DestructiveValidationCandidateEvidence.Signing.MALFORMED, report.signing)
            assertFalse(report.trustedExpectationMinted)
            assertFalse(report.runtimeAuthorization)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `unsigned zip apk without sdk tools stays ineligible`() {
        val root = Files.createTempDirectory("candidate-unsigned-zip").toFile()
        try {
            val apk = writeZipApk(File(root, "app-release-unsigned.apk"))
            val report = DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                apk = apk,
                androidSdkDir = File(root, "no-sdk"),
                gitProvenance = unavailableGit(),
            )
            assertEquals("INELIGIBLE", report.candidateStatus)
            assertTrue(
                report.signing == DestructiveValidationCandidateEvidence.Signing.UNSIGNED ||
                    report.signing == DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE,
            )
            assertFalse(report.trustedExpectationMinted)
            assertTrue(report.render().contains("authority=UNTRUSTED_CANDIDATE_ONLY"))
            assertTrue(report.render().contains("build_purpose_observed=UNAVAILABLE"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `loose zip marker is not treated as observed build purpose`() {
        val xmltree = """
            E: application (line=4)
              E: meta-data (line=10)
                A: android:name(0x01010003)="android.app.device_admin" (Raw: "android.app.device_admin")
        """.trimIndent()
        val parsed = DestructiveValidationCandidateInspectors.inspectObservedBuildPurpose(xmltree)
        assertEquals(DestructiveValidationBuildPurposeParser.STATUS_UNAVAILABLE, parsed.status)
        assertNull(parsed.observed)
        assertFalse(parsed.observed == "DISPOSABLE_DEVICE_VALIDATION")
    }

    @Test
    fun `unsigned matching purpose remains ineligible without a certificate or trust`() {
        val report = inspect(
            signing = unsignedSigning(),
            identity = matchingIdentity().copy(
                buildPurposeObserved = "DISPOSABLE_DEVICE_VALIDATION",
                buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_OBSERVED,
            ),
        )
        assertEquals("INELIGIBLE", report.candidateStatus)
        assertEquals(DestructiveValidationCandidateEvidence.Signing.UNSIGNED, report.signing)
        assertEquals("DISPOSABLE_DEVICE_VALIDATION", report.buildPurposeObserved)
        assertTrue(report.render().contains("build_purpose_matches=true"))
        assertFalse(report.expectedCertificateConfigured)
        assertFalse(report.trustedExpectationMinted)
        assertFalse(report.runtimeAuthorization)
        assertTrue(report.ineligibilityReasons.contains("expected_certificate_unconfigured"))
        assertTrue(report.ineligibilityReasons.contains("signing=UNSIGNED"))
        DestructiveValidationCandidateEvidence.assertDisposableValidationUnsignedIneligibleProof(report)
    }

    @Test
    fun `findUnsignedDisposableValidationApk accepts only the dedicated unsigned apk`() {
        val root = Files.createTempDirectory("candidate-disposable").toFile()
        try {
            writeZipApk(File(root, "app-release-unsigned.apk"))
            assertFailsWith<IllegalStateException> {
                DestructiveValidationCandidateEvidence.findUnsignedDisposableValidationApk(root)
            }
            val dedicated = writeZipApk(File(root, "app-disposableValidation-unsigned.apk"))
            assertEquals(
                dedicated.name,
                DestructiveValidationCandidateEvidence.findUnsignedDisposableValidationApk(root).name,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `observed identity is never copied into the expected fields`() {
        val observed = matchingIdentity().copy(packageName = "com.observed.only")
        val report = inspect(signing = unsignedSigning(), identity = observed)
        assertEquals("com.example.devicemanagement", report.packageExpected)
        assertEquals("com.observed.only", report.packageObserved)
        assertFalse(report.packageObserved == report.packageExpected)
        assertEquals("INELIGIBLE", report.candidateStatus)
    }

    @Test
    fun `status lines omit the apk digest`() {
        val report = inspect(signing = unsignedSigning(), identity = matchingIdentity())
        val status = report.statusLinesWithoutDigest()
        assertTrue(status.contains("candidate_status=INELIGIBLE"))
        assertTrue(status.contains("signing=UNSIGNED"))
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(status))
        assertFalse(status.contains("apk_sha256="))
    }

    @Test
    fun `unsigned proof rejects a non-unsigned classification`() {
        val report = inspect(signing = unknownSigned(), identity = matchingIdentity())
        assertFailsWith<IllegalStateException> {
            DestructiveValidationCandidateEvidence.assertUnsignedIneligibleProof(report)
        }
    }

    @Test
    fun `findUnsignedReleaseApk rejects a directory that only has a signed apk`() {
        val root = Files.createTempDirectory("candidate-signed-only").toFile()
        try {
            writeZipApk(File(root, "app-release.apk"))
            assertFailsWith<IllegalStateException> {
                DestructiveValidationCandidateEvidence.findUnsignedReleaseApk(root)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `successful inspection still fails when cleanup leaves a snapshot`() {
        val root = Files.createTempDirectory("candidate-cleanup-left").toFile()
        val snapshotDir = File(root, "explicit-snapshot")
        try {
            val apk = writeZipApk(File(root, "supplied-candidate.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.SnapshotCleanupException> {
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    signingInspector = { unsignedSigning() },
                    identityInspector = { matchingIdentity() },
                    gitProvenance = unavailableGit(),
                    cleanup = { },
                )
            }
            assertTrue(thrown.message.orEmpty().contains("task-private snapshot remained"))
            assertTrue(apk.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `successful explicit-candidate cleanup deletes only the owned snapshot`() {
        val root = Files.createTempDirectory("candidate-cleanup-success").toFile()
        val snapshotDir = File(root, "explicit-snapshot")
        val otherSnapshotDir = File(root, "other-snapshot")
        val otherReport = File(root, "other-report.txt")
        try {
            val apk = writeZipApk(File(root, "supplied-candidate.apk"))
            otherSnapshotDir.mkdirs()
            val otherSnapshot = DestructiveValidationCandidateEvidence.ownedSnapshotFile(otherSnapshotDir)
            otherSnapshot.writeBytes(byteArrayOf(0x01))
            otherReport.writeText("keep")
            val report = DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                apk = apk,
                snapshotDirectory = snapshotDir,
                signingInspector = { unsignedSigning() },
                identityInspector = { matchingIdentity() },
                gitProvenance = unavailableGit(),
            )
            assertEquals("INELIGIBLE", report.candidateStatus)
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
            assertTrue(DestructiveValidationCandidateEvidence.snapshotStillPresent(otherSnapshotDir))
            assertTrue(otherReport.isFile)
            assertTrue(apk.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `failed explicit-candidate cleanup fails the inspection and preserves the original error`() {
        val root = Files.createTempDirectory("candidate-cleanup-failed").toFile()
        val snapshotDir = File(root, "explicit-snapshot")
        try {
            val apk = writeZipApk(File(root, "supplied-candidate.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterInitialDigest = { apk.appendBytes(byteArrayOf(0x09)) },
                    signingInspector = { unsignedSigning() },
                    identityInspector = { matchingIdentity() },
                    gitProvenance = unavailableGit(),
                    cleanup = { },
                )
            }
            assertTrue(thrown.message.orEmpty().contains("changed during"))
            assertTrue(
                thrown.suppressed.any {
                    it is DestructiveValidationCandidateEvidence.SnapshotCleanupException
                },
            )
            assertTrue(apk.isFile)
        } finally {
            snapshotDir.setWritable(true)
            root.deleteRecursively()
        }
    }

    @Test
    fun `successful 19F proof cleanup deletes the unsigned-release snapshot`() {
        val root = Files.createTempDirectory("unsigned-cleanup-success").toFile()
        val snapshotDir = File(root, "unsigned-release-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-release-unsigned.apk"))
            val report = DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                apk = apk,
                snapshotDirectory = snapshotDir,
                signingInspector = { unsignedSigning() },
                identityInspector = { matchingIdentity() },
                gitProvenance = unavailableGit(),
            )
            DestructiveValidationCandidateEvidence.assertUnsignedIneligibleProof(report)
            DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
                snapshotDirectory = snapshotDir,
                inspect = { report },
                write = { },
                assertProof = DestructiveValidationCandidateEvidence::assertUnsignedIneligibleProof,
            )
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `failed 19F proof cleanup fails the task envelope`() {
        val root = Files.createTempDirectory("unsigned-cleanup-failed").toFile()
        val snapshotDir = File(root, "unsigned-release-snapshot")
        try {
            snapshotDir.mkdirs()
            val leftover = DestructiveValidationCandidateEvidence.ownedSnapshotFile(snapshotDir)
            leftover.writeBytes(byteArrayOf(0x03))
            assertTrue(leftover.isFile)
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.SnapshotCleanupException> {
                DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
                    snapshotDirectory = snapshotDir,
                    inspect = { "ok" },
                    write = { },
                )
            }
            assertTrue(thrown.message.orEmpty().contains("task-private snapshot remained"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `successful 19G proof cleanup deletes the disposable-purpose snapshot`() {
        val root = Files.createTempDirectory("purpose-cleanup-success").toFile()
        val snapshotDir = File(root, "disposable-purpose-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-disposableValidation-unsigned.apk"))
            val report = DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                apk = apk,
                snapshotDirectory = snapshotDir,
                signingInspector = { unsignedSigning() },
                identityInspector = {
                    matchingIdentity().copy(
                        buildPurposeObserved = "DISPOSABLE_DEVICE_VALIDATION",
                        buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_OBSERVED,
                    )
                },
                gitProvenance = unavailableGit(),
            )
            DestructiveValidationCandidateEvidence.assertDisposableValidationUnsignedIneligibleProof(
                report,
            )
            DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
                snapshotDirectory = snapshotDir,
                inspect = { report },
                write = { },
                assertProof =
                    DestructiveValidationCandidateEvidence::assertDisposableValidationUnsignedIneligibleProof,
            )
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `failed 19G proof cleanup fails the task envelope`() {
        val root = Files.createTempDirectory("purpose-cleanup-failed").toFile()
        val snapshotDir = File(root, "disposable-purpose-snapshot")
        try {
            snapshotDir.mkdirs()
            val leftover = DestructiveValidationCandidateEvidence.ownedSnapshotFile(snapshotDir)
            leftover.writeBytes(byteArrayOf(0x04))
            assertTrue(leftover.isFile)
            assertFailsWith<DestructiveValidationCandidateEvidence.SnapshotCleanupException> {
                DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
                    snapshotDirectory = snapshotDir,
                    inspect = { "ok" },
                    write = { },
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `one task cleanup cannot remove another task snapshot or report`() {
        val root = Files.createTempDirectory("isolated-cleanup").toFile()
        val first = File(root, "explicit-candidate-snapshot")
        val second = File(root, "unsigned-release-snapshot")
        val firstReport = File(root, "explicit-candidate.txt")
        val secondReport = File(root, "unsigned-release.txt")
        try {
            first.mkdirs()
            second.mkdirs()
            DestructiveValidationCandidateEvidence.ownedSnapshotFile(first).writeBytes(byteArrayOf(0x11))
            DestructiveValidationCandidateEvidence.ownedSnapshotFile(second).writeBytes(byteArrayOf(0x22))
            firstReport.writeText("first")
            secondReport.writeText("second")
            DestructiveValidationCandidateEvidence.deleteOwnedSnapshot(first)
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(first))
            assertTrue(DestructiveValidationCandidateEvidence.snapshotStillPresent(second))
            assertTrue(firstReport.isFile)
            assertTrue(secondReport.isFile)
            assertEquals("second", secondReport.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `owned snapshot cleanup refuses a path outside the task-private directory`() {
        val root = Files.createTempDirectory("cleanup-outside").toFile()
        try {
            val userApk = writeZipApk(File(root, "user-supplied.apk"))
            val snapshotDir = File(root, "snapshot")
            snapshotDir.mkdirs()
            DestructiveValidationCandidateEvidence.deleteOwnedSnapshot(snapshotDir)
            assertTrue(userApk.isFile)
            assertFalse(snapshotDir.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun inspect(
        signing: DestructiveValidationCandidateEvidence.CandidateSigningInspection,
        identity: DestructiveValidationCandidateEvidence.CandidateApkIdentity,
    ): DestructiveValidationCandidateEvidence.CandidateEvidenceReport {
        val root = Files.createTempDirectory("candidate-inspect").toFile()
        return try {
            val apk = writeZipApk(File(root, "app-release-unsigned.apk"))
            DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                apk = apk,
                signingInspector = { signing },
                identityInspector = { identity },
                gitProvenance = unavailableGit(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun evaluateFullyMatchingFixture(
        identity: DestructiveValidationCandidateEvidence.CandidateApkIdentity =
            matchingIdentity().copy(
                buildPurposeObserved = "DISPOSABLE_DEVICE_VALIDATION",
                buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_OBSERVED,
            ),
        git: DestructiveValidationCandidateEvidence.GitProvenance =
            DestructiveValidationCandidateEvidence.GitProvenance(
                revision = "a".repeat(40),
                worktree = "CLEAN",
            ),
    ): DestructiveValidationCandidateEvidence.CandidateEvidenceReport {
        val independentlySuppliedExpected =
            DestructiveValidationExpectedIdentity.repositoryContract().copy(
                expectedCertificateSha256 = TEST_ONLY_CERT,
            )
        return DestructiveValidationCandidateEvidence.evaluate(
            apkSha256 = "cd".repeat(32),
            signing = signedUnclassified().copy(lineagePresent = true),
            identity = identity,
            git = git,
            expected = independentlySuppliedExpected,
        )
    }

    private fun unsignedSigning(): DestructiveValidationCandidateEvidence.CandidateSigningInspection {
        return DestructiveValidationCandidateEvidence.CandidateSigningInspection(
            classification = DestructiveValidationCandidateEvidence.Signing.UNSIGNED,
            certificateSha256 = null,
            signerCount = 0,
            apksignerAvailable = true,
            apksignerExecuted = true,
            detail = "fixture",
        )
    }

    private fun unknownSigned(): DestructiveValidationCandidateEvidence.CandidateSigningInspection {
        return DestructiveValidationCandidateEvidence.CandidateSigningInspection(
            classification = DestructiveValidationCandidateEvidence.Signing.UNKNOWN,
            certificateSha256 = "ab".repeat(32),
            signerCount = 1,
            apksignerAvailable = true,
            apksignerExecuted = true,
            detail = "fixture",
        )
    }

    private fun signedUnclassified(): DestructiveValidationCandidateEvidence.CandidateSigningInspection {
        return DestructiveValidationCandidateEvidence.CandidateSigningInspection(
            classification = DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED,
            certificateSha256 = TEST_ONLY_CERT,
            signerCount = 1,
            signerCountReliable = true,
            lineagePresent = false,
            apksignerAvailable = true,
            apksignerExecuted = true,
            detail = "fixture",
        )
    }

    private fun matchingIdentity(): DestructiveValidationCandidateEvidence.CandidateApkIdentity {
        val expected = DestructiveValidationExpectedIdentity.repositoryContract()
        return DestructiveValidationCandidateEvidence.CandidateApkIdentity(
            packageName = expected.packageName,
            adminComponent = expected.adminComponent,
            policies = expected.policies,
            versionCode = "1",
            versionName = "1.0",
            minSdk = expected.minSdk.toString(),
            targetSdk = expected.targetSdk.toString(),
            buildPurposeObserved = null,
            aapt2Available = true,
            detail = "fixture",
        )
    }

    private fun unavailableGit(): DestructiveValidationCandidateEvidence.GitProvenance {
        return DestructiveValidationCandidateEvidence.GitProvenance(
            revision = "UNAVAILABLE",
            worktree = "UNAVAILABLE",
        )
    }

    private fun writeZipApk(
        file: File,
        extraEntry: Pair<String, String>? = null,
    ): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write("Manifest-Version: 1.0\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("not-a-real-manifest".toByteArray())
            zip.closeEntry()
            if (extraEntry != null) {
                zip.putNextEntry(ZipEntry(extraEntry.first))
                zip.write(extraEntry.second.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private companion object {
        const val TEST_ONLY_CERT =
            "abababababababababababababababababababababababababababababababab"
    }
}
