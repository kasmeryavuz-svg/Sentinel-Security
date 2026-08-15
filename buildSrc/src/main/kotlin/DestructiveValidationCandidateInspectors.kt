import java.io.File
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import java.util.jar.JarException
import java.io.IOException

/**
 * Official-SDK inspectors for untrusted candidate APKs.
 *
 * Signing uses apksigner from Android build-tools. Identity uses aapt2.
 * Expected identity is never copied from observed values. Production
 * signing environment variables are never read.
 */
object DestructiveValidationCandidateInspectors {
    private val debugMarkers = listOf("CN=Android Debug", "Android Debug")
    private val testMarkers = listOf("androidtest", "Android Test", "CN=Android Test")
    private val packageName = Regex("""package:\s+name='([^']+)'""")
    private val versionCode = Regex("""versionCode='([^']+)'""")
    private val versionName = Regex("""versionName='([^']+)'""")
    private val minSdk = Regex("""sdkVersion:'([^']+)'""")
    private val targetSdk = Regex("""targetSdkVersion:'([^']+)'""")
    private val xmlName = Regex("""android:name[^=]*="([^"]+)"""")
    private val xmlElement = Regex("""^(\s*)E:\s+([A-Za-z0-9_.-]+)\b""")
    private const val BUILD_PURPOSE_ENTRY = "META-INF/sentinel-destructive-build-purpose"

    fun inspectSigning(
        apk: File,
        androidSdkDir: File?,
    ): DestructiveValidationCandidateEvidence.CandidateSigningInspection {
        val archiveKind = classifyArchiveShape(apk)
        if (archiveKind == ArchiveKind.MALFORMED) {
            return DestructiveValidationCandidateEvidence.CandidateSigningInspection(
                classification = DestructiveValidationCandidateEvidence.Signing.MALFORMED,
                certificateSha256 = null,
                signerCount = 0,
                apksignerAvailable = locateApksigner(androidSdkDir) != null,
                apksignerExecuted = false,
                detail = "not a readable APK/ZIP archive",
            )
        }
        val apksigner = locateApksigner(androidSdkDir)
        if (apksigner == null) {
            return DestructiveValidationCandidateEvidence.CandidateSigningInspection(
                classification = DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE,
                certificateSha256 = null,
                signerCount = 0,
                apksignerAvailable = false,
                apksignerExecuted = false,
                detail = "apksigner unavailable",
            )
        }
        val command = ApksignerLocator.commandLine(
            apksigner,
            "verify",
            "--print-certs",
            "--verbose",
            apk.absolutePath,
        )
        val result = runCommand(command)
        if (result == null) {
            return DestructiveValidationCandidateEvidence.CandidateSigningInspection(
                classification = DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE,
                certificateSha256 = null,
                signerCount = 0,
                apksignerAvailable = true,
                apksignerExecuted = false,
                detail = "apksigner could not be executed",
            )
        }
        val parsed = DestructiveValidationApksignerSignerParser.parse(result.output)
        val classification = classifyApksigner(
            exitCode = result.exitCode,
            output = result.output,
            parsed = parsed,
            archiveKind = archiveKind,
        )
        val signerCount = parsed.currentSignerCount ?: -1
        return DestructiveValidationCandidateEvidence.CandidateSigningInspection(
            classification = classification,
            certificateSha256 = parsed.currentCertificateSha256,
            signerCount = signerCount,
            signerCountReliable = parsed.reliable && parsed.currentSignerCount != null,
            lineagePresent = parsed.lineagePresent,
            apksignerAvailable = true,
            apksignerExecuted = true,
            detail = "apksigner_exit=${result.exitCode};" +
                (parsed.unreliabilityReason ?: "signer_parse_reliable"),
        )
    }

    fun inspectIdentity(
        apk: File,
        androidSdkDir: File?,
    ): DestructiveValidationCandidateEvidence.CandidateApkIdentity {
        val aapt2 = locateAapt2(androidSdkDir)
        if (aapt2 == null) {
            return unavailableIdentity(aapt2Available = false, detail = "aapt2 unavailable")
        }
        val badging = runCommand(listOf(aapt2.absolutePath, "dump", "badging", apk.absolutePath))
            ?: return unavailableIdentity(aapt2Available = true, detail = "aapt2 badging failed")
        if (badging.exitCode != 0) {
            return unavailableIdentity(aapt2Available = true, detail = "aapt2 badging exit ${badging.exitCode}")
        }
        val pkg = packageName.find(badging.output)?.groupValues?.get(1)
        val observedVersionCode = versionCode.find(badging.output)?.groupValues?.get(1)
        val observedVersionName = versionName.find(badging.output)?.groupValues?.get(1)
        val observedMinSdk = minSdk.find(badging.output)?.groupValues?.get(1)
        val observedTargetSdk = targetSdk.find(badging.output)?.groupValues?.get(1)
        val manifest = runCommand(
            listOf(
                aapt2.absolutePath,
                "dump",
                "xmltree",
                apk.absolutePath,
                "--file",
                "AndroidManifest.xml",
            ),
        )
        val admin = manifest
            ?.takeIf { it.exitCode == 0 }
            ?.let { extractAdminComponent(it.output, pkg) }
        val policiesDump = runCommand(
            listOf(
                aapt2.absolutePath,
                "dump",
                "xmltree",
                apk.absolutePath,
                "--file",
                "res/xml/device_admin_receiver.xml",
            ),
        )
        val policies = policiesDump
            ?.takeIf { it.exitCode == 0 }
            ?.let { extractPolicies(it.output) }
        return DestructiveValidationCandidateEvidence.CandidateApkIdentity(
            packageName = pkg,
            adminComponent = admin,
            policies = policies,
            versionCode = observedVersionCode,
            versionName = observedVersionName,
            minSdk = observedMinSdk,
            targetSdk = observedTargetSdk,
            buildPurposeObserved = inspectObservedBuildPurpose(apk),
            aapt2Available = true,
            detail = "aapt2 inspected",
        )
    }

    fun classifyOfficialApksignerOutput(
        exitCode: Int,
        output: String,
        archiveSigned: Boolean = true,
    ): DestructiveValidationCandidateEvidence.Signing {
        val parsed = DestructiveValidationApksignerSignerParser.parse(output)
        return classifyApksigner(
            exitCode = exitCode,
            output = output,
            parsed = parsed,
            archiveKind = if (archiveSigned) ArchiveKind.SIGNED else ArchiveKind.UNSIGNED,
        )
    }

    fun inspectObservedBuildPurpose(apk: File): String? {
        return try {
            java.util.jar.JarFile(apk, false).use { jar ->
                val entry = jar.getEntry(BUILD_PURPOSE_ENTRY) ?: return null
                if (entry.isDirectory) {
                    return null
                }
                jar.getInputStream(entry).bufferedReader().use { it.readText() }
                    .trim()
                    .takeIf { candidate ->
                        candidate.isNotEmpty() &&
                            '\n' !in candidate &&
                            candidate.length <= 128
                    }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun captureGit(projectRoot: File?): DestructiveValidationCandidateEvidence.GitProvenance {
        if (projectRoot == null || !projectRoot.isDirectory) {
            return unavailableGit()
        }
        val revision = runCommand(listOf("git", "-C", projectRoot.absolutePath, "rev-parse", "HEAD"))
            ?.takeIf { it.exitCode == 0 }
            ?.output
            ?.trim()
            ?.takeIf { it.matches(Regex("[0-9a-f]{40}")) }
            ?: return unavailableGit()
        val status = runCommand(
            listOf("git", "-C", projectRoot.absolutePath, "status", "--porcelain"),
        ) ?: return DestructiveValidationCandidateEvidence.GitProvenance(
            revision = revision,
            worktree = "UNAVAILABLE",
        )
        if (status.exitCode != 0) {
            return DestructiveValidationCandidateEvidence.GitProvenance(
                revision = revision,
                worktree = "UNAVAILABLE",
            )
        }
        val dirty = status.output.isNotBlank()
        return DestructiveValidationCandidateEvidence.GitProvenance(
            revision = revision,
            worktree = if (dirty) "DIRTY" else "CLEAN",
        )
    }

    fun locateApksigner(androidSdkDir: File?): File? {
        val buildTools = preferredBuildTools(androidSdkDir)
        buildTools?.let { ApksignerLocator.resolveFromBuildTools(it) }?.let { return it }
        val all = File(androidSdkDir ?: return null, "build-tools")
        if (!all.isDirectory) {
            return null
        }
        return all.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.firstNotNullOfOrNull { ApksignerLocator.resolveFromBuildTools(it) }
    }

    fun locateAapt2(androidSdkDir: File?): File? {
        val preferred = preferredBuildTools(androidSdkDir)?.let { File(it, "aapt2") }
        if (preferred != null && preferred.isFile) {
            return preferred
        }
        val all = File(androidSdkDir ?: return null, "build-tools")
        if (!all.isDirectory) {
            return null
        }
        return all.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "aapt2") }
            ?.firstOrNull { it.isFile }
    }

    private fun preferredBuildTools(androidSdkDir: File?): File? {
        if (androidSdkDir == null || !androidSdkDir.isDirectory) {
            return null
        }
        val preferred = File(androidSdkDir, "build-tools/35.0.0")
        return preferred.takeIf { it.isDirectory }
    }

    private fun classifyApksigner(
        exitCode: Int,
        output: String,
        parsed: DestructiveValidationApksignerSignerParser.Parse,
        archiveKind: ArchiveKind,
    ): DestructiveValidationCandidateEvidence.Signing {
        if (parsed.reliable && (parsed.currentSignerCount ?: 0) >= 2) {
            return DestructiveValidationCandidateEvidence.Signing.MULTIPLE_SIGNERS
        }
        if (!parsed.reliable) {
            return when {
                exitCode != 0 &&
                    (archiveKind == ArchiveKind.UNSIGNED || looksUnsigned(output)) ->
                    DestructiveValidationCandidateEvidence.Signing.UNSIGNED
                exitCode != 0 &&
                    (archiveKind == ArchiveKind.MALFORMED || looksMalformed(output)) ->
                    DestructiveValidationCandidateEvidence.Signing.MALFORMED
                else -> DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE
            }
        }
        if (exitCode != 0) {
            return when {
                archiveKind == ArchiveKind.UNSIGNED ||
                    looksUnsigned(output) ->
                    DestructiveValidationCandidateEvidence.Signing.UNSIGNED
                archiveKind == ArchiveKind.MALFORMED ||
                    looksMalformed(output) ->
                    DestructiveValidationCandidateEvidence.Signing.MALFORMED
                else -> DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE
            }
        }
        if ((parsed.currentSignerCount ?: 0) == 0) {
            return if (archiveKind == ArchiveKind.UNSIGNED || looksUnsigned(output)) {
                DestructiveValidationCandidateEvidence.Signing.UNSIGNED
            } else {
                DestructiveValidationCandidateEvidence.Signing.MALFORMED
            }
        }
        if (parsed.currentSignerCount != 1 || parsed.currentCertificateSha256 == null) {
            return DestructiveValidationCandidateEvidence.Signing.UNVERIFIABLE
        }
        if (debugMarkers.any { marker -> marker in output }) {
            return DestructiveValidationCandidateEvidence.Signing.DEBUG_SIGNED
        }
        val lower = output.lowercase()
        if (testMarkers.any { marker -> marker.lowercase() in lower } ||
            ReleaseArtifactSecurityVerifier.isDebugOrTestCertificate(output)
        ) {
            return DestructiveValidationCandidateEvidence.Signing.TEST_SIGNED
        }
        return DestructiveValidationCandidateEvidence.Signing.SIGNED_UNCLASSIFIED
    }

    private fun looksUnsigned(output: String): Boolean {
        val lower = output.lowercase()
        return "does not verify" in lower &&
            (
                "no signature" in lower ||
                    "not signed" in lower ||
                    "missing" in lower && "meta-inf" in lower ||
                    "unsigned" in lower
                ) ||
            "not signed" in lower ||
            "no APK signature" in output
    }

    private fun looksMalformed(output: String): Boolean {
        val lower = output.lowercase()
        return "malformed" in lower ||
            "not a zip" in lower ||
            "end-of-central-directory" in lower ||
            "invalid apk" in lower
    }

    private enum class ArchiveKind {
        UNSIGNED,
        SIGNED,
        MALFORMED,
    }

    private fun classifyArchiveShape(apk: File): ArchiveKind {
        return try {
            JarFile(apk, false).use { jar ->
                val entries = jar.entries()
                var signatureBlocks = 0
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name.replace('\\', '/').uppercase()
                    if (name.startsWith("META-INF/") &&
                        (
                            name.endsWith(".RSA") ||
                                name.endsWith(".DSA") ||
                                name.endsWith(".EC") ||
                                name.endsWith(".SIG")
                            )
                    ) {
                        signatureBlocks += 1
                    }
                }
                if (signatureBlocks == 0) {
                    ArchiveKind.UNSIGNED
                } else {
                    ArchiveKind.SIGNED
                }
            }
        } catch (_: JarException) {
            ArchiveKind.MALFORMED
        } catch (_: IOException) {
            ArchiveKind.MALFORMED
        } catch (_: SecurityException) {
            ArchiveKind.MALFORMED
        }
    }

    private fun extractAdminComponent(manifestDump: String, packageName: String?): String? {
        if (packageName.isNullOrBlank()) {
            return null
        }
        val receivers = mutableListOf<Pair<String, Boolean>>()
        val lines = manifestDump.lineSequence().toList()
        var index = 0
        while (index < lines.size) {
            val element = xmlElement.find(lines[index])
            if (element == null || element.groupValues[2] != "receiver") {
                index += 1
                continue
            }
            val indent = element.groupValues[1].length
            var name: String? = null
            var deviceAdmin = false
            var cursor = index + 1
            while (cursor < lines.size) {
                val next = xmlElement.find(lines[cursor])
                if (next != null && next.groupValues[1].length <= indent) {
                    break
                }
                xmlName.find(lines[cursor])?.groupValues?.get(1)?.let { value ->
                    if (name == null && !value.startsWith("android.")) {
                        name = value
                    }
                    if (value == "android.app.action.DEVICE_ADMIN_ENABLED" ||
                        value == "android.permission.BIND_DEVICE_ADMIN" ||
                        value == "android.app.device_admin"
                    ) {
                        deviceAdmin = true
                    }
                }
                if ("BIND_DEVICE_ADMIN" in lines[cursor] ||
                    "DEVICE_ADMIN_ENABLED" in lines[cursor] ||
                    "android.app.device_admin" in lines[cursor]
                ) {
                    deviceAdmin = true
                }
                cursor += 1
            }
            val resolved = name?.let { resolveClassName(packageName, it) }
            if (resolved != null) {
                receivers += resolved to deviceAdmin
            }
            index = cursor
        }
        val admins = receivers.filter { it.second }.map { it.first }.distinct()
        if (admins.size != 1) {
            return admins.singleOrNull()
        }
        return "$packageName/${admins.single()}"
    }

    private fun resolveClassName(packageName: String, raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith(".") -> packageName + trimmed
            trimmed.contains('.') -> trimmed
            else -> "$packageName.$trimmed"
        }
    }

