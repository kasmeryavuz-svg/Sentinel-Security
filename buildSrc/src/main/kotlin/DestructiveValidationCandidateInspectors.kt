import java.io.File
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
    private const val MAX_PACKAGED_XML_CANDIDATES = 512
    private const val DEVICE_ADMIN_RESOURCE_NAME = "xml/device_admin_receiver"
    private val debugMarkers = listOf("CN=Android Debug", "Android Debug")
    private val testMarkers = listOf("androidtest", "Android Test", "CN=Android Test")
    private val packageName = Regex("""package:\s+name='([^']+)'""")
    private val versionCode = Regex("""versionCode='([^']+)'""")
    private val versionName = Regex("""versionName='([^']+)'""")
    private val minSdk = Regex("""sdkVersion:'([^']+)'""")
    private val targetSdk = Regex("""targetSdkVersion:'([^']+)'""")
    private val xmlName = Regex("""android:name[^=]*="([^"]+)"""")
    private val xmlElement = Regex("""^(\s*)E:\s+([A-Za-z0-9_.-]+)\b""")
    private val manifestSdkAttribute = Regex(
        """android:(minSdkVersion|targetSdkVersion)[^=]*=\s*""" +
            """(?:\(type\s+[^)]+\)\s*)?(0x[0-9a-fA-F]+|[0-9]+)""",
    )
    private val resourceTableEntry = Regex("""^\s*resource\s+\S+\s+(.+)$""")
    private val resourceTableFile = Regex("""\(file\)\s+([^\s]+\.xml)\b""")

    data class ObservedSdkVersions(
        val minSdk: String?,
        val targetSdk: String?,
    )
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
            return unavailableIdentity(
                aapt2Available = false,
                detail = "aapt2 unavailable",
                buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_UNINSPECTABLE,
            )
        }
        val badging = runCommand(listOf(aapt2.absolutePath, "dump", "badging", apk.absolutePath))
            ?: return unavailableIdentity(
                aapt2Available = true,
                detail = "aapt2 badging failed",
                buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_UNINSPECTABLE,
            )
        if (badging.exitCode != 0) {
            return unavailableIdentity(
                aapt2Available = true,
                detail = "aapt2 badging exit ${badging.exitCode}",
                buildPurposeStatus = DestructiveValidationBuildPurposeParser.STATUS_UNINSPECTABLE,
            )
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
        val manifestSdkVersions = manifest
            ?.takeIf { it.exitCode == 0 }
            ?.let { inspectObservedSdkVersions(it.output) }
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
        val directPolicies = policiesDump
            ?.takeIf { it.exitCode == 0 }
            ?.let { extractPolicies(it.output) }
        val discoveredPolicies = if (directPolicies == null) {
            discoverPackagedDeviceAdminPolicies(apk, aapt2)
        } else {
            null
        }
        val policies = directPolicies ?: discoveredPolicies
        val purpose = if (manifest == null || manifest.exitCode != 0) {
            DestructiveValidationBuildPurposeParser.uninspectable("aapt2 manifest xmltree failed")
        } else {
            inspectObservedBuildPurpose(manifest.output)
        }
        return DestructiveValidationCandidateEvidence.CandidateApkIdentity(
            packageName = pkg,
            adminComponent = admin,
            policies = policies,
            versionCode = observedVersionCode,
            versionName = observedVersionName,
            minSdk = normalizeNumericSdk(observedMinSdk) ?: manifestSdkVersions?.minSdk,
            targetSdk = normalizeNumericSdk(observedTargetSdk) ?: manifestSdkVersions?.targetSdk,
            buildPurposeObserved = purpose.observed,
            buildPurposeStatus = purpose.status,
            aapt2Available = true,
            detail = "aapt2 inspected;${purpose.detail};" +
                "policies=" + when {
                    directPolicies != null -> "direct"
                    discoveredPolicies != null -> "discovered"
                    else -> "unavailable"
                },
        )
    }

    fun inspectObservedSdkVersions(manifestXmltree: String): ObservedSdkVersions {
        val values = manifestSdkAttribute.findAll(manifestXmltree)
            .mapNotNull { match ->
                val value = parseAapt2Integer(match.groupValues[2]) ?: return@mapNotNull null
                match.groupValues[1] to value.toString()
            }
            .groupBy({ it.first }, { it.second })
        return ObservedSdkVersions(
            minSdk = values["minSdkVersion"]?.distinct()?.singleOrNull(),
            targetSdk = values["targetSdkVersion"]?.distinct()?.singleOrNull(),
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

    fun inspectObservedBuildPurpose(
        manifestXmltree: String,
    ): DestructiveValidationBuildPurposeParser.Observation {
        return DestructiveValidationBuildPurposeParser.parse(manifestXmltree)
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
        return Aapt2Locator.locate(androidSdkDir)
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

    private fun discoverPackagedDeviceAdminPolicies(apk: File, aapt2: File): List<String>? {
        val packagedXml = packagedXmlCandidatePaths(apk)?.toSet() ?: return null
        val resources = runCommand(
            listOf(aapt2.absolutePath, "dump", "resources", apk.absolutePath),
        )?.takeIf { it.exitCode == 0 } ?: return null
        val candidate = inspectObservedResourceFilePath(
            resourceTableDump = resources.output,
            resourceName = DEVICE_ADMIN_RESOURCE_NAME,
        )?.takeIf { it in packagedXml } ?: return null
        return runCommand(
            listOf(
                aapt2.absolutePath,
                "dump",
                "xmltree",
                apk.absolutePath,
                "--file",
                candidate,
            ),
        )
            ?.takeIf { it.exitCode == 0 }
            ?.let { extractPolicies(it.output) }
    }

    fun inspectObservedResourceFilePath(
        resourceTableDump: String,
        resourceName: String,
    ): String? {
        val matches = mutableListOf<String>()
        var matchingResource = false
        resourceTableDump.lineSequence().forEach { line ->
            val entry = resourceTableEntry.find(line)
            if (entry != null) {
                matchingResource = entry.groupValues[1]
                    .substringAfter(':')
                    .trim()
                    .substringBefore(' ') == resourceName
            } else if (matchingResource) {
                resourceTableFile.find(line)?.groupValues?.get(1)?.let(matches::add)
            }
        }
        return matches.distinct().singleOrNull()
    }

    fun packagedXmlCandidatePaths(apk: File): List<String>? {
        return try {
            JarFile(apk).use { jar ->
                val paths = mutableListOf<String>()
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (
                        !entry.isDirectory &&
                        entry.name != "AndroidManifest.xml" &&
                        entry.name.endsWith(".xml", ignoreCase = true)
                    ) {
                        paths += entry.name
                        if (paths.size > MAX_PACKAGED_XML_CANDIDATES) {
                            return null
                        }
                    }
                }
                paths.sorted()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeNumericSdk(value: String?): String? {
        return value?.toIntOrNull()?.toString()
    }

    private fun parseAapt2Integer(value: String): Int? {
        return if (value.startsWith("0x", ignoreCase = true)) {
            value.substring(2).toIntOrNull(16)
        } else {
            value.toIntOrNull()
        }
    }

    private fun unavailableIdentity(
        aapt2Available: Boolean,
        detail: String,
        buildPurposeStatus: String = DestructiveValidationBuildPurposeParser.STATUS_UNAVAILABLE,
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
            buildPurposeStatus = buildPurposeStatus,
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
        val result = BoundedProcessRunner.run(command) ?: return null
        return CommandResult(
            exitCode = result.exitCode,
            output = result.output,
        )
    }
}
