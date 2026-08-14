import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ProductionBytecodePolicyTask : DefaultTask() {
    @get:Input
    abstract val artifactPath: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirectories: ListProperty<Directory>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val additionalClassFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productionFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedNativeLibraries: ConfigurableFileCollection

    init {
        classJars.convention(emptyList())
        classDirectories.convention(emptyList())
    }

    @TaskAction
    fun verify() {
        val artifact = artifactPath.get()
        val classRoots =
            classJars.get().map { it.asFile } +
                classDirectories.get().map { it.asFile } +
                additionalClassFiles.files
        val classTargets = ProductionBytecodePolicyVerifier.classTargets(artifact, classRoots)
        check(classTargets.isNotEmpty()) {
            "$artifact produced no classes for compiled production policy verification"
        }

        val violations = ProductionBytecodePolicyVerifier.verify(classTargets) +
            verifyNativeProductionInputs(productionFiles.files) +
            verifyMergedNativeLibraries()
        check(violations.isEmpty()) {
            "Production policy verification failed:\n${violations.joinToString("\n")}"
        }
    }

    private fun verifyMergedNativeLibraries(): List<String> {
        val nativeLibraries = mergedNativeLibraries.files.flatMap { root ->
            if (root.exists()) {
                root.walkTopDown().filter { it.isFile }.toList()
            } else {
                emptyList()
            }
        }
        return nativeLibraries.map { file ->
            "${artifactPath.get()}:${file.path}: packaged production native library is forbidden"
        }
    }

    private fun verifyNativeProductionInputs(files: Set<File>): List<String> {
        val nativeExtensions = setOf(
            "so",
            "dll",
            "dylib",
            "a",
            "o",
            "c",
            "cc",
            "cpp",
            "cxx",
            "h",
            "hpp",
            "m",
            "mm",
            "s",
            "asm",
            "rs",
            "go",
            "zig",
            "wasm",
        )
        return files.flatMap { file ->
            val violations = mutableListOf<String>()
            val normalizedPath = file.invariantSeparatorsPath
            val isNativeSourcePath =
                "/jni/" in normalizedPath || "/jniLibs/" in normalizedPath
            if (
                file.isFile &&
                (file.extension.lowercase() in nativeExtensions || isNativeSourcePath)
            ) {
                violations +=
                    "${artifactPath.get()}:${file.path}: production native code/library is forbidden"
            }
            if (file.isFile && file.extension.lowercase() == "xml") {
                val compact = file.readText()
                    .replace(Regex("""<!--[\s\S]*?-->"""), "")
                    .filterNot(Char::isWhitespace)
                if (
                    "android.app.NativeActivity" in compact ||
                    "android.app.lib_name" in compact
                ) {
                    violations +=
                        "${artifactPath.get()}:${file.path}: native Android loading path is forbidden"
                }
            }
            violations
        }
    }
}
