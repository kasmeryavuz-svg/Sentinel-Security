import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarException
import java.util.jar.JarFile

object ReleaseArtifactSecurityVerifier {
    private val requiredClassDescriptors = listOf(
        "Lcom/example/devicemanagement/management/SentinelDeviceAdminReceiver;",
        "Lcom/example/devicemanagement/provisioning/GetProvisioningModeActivity;",
        "Lcom/example/devicemanagement/provisioning/AdminPolicyComplianceActivity;",
        "Lcom/example/devicemanagement/ui/MainActivity;",
        "Lcom/example/devicemanagement/app/DeviceManagementApp;",
        "Lcom/example/devicemanagement/management/DeviceManagement;",
        "Lcom/example/devicemanagement/internal/DeviceManagementImplementation;",
        "Lcom/example/devicemanagement/recovery/RecoveryInspection;",
        "Lcom/example/devicemanagement/app/AppContainer;",
    )

    private val requiredMappingNames = listOf(
        "com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
        "com.example.devicemanagement.provisioning.GetProvisioningModeActivity",
        "com.example.devicemanagement.provisioning.AdminPolicyComplianceActivity",
        "com.example.devicemanagement.ui.MainActivity",
        "com.example.devicemanagement.app.DeviceManagementApp",
        "com.example.devicemanagement.management.DeviceManagement",
        "com.example.devicemanagement.internal.DeviceManagementImplementation",
        "com.example.devicemanagement.recovery.RecoveryInspection",
        "com.example.devicemanagement.action.SensitiveActionController",
    )

    private val forbiddenDexTokens = listOf(
        "wipeData",
        "wipeDevice",
        "lockNow",
        "resetPassword",
        "removeUser",
        "uninstallPackageWithActiveAdmins",
        "clearDeviceOwnerApp",
        "clearApplicationUserData",
        "setKeyguardDisabled",
        "setKeyguardDisabledFeatures",
        "installExistingPackage",
        "installPackage",
        "reboot",
    )

    private val debugCertMarkers = listOf(
        "CN=Android Debug",
        "OU=Android",
        "O=Android",
        "Android Debug",
    )

    private val fingerprintSeparators = Regex("[\\s:._-]")
    private val sha256Hex = Regex("[0-9a-fA-F]{64}")

    enum class SigningClassification {
        UNSIGNED,
        TEST_SIGNED,
        PRODUCTION_SIGNED,
        UNKNOWN,
    }

    data class ArchiveSigningEvidence(
        val signed: Boolean,
        val verificationFailed: Boolean,
        val certificateOutput: String,
        val fingerprints: List<String>,
    )

    fun verifyPackagedDex(
        strings: Set<String>,
        sourceName: String,
    ): List<String> {
        val violations = mutableListOf<String>()
        requiredClassDescriptors.forEach { descriptor ->
            if (descriptor !in strings) {
                violations +=
                    "$sourceName is missing required class descriptor $descriptor after R8"
            }
        }
        forbiddenDexTokens.forEach { token ->
            if (token in strings) {
                violations +=
                    "$sourceName contains forbidden destructive API token $token"
            }
        }
        return violations
    }

    fun verifyMapping(mappingFile: File?): List<String> {
        if (mappingFile == null || !mappingFile.isFile) {
            return listOf("R8 mapping file is missing; release minification must produce one")
        }
        val text = mappingFile.readText()
        val violations = mutableListOf<String>()
        requiredMappingNames.forEach { className ->
            if (className !in text) {
                violations += "R8 mapping does not mention required class $className"
            }
        }
        return violations
    }

    fun normalizeSha256Fingerprint(raw: String?): String? {
        if (raw.isNullOrBlank()) {
            return null
        }
        val stripped = fingerprintSeparators.replace(raw.trim(), "")
        if (!stripped.matches(sha256Hex)) {
            return null
        }
        return stripped.lowercase()
    }

