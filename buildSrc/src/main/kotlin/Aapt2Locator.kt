import java.io.File

/**
 * Cross-platform resolver for the official Android build-tools aapt2
 * executable. Windows prefers [WINDOWS_NAME]; Linux and macOS prefer
 * [UNIX_NAME]. Callers may pass an explicit OS name so tests do not
 * mutate global system properties.
 *
 * Missing aapt2 remains unresolved. This object never signs, never
 * reads signing secrets, and never inspects an APK by itself.
 */
object Aapt2Locator {
    const val PREFERRED_BUILD_TOOLS_VERSION = "35.0.0"
    const val UNIX_NAME = "aapt2"
    const val WINDOWS_NAME = "aapt2.exe"

    fun defaultOsName(): String = System.getProperty("os.name").orEmpty()

    fun isWindows(osName: String): Boolean {
        return osName.lowercase().contains("win")
    }

    fun candidateNames(osName: String): List<String> {
        return if (isWindows(osName)) {
            listOf(WINDOWS_NAME, UNIX_NAME)
        } else {
            listOf(UNIX_NAME, WINDOWS_NAME)
        }
    }

    fun resolveFromBuildTools(
        buildToolsDir: File,
        osName: String = defaultOsName(),
    ): File? {
        if (!buildToolsDir.isDirectory) {
            return null
        }
        return candidateNames(osName)
            .map { File(buildToolsDir, it) }
            .firstOrNull(::isExistingRegularFile)
    }

    fun resolve(
        configuredPath: String?,
        osName: String = defaultOsName(),
    ): File? {
        if (configuredPath.isNullOrBlank()) {
            return null
        }
        val configured = File(configuredPath)
        if (isExistingRegularFile(configured)) {
            return configured
        }
        if (configured.isDirectory) {
            return resolveFromBuildTools(configured, osName)
        }
        val parent = configured.parentFile
        if (parent != null) {
            resolveFromBuildTools(parent, osName)?.let { return it }
        }
        return siblingFallback(configured, osName)
    }

    fun locate(
        androidSdkDir: File?,
        preferredBuildToolsVersion: String = PREFERRED_BUILD_TOOLS_VERSION,
        osName: String = defaultOsName(),
    ): File? {
        if (androidSdkDir == null || !androidSdkDir.isDirectory) {
            return null
        }
        val preferred = File(androidSdkDir, "build-tools/$preferredBuildToolsVersion")
        resolveFromBuildTools(preferred, osName)?.let { return it }
        val all = File(androidSdkDir, "build-tools")
        if (!all.isDirectory) {
            return null
        }
        return all.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?.firstNotNullOfOrNull { resolveFromBuildTools(it, osName) }
    }

    private fun siblingFallback(configured: File, osName: String): File? {
        val parent = configured.parentFile ?: return null
        val name = configured.name
        val alternate = when {
            name.equals(UNIX_NAME, ignoreCase = true) -> File(parent, WINDOWS_NAME)
            name.equals(WINDOWS_NAME, ignoreCase = true) -> File(parent, UNIX_NAME)
            else -> return resolveFromBuildTools(parent, osName)
        }
        return alternate.takeIf(::isExistingRegularFile)
    }

    private fun isExistingRegularFile(file: File): Boolean {
        return file.isFile && !file.isDirectory
    }
}
