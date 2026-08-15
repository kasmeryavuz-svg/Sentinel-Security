package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class Checkpoint19HWipeBoundaryFreezeTest {
    @Test
    fun `DeviceAdmin metadata remains exactly disable-camera and wipe-data after 19H`() {
        val metadataFile = File(requireNotNull(System.getProperty("deviceAdminMetadataFile")))
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(metadataFile)
        val declared = document.documentElement
            .childNodes
            .asElements()
            .single { it.tagName == "uses-policies" }
            .childNodes
            .asElements()
            .map { it.tagName }
            .toSet()

        assertEquals(setOf("disable-camera", "wipe-data"), declared)
    }

    @Test
    fun `implementation sources retain production chain without a 19H trigger`() {
        val sourceRoot = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java",
        )
        sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                val text = file.readText()
                if (file.name == "AndroidDevicePolicyFactoryResetService.kt") {
                    assertTrue(file.path, text.contains("wipeDevice(0)"))
                    assertFalse(file.path, text.contains("wipeData"))
                } else {
                    assertFalse(file.path, text.contains("wipeDevice"))
                    assertFalse(file.path, text.contains("wipeData"))
                }
            }
        val sources = sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(sources.contains("Checkpoint19HDecision"))
        assertFalse(sources.contains("Checkpoint19JDecision"))
        assertFalse(sources.contains("DestructiveSigningCeremonyPreparation"))
        assertFalse(sources.contains("checkDestructiveSigningCeremonyPreparation"))
        assertTrue(sources.contains("ProductionDestructiveRealChain.retainForProduction"))
    }

    @Test
    fun `19H freeze tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            File(requireNotNull(System.getProperty("deviceManagementSourceDir"))),
            "../test/java/com/example/devicemanagement/management/Checkpoint19HWipeBoundaryFreezeTest.kt",
        ).canonicalFile.readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length)
            .map(::item)
            .filterIsInstance<Element>()
    }
}
