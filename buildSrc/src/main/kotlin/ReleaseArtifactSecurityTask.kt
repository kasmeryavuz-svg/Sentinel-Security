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

    @get:OutputFile
    abstract val signingReport: RegularFileProperty

    @TaskAction
    fun verify() {
        val variant = variantName.get()
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val violations = mutableListOf<String>()

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
        val networkConfig = networkSecurityConfig.get().asFile
        if (!networkConfig.isFile) {
            violations += "network security config is missing"
        } else {
            val networkText = networkConfig.readText()
            if ("cleartextTrafficPermitted=\"false\"" !in networkText) {
                violations += "network security config must deny cleartext traffic"
            }
            if ("debug-overrides" in networkText) {
                violations += "network security config must not include debug-overrides"
            }
        }

        if (mappingFile.isPresent) {
            violations += ReleaseArtifactSecurityVerifier.verifyMapping(mappingFile.get().asFile)
        } else if (requireNonDebuggable.get()) {
            violations += "release R8 mapping file is missing"
        }

        var classification = ReleaseArtifactSecurityVerifier.SigningClassification.UNKNOWN
        var artifactName = "none"
        if (apkDirectory.isPresent) {
            val apk = findApk(apkDirectory.get().asFile)
            artifactName = apk.name
            val strings = DexStringTable.stringsFromApk(apk)
            violations += ReleaseArtifactSecurityVerifier.verifyPackagedDex(strings, apk.name)
            val signing = classifyApkSigning(apk)
            classification = signing
            violations += ReleaseArtifactSecurityVerifier.verifySigningBoundary(
                classification = signing,
                productionDistributionRequested = productionDistributionRequested.get(),
            )
        }
        if (bundleFile.isPresent) {
            val bundle = bundleFile.get().asFile
            artifactName = bundle.name
            val strings = DexStringTable.stringsFromAab(bundle)
            violations += ReleaseArtifactSecurityVerifier.verifyPackagedDex(strings, bundle.name)
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
            .filter { it.isFile && it.extension == "apk" && "unsigned" !in it.name }
            .toList()
        val preferred = apks.filterNot { "unsigned" in it.name }
        val chosen = preferred.singleOrNull() ?: apks.singleOrNull()
        check(chosen != null && chosen.isFile) {
            "Expected one release APK under $directory; found ${apks.map { it.name }}"
        }
        return chosen
    }

    private fun classifyApkSigning(
        apk: File,
    ): ReleaseArtifactSecurityVerifier.SigningClassification {
        val apksigner = apksignerPath.orNull?.let(::File)
        if (apksigner == null || !apksigner.isFile) {
            return ReleaseArtifactSecurityVerifier.SigningClassification.UNKNOWN
        }
        val output = ByteArrayOutputStream()
        val result = project.exec {
            commandLine(
                apksigner.absolutePath,
                "verify",
                "--print-certs",
                apk.absolutePath,
            )
            standardOutput = output
            errorOutput = output
            isIgnoreExitValue = true
        }
        val text = output.toString(Charsets.UTF_8)
        val signed = result.exitValue == 0
        return ReleaseArtifactSecurityVerifier.classifySigning(text, signed)
    }
}
