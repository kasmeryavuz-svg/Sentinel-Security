import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationOnlySignedCandidateEvidenceTest {
    @Test
    fun `stable immutable snapshot is inspected by every checker then deleted`() {
        val root = Files.createTempDirectory("19r-stable-snapshot").toFile()
        val snapshotDir = File(root, "signed-disposable-validation-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-disposableValidation.apk"))
            val originalDigest = DestructiveValidationCandidateEvidence.sha256OfExactBytes(apk)
            var snapshotDuringInspect: File? = null
            var signingPath: String? = null
            var identityPath: String? = null
            var schemePath: String? = null
            val result = inspectAccepted(
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
                    assertFalse(snapshot.absolutePath == apk.absolutePath)
                },
                signingInspector = { file ->
                    signingPath = file.absolutePath
                    acceptedSigning()
                },
                identityInspector = { file ->
                    identityPath = file.absolutePath
                    acceptedIdentity()
                },
                schemeInspector = { file ->
                    schemePath = file.absolutePath
                    acceptedSchemes()
                },
            )
            assertEquals(
                ValidationOnlySigningGate.SignedCandidateDecision.ACCEPT_UNTRUSTED_CANDIDATE,
                result.decision,
            )
            assertTrue(result.sameSnapshotForAllInspectors)
            assertEquals(snapshotDuringInspect!!.absolutePath, result.signingSnapshotPath)
            assertEquals(result.signingSnapshotPath, signingPath)
            assertEquals(result.identitySnapshotPath, identityPath)
            assertEquals(result.schemeSnapshotPath, schemePath)
            assertEquals(signingPath, identityPath)
            assertEquals(identityPath, schemePath)
            assertTrue(
                result.signingSnapshotPath.endsWith(
                    DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME,
                ),
            )
            assertFalse(result.signingSnapshotPath == apk.absolutePath)
            assertEquals(
                originalDigest,
                DestructiveValidationCandidateEvidence.sha256OfExactBytes(apk),
            )
            assertTrue(apk.isFile)
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `persistent source mutation during inspection is rejected and snapshot is deleted`() {
        val root = Files.createTempDirectory("19r-source-mutated").toFile()
        val snapshotDir = File(root, "signed-disposable-validation-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-disposableValidation.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                inspectAccepted(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterInitialDigest = { apk.appendBytes(byteArrayOf(0x01)) },
                )
            }
            assertTrue(thrown.message.orEmpty().contains("changed during"))
            assertTrue(apk.isFile)
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `swap-and-restore of the source during inspection is rejected`() {
        val root = Files.createTempDirectory("19r-source-swap").toFile()
        val snapshotDir = File(root, "signed-disposable-validation-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-disposableValidation.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                inspectAccepted(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterInitialDigest = {
                        val bytes = apk.readBytes()
                        check(apk.delete())
                        apk.writeBytes(bytes)
                        apk.setLastModified(apk.lastModified() + 5_000L)
                    },
                )
            }
            assertTrue(thrown.message.orEmpty().contains("changed during"))
            assertTrue(apk.isFile)
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `source replacement during inspection is rejected`() {
        val root = Files.createTempDirectory("19r-source-replaced").toFile()
        val snapshotDir = File(root, "signed-disposable-validation-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-disposableValidation.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                inspectAccepted(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterInitialDigest = {
                        apk.delete()
                        writeZipApk(apk, extraEntry = "replaced" to "different-bytes")
                    },
                )
            }
            assertTrue(thrown.message.orEmpty().contains("changed during"))
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `source symlink is rejected`() {
        val root = Files.createTempDirectory("19r-source-link").toFile()
        try {
            val real = writeZipApk(File(root, "app-disposableValidation.apk"))
            val link = File(root, "linked-disposableValidation.apk")
            Files.createSymbolicLink(link.toPath(), real.toPath())
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                inspectAccepted(apk = link, snapshotDirectory = File(root, "snapshot"))
            }
            assertTrue(thrown.message.orEmpty().contains("symlink"))
            assertTrue(real.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `parent-directory symlink is rejected`() {
        val root = Files.createTempDirectory("19r-parent-link").toFile()
        try {
            val realDir = File(root, "real").apply { mkdirs() }
            writeZipApk(File(realDir, "app-disposableValidation.apk"))
            val linkDir = File(root, "link")
            Files.createSymbolicLink(linkDir.toPath(), realDir.toPath())
            val viaLink = File(linkDir, "app-disposableValidation.apk")
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                inspectAccepted(apk = viaLink, snapshotDirectory = File(root, "snapshot"))
            }
            assertTrue(thrown.message.orEmpty().contains("symbolic link"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `snapshot cleanup after success deletes only the owned snapshot`() {
        val root = Files.createTempDirectory("19r-cleanup-success").toFile()
        val snapshotDir = File(root, "signed-disposable-validation-snapshot")
        val otherSnapshotDir = File(root, "other-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-disposableValidation.apk"))
            val dummyKeystore = File(root, "validation-placeholder.store").apply {
                writeText("not-a-keystore")
            }
            otherSnapshotDir.mkdirs()
            val otherSnapshot =
                DestructiveValidationCandidateEvidence.ownedSnapshotFile(otherSnapshotDir)
            otherSnapshot.writeBytes(byteArrayOf(0x01))
            val result = inspectAccepted(apk = apk, snapshotDirectory = snapshotDir)
            assertEquals(
                ValidationOnlySigningGate.SignedCandidateDecision.ACCEPT_UNTRUSTED_CANDIDATE,
                result.decision,
            )
            DestructiveValidationCandidateEvidenceTaskSupport.inspectWriteAndAssertCleanup(
                snapshotDirectory = snapshotDir,
                inspect = { result },
                write = { },
            )
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
            assertTrue(DestructiveValidationCandidateEvidence.snapshotStillPresent(otherSnapshotDir))
            assertTrue(apk.isFile)
            assertTrue(dummyKeystore.isFile)
            assertEquals("not-a-keystore", dummyKeystore.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `snapshot cleanup after inspection failure still removes the owned snapshot`() {
        val root = Files.createTempDirectory("19r-cleanup-after-failure").toFile()
        val snapshotDir = File(root, "signed-disposable-validation-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-disposableValidation.apk"))
            val dummyKeystore = File(root, "validation-placeholder.store").apply {
                writeText("keep-keystore-path")
            }
            assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                inspectAccepted(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterInitialDigest = { apk.appendBytes(byteArrayOf(0x02)) },
                )
            }
            assertFalse(DestructiveValidationCandidateEvidence.snapshotStillPresent(snapshotDir))
            assertTrue(apk.isFile)
            assertTrue(dummyKeystore.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `cleanup failure after inspection failure is suppressed on the primary exception`() {
        val root = Files.createTempDirectory("19r-cleanup-suppressed").toFile()
        val snapshotDir = File(root, "signed-disposable-validation-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-disposableValidation.apk"))
            val thrown = assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                inspectAccepted(
                    apk = apk,
                    snapshotDirectory = snapshotDir,
                    afterInitialDigest = { apk.appendBytes(byteArrayOf(0x03)) },
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
    fun `successful inspection still fails when cleanup leaves a snapshot`() {
        val root = Files.createTempDirectory("19r-cleanup-left").toFile()
        val snapshotDir = File(root, "signed-disposable-validation-snapshot")
        try {
            val apk = writeZipApk(File(root, "app-disposableValidation.apk"))
            val thrown =
                assertFailsWith<DestructiveValidationCandidateEvidence.SnapshotCleanupException> {
                    inspectAccepted(
                        apk = apk,
                        snapshotDirectory = snapshotDir,
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
    fun `namespace separation is not cryptographic key-separation verification`() {
        assertTrue(ValidationOnlySigningGate.validationInputNamespaceSeparateFromProduction())
        assertFalse(ValidationOnlySigningGate.validationKeySeparationVerified())
        assertFalse(ValidationOnlySigningGate.mayAttachToProductionRelease())
        val gate = File("src/main/kotlin/ValidationOnlySigningGate.kt").readText()
        assertTrue(gate.contains("fun validationInputNamespaceSeparateFromProduction()"))
        assertTrue(gate.contains("fun validationKeySeparationVerified(): Boolean = false"))
        assertFalse(gate.contains("fun validationKeySeparateFromProduction()"))
        DestructiveValidationCandidateEvidence.assertCandidateEvidenceTasksIsolated()
        assertEquals(3, DestructiveValidationCandidateEvidence.isolatedCandidateEvidenceTasks.size)
        assertFalse(
            DestructiveValidationCandidateEvidence.isolatedCandidateEvidenceTasks.any {
                it.snapshotRelativePath ==
                    ValidationOnlySignedCandidateEvidence.SNAPSHOT_RELATIVE_PATH
            },
        )
    }

    @Test
    fun `ordinary release and ordinary disposableValidation remain unsigned`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        assertTrue(
            appGradle.contains(
                "ordinary assembleRelease/bundleRelease must remain unsigned",
            ),
        )
        assertTrue(
            appGradle.contains(
                "ordinary assembleDisposableValidation must remain unsigned",
            ),
        )
        assertTrue(
            appGradle.contains(
                "disposableValidation must remain unsigned even if production-signing",
            ),
        )
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        assertTrue(workflow.contains("signing=UNSIGNED"))
        assertTrue(workflow.contains("signed-disposable-validation-snapshot"))
        assertFalse(workflow.contains("assembleSignedDisposableValidation"))
        assertFalse(workflow.contains("checkSignedDisposableValidation"))
    }

    private fun inspectAccepted(
        apk: File,
        snapshotDirectory: File,
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
    ): ValidationOnlySignedCandidateEvidence.Result {
        return ValidationOnlySignedCandidateEvidence.inspect(
            apk = apk,
            snapshotDirectory = snapshotDirectory,
            expectedCertificateSha256 = TEST_ONLY_CERT,
            afterInitialDigest = afterInitialDigest,
            afterSnapshotCreated = afterSnapshotCreated,
            signingInspector = signingInspector ?: { acceptedSigning() },
            identityInspector = identityInspector ?: { acceptedIdentity() },
            schemeInspector = schemeInspector ?: { acceptedSchemes() },
            cleanup = cleanup,
        )
    }

    private fun acceptedSigning():
        DestructiveValidationCandidateEvidence.CandidateSigningInspection {
        return DestructiveValidationCandidateEvidence.CandidateSigningInspection(
            classification = DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED,
            certificateSha256 = TEST_ONLY_CERT,
            signerCount = 1,
            signerCountReliable = true,
            apksignerAvailable = true,
            apksignerExecuted = true,
            detail = "fixture",
        )
    }

    private fun acceptedIdentity():
        DestructiveValidationCandidateEvidence.CandidateApkIdentity {
        val expected = DestructiveValidationExpectedIdentity.repositoryContract()
        return DestructiveValidationCandidateEvidence.CandidateApkIdentity(
            packageName = expected.packageName,
            adminComponent = expected.adminComponent,
            policies = expected.policies,
            versionCode = "1",
            versionName = "1.0",
            minSdk = expected.minSdk.toString(),
            targetSdk = expected.targetSdk.toString(),
            buildPurposeObserved =
                DestructiveValidationExpectedIdentity.BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION,
            buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_OBSERVED,
            aapt2Available = true,
            detail = "fixture",
        )
    }

    private fun acceptedSchemes(): ValidationOnlySigningGate.ObservedSignatureSchemes {
        return ValidationOnlySigningGate.ObservedSignatureSchemes(
            v2Present = true,
            v3Present = true,
            reliable = true,
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
