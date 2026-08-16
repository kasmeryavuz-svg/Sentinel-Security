import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Aapt2LocatorTest {
    @Test
    fun `windows layout containing only aapt2 exe resolves`() {
        val buildTools = buildToolsDir("win-only-exe")
        val exe = File(buildTools, "aapt2.exe").apply { writeText("fake") }
        val resolved = Aapt2Locator.resolveFromBuildTools(buildTools, osName = "Windows 10")
        assertEquals(exe.canonicalFile, resolved?.canonicalFile)
        assertTrue(resolved!!.isFile)
    }

    @Test
    fun `unix layout containing only aapt2 resolves`() {
        val buildTools = buildToolsDir("unix-only")
        val unix = File(buildTools, "aapt2").apply { writeText("#!/bin/sh\n") }
        val resolved = Aapt2Locator.resolveFromBuildTools(buildTools, osName = "Linux")
        assertEquals(unix.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `windows prefers exe when both names exist`() {
        val buildTools = buildToolsDir("both-windows")
        val unix = File(buildTools, "aapt2").apply { writeText("unix") }
        val exe = File(buildTools, "aapt2.exe").apply { writeText("windows") }
        val resolved = Aapt2Locator.resolveFromBuildTools(buildTools, osName = "Windows 11")
        assertEquals(exe.canonicalFile, resolved?.canonicalFile)
        assertTrue(unix.isFile)
    }

    @Test
    fun `unix prefers extensionless binary when both names exist`() {
        val buildTools = buildToolsDir("both-unix")
        val unix = File(buildTools, "aapt2").apply { writeText("unix") }
        val exe = File(buildTools, "aapt2.exe").apply { writeText("windows") }
        val resolved = Aapt2Locator.resolveFromBuildTools(buildTools, osName = "Mac OS X")
        assertEquals(unix.canonicalFile, resolved?.canonicalFile)
        assertTrue(exe.isFile)
    }

    @Test
    fun `preferred build-tools version is selected before newer fallback`() {
        val sdk = sdkDir("preferred-first")
        val preferred = File(sdk, "build-tools/35.0.0").apply { mkdirs() }
        val newer = File(sdk, "build-tools/36.0.0").apply { mkdirs() }
        val preferredBinary = File(preferred, "aapt2").apply { writeText("preferred") }
        File(newer, "aapt2").writeText("newer")
        val resolved = Aapt2Locator.locate(sdk, osName = "Linux")
        assertEquals(preferredBinary.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `fallback build-tools version is used when preferred is missing`() {
        val sdk = sdkDir("fallback-only")
        val fallback = File(sdk, "build-tools/34.0.0").apply { mkdirs() }
        val fallbackBinary = File(fallback, "aapt2").apply { writeText("fallback") }
        val resolved = Aapt2Locator.locate(sdk, osName = "Linux")
        assertEquals(fallbackBinary.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `windows fallback discovers exe in a non-preferred build-tools directory`() {
        val sdk = sdkDir("fallback-exe")
        val fallback = File(sdk, "build-tools/34.0.0").apply { mkdirs() }
        val exe = File(fallback, "aapt2.exe").apply { writeText("windows-fallback") }
        val resolved = Aapt2Locator.locate(sdk, osName = "Windows 10")
        assertEquals(exe.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `missing executable returns null`() {
        val empty = buildToolsDir("empty")
        val sdk = sdkDir("empty-sdk")
        File(sdk, "build-tools").mkdirs()
        assertNull(Aapt2Locator.resolveFromBuildTools(empty, osName = "Linux"))
        assertNull(Aapt2Locator.resolveFromBuildTools(empty, osName = "Windows 10"))
        assertNull(Aapt2Locator.resolve(null, osName = "Linux"))
        assertNull(Aapt2Locator.resolve("", osName = "Linux"))
        assertNull(Aapt2Locator.locate(sdk, osName = "Linux"))
        assertNull(Aapt2Locator.locate(null, osName = "Linux"))
        val directoryNamedAapt2 = File(empty, "aapt2").apply { mkdirs() }
        assertTrue(directoryNamedAapt2.isDirectory)
        assertNull(Aapt2Locator.resolveFromBuildTools(empty, osName = "Linux"))
    }

    @Test
    fun `configured extensionless path falls back to sibling exe`() {
        val buildTools = buildToolsDir("sibling-exe")
        val missingUnix = File(buildTools, "aapt2")
        val exe = File(buildTools, "aapt2.exe").apply { writeText("windows") }
        val resolved = Aapt2Locator.resolve(missingUnix.absolutePath, osName = "Windows 10")
        assertEquals(exe.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `explicitly selected build-tools directory wins over later discovery`() {
        val sdk = sdkDir("selected-dir")
        val selected = File(sdk, "build-tools/34.0.0").apply { mkdirs() }
        val preferred = File(sdk, "build-tools/35.0.0").apply { mkdirs() }
        val selectedBinary = File(selected, "aapt2").apply { writeText("selected") }
        File(preferred, "aapt2").writeText("preferred")
        val resolved = Aapt2Locator.resolveFromBuildTools(selected, osName = "Linux")
            ?: Aapt2Locator.locate(sdk, osName = "Linux")
        assertEquals(selectedBinary.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `inspectors and app gradle use the shared locator instead of a hardcoded unix path`() {
        val inspectors = File("src/main/kotlin/DestructiveValidationCandidateInspectors.kt")
            .readText()
        val appGradle = File("../app/build.gradle.kts").readText()
        assertTrue(inspectors.contains("Aapt2Locator.locate"))
        assertTrue(inspectors.contains("fun locateAapt2"))
        assertFalse(inspectors.contains("File(it, \"aapt2\")"))
        assertFalse(inspectors.contains("File(it, \"aapt2.exe\")"))
        assertTrue(appGradle.contains("Aapt2Locator.resolveFromBuildTools"))
        assertTrue(appGradle.contains("Aapt2Locator.locate"))
        assertFalse(appGradle.contains("\${android.buildToolsVersion}/aapt2"))
        val metadataBlock = appGradle
            .substringAfter("check\${capitalized}EffectiveDeviceAdminMetadata")
            .substringBefore("check\${capitalized}ProductionBytecodePolicy")
        assertFalse(metadataBlock.contains("/aapt2\""))
        assertFalse(metadataBlock.contains("/aapt2,"))
        assertFalse(metadataBlock.contains("/aapt2)"))
        val sdk = sdkDir("inspector-delegate")
        val preferred = File(sdk, "build-tools/35.0.0").apply { mkdirs() }
        val exe = File(preferred, "aapt2.exe").apply { writeText("windows-only") }
        val resolved = DestructiveValidationCandidateInspectors.locateAapt2(sdk)
        assertEquals(exe.canonicalFile, resolved?.canonicalFile)
    }

    private fun buildToolsDir(label: String): File {
        return File.createTempFile("aapt2-$label-", ".dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }

    private fun sdkDir(label: String): File {
        return File.createTempFile("android-sdk-$label-", ".dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }
}
