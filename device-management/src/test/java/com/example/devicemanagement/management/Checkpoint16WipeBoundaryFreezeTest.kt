package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class Checkpoint16WipeBoundaryFreezeTest {
    @Test
    fun `DeviceAdmin metadata remains exactly disable-camera`() {
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
        assertTrue("wipe-data" in declared)
    }

    @Test
    fun `production implementation sources do not invoke destructive DPM APIs`() {
        assertFactoryResetWipeDeviceOrigin(File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java",
        ))
        val sources = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(sources.contains("WIPE_EXTERNAL_STORAGE"))
        assertFalse(sources.contains("WIPE_RESET_PROTECTION_DATA"))
        assertFalse(sources.contains("WIPE_EUICC"))
        assertFalse(sources.contains("WIPE_SILENTLY"))
        assertFalse(sources.contains("USES_POLICY_WIPE_DATA"))
        assertTrue(sources.contains("setScreenCaptureDisabled"))
        assertTrue(sources.contains("setCameraDisabled"))
        assertTrue(sources.contains("setStatusBarDisabled"))
    }

    @Test
    fun `verified mutation remain exactly the three reversible variants`() {
        val source = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java/com/example/devicemanagement/management/VerifiedPolicyMutation.kt",
        ).readText()
        val variants = Regex("data class ([A-Za-z]+)\\(")
            .findAll(
                source.substringAfter("internal sealed interface VerifiedPolicyMutation")
                    .substringBefore("internal sealed interface PolicyMutation"),
            )
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(setOf("ScreenCapture", "Camera", "StatusBar"), variants)
        assertFalse(source.contains("Wipe"))
        assertFalse(source.contains("wipeData"))
        assertFalse(source.contains("wipeDevice"))
    }

    private fun assertFactoryResetWipeDeviceOrigin(sourceRoot: File) {
        sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                val text = file.readText()
                if (file.name == "AndroidDevicePolicyFactoryResetService.kt") {
                    assertTrue(file.path, text.contains("wipeDevice(0)"))
                    assertFalse(file.path, text.contains("wipeData"))
                    assertFalse(file.path, text.contains("WIPE_SILENTLY"))
                    assertFalse(file.path, text.contains("WIPE_RESET_PROTECTION_DATA"))
                    assertFalse(file.path, text.contains("WIPE_EUICC"))
                    assertFalse(file.path, text.contains("WIPE_EXTERNAL_STORAGE"))
                } else {
                    assertFalse(file.path, text.contains("wipeDevice"))
                    assertFalse(file.path, text.contains("wipeData"))
                }
            }
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length)
            .map(::item)
            .filterIsInstance<Element>()
    }
}