    fun extractSha256Fingerprints(certOutput: String): List<String> {
        return certOutput.lineSequence().mapNotNull { line ->
            val lower = line.lowercase()
            val mentionsSha256 = "sha-256" in lower || "sha256" in lower
            val mentionsIdentity = "digest" in lower || "fingerprint" in lower
            if (!mentionsSha256 || !mentionsIdentity) {
                return@mapNotNull null
            }
            val value = line.substringAfter(':', missingDelimiterValue = "")
                .ifBlank { line.substringAfter('=', missingDelimiterValue = "") }
                .trim()
            normalizeSha256Fingerprint(value)
        }.toList()
    }

    fun isDebugOrTestCertificate(certOutput: String): Boolean {
        return debugCertMarkers.any { marker -> marker in certOutput }
    }

    fun classifySigning(
        certOutput: String,
        signed: Boolean,
        expectedProductionFingerprint: String? = null,
        observedFingerprints: List<String> = extractSha256Fingerprints(certOutput),
        cryptographicallyVerified: Boolean = true,
    ): SigningClassification {
        if (!cryptographicallyVerified) {
            return SigningClassification.UNKNOWN
        }
        if (!signed) {
            return SigningClassification.UNSIGNED
        }
        if (isDebugOrTestCertificate(certOutput)) {
            return SigningClassification.TEST_SIGNED
        }
        val expected = normalizeSha256Fingerprint(expectedProductionFingerprint)
            ?: return SigningClassification.UNKNOWN
        val observed = observedFingerprints.mapNotNull(::normalizeSha256Fingerprint)
        if (observed.isEmpty()) {
            return SigningClassification.UNKNOWN
        }
        return if (observed.all { fingerprint -> fingerprint == expected }) {
            SigningClassification.PRODUCTION_SIGNED
        } else {
            SigningClassification.UNKNOWN
        }
    }

