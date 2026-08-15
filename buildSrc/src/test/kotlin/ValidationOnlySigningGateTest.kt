import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationOnlySigningGateTest {
    @Test
    fun `explicit validation request plus complete valid inputs is the only attach route`() {
        val attach = ValidationOnlySigningGate.decide(
            validationSigningRequested = true,
            productionReleaseTarget = false,
            inputs = syntheticAvailableInputs(),
        )
        val ordinary = ValidationOnlySigningGate.decide(
            validationSigningRequested = false,
            productionReleaseTarget = false,
            inputs = syntheticAvailableInputs(),
        )
        assertEquals(ValidationOnlySigningGate.Decision.ATTACH, attach)
        assertEquals(ValidationOnlySigningGate.Decision.DO_NOT_ATTACH, ordinary)
        assertTrue(ValidationOnlySigningGate.mustAttach(attach))
        assertFalse(ValidationOnlySigningGate.mustRefuse(attach))
        assertFalse(ValidationOnlySigningGate.mustAttach(ordinary))
    }

    @Test
    fun `ordinary builds never attach validation signing even if inputs exist`() {
        val missing = ValidationOnlySigningGate.decide(
            validationSigningRequested = false,
            productionReleaseTarget = false,
            inputs = null,
        )
        val present = ValidationOnlySigningGate.decide(
            validationSigningRequested = false,
            productionReleaseTarget = false,
            inputs = syntheticAvailableInputs(),
        )
        assertEquals(ValidationOnlySigningGate.Decision.DO_NOT_ATTACH, missing)
        assertEquals(ValidationOnlySigningGate.Decision.DO_NOT_ATTACH, present)
        assertFalse(ValidationOnlySigningGate.mustRefuse(present))
    }

    @Test
    fun `incomplete or missing validation inputs fail closed`() {
        val none = ValidationOnlySigningGate.decide(
            validationSigningRequested = true,
            productionReleaseTarget = false,
            inputs = null,
        )
        val empty = ValidationOnlySigningGate.decide(
            validationSigningRequested = true,
            productionReleaseTarget = false,
            inputs = ValidationOnlySigningGate.ObservedSigningInputs(
                storeFilePresent = false,
                storePasswordPresent = false,
                keyAliasPresent = false,
                keyPasswordPresent = false,
                certificateFingerprintPresent = false,
                storeFileExists = false,
                storeFileLooksLikeDebugOrTest = false,
                certificateFingerprintValid = false,
            ),
        )
        val incomplete = ValidationOnlySigningGate.decide(
            validationSigningRequested = true,
            productionReleaseTarget = false,
            inputs = syntheticAvailableInputs().copy(keyPasswordPresent = false),
        )
        val missingStore = ValidationOnlySigningGate.decide(
            validationSigningRequested = true,
            productionReleaseTarget = false,
            inputs = syntheticAvailableInputs().copy(storeFileExists = false),
        )
        assertEquals(ValidationOnlySigningGate.Decision.REFUSE_MISSING_INPUTS, none)
        assertEquals(ValidationOnlySigningGate.Decision.REFUSE_MISSING_INPUTS, empty)
        assertEquals(ValidationOnlySigningGate.Decision.REFUSE_INCOMPLETE_INPUTS, incomplete)
        assertEquals(ValidationOnlySigningGate.Decision.REFUSE_MISSING_INPUTS, missingStore)
        assertTrue(ValidationOnlySigningGate.mustRefuse(none))
        assertFalse(ValidationOnlySigningGate.mustAttach(none))
    }

    @Test
    fun `debug key material and invalid certificate fingerprints are rejected`() {
        val debugMaterial = ValidationOnlySigningGate.decide(
            validationSigningRequested = true,
            productionReleaseTarget = false,
            inputs = syntheticAvailableInputs().copy(storeFileLooksLikeDebugOrTest = true),
        )
        val invalidFingerprint = ValidationOnlySigningGate.decide(
            validationSigningRequested = true,
            productionReleaseTarget = false,
            inputs = syntheticAvailableInputs().copy(certificateFingerprintValid = false),
        )
        assertEquals(
            ValidationOnlySigningGate.Decision.REFUSE_DEBUG_OR_TEST_MATERIAL,
            debugMaterial,
        )
        assertEquals(
            ValidationOnlySigningGate.Decision.REFUSE_INVALID_CERTIFICATE_FINGERPRINT,
            invalidFingerprint,
        )
        assertTrue(
            ValidationOnlySigningGate.looksLikeDebugOrTestMaterial(
                "debug.keystore",
                "upload",
            ),
        )
        assertTrue(
            ValidationOnlySigningGate.looksLikeDebugOrTestMaterial(
                "validation.jks",
                "androiddebugkey",
            ),
        )
        assertFalse(
            ValidationOnlySigningGate.looksLikeDebugOrTestMaterial(
                "validation-only.jks",
                "validation",
            ),
        )
    }

    @Test
    fun `production release cannot receive validation signing`() {
        val crossUse = ValidationOnlySigningGate.decide(
            validationSigningRequested = true,
            productionReleaseTarget = true,
            inputs = syntheticAvailableInputs(),
        )
        val productionOrdinary = ValidationOnlySigningGate.decide(
            validationSigningRequested = false,
            productionReleaseTarget = true,
            inputs = syntheticAvailableInputs(),
        )
        assertEquals(ValidationOnlySigningGate.Decision.REFUSE_PRODUCTION_CROSS_USE, crossUse)
        assertEquals(
            ValidationOnlySigningGate.Decision.REFUSE_PRODUCTION_CROSS_USE,
            productionOrdinary,
        )
        assertFalse(ValidationOnlySigningGate.mayAttachToProductionRelease())
        assertTrue(ValidationOnlySigningGate.validationKeySeparateFromProduction())
        assertTrue(
            ValidationOnlySigningGate.INPUT_NAMES.none {
                it in ValidationOnlySigningGate.PRODUCTION_INPUT_NAMES
            },
        )
    }

    @Test
    fun `validation signing cannot mint runtime trust`() {
        val accepted = ValidationOnlySigningGate.evaluateSignedCandidate(syntheticAcceptedApk())
        val authority = ValidationOnlySigningGate.closedAuthority()
        assertEquals(
            ValidationOnlySigningGate.SignedCandidateDecision.ACCEPT_UNTRUSTED_CANDIDATE,
            accepted,
        )
        assertEquals(ValidationOnlySigningGate.AUTHORITY, authority.authority)
        assertFalse(authority.runtimeAuthorization)
        assertFalse(authority.trustedExpectationMinted)
        assertFalse(authority.customerDeviceAuthorized)
        assertFalse(authority.productionDistribution)
        assertNull(ValidationOnlySigningGate.refuseTrustedExpectationMint("unused"))
        assertNull(ValidationOnlySigningGate.refuseTrustedExpectationMint(null))
        val rendered = ValidationOnlySigningGate.statusLinesWithoutDigest(true)
        assertTrue(rendered.contains("authority=UNTRUSTED_CANDIDATE_ONLY"))
        assertTrue(rendered.contains("runtime_authorization=false"))
        assertTrue(rendered.contains("trusted_expectation_minted=false"))
        assertTrue(rendered.contains("customer_device_authorized=false"))
        assertTrue(rendered.contains("production_distribution=false"))
        assertTrue(rendered.contains("ceremony_status=NOT_READY"))
        assertTrue(rendered.contains("independent_witness_equivalent=false"))
        assertFalse(HEX_SHA256.containsMatchIn(rendered))
    }

    @Test
    fun `signed candidate inspection fails closed on policy purpose and identity`() {
        assertEquals(
            ValidationOnlySigningGate.SignedCandidateDecision.REFUSE_SIGNER_POLICY,
            ValidationOnlySigningGate.evaluateSignedCandidate(
                syntheticAcceptedApk().copy(signerCount = 2),
            ),
        )
        assertEquals(
            ValidationOnlySigningGate.SignedCandidateDecision.REFUSE_SIGNATURE_SCHEME,
            ValidationOnlySigningGate.evaluateSignedCandidate(
                syntheticAcceptedApk().copy(v3Present = false),
            ),
        )
        assertEquals(
            ValidationOnlySigningGate.SignedCandidateDecision.REFUSE_DEBUG_OR_TEST_CERTIFICATE,
            ValidationOnlySigningGate.evaluateSignedCandidate(
                syntheticAcceptedApk().copy(debugOrTestCertificate = true),
            ),
        )
        assertEquals(
            ValidationOnlySigningGate.SignedCandidateDecision.REFUSE_CERTIFICATE_MISMATCH,
            ValidationOnlySigningGate.evaluateSignedCandidate(
                syntheticAcceptedApk().copy(certificateFingerprintMatches = false),
            ),
        )
        assertEquals(
            ValidationOnlySigningGate.SignedCandidateDecision.REFUSE_BUILD_PURPOSE,
            ValidationOnlySigningGate.evaluateSignedCandidate(
                syntheticAcceptedApk().copy(buildPurpose = "PRODUCTION"),
            ),
        )
        assertEquals(
            ValidationOnlySigningGate.SignedCandidateDecision.REFUSE_IDENTITY,
            ValidationOnlySigningGate.evaluateSignedCandidate(
                syntheticAcceptedApk().copy(identityMatches = false),
            ),
        )
        assertEquals(
            ValidationOnlySigningGate.SignedCandidateDecision.REFUSE_UNVERIFIABLE,
            ValidationOnlySigningGate.evaluateSignedCandidate(
                syntheticAcceptedApk().copy(signingVerified = false),
            ),
        )
        assertTrue(
            ValidationOnlySigningGate.identityMatchesRepositoryContract(
                packageName = DestructiveValidationExpectedIdentity.REPOSITORY_PACKAGE,
                adminComponent = DestructiveValidationExpectedIdentity.REPOSITORY_ADMIN_COMPONENT,
                policies = listOf("wipe-data", "disable-camera"),
                minSdk = 26,
                targetSdk = 36,
            ),
        )
        assertFalse(
            ValidationOnlySigningGate.identityMatchesRepositoryContract(
                packageName = "com.example.other",
                adminComponent = DestructiveValidationExpectedIdentity.REPOSITORY_ADMIN_COMPONENT,
                policies = listOf("disable-camera", "wipe-data"),
                minSdk = 26,
                targetSdk = 36,
            ),
        )
    }

    @Test
    fun `signature scheme parser requires explicit v2 and v3 lines`() {
        val complete = ValidationOnlySigningGate.parseSignatureSchemes(
            """
            Verified using v1 scheme (JAR signing): true
            Verified using v2 scheme (APK Signature Scheme v2): true
            Verified using v3 scheme (APK Signature Scheme v3): true
            Verified using v3.1 scheme (APK Signature Scheme v3.1): false
            """.trimIndent(),
        )
        val v1Only = ValidationOnlySigningGate.parseSignatureSchemes(
            """
            Verified using v1 scheme (JAR signing): true
            Verified using v2 scheme (APK Signature Scheme v2): false
            Verified using v3 scheme (APK Signature Scheme v3): false
            """.trimIndent(),
        )
        val missing = ValidationOnlySigningGate.parseSignatureSchemes("Number of signers: 1")
        assertTrue(complete.reliable && complete.v2Present && complete.v3Present)
        assertTrue(v1Only.reliable)
        assertFalse(v1Only.v2Present)
        assertFalse(v1Only.v3Present)
        assertFalse(missing.reliable)
    }

    @Test
    fun `signed disposableValidation finder accepts only a non-unsigned dedicated apk`() {
        val root = File("build/tmp/19r-signed-apk-finder").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            File(root, "app-release-unsigned.apk").writeText("no")
            File(root, "app-disposableValidation-unsigned.apk").writeText("no")
            val signed = File(root, "app-disposableValidation.apk").apply { writeText("yes") }
            assertEquals(
                signed.name,
                CheckSignedDisposableValidationTask.findSignedDisposableValidationApk(root).name,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `dedicated task is absent from independent CI execution`() {
        val workflow = File("../.github/workflows/checkpoint-19e-independent-ci.yml").readText()
        assertFalse(workflow.contains(ValidationOnlySigningGate.DEDICATED_ASSEMBLE_TASK))
        assertFalse(workflow.contains(ValidationOnlySigningGate.DEDICATED_CHECK_TASK))
        assertTrue(workflow.contains("SENTINEL_VALIDATION_STORE_FILE"))
        assertTrue(workflow.contains("Unexpected validation-signing environment variable"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_STORE_FILE:"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_STORE_PASSWORD:"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_KEY_ALIAS:"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_KEY_PASSWORD:"))
        assertFalse(workflow.contains("SENTINEL_VALIDATION_CERT_SHA256:"))
        assertTrue(workflow.contains("signing=UNSIGNED"))
        assertTrue(workflow.contains("build_purpose_observed=DISPOSABLE_DEVICE_VALIDATION"))
        assertFalse(workflow.contains("apksigner sign"))
        assertFalse(workflow.contains("keytool"))
    }

    @Test
    fun `main evaluator and ceremony remain NOT_READY`() {
        val evaluation = DestructiveSigningCeremonyPreparation.evaluateRepositoryDefault()
        DestructiveSigningCeremonyPreparation.assertRepositoryDefaultStillNotReady(evaluation)
        assertEquals(SigningCeremonyStatus.NOT_READY, evaluation.status)
        assertFalse(evaluation.witnessApprovalAvailable)
        assertFalse(evaluation.operatorApprovalAvailable)
        assertFalse(evaluation.signedValidationCandidateProduced)
        assertFalse(evaluation.trustedExpectationMinted)
        assertFalse(evaluation.productionSigningAuthorized)
        assertTrue(
            evaluation.blockers.contains(
                SigningCeremonyBlocker.MISSING_INDEPENDENT_WITNESS_APPROVAL,
            ),
        )
        assertTrue(
            File("src/main/kotlin/DestructiveSigningCeremonyPreparation.kt")
                .readText()
                .contains("MISSING_INDEPENDENT_WITNESS_APPROVAL"),
        )
    }

    @Test
    fun `gradle attaches validation signing only on the dedicated disposableValidation path`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        assertTrue(appGradle.contains("if (requestValidationOnlySigning)"))
        assertTrue(appGradle.contains("ValidationOnlySigningGate.decide"))
        assertTrue(appGradle.contains("readValidationOnlySigningSecrets()"))
        assertTrue(appGradle.contains("assembleSignedDisposableValidation"))
        assertTrue(appGradle.contains("checkSignedDisposableValidation"))
        assertTrue(appGradle.contains("create(\"validationOnly\")"))
        assertTrue(
            appGradle.contains(
                "ordinary assembleDisposableValidation must remain unsigned",
            ),
        )
        assertTrue(
            appGradle.contains(
                "release must never use the validation-only signing key",
            ),
        )
        assertTrue(
            appGradle.contains(
                "disposableValidation must remain unsigned even if production-signing",
            ),
        )
        val ordinaryRead = appGradle.substringBefore("val requestValidationOnlySigning")
        assertFalse(ordinaryRead.contains("readValidationOnlySigningSecrets()"))
        assertFalse(ordinaryRead.contains("readExpectedValidationCertSha256()"))
        val releaseBlock = appGradle
            .substringAfter("release {")
            .substringBefore(
                "create(DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE)",
            )
        assertFalse(releaseBlock.contains("validationOnly"))
        assertFalse(releaseBlock.contains("readValidationOnlySigningSecrets"))
        val disposableBlock = appGradle
            .substringAfter(
                "create(DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE)",
            )
            .substringBefore("val disposableValidationSigning")
        assertTrue(disposableBlock.contains("signingConfig = null"))
        assertFalse(appGradle.contains("apksigner sign"))
        assertFalse(appGradle.contains("keytool -genkeypair"))
        val task = File("src/main/kotlin/ValidationOnlySigningTask.kt").readText()
        assertTrue(task.contains("DestructiveProofTaskSemantics.neverReuseOutputs"))
        assertTrue(task.contains("@DisableCachingByDefault"))
        assertFalse(task.contains("apksigner sign"))
        assertFalse(task.contains("keytool"))
        assertFalse(HEX_SHA256.containsMatchIn(task))
        assertFalse(HEX_SHA256.containsMatchIn(appGradle))
    }

    @Test
    fun `19R document stays closed and does not claim a witness or signing`() {
        val docs = File("../docs/WIPE_19R_VALIDATION_ONLY_SIGNING_PATH.md").readText()
        assertTrue(docs.contains("CHECKPOINT_19R_VALIDATION_ONLY_SIGNING_PATH = YES"))
        assertTrue(docs.contains("VALIDATION_SIGNING_PATH_PRESENT = true"))
        assertTrue(docs.contains("VALIDATION_SIGNING_PERFORMED = false"))
        assertTrue(docs.contains("ORDINARY_RELEASE_SIGNING = UNSIGNED"))
        assertTrue(docs.contains("ORDINARY_DISPOSABLE_VALIDATION_SIGNING = UNSIGNED"))
        assertTrue(docs.contains("VALIDATION_KEY_SEPARATE_FROM_PRODUCTION = true"))
        assertTrue(docs.contains("CUSTOMER_DEVICE_PRODUCTION_AUTHORIZED = false"))
        assertTrue(docs.contains("TRUSTED_EXPECTATION_MINTED = false"))
        assertTrue(docs.contains("CEREMONY_STATUS = NOT_READY"))
        assertTrue(docs.contains("A VALIDATION-ONLY PATH IS NOT AN INDEPENDENT WITNESS"))
        assertTrue(docs.contains("MISSING_INDEPENDENT_WITNESS_APPROVAL"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertFalse(docs.contains("VALIDATION_SIGNING_PERFORMED = true"))
        assertFalse(docs.contains("CEREMONY_STATUS = READY"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
    }

    @Test
    fun `runtime modules cannot access the validation-only signing path`() {
        val tokens = listOf(
            "ValidationOnlySigningGate",
            "CheckSignedDisposableValidationTask",
            "assembleSignedDisposableValidation",
            "SENTINEL_VALIDATION_",
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
                    tokens.forEach { token ->
                        assertFalse(text.contains(token), "${file.path} $token")
                    }
                }
        }
    }

    private fun syntheticAvailableInputs(): ValidationOnlySigningGate.ObservedSigningInputs {
        return ValidationOnlySigningGate.ObservedSigningInputs(
            storeFilePresent = true,
            storePasswordPresent = true,
            keyAliasPresent = true,
            keyPasswordPresent = true,
            certificateFingerprintPresent = true,
            storeFileExists = true,
            storeFileLooksLikeDebugOrTest = false,
            certificateFingerprintValid = true,
        )
    }

    private fun syntheticAcceptedApk(): ValidationOnlySigningGate.ObservedSignedValidationApk {
        return ValidationOnlySigningGate.ObservedSignedValidationApk(
            signingVerified = true,
            signerCount = 1,
            signerCountReliable = true,
            v2Present = true,
            v3Present = true,
            schemesReliable = true,
            buildPurpose =
                DestructiveValidationExpectedIdentity.BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION,
            identityMatches = true,
            debugOrTestCertificate = false,
            certificateFingerprintMatches = true,
        )
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
