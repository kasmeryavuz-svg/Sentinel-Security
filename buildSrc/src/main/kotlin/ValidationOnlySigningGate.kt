/**
 * Build-only decision for whether the dedicated disposable-validation
 * signing task may attach a validation-only key.
 *
 * This object never reads environment secrets, never opens a keystore,
 * and never signs an artifact. Tests may supply synthetic input
 * presence. A later signed validation APK remains an untrusted
 * candidate and cannot mint runtime trust.
 *
 * This route is not an independent witness and does not change the
 * Checkpoint 19H ceremony contract.
 */
object ValidationOnlySigningGate {
    const val DEDICATED_ASSEMBLE_TASK = "assembleSignedDisposableValidation"
    const val DEDICATED_CHECK_TASK = "checkSignedDisposableValidation"
    const val AUTHORITY = "UNTRUSTED_CANDIDATE_ONLY"
    const val STORE_FILE = "SENTINEL_VALIDATION_STORE_FILE"
    const val STORE_PASSWORD = "SENTINEL_VALIDATION_STORE_PASSWORD"
    const val KEY_ALIAS = "SENTINEL_VALIDATION_KEY_ALIAS"
    const val KEY_PASSWORD = "SENTINEL_VALIDATION_KEY_PASSWORD"
    const val CERT_SHA256 = "SENTINEL_VALIDATION_CERT_SHA256"

    val INPUT_NAMES = listOf(
        STORE_FILE,
        STORE_PASSWORD,
        KEY_ALIAS,
        KEY_PASSWORD,
        CERT_SHA256,
    )

    val PRODUCTION_INPUT_NAMES = listOf(
        "SENTINEL_RELEASE_STORE_FILE",
        "SENTINEL_RELEASE_STORE_PASSWORD",
        "SENTINEL_RELEASE_KEY_ALIAS",
        "SENTINEL_RELEASE_KEY_PASSWORD",
        "SENTINEL_RELEASE_CERT_SHA256",
    )

    enum class Decision {
        DO_NOT_ATTACH,
        ATTACH,
        REFUSE_MISSING_INPUTS,
        REFUSE_INCOMPLETE_INPUTS,
        REFUSE_DEBUG_OR_TEST_MATERIAL,
        REFUSE_INVALID_CERTIFICATE_FINGERPRINT,
        REFUSE_PRODUCTION_CROSS_USE,
    }

    enum class SignedCandidateDecision {
        ACCEPT_UNTRUSTED_CANDIDATE,
        REFUSE_UNVERIFIABLE,
        REFUSE_SIGNER_POLICY,
        REFUSE_SIGNATURE_SCHEME,
        REFUSE_DEBUG_OR_TEST_CERTIFICATE,
        REFUSE_CERTIFICATE_MISMATCH,
        REFUSE_BUILD_PURPOSE,
        REFUSE_IDENTITY,
    }

    data class ObservedSigningInputs(
        val storeFilePresent: Boolean,
        val storePasswordPresent: Boolean,
        val keyAliasPresent: Boolean,
        val keyPasswordPresent: Boolean,
        val certificateFingerprintPresent: Boolean,
        val storeFileExists: Boolean,
        val storeFileLooksLikeDebugOrTest: Boolean,
        val certificateFingerprintValid: Boolean,
    ) {
        val anySecretPresent: Boolean =
            storeFilePresent ||
                storePasswordPresent ||
                keyAliasPresent ||
                keyPasswordPresent ||
                certificateFingerprintPresent

        val allSecretsPresent: Boolean =
            storeFilePresent &&
                storePasswordPresent &&
                keyAliasPresent &&
                keyPasswordPresent
    }

    data class ObservedSignedValidationApk(
        val signingVerified: Boolean,
        val signerCount: Int,
        val signerCountReliable: Boolean,
        val v2Present: Boolean,
        val v3Present: Boolean,
        val schemesReliable: Boolean,
        val buildPurpose: String?,
        val identityMatches: Boolean,
        val debugOrTestCertificate: Boolean,
        val certificateFingerprintMatches: Boolean,
    )

    data class ClosedCandidateAuthority(
        val authority: String = AUTHORITY,
        val runtimeAuthorization: Boolean = false,
        val trustedExpectationMinted: Boolean = false,
        val customerDeviceAuthorized: Boolean = false,
        val productionDistribution: Boolean = false,
    )

    data class ObservedSignatureSchemes(
        val v2Present: Boolean,
        val v3Present: Boolean,
        val reliable: Boolean,
    )

    fun decide(
        validationSigningRequested: Boolean,
        productionReleaseTarget: Boolean,
        inputs: ObservedSigningInputs?,
    ): Decision {
        if (productionReleaseTarget) {
            return Decision.REFUSE_PRODUCTION_CROSS_USE
        }
        if (!validationSigningRequested) {
            return Decision.DO_NOT_ATTACH
        }
        val observed = inputs ?: return Decision.REFUSE_MISSING_INPUTS
        if (!observed.anySecretPresent) {
            return Decision.REFUSE_MISSING_INPUTS
        }
        if (!observed.allSecretsPresent || !observed.certificateFingerprintPresent) {
            return Decision.REFUSE_INCOMPLETE_INPUTS
        }
        if (!observed.storeFileExists) {
            return Decision.REFUSE_MISSING_INPUTS
        }
        if (observed.storeFileLooksLikeDebugOrTest) {
            return Decision.REFUSE_DEBUG_OR_TEST_MATERIAL
        }
        if (!observed.certificateFingerprintValid) {
            return Decision.REFUSE_INVALID_CERTIFICATE_FINGERPRINT
        }
        return Decision.ATTACH
    }

