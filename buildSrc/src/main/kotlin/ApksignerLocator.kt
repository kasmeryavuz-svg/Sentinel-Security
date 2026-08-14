import java.io.File

object ApksignerLocator {
    fun resolve(configuredPath: String?): File? {
        if (configuredPath.isNullOrBlank()) {
            return null
        }
        val configured = File(configuredPath)
        if (configured.isFile) {
            return configured
        }
        if (configured.isDirectory) {
            return resolveFromBuildTools(configured)
        }
        val parent = configured.parentFile
        if (parent != null) {
            resolveFromBuildTools(parent)?.let { return it }
        }
        return siblingFallback(configured)
    }

    fun resolveFromBuildTools(buildToolsDir: File): File? {
        if (!buildToolsDir.isDirectory) {
            return null
        }
        val unix = File(buildToolsDir, "apksigner")
        val windows = File(buildToolsDir, "apksigner.bat")
        val windowsOs = System.getProperty("os.name")
            .orEmpty()
            .lowercase()
            .contains("win")
        val ordered = if (windowsOs) {
            listOf(windows, unix)
        } else {
            listOf(unix, windows)
        }
        return ordered.firstOrNull { it.isFile }
    }

    fun commandLine(apksigner: File, vararg args: String): List<String> {
        return if (apksigner.name.endsWith(".bat", ignoreCase = true)) {
            listOf("cmd.exe", "/c", apksigner.absolutePath) + args
        } else {
            listOf(apksigner.absolutePath) + args
        }
    }

    private fun siblingFallback(configured: File): File? {
        val parent = configured.parentFile ?: return null
        val name = configured.name
        return when {
            name.equals("apksigner", ignoreCase = true) ->
                File(parent, "apksigner.bat").takeIf { it.isFile }
            name.equals("apksigner.bat", ignoreCase = true) ->
                File(parent, "apksigner").takeIf { it.isFile }
            else -> null
        }
    }
}
