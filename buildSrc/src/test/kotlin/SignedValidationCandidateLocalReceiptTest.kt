import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedValidationCandidateLocalReceiptTest {
    @Test
    fun `accepted candidate becomes local-only non-authorizing receipt`() {
        val root = Files.createTempDirectory("19s-local-receipt").toFile()
        try {
            val receipt = SignedValidationCandidateLocalReceipt.create(
                result = acceptedResult(root),
                sourceHeadClaimed = "12".repeat(20),
                independentlySuppliedPublicCertificateSha256 = CERTIFICATE_SHA256,
            )
            val rendered = receipt.render()
            assertTrue(rendered.contains("checkpoint=19S"))
            assertTrue(rendered.contains("receipt_status=RECORDED_LOCAL_ONLY"))
            assertTrue(rendered.contains("apk_sha256=$APK_SHA256"))
            assertTrue(
                rendered.contains(
                    "validation_certificate_sha256=$CERTIFICATE_SHA256",
                ),
            )
            assertTrue(rendered.contains("validation_signing_performed_observed=true"))
            assertTrue(rendered.contains("signed_validation_candidate_accepted=true"))
            assertTrue(rendered.contains("source_head_proves_apk_origin=false"))
            assertTrue(rendered.contains("local_receipt_is_independent_witness=false"))
            assertTrue(rendered.contains("independent_witness_approval=false"))
            assertTrue(rendered.contains("runtime_authorization=false"))
            assertTrue(rendered.contains("trusted_expectation_minted=false"))
            assertTrue(rendered.contains("hardware_validation_approved=false"))
            assertTrue(rendered.contains("receipt_authorizes_wipe=false"))
            assertFalse(rendered.contains(root.absolutePath))
            assertFalse(rendered.contains("password", ignoreCase = true))
            assertFalse(rendered.contains("keystore", ignoreCase = true))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `certificate mismatch and invalid source head fail closed`() {
        val root = Files.createTempDirectory("19s-local-refusal").toFile()
        try {
            assertFailsWith<IllegalStateException> {
                SignedValidationCandidateLocalReceipt.create(
                    result = acceptedResult(root),
                    sourceHeadClaimed = "12".repeat(20),
                    independentlySuppliedPublicCertificateSha256 = "cc".repeat(32),
                )
            }
            assertFailsWith<IllegalStateException> {
                SignedValidationCandidateLocalReceipt.create(
                    result = acceptedResult(root),
                    sourceHeadClaimed = "not-a-commit",
                    independentlySuppliedPublicCertificateSha256 = CERTIFICATE_SHA256,
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `identity or signature regression fails closed`() {
        val root = Files.createTempDirectory("19s-identity-refusal").toFile()
        try {
            assertFailsWith<IllegalStateException> {
                SignedValidationCandidateLocalReceipt.create(
                    result = acceptedResult(root).copy(policiesMatch = false),
                    sourceHeadClaimed = "12".repeat(20),
                    independentlySuppliedPublicCertificateSha256 = CERTIFICATE_SHA256,
                )
            }
            assertFailsWith<IllegalStateException> {
                SignedValidationCandidateLocalReceipt.create(
                    result = acceptedResult(root).copy(v3Present = false),
                    sourceHeadClaimed = "12".repeat(20),
                    independentlySuppliedPublicCertificateSha256 = CERTIFICATE_SHA256,
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `public certificate is hashed independently and stably`() {
        val root = Files.createTempDirectory("19s-public-certificate").toFile()
        try {
            val certificate = File(root, "validation-public.cer").apply {
                writeText("abc")
            }
            assertEquals(
                "ba7816bf8f01cfea414140de5dae2223" +
                    "b00361a396177a9cb410ff61f20015ad",
                SignedValidationCandidateLocalReceipt.publicCertificateSha256(certificate),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `receipt write is one-shot verified and leaves no temporary file`() {
        val root = Files.createTempDirectory("19s-write-once").toFile()
        try {
            val destination = File(root, "local/signed-validation-candidate-receipt.txt")
            val receipt = SignedValidationCandidateLocalReceipt.create(
                result = acceptedResult(root),
                sourceHeadClaimed = "12".repeat(20),
                independentlySuppliedPublicCertificateSha256 = CERTIFICATE_SHA256,
            )
            SignedValidationCandidateLocalReceipt.writeOnce(receipt, destination)
            assertEquals(receipt.render(), destination.readText())
            assertFalse(
                destination.parentFile.listFiles().orEmpty().any {
                    it.name.startsWith(".signed-validation-candidate-receipt-")
                },
            )
            assertFailsWith<IllegalStateException> {
                SignedValidationCandidateLocalReceipt.writeOnce(receipt, destination)
            }
            assertEquals(receipt.render(), destination.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `task stays explicit local and absent from independent CI`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        val taskSource = File(
            "src/main/kotlin/RecordSignedDisposableValidationCandidateReceiptTask.kt",
        ).readText()
        val receiptSource = File(
            "src/main/kotlin/SignedValidationCandidateLocalReceipt.kt",
        ).readText()
        val ignore = File("../.gitignore").readText()
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        val docs = File("../docs/WIPE_19S_SIGNED_VALIDATION_LOCAL_RECEIPT.md").readText()
        assertTrue(
            appGradle.contains("recordSignedDisposableValidationCandidateReceipt"),
        )
        assertTrue(receiptSource.contains("sentinel.signedValidationCandidateApk"))
        assertTrue(receiptSource.contains("sentinel.validationPublicCertificate"))
        assertTrue(receiptSource.contains("sentinel.signedValidationSourceHead"))
        assertFalse(taskSource.contains("SENTINEL_VALIDATION_STORE"))
        assertFalse(taskSource.contains("SENTINEL_VALIDATION_KEY"))
        assertFalse(taskSource.contains("STORE_PASSWORD"))
        assertFalse(taskSource.contains("KEY_PASSWORD"))
        assertFalse(taskSource.contains("assembleSignedDisposableValidation"))
        assertTrue(ignore.contains("local/signed-validation-candidate-receipt.txt"))
        assertFalse(workflow.contains("recordSignedDisposableValidationCandidateReceipt"))
        assertFalse(File("../local/signed-validation-candidate-receipt.txt").exists())
        assertTrue(
            docs.contains(
                "CHECKPOINT_19S_SIGNED_VALIDATION_LOCAL_RECEIPT_PREPARATION = YES",
            ),
        )
        assertTrue(docs.contains("19S_LOCAL_RECEIPT_RECORDED = false"))
        assertTrue(docs.contains("19S_RECEIPT_AUTHORIZES_WIPE = false"))
        assertTrue(docs.contains("DO NOT MERGE"))
    }

    private fun acceptedResult(root: File): ValidationOnlySignedCandidateEvidence.Result {
        val snapshot = File(
            root,
            DestructiveValidationCandidateEvidence.SNAPSHOT_FILE_NAME,
        ).absolutePath
        return ValidationOnlySignedCandidateEvidence.Result(
            decision =
                ValidationOnlySigningGate.SignedCandidateDecision.ACCEPT_UNTRUSTED_CANDIDATE,
            signingSnapshotPath = snapshot,
            identitySnapshotPath = snapshot,
            schemeSnapshotPath = snapshot,
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
            apkSha256 = APK_SHA256,
            signingCertificateSha256 = CERTIFICATE_SHA256,
            signerCount = 1,
            signerCountReliable = true,
            v2Present = true,
            v3Present = true,
            schemesReliable = true,
        )
    }

    private companion object {
        const val APK_SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val CERTIFICATE_SHA256 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