    fun inspectSignedArchive(file: File): ArchiveSigningEvidence {
        if (!file.isFile) {
            return unsignedEvidence()
        }
        val fingerprints = linkedSetOf<String>()
        val distinguishedNames = mutableListOf<String>()
        var signedEntries = 0
        var unsignedPayloadEntries = 0
        return try {
            JarFile(file, true).use { jar ->
                val buffer = ByteArray(8192)
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) {
                        continue
                    }
                    jar.getInputStream(entry).use { input ->
                        while (input.read(buffer) != -1) {
                            // Drain every entry so JarFile performs signature checks.
                        }
                    }
                    val signers = entry.codeSigners
                    if (signers != null && signers.isNotEmpty()) {
                        signedEntries += 1
                        signers.forEach { signer ->
                            signer.signerCertPath.certificates.forEach { certificate ->
                                fingerprints += sha256Hex(certificate.encoded)
                                if (certificate is X509Certificate) {
                                    distinguishedNames += certificate.subjectX500Principal.name
                                }
                            }
                        }
                    } else if (!isJarSignatureMetadata(entry.name)) {
                        unsignedPayloadEntries += 1
                    }
                }
            }
            if (signedEntries > 0 && unsignedPayloadEntries > 0) {
                return failedEvidence(
                    "ARCHIVE HAS UNSIGNED PAYLOAD ENTRIES AFTER SIGNATURE VERIFICATION",
                )
            }
            val signed = signedEntries > 0
            ArchiveSigningEvidence(
                signed = signed,
                verificationFailed = false,
                certificateOutput = certificateOutput(distinguishedNames, fingerprints),
                fingerprints = fingerprints.toList(),
            )
        } catch (failed: SecurityException) {
            failedEvidence("SIGNATURE VERIFICATION FAILED: ${failed.message}")
        } catch (failed: GeneralSecurityException) {
            failedEvidence("SIGNATURE VERIFICATION FAILED: ${failed.message}")
        } catch (failed: JarException) {
            failedEvidence("SIGNATURE VERIFICATION FAILED: ${failed.message}")
        } catch (failed: IOException) {
            failedEvidence("ARCHIVE INSPECTION FAILED: ${failed.message}")
        }
    }

    fun classifyArchiveSigning(
        evidence: ArchiveSigningEvidence,
        expectedProductionFingerprint: String? = null,
    ): SigningClassification {
        if (evidence.verificationFailed) {
            return SigningClassification.UNKNOWN
        }
        return classifySigning(
            certOutput = evidence.certificateOutput,
            signed = evidence.signed,
            expectedProductionFingerprint = expectedProductionFingerprint,
            observedFingerprints = evidence.fingerprints,
            cryptographicallyVerified = true,
        )
    }

    fun verifyExpectedProductionFingerprint(
        productionDistributionRequested: Boolean,
        expectedProductionFingerprint: String?,
    ): List<String> {
        if (!productionDistributionRequested) {
            return emptyList()
        }
        if (expectedProductionFingerprint.isNullOrBlank()) {
            return listOf(
                "Production distribution requires an expected production signing " +
                    "certificate SHA-256 fingerprint via SENTINEL_RELEASE_CERT_SHA256 " +
                    "or gitignored local.properties",
            )
        }
        if (normalizeSha256Fingerprint(expectedProductionFingerprint) == null) {
            return listOf(
                "SENTINEL_RELEASE_CERT_SHA256 is not a valid SHA-256 fingerprint",
            )
        }
        return emptyList()
    }

    fun verifyApksignerAvailability(
        productionDistributionRequested: Boolean,
        apksignerAvailable: Boolean,
    ): List<String> {
        if (!productionDistributionRequested || apksignerAvailable) {
            return emptyList()
        }
        return listOf(
            "Production distribution verification requires a usable apksigner or " +
                "apksigner.bat; the verifier was not located or could not be executed",
        )
    }

    fun verifySigningBoundary(
        classification: SigningClassification,
        productionDistributionRequested: Boolean,
    ): List<String> {
        if (!productionDistributionRequested) {
            return emptyList()
        }
        return when (classification) {
            SigningClassification.PRODUCTION_SIGNED -> emptyList()
            SigningClassification.TEST_SIGNED -> listOf(
                "Production distribution artifact is signed with a debug/test key",
            )
            SigningClassification.UNSIGNED -> listOf(
                "Production distribution artifact is unsigned",
            )
            SigningClassification.UNKNOWN -> listOf(
                "Production distribution artifact signing is not the configured " +
                    "production certificate identity",
            )
        }
    }

    fun signingReport(
        classification: SigningClassification,
        artifactName: String,
    ): String {
        return buildString {
            appendLine("artifact=$artifactName")
            appendLine("signing=$classification")
            appendLine(
                when (classification) {
                    SigningClassification.UNSIGNED ->
                        "This artifact is unsigned and is not a production distribution."
                    SigningClassification.TEST_SIGNED ->
                        "This artifact is test-signed (Android debug/test key) and is not a " +
                            "production distribution."
                    SigningClassification.PRODUCTION_SIGNED ->
                        "This artifact is signed with the configured production certificate " +
                            "SHA-256 fingerprint."
                    SigningClassification.UNKNOWN ->
                        "Signing classification is unknown or does not match the configured " +
                            "production certificate identity. Do not distribute."
                },
            )
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun unsignedEvidence(): ArchiveSigningEvidence {
        return ArchiveSigningEvidence(
            signed = false,
            verificationFailed = false,
            certificateOutput = "",
            fingerprints = emptyList(),
        )
    }

    private fun failedEvidence(reason: String): ArchiveSigningEvidence {
        return ArchiveSigningEvidence(
            signed = false,
            verificationFailed = true,
            certificateOutput = reason,
            fingerprints = emptyList(),
        )
    }

    private fun certificateOutput(
        distinguishedNames: List<String>,
        fingerprints: Set<String>,
    ): String {
        return buildString {
            distinguishedNames.distinct().forEach { name ->
                appendLine("certificate DN: $name")
            }
            fingerprints.forEach { fingerprint ->
                appendLine("certificate SHA-256 digest: $fingerprint")
            }
        }
    }

    private fun isJarSignatureMetadata(name: String): Boolean {
        val normalized = name.replace('\\', '/').uppercase()
        if (!normalized.startsWith("META-INF/")) {
            return false
        }
        return normalized == "META-INF/MANIFEST.MF" ||
            normalized.endsWith(".SF") ||
            normalized.endsWith(".RSA") ||
            normalized.endsWith(".DSA") ||
            normalized.endsWith(".EC") ||
            normalized.endsWith(".SIG")
    }
}
