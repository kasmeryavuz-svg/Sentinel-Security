import java.io.File
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarFile
import java.util.zip.ZipFile

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
    ): SigningClassification {
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
            return ArchiveSigningEvidence(
                signed = false,
                certificateOutput = "",
                fingerprints = emptyList(),
            )
        }
        val fingerprints = linkedSetOf<String>()
        val distinguishedNames = mutableListOf<String>()
        var hasCodeSigners = false
        runCatching {
            JarFile(file, false).use { jar ->
                val entries = jar.entries()
                val buffer = ByteArray(8192)
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    jar.getInputStream(entry).use { input ->
                        while (input.read(buffer) != -1) {
                            // Drain the entry so the JAR signer table is populated.
                        }
                    }
                    val signers = entry.codeSigners ?: continue
                    hasCodeSigners = true
                    signers.forEach { signer ->
                        signer.signerCertPath.certificates.forEach { certificate ->
                            fingerprints += sha256Hex(certificate.encoded)
                            if (certificate is X509Certificate) {
                                distinguishedNames += certificate.subjectX500Principal.name
                            }
                        }
                    }
                }
            }
        }
        if (fingerprints.isEmpty()) {
            certificatesFromPkcs7(file).forEach { certificate ->
                fingerprints += sha256Hex(certificate.encoded)
                if (certificate is X509Certificate) {
                    distinguishedNames += certificate.subjectX500Principal.name
                }
            }
        }
        val signed = hasCodeSigners ||
            fingerprints.isNotEmpty() ||
            archiveHasSignatureFiles(file)
        val certificateOutput = buildString {
            distinguishedNames.distinct().forEach { name ->
                appendLine("certificate DN: $name")
            }
            fingerprints.forEach { fingerprint ->
                appendLine("certificate SHA-256 digest: $fingerprint")
            }
        }
        return ArchiveSigningEvidence(
            signed = signed,
            certificateOutput = certificateOutput,
            fingerprints = fingerprints.toList(),
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

    private fun archiveHasSignatureFiles(file: File): Boolean {
        return runCatching {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().any { entry ->
                    val name = entry.name.uppercase()
                    name.startsWith("META-INF/") &&
                        (
                            name.endsWith(".RSA") ||
                                name.endsWith(".DSA") ||
                                name.endsWith(".EC")
                            )
                }
            }
        }.getOrDefault(false)
    }

    private fun certificatesFromPkcs7(file: File): List<Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        val certificates = mutableListOf<Certificate>()
        runCatching {
            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { entry ->
                        val name = entry.name.uppercase()
                        name.startsWith("META-INF/") &&
                            (
                                name.endsWith(".RSA") ||
                                    name.endsWith(".DSA") ||
                                    name.endsWith(".EC")
                                )
                    }
                    .forEach { entry ->
                        zip.getInputStream(entry).use { input ->
                            runCatching {
                                certificates += factory.generateCertificates(input)
                            }
                        }
                    }
            }
        }
        return certificates
    }
}