    private fun extractPolicies(xmltree: String): List<String>? {
        val elementLines = xmltree.lineSequence()
            .mapNotNull { line ->
                val match = xmlElement.find(line) ?: return@mapNotNull null
                match.groupValues[1].length to match.groupValues[2]
            }
            .toList()
        if (elementLines.isEmpty()) {
            return null
        }
        if (elementLines.firstOrNull()?.second != "device-admin") {
            return null
        }
        val usesPoliciesIndex = elementLines.indexOfFirst { it.second == "uses-policies" }
        if (usesPoliciesIndex < 0) {
            return null
        }
        val usesPoliciesIndent = elementLines[usesPoliciesIndex].first
        return elementLines
            .drop(usesPoliciesIndex + 1)
            .takeWhile { it.first > usesPoliciesIndent }
            .map { it.second }
    }

    private fun unavailableIdentity(
        aapt2Available: Boolean,
        detail: String,
    ): DestructiveValidationCandidateEvidence.CandidateApkIdentity {
        return DestructiveValidationCandidateEvidence.CandidateApkIdentity(
            packageName = null,
            adminComponent = null,
            policies = null,
            versionCode = null,
            versionName = null,
            minSdk = null,
            targetSdk = null,
            buildPurposeObserved = null,
            aapt2Available = aapt2Available,
            detail = detail,
        )
    }

    private fun unavailableGit(): DestructiveValidationCandidateEvidence.GitProvenance {
        return DestructiveValidationCandidateEvidence.GitProvenance(
            revision = "UNAVAILABLE",
            worktree = "UNAVAILABLE",
        )
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
    )

    private fun runCommand(command: List<String>): CommandResult? {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(60, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            CommandResult(
                exitCode = process.exitValue(),
                output = process.inputStream.bufferedReader().use { it.readText() },
            )
        } catch (_: Exception) {
            null
        }
    }
}
