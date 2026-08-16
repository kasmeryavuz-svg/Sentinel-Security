import java.io.File
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndependentWitnessVerificationTest {
    @Test
    fun `valid signature is not independent witness approval`() {
        val evaluation = IndependentWitnessVerification.evaluateMechanics(
            candidateReinspectionPassed = true,
            receiptMatchesCandidate = true,
            validationCertificateMatches = true,
            witnessStatementPresent = true,
            witnessSignatureVerified = true,
            witnessEvidenceMatchesCandidate = true,
            witnessIdentifier = "local-operator",
        )
        assertTrue(evaluation.witnessStatementPresent)
        assertTrue(evaluation.witnessSignatureVerified)
        assertTrue(evaluation.witnessEvidenceMatchesCandidate)
        assertFalse(evaluation.witnessIndependenceEstablished)
        assertFalse(evaluation.independentWitnessApproval)
        assertFalse(
            IndependentWitnessAuthorityContract.independenceEstablished("local-operator"),
        )
        assertTrue(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty())
        assertFalse(IndependentWitnessAuthorityContract.ciIsIndependentWitness())
        assertFalse(IndependentWitnessAuthorityContract.localOperatorIsIndependentWitness())
        assertFalse(IndependentWitnessAuthorityContract.localReceiptIsIndependentWitness())
        val rendered = evaluation.render()
        assertTrue(rendered.contains("independent_witness_approval=false"))
        assertTrue(rendered.contains("authority=UNTRUSTED_CANDIDATE_ONLY"))
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(rendered))
    }

    @Test
    fun `synthetic signed statement verifies against immutable candidate evidence`() {
        val root = Files.createTempDirectory("19t-positive-verify").toFile()
        try {
            val fixture = acceptedFixture(root)
            val statement = matchingStatement(fixture)
            val signature = sign(statement, rsa.private)
            writeStatement(root, statement, signature)
            Files.write(File(root, "witness.pub").toPath(), rsa.public.encoded)
            val evaluation = verifyFixture(
                fixture = fixture,
                statementFile = File(root, "statement.txt"),
                keyFile = File(root, "witness.pub"),
            )
            assertTrue(evaluation.candidateReinspectionPassed)
            assertTrue(evaluation.receiptMatchesCandidate)
            assertTrue(evaluation.validationCertificateMatches)
            assertTrue(evaluation.witnessStatementPresent)
            assertTrue(evaluation.witnessSignatureVerified)
            assertTrue(evaluation.witnessEvidenceMatchesCandidate)
            assertFalse(evaluation.witnessIndependenceEstablished)
            assertFalse(evaluation.independentWitnessApproval)
            val receiptBefore = fixture.receiptFile.readText()
            IndependentWitnessVerification.writeReport(
                evaluation,
                File(root, "local/independent-witness-verification.txt"),
            )
            assertEquals(receiptBefore, fixture.receiptFile.readText())
            assertFalse(
                DestructiveValidationCandidateEvidence.snapshotStillPresent(fixture.snapshotDir),
            )
            assertTrue(fixture.apk.isFile)
            assertTrue(fixture.certificate.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `negative evidence and signature cases fail closed`() {
        fun refuse(mutator: (AcceptedFixture) -> Unit) {
            val root = Files.createTempDirectory("19t-negative").toFile()
            try {
                val fixture = acceptedFixture(root)
                mutator(fixture)
                assertFailsWith<IllegalStateException> {
                    verifyFixture(fixture)
                }
                assertTrue(fixture.apk.isFile)
                assertTrue(fixture.receiptFile.isFile)
                assertTrue(fixture.certificate.isFile)
                assertFalse(
                    DestructiveValidationCandidateEvidence.snapshotStillPresent(fixture.snapshotDir),
                )
            } finally {
                root.deleteRecursively()
            }
        }

        refuse { fixture ->
            val statement = matchingStatement(fixture).copy(candidateApkSha256 = "cc".repeat(32))
            writeSigned(fixture, statement)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture).copy(
                validationCertificateSha256 = "cc".repeat(32),
            )
            writeSigned(fixture, statement)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture).copy(sourceHeadClaimed = "34".repeat(20))
            writeSigned(fixture, statement)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture).copy(packageName = "com.example.other")
            writeSigned(fixture, statement)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture).copy(adminComponent = "other/Admin")
            writeSigned(fixture, statement)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture).copy(policies = listOf("wipe-data"))
            writeSigned(fixture, statement)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture).copy(minSdk = 24)
            writeSigned(fixture, statement)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture).copy(targetSdk = 33)
            writeSigned(fixture, statement)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture).copy(buildPurpose = "OTHER")
            writeSigned(fixture, statement)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture)
            writeSigned(fixture, statement, keyPair = rsa)
            Files.write(File(fixture.root, "witness.pub").toPath(), otherRsa.public.encoded)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture)
            val signature = sign(statement, rsa.private).replace('A', 'B')
            writeStatement(fixture.root, statement, signature)
            Files.write(File(fixture.root, "witness.pub").toPath(), rsa.public.encoded)
        }
        refuse { fixture ->
            val statement = matchingStatement(fixture)
            val signature = sign(statement, rsa.private)
            val rendered = IndependentWitnessStatement.render(statement, signature)
                .replace("package_name=${identity.packageName}", "package_name=com.example.other")
            File(fixture.root, "statement.txt").writeText(rendered)
            Files.write(File(fixture.root, "witness.pub").toPath(), rsa.public.encoded)
        }
    }

    @Test
    fun `signer scheme receipt disagreement and source mutation fail closed`() {
        val root = Files.createTempDirectory("19t-reinspection-refusal").toFile()
        try {
            val multipleSigners = acceptedFixture(File(root, "multi"))
            assertFailsWith<IllegalStateException> {
                verifyFixture(
                    multipleSigners,
                    signingInspector = {
                        acceptedSigning(multipleSigners.certificateSha256).copy(
                            signerCount = 2,
                            signerCountReliable = true,
                        )
                    },
                )
            }
            val missingV2 = acceptedFixture(File(root, "v2"))
            assertFailsWith<IllegalStateException> {
                verifyFixture(
                    missingV2,
                    schemeInspector = {
                        ValidationOnlySigningGate.ObservedSignatureSchemes(
                            v2Present = false,
                            v3Present = true,
                            reliable = true,
                        )
                    },
                )
            }
            val missingV3 = acceptedFixture(File(root, "v3"))
            assertFailsWith<IllegalStateException> {
                verifyFixture(
                    missingV3,
                    schemeInspector = {
                        ValidationOnlySigningGate.ObservedSignatureSchemes(
                            v2Present = true,
                            v3Present = false,
                            reliable = true,
                        )
                    },
                )
            }
            val disagree = acceptedFixture(File(root, "disagree"))
            val tamperedReceipt = disagree.receiptFile.readText().replace(
                disagree.apkSha256,
                "cc".repeat(32),
            )
            disagree.receiptFile.writeText(tamperedReceipt)
            assertFailsWith<IllegalStateException> {
                verifyFixture(disagree)
            }
            val mutated = acceptedFixture(File(root, "mutated"))
            assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                verifyFixture(
                    mutated,
                    afterInitialDigest = { mutated.apk.appendBytes(byteArrayOf(0x01)) },
                )
            }
            assertFalse(
                DestructiveValidationCandidateEvidence.snapshotStillPresent(mutated.snapshotDir),
            )
            assertTrue(mutated.apk.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `symlink inputs are rejected and source files survive`() {
        val root = Files.createTempDirectory("19t-symlink").toFile()
        try {
            val fixture = acceptedFixture(root)
            val linkedReceipt = File(root, "linked-receipt.txt")
            Files.createSymbolicLink(linkedReceipt.toPath(), fixture.receiptFile.toPath())
            assertFailsWith<IllegalStateException> {
                IndependentWitnessVerification.verify(
                    apk = fixture.apk,
                    receiptFile = linkedReceipt,
                    publicCertificate = fixture.certificate,
                    snapshotDirectory = File(root, "snap-receipt"),
                    signingInspector = { acceptedSigning(fixture.certificateSha256) },
                    identityInspector = { acceptedIdentity() },
                    schemeInspector = { acceptedSchemes() },
                )
            }
            val realApk = fixture.apk
            val linkedApk = File(root, "linked-disposableValidation.apk")
            Files.createSymbolicLink(linkedApk.toPath(), realApk.toPath())
            assertFailsWith<DestructiveValidationCandidateEvidence.RejectedException> {
                IndependentWitnessVerification.verify(
                    apk = linkedApk,
                    receiptFile = fixture.receiptFile,
                    publicCertificate = fixture.certificate,
                    snapshotDirectory = File(root, "snap-apk"),
                    signingInspector = { acceptedSigning(fixture.certificateSha256) },
                    identityInspector = { acceptedIdentity() },
                    schemeInspector = { acceptedSchemes() },
                )
            }
            assertTrue(realApk.isFile)
            assertTrue(fixture.receiptFile.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `cleanup after success and after verification failure leaves sources`() {
        val root = Files.createTempDirectory("19t-cleanup").toFile()
        try {
            val success = acceptedFixture(File(root, "success"))
            val other = File(root, "other-snapshot").apply { mkdirs() }
            val otherSnapshot = DestructiveValidationCandidateEvidence.ownedSnapshotFile(other)
            otherSnapshot.writeBytes(byteArrayOf(0x01))
            verifyFixture(success)
            assertFalse(
                DestructiveValidationCandidateEvidence.snapshotStillPresent(success.snapshotDir),
            )
            assertTrue(DestructiveValidationCandidateEvidence.snapshotStillPresent(other))
            assertTrue(success.apk.isFile)
            assertTrue(success.certificate.isFile)
            assertTrue(success.receiptFile.isFile)

            val failure = acceptedFixture(File(root, "failure"))
            assertFailsWith<IllegalStateException> {
                verifyFixture(
                    failure,
                    signingInspector = {
                        acceptedSigning(failure.certificateSha256).copy(signerCount = 2)
                    },
                )
            }
            assertFalse(
                DestructiveValidationCandidateEvidence.snapshotStillPresent(failure.snapshotDir),
            )
            assertTrue(failure.apk.isFile)
            assertTrue(failure.receiptFile.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `successful inspection still fails when cleanup leaves a snapshot`() {
        val root = Files.createTempDirectory("19t-leftover").toFile()
        try {
            val fixture = acceptedFixture(root)
            assertFailsWith<DestructiveValidationCandidateEvidence.SnapshotCleanupException> {
                verifyFixture(fixture, cleanup = { })
            }
            assertTrue(fixture.apk.isFile)
            assertTrue(fixture.receiptFile.isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `wrong supplied source head is rejected`() {
        val root = Files.createTempDirectory("19t-wrong-head").toFile()
        try {
            val fixture = acceptedFixture(root)
            assertFailsWith<IllegalStateException> {
                IndependentWitnessVerification.verify(
                    apk = fixture.apk,
                    receiptFile = fixture.receiptFile,
                    publicCertificate = fixture.certificate,
                    snapshotDirectory = fixture.snapshotDir,
                    sourceHeadClaimed = "34".repeat(20),
                    signingInspector = { acceptedSigning(fixture.certificateSha256) },
                    identityInspector = { acceptedIdentity() },
                    schemeInspector = { acceptedSchemes() },
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `contract task sources stay local fail-closed and absent from runtime`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        val verifyTask = File(
            "src/main/kotlin/VerifyIndependentWitnessStatementTask.kt",
        ).readText()
        val contractTask = File(
            "src/main/kotlin/CheckIndependentWitnessVerificationContractTask.kt",
        ).readText()
        val ignore = File("../.gitignore").readText()
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        val docs = File("../docs/WIPE_19T_INDEPENDENT_WITNESS_VERIFICATION.md").readText()
        val template = File("../docs/templates/INDEPENDENT_WITNESS_STATEMENT.template.txt")
            .readText()
        assertTrue(appGradle.contains("checkIndependentWitnessVerificationContract"))
        assertTrue(appGradle.contains("verifyIndependentWitnessStatement"))
        assertTrue(appGradle.contains("IndependentWitnessVerification.RECEIPT_PROPERTY"))
        assertTrue(appGradle.contains("IndependentWitnessStatement.STATEMENT_PROPERTY"))
        assertTrue(
            File("src/main/kotlin/IndependentWitnessVerification.kt").readText()
                .contains("sentinel.signedValidationReceipt"),
        )
        assertTrue(
            File("src/main/kotlin/IndependentWitnessStatement.kt").readText()
                .contains("sentinel.independentWitnessStatement"),
        )
        assertFalse(verifyTask.contains("SENTINEL_VALIDATION_STORE"))
        assertFalse(verifyTask.contains("STORE_PASSWORD"))
        assertFalse(verifyTask.contains("KEY_PASSWORD"))
        assertFalse(verifyTask.contains("KeyPairGenerator"))
        assertFalse(contractTask.contains("SENTINEL_VALIDATION_"))
        assertTrue(ignore.contains("local/independent-witness-verification.txt"))
        assertTrue(ignore.contains("local/signed-validation-candidate-receipt.txt"))
        assertTrue(workflow.contains(":app:checkIndependentWitnessVerificationContract"))
        assertFalse(workflow.contains("verifyIndependentWitnessStatement"))
        assertFalse(workflow.contains("upload-artifact"))
        assertFalse(File("../local/independent-witness-verification.txt").exists())
        assertFalse(File("../local/signed-validation-candidate-receipt.txt").exists())
        assertTrue(docs.contains("CHECKPOINT_19T_INDEPENDENT_WITNESS_VERIFICATION = YES"))
        assertTrue(docs.contains("19T_INDEPENDENT_WITNESS_APPROVAL = false"))
        assertTrue(docs.contains("valid_signature != independent_witness_approval"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertTrue(template.contains("signature_algorithm=SHA256withRSA"))
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(template))
        assertFalse(Regex("\\b[0-9a-fA-F]{64}\\b").containsMatchIn(docs))
        DestructiveValidationCandidateEvidence.assertCandidateEvidenceTasksIsolated()
        assertEquals(3, DestructiveValidationCandidateEvidence.isolatedCandidateEvidenceTasks.size)
        assertFalse(
            DestructiveValidationCandidateEvidence.isolatedCandidateEvidenceTasks.any {
                it.snapshotRelativePath ==
                    IndependentWitnessVerification.SNAPSHOT_RELATIVE_PATH
            },
        )
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
                    assertFalse(text.contains("IndependentWitnessVerification"), file.path)
                    assertFalse(text.contains("IndependentWitnessStatement"), file.path)
                    assertFalse(text.contains("verifyIndependentWitnessStatement"), file.path)
                }
        }
    }

    private fun verifyFixture(
        fixture: AcceptedFixture,
        statementFile: File? = File(fixture.root, "statement.txt").takeIf { it.isFile },
        keyFile: File? = File(fixture.root, "witness.pub").takeIf { it.isFile },
        afterInitialDigest: (() -> Unit)? = null,
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
    ): IndependentWitnessVerification.Evaluation {
        return IndependentWitnessVerification.verify(
            apk = fixture.apk,
            receiptFile = fixture.receiptFile,
            publicCertificate = fixture.certificate,
            snapshotDirectory = fixture.snapshotDir,
            sourceHeadClaimed = SOURCE_HEAD,
            witnessStatementFile = statementFile,
            witnessVerificationKeyFile = keyFile,
            afterInitialDigest = afterInitialDigest,
            signingInspector = signingInspector
                ?: { acceptedSigning(fixture.certificateSha256) },
            identityInspector = identityInspector ?: { acceptedIdentity() },
            schemeInspector = schemeInspector ?: { acceptedSchemes() },
            cleanup = cleanup,
        )
    }

    private fun writeSigned(
        fixture: AcceptedFixture,
        statement: IndependentWitnessStatement.Statement,
        keyPair: KeyPair = rsa,
    ) {
        writeStatement(fixture.root, statement, sign(statement, keyPair.private))
        Files.write(File(fixture.root, "witness.pub").toPath(), keyPair.public.encoded)
    }

    private fun writeStatement(
        root: File,
        statement: IndependentWitnessStatement.Statement,
        signature: String,
    ) {
        File(root, "statement.txt").writeText(
            IndependentWitnessStatement.render(statement, signature),
        )
    }

    private fun matchingStatement(
        fixture: AcceptedFixture,
    ): IndependentWitnessStatement.Statement {
        return IndependentWitnessStatement.Statement(
            candidateApkSha256 = fixture.apkSha256,
            validationCertificateSha256 = fixture.certificateSha256,
            sourceHeadClaimed = SOURCE_HEAD,
            packageName = identity.packageName,
            adminComponent = identity.adminComponent,
            policies = identity.policies,
            minSdk = identity.minSdk,
            targetSdk = identity.targetSdk,
            buildPurpose = identity.buildPurpose,
            witnessIdentifier = "external-witness",
            witnessTimestampUtc = "2026-08-16T03:04:05Z",
        )
    }

    private fun acceptedFixture(root: File): AcceptedFixture {
        root.mkdirs()
        val apk = writeZipApk(File(root, "app-disposableValidation.apk"))
        val certificate = File(root, "validation-public.cer").apply { writeText("abc") }
        val certificateSha256 =
            SignedValidationCandidateLocalReceipt.publicCertificateSha256(certificate)
        val apkSha256 = DestructiveValidationCandidateEvidence.sha256OfExactBytes(apk)
        val receipt = SignedValidationCandidateLocalReceipt.create(
            result = ValidationOnlySignedCandidateEvidence.Result(
                decision =
                    ValidationOnlySigningGate.SignedCandidateDecision.ACCEPT_UNTRUSTED_CANDIDATE,
                signingSnapshotPath = File(
                    root,
                    DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME,
                ).absolutePath,
                identitySnapshotPath = File(
                    root,
                    DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME,
                ).absolutePath,
                schemeSnapshotPath = File(
                    root,
                    DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME,
                ).absolutePath,
                buildPurposeObserved =
                    DestructiveValidationExpectedIdentity.BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION,
                buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_OBSERVED,
                aapt2Available = true,
                identityDetail = "fixture",
                packageMatches = true,
                adminMatches = true,
                policiesMatch = true,
                minSdkMatches = true,
                targetSdkMatches = true,
                apkSha256 = apkSha256,
                signingCertificateSha256 = certificateSha256,
                signerCount = 1,
                signerCountReliable = true,
                v2Present = true,
                v3Present = true,
                schemesReliable = true,
            ),
            sourceHeadClaimed = SOURCE_HEAD,
            independentlySuppliedPublicCertificateSha256 = certificateSha256,
        )
        val receiptFile = File(root, "signed-validation-candidate-receipt.txt")
        SignedValidationCandidateLocalReceipt.writeOnce(receipt, receiptFile)
        return AcceptedFixture(
            root = root,
            apk = apk,
            certificate = certificate,
            receiptFile = receiptFile,
            snapshotDir = File(root, "independent-witness-verification-snapshot"),
            apkSha256 = apkSha256,
            certificateSha256 = certificateSha256,
        )
    }

    private fun acceptedSigning(
        certificateSha256: String,
    ): DestructiveValidationCandidateEvidence.CandidateSigningInspection {
        return DestructiveValidationCandidateEvidence.CandidateSigningInspection(
            classification = DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED,
            certificateSha256 = certificateSha256,
            signerCount = 1,
            signerCountReliable = true,
            apksignerAvailable = true,
            apksignerExecuted = true,
            detail = "fixture",
        )
    }

    private fun acceptedIdentity():
        DestructiveValidationCandidateEvidence.CandidateApkIdentity {
        return DestructiveValidationCandidateEvidence.CandidateApkIdentity(
            packageName = identity.packageName,
            adminComponent = identity.adminComponent,
            policies = identity.policies,
            versionCode = "1",
            versionName = "1.0",
            minSdk = identity.minSdk.toString(),
            targetSdk = identity.targetSdk.toString(),
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

    private fun sign(
        statement: IndependentWitnessStatement.Statement,
        privateKey: java.security.PrivateKey,
    ): String {
        val verifier = Signature.getInstance(IndependentWitnessStatement.SIGNATURE_ALGORITHM)
        verifier.initSign(privateKey)
        verifier.update(IndependentWitnessStatement.canonicalBytes(statement))
        return Base64.getEncoder().encodeToString(verifier.sign())
    }

    private data class AcceptedFixture(
        val root: File,
        val apk: File,
        val certificate: File,
        val receiptFile: File,
        val snapshotDir: File,
        val apkSha256: String,
        val certificateSha256: String,
    )

    private companion object {
        val identity = DestructiveValidationExpectedIdentity.repositoryContract()
        val SOURCE_HEAD = "12".repeat(20)
        val rsa: KeyPair by lazy {
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        }
        val otherRsa: KeyPair by lazy {
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        }
    }
}
