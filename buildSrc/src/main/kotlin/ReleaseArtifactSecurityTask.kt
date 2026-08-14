import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.io.File

abstract class ReleaseArtifactSecurityTask : DefaultTask() {
    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val requireNonDebuggable: Property<Boolean>

    @get:Input
    abstract val productionDistributionRequested: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val expectedProductionCertSha256: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val backupRules: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataExtractionRules: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val networkSecurityConfig: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mappingFile: RegularFileProperty

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundleFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val apksignerPath: Property<String>

    @get:Input
    @get:Optional
    abstract val buildToolsDirectory: Property<String>

    @get:OutputFile
    abstract val signingReport: RegularFileProperty

    @TaskAction
    fun verify() {
        val variant = variantName.get()
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val violations = mutableListOf<String>()
        val productionRequested = productionDistributionRequested.get()
        val expectedFingerprint = expectedProductionCertSha256.orNull

        val manifestFile = mergedManifest.get().asFile
        val manifest = EffectiveManifestSecurityVerifier.parse(manifestFile)
        violations += EffectiveManifestSecurityVerifier.verify(
            manifest = manifest,
            androidNamespace = androidNamespace,
            variantName = variant,
            requireNonDebuggable = requireNonDebuggable.get(),
        )
        violations += BackupPolicyVerifier.verify(
            backupRules.get().asFile,
            dataExtractionRules.get().asFile,
        )
        violations += NetworkSecurityConfigVerifier.verify(networkSecurityConfig.get().asFile)

        if (mappingFile.isPresent) {
            violations += ReleaseArtifactSecurityVerifier.verifyMapping(mappingFile.get().asFile)
        } else if (requireNonDebuggable.get()) {
            violations += "release R8 mapping file is missing"
        }

        violations += ReleaseArtifactSecurityVerifier.verifyExpectedProductionFingerprint(
            productionDistributionRequested = productionRequested,
            expectedProductionFingerprint = expectedFingerprint,
        )

        var classification = ReleaseArtifactSecurityVerifier.SigningClassification.UNKNOWN
        var artifactName = "none"
        if (apkDirectory.isPresent) {
            val apk = findApk(apkDirectory.get().asFile)
            artifactName = apk.name
            val strings = DexStringTable.stringsFromApk(apk)
            violations += ReleaseArtifactSecurityVerifier.verifyPackagedDex(strings, apk.name)
            val signing = classifyApkSigning(
                apk = apk,
                expectedFingerprint = expectedFingerprint,
                productionRequested = productionRequested,
            )
            classification = signing.classification
            violations += ReleaseArtifactSecurityVerifier.verifyApksignerAvailability(
                productionDistributionRequested = productionRequested,
                apksignerAvailable = signing.apksignerAvailable,
            )
            violations += ReleaseArtifactSecurityVerifier.verifySigningBoundary(
                classification = signing.classification,
                productionDistributionRequested = productionRequested,
            )
        }
        if (bundleFile.isPresent) {
            val bundle = bundleFile.get().asFile
            artifactName = bundle.name
            val strings = DexStringTable.stringsFromAab(bundle)
            violations += ReleaseArtifactSecurityVerifier.verifyPackagedDex(strings, bundle.name)
            val signing = classifyBundleSigning(bundle, expectedFingerprint)
            classification = signing
            violations += ReleaseArtifactSecurityVerifier.verifyApksignerAvailability(
                productionDistributionRequested = productionRequested,
                apksignerAvailable = resolveApksigner() != null,
            )
            violations += ReleaseArtifactSecurityVerifier.verifySigningBoundary(
                classification = signing,
                productionDistributionRequested = productionRequested,
            )
        }

        val report = signingReport.get().asFile
        report.parentFile.mkdirs()
        report.writeText(
            ReleaseArtifactSecurityVerifier.signingReport(classification, artifactName),
        )
        logger.lifecycle(report.readText().trim())

        check(violations.isEmpty()) {
            "Release production security verification failed:\n" +
                violations.joinToString("\n")
        }
    }

