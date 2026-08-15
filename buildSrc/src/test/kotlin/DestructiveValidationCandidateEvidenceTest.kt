import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
        val rendered = report.render()
        assertTrue(rendered.contains("authority=UNTRUSTED_CANDIDATE_ONLY"))
        assertTrue(rendered.contains("runtime_authorization=false"))
        assertTrue(rendered.contains("trusted_expectation_minted=false"))
        assertTrue(rendered.contains("production_signing_enabled=false"))
        assertTrue(rendered.contains("hardware_validation_approved=false"))
        assertTrue(rendered.contains("candidate_status=INELIGIBLE"))
        assertTrue(rendered.contains("signing=UNSIGNED"))
        assertTrue(rendered.contains("build_purpose=DISPOSABLE_DEVICE_VALIDATION"))
        DestructiveValidationCandidateEvidence.assertUnsignedIneligibleProof(report)
    }

    @Test
    fun `debug test unknown malformed and multi-signer artifacts stay ineligible`() {
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
    fun `production-looking signature without a configured expected certificate stays ineligible`() {
        val report = inspect(
            signing = DestructiveValidationCandidateEvidence.CandidateSigningInspection(
                classification = DestructiveValidationCandidateEvidence.Signing.PRODUCTION_SIGNED,
                certificateSha256 = "cd".repeat(32),
                signerCount = 1,
                apksignerAvailable = true,
                apksignerExecuted = true,
                detail = "fixture",
            ),
            identity = matchingIdentity(),
        )
        assertEquals("INELIGIBLE", report.candidateStatus)
        assertTrue(report.ineligibilityReasons.contains("expected_certificate_unconfigured"))
        assertFalse(report.trustedExpectationMinted)
        assertFalse(report.expectedCertificateConfigured)
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
    fun `bytes that change during inspection are rejected`() {
        val root = Files.createTempDirectory("candidate-changed").toFile()
        try {
            val apk = writeZipApk(File(root, "app-release-unsigned.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                DestructiveValidationCandidateEvidence.inspectExplicitCandidate(
                    apk = apk,
                    afterInitialDigest = { apk.appendBytes(byteArrayOf(0x01)) },
                    signingInspector = { unsignedSigning() },
                    identityInspector = { matchingIdentity() },
                    gitProvenance = DestructiveValidationCandidateEvidence.GitProvenance(
                        revision = "UNAVAILABLE",
                        worktree = "UNAVAILABLE",
                    ),
                )
            }
            assertTrue(thrown.message.orEmpty().contains("changed during inspection"))
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
                gitProvenance = DestructiveValidationCandidateEvidence.GitProvenance(
                    revision = "UNAVAILABLE",
                    worktree = "UNAVAILABLE",
                ),
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
                gitProvenance = DestructiveValidationCandidateEvidence.GitProvenance(
                    revision = "UNAVAILABLE",
                    worktree = "UNAVAILABLE",
                ),
            )
            assertEquals("INELIGIBLE", report.candidateStatus)
            assertTrue(
                report.signing == DestructiveValidationCandidateEvidence.Signing.UNSIGNED ||
                    report.signing == DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE,
            )
            assertFalse(report.trustedExpectationMinted)
            assertTrue(report.render().contains("authority=UNTRUSTED_CANDIDATE_ONLY"))
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
                gitProvenance = DestructiveValidationCandidateEvidence.GitProvenance(
                    revision = "UNAVAILABLE",
                    worktree = "UNAVAILABLE",
                ),
            )
        } finally {
            root.deleteRecursively()
        }
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
            aapt2Available = true,
            detail = "fixture",
        )
    }

    private fun writeZipApk(file: File): File {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write("Manifest-Version: 1.0\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write("not-a-real-manifest".toByteArray())
            zip.closeEntry()
        }
        return file
    }
}