    fun mustAttach(decision: Decision): Boolean = decision == Decision.ATTACH

    fun mustRefuse(decision: Decision): Boolean =
        decision != Decision.DO_NOT_ATTACH && decision != Decision.ATTACH

    fun mayAttachToProductionRelease(): Boolean = false

    fun validationInputNamespaceSeparateFromProduction(): Boolean {
        return INPUT_NAMES.none { it in PRODUCTION_INPUT_NAMES } &&
            INPUT_NAMES.all { it.startsWith("SENTINEL_VALIDATION_") } &&
            PRODUCTION_INPUT_NAMES.all { it.startsWith("SENTINEL_RELEASE_") }
    }

    fun validationKeySeparationVerified(): Boolean = false

    fun looksLikeDebugOrTestMaterial(storeFileName: String, keyAlias: String): Boolean {
        val haystack = "$storeFileName $keyAlias".lowercase()
        return listOf("debug", "androiddebugkey", "androidtest").any { it in haystack }
    }

    fun identityMatchesRepositoryContract(
        packageName: String?,
        adminComponent: String?,
        policies: Collection<String>?,
        minSdk: Int?,
        targetSdk: Int?,
    ): Boolean {
        val expected = DestructiveValidationExpectedIdentity.repositoryContract()
        return packageName == expected.packageName &&
            adminComponent == expected.adminComponent &&
            policies?.toSet() == expected.policies.toSet() &&
            minSdk == expected.minSdk &&
            targetSdk == expected.targetSdk
    }

    fun evaluateSignedCandidate(
        observed: ObservedSignedValidationApk,
    ): SignedCandidateDecision {
        if (!observed.signingVerified || !observed.signerCountReliable) {
            return SignedCandidateDecision.REFUSE_UNVERIFIABLE
        }
        if (observed.signerCount != 1) {
            return SignedCandidateDecision.REFUSE_SIGNER_POLICY
        }
        if (!observed.schemesReliable || !observed.v2Present || !observed.v3Present) {
            return SignedCandidateDecision.REFUSE_SIGNATURE_SCHEME
        }
        if (observed.debugOrTestCertificate) {
            return SignedCandidateDecision.REFUSE_DEBUG_OR_TEST_CERTIFICATE
        }
        if (!observed.certificateFingerprintMatches) {
            return SignedCandidateDecision.REFUSE_CERTIFICATE_MISMATCH
        }
        if (
            observed.buildPurpose !=
            DestructiveValidationExpectedIdentity.BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION
        ) {
            return SignedCandidateDecision.REFUSE_BUILD_PURPOSE
        }
        if (!observed.identityMatches) {
            return SignedCandidateDecision.REFUSE_IDENTITY
        }
        return SignedCandidateDecision.ACCEPT_UNTRUSTED_CANDIDATE
    }

    fun closedAuthority(): ClosedCandidateAuthority = ClosedCandidateAuthority()

    fun refuseTrustedExpectationMint(
        @Suppress("UNUSED_PARAMETER") digest: String?,
    ): Nothing? {
        return null
    }

    fun parseSignatureSchemes(apksignerVerboseOutput: String): ObservedSignatureSchemes {
        val v2 = V2_SCHEME.find(apksignerVerboseOutput)?.groupValues?.get(1)
        val v3 = V3_SCHEME.find(apksignerVerboseOutput)?.groupValues?.get(1)
        if (v2 == null || v3 == null) {
            return ObservedSignatureSchemes(
                v2Present = false,
                v3Present = false,
                reliable = false,
            )
        }
        return ObservedSignatureSchemes(
            v2Present = v2.equals("true", ignoreCase = true),
            v3Present = v3.equals("true", ignoreCase = true),
            reliable = true,
        )
    }

    fun renderClosedReport(signedCandidateAccepted: Boolean): String {
        val authority = closedAuthority()
        return buildString {
            appendLine("authority=${authority.authority}")
            appendLine("runtime_authorization=${authority.runtimeAuthorization}")
            appendLine("trusted_expectation_minted=${authority.trustedExpectationMinted}")
            appendLine("customer_device_authorized=${authority.customerDeviceAuthorized}")
            appendLine("production_distribution=${authority.productionDistribution}")
            appendLine("signed_validation_candidate_accepted=$signedCandidateAccepted")
            appendLine("ceremony_status=NOT_READY")
            appendLine("independent_witness_equivalent=false")
        }
    }

    fun statusLinesWithoutDigest(signedCandidateAccepted: Boolean): String {
        val rendered = renderClosedReport(signedCandidateAccepted)
        check(!HEX_VALUE.containsMatchIn(rendered)) {
            "validation-only signing report must not contain digest values"
        }
        return rendered
    }

    private val V2_SCHEME = Regex(
        """(?im)^\s*Verified using v2 scheme[^:]*:\s*(true|false)\s*$""",
    )
    private val V3_SCHEME = Regex(
        """(?im)^\s*Verified using v3 scheme[^:]*:\s*(true|false)\s*$""",
    )
    private val HEX_VALUE = Regex("\\b[0-9a-fA-F]{40,}\\b")
}