    private fun findApk(directory: File): File {
        val apks = directory.walkTopDown()
            .filter { it.isFile && it.extension == "apk" }
            .toList()
        val signed = apks.filterNot { "unsigned" in it.name.lowercase() }
        val chosen = signed.singleOrNull() ?: apks.singleOrNull()
        check(chosen != null && chosen.isFile) {
            "Expected one release APK under $directory; found ${apks.map { it.name }}"
        }
        return chosen
    }

    private fun resolveApksigner(): File? {
        ApksignerLocator.resolve(apksignerPath.orNull)?.let { return it }
        val buildTools = buildToolsDirectory.orNull?.takeIf { it.isNotBlank() }?.let(::File)
        return buildTools?.let(ApksignerLocator::resolveFromBuildTools)
    }

    private fun classifyApkSigning(
        apk: File,
        expectedFingerprint: String?,
        productionRequested: Boolean,
    ): ApkSigningResult {
        val apksigner = resolveApksigner()
        if (apksigner == null) {
            return archiveFallback(
                apk = apk,
                expectedFingerprint = expectedFingerprint,
                apksignerAvailable = false,
                productionRequested = productionRequested,
            )
        }
        return try {
            val output = ByteArrayOutputStream()
            val verifierCommand = ApksignerLocator.commandLine(
                apksigner,
                "verify",
                "--print-certs",
                apk.absolutePath,
            )
            val result = project.exec {
                commandLine(*verifierCommand.toTypedArray())
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
            val text = output.toString(Charsets.UTF_8)
            val signed = result.exitValue == 0
            val classification = ReleaseArtifactSecurityVerifier.classifySigning(
                certOutput = text,
                signed = signed,
                expectedProductionFingerprint = expectedFingerprint,
            )
            ApkSigningResult(
                classification = classification,
                apksignerAvailable = true,
            )
        } catch (_: Exception) {
            archiveFallback(
                apk = apk,
                expectedFingerprint = expectedFingerprint,
                apksignerAvailable = false,
                productionRequested = productionRequested,
            )
        }
    }

    private fun archiveFallback(
        apk: File,
        expectedFingerprint: String?,
        apksignerAvailable: Boolean,
        productionRequested: Boolean,
    ): ApkSigningResult {
        val archive = ReleaseArtifactSecurityVerifier.inspectSignedArchive(apk)
        val classification = if (!archive.signed) {
            ReleaseArtifactSecurityVerifier.SigningClassification.UNSIGNED
        } else if (productionRequested) {
            ReleaseArtifactSecurityVerifier.SigningClassification.UNKNOWN
        } else {
            ReleaseArtifactSecurityVerifier.classifySigning(
                certOutput = archive.certificateOutput,
                signed = true,
                expectedProductionFingerprint = expectedFingerprint,
                observedFingerprints = archive.fingerprints,
            ).let { classified ->
                if (classified ==
                    ReleaseArtifactSecurityVerifier.SigningClassification.PRODUCTION_SIGNED
                ) {
                    ReleaseArtifactSecurityVerifier.SigningClassification.UNKNOWN
                } else {
                    classified
                }
            }
        }
        return ApkSigningResult(
            classification = classification,
            apksignerAvailable = apksignerAvailable,
        )
    }

    private fun classifyBundleSigning(
        bundle: File,
        expectedFingerprint: String?,
    ): ReleaseArtifactSecurityVerifier.SigningClassification {
        val evidence = ReleaseArtifactSecurityVerifier.inspectSignedArchive(bundle)
        return ReleaseArtifactSecurityVerifier.classifySigning(
            certOutput = evidence.certificateOutput,
            signed = evidence.signed,
            expectedProductionFingerprint = expectedFingerprint,
            observedFingerprints = evidence.fingerprints,
        )
    }

    private data class ApkSigningResult(
        val classification: ReleaseArtifactSecurityVerifier.SigningClassification,
        val apksignerAvailable: Boolean,
    )
}
