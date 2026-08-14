import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApksignerLocatorTest {
    @Test
    fun `resolves unix apksigner from build-tools`() {
        val buildTools = File.createTempFile("build-tools", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val unix = File(buildTools, "apksigner").apply { writeText("#!/bin/sh\n") }
        val resolved = ApksignerLocator.resolveFromBuildTools(buildTools)
        assertEquals(unix.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `resolves windows apksigner bat when unix binary is absent`() {
        val buildTools = File.createTempFile("build-tools-win", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val bat = File(buildTools, "apksigner.bat").apply { writeText("@echo off\n") }
        val resolved = ApksignerLocator.resolveFromBuildTools(buildTools)
        assertEquals(bat.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `configured unix path falls back to sibling bat`() {
        val buildTools = File.createTempFile("build-tools-fallback", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val missingUnix = File(buildTools, "apksigner")
        val bat = File(buildTools, "apksigner.bat").apply { writeText("@echo off\n") }
        val resolved = ApksignerLocator.resolve(missingUnix.absolutePath)
        assertEquals(bat.canonicalFile, resolved?.canonicalFile)
    }

    @Test
    fun `missing verifier resolves to null`() {
        val empty = File.createTempFile("build-tools-empty", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        assertNull(ApksignerLocator.resolveFromBuildTools(empty))
        assertNull(ApksignerLocator.resolve(null))
        assertNull(ApksignerLocator.resolve(""))
        assertNull(ApksignerLocator.resolve(File(empty, "apksigner").absolutePath))
    }

    @Test
    fun `windows command line uses cmd and bat`() {
        val bat = File.createTempFile("apksigner", ".bat")
        val command = ApksignerLocator.commandLine(bat, "verify", "--print-certs", "app.apk")
        assertEquals("cmd.exe", command.first())
        assertTrue(command.contains("/c"))
        assertTrue(command.contains(bat.absolutePath))
        assertTrue(command.contains("verify"))
    }

    @Test
    fun `unix command line invokes apksigner directly`() {
        val unix = File.createTempFile("apksigner", "")
        val command = ApksignerLocator.commandLine(unix, "verify", "app.apk")
        assertEquals(listOf(unix.absolutePath, "verify", "app.apk"), command)
    }
}
