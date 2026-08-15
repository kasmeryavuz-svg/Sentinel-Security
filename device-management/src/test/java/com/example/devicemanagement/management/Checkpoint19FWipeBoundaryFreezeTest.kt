package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class Checkpoint19FWipeBoundaryFreezeTest {
    @Test
    fun `DeviceAdmin metadata remains exactly disable-camera and wipe-data after 19F`() {
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
        assertFalse("reset-password" in declared)
        assertFalse("force-lock" in declared)
        assertFalse("limit-password" in declared)
        assertFalse("watch-login" in declared)
        assertFalse("expire-password" in declared)
        assertFalse("encrypted-storage" in declared)
        assertFalse("disable-keyguard-features" in declared)
    }

    @Test
    fun `implementation sources retain production chain without a 19F trigger`() {
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
                    assertFalse(file.path, text.contains("wipeDevice(1)"))
                    assertFalse(file.path, text.contains("WIPE_SILENTLY"))
                    assertFalse(file.path, text.contains("WIPE_RESET_PROTECTION_DATA"))
                    assertFalse(file.path, text.contains("WIPE_EUICC"))
                } else {
                    assertFalse(file.path, text.contains("wipeDevice"))
                    assertFalse(file.path, text.contains("wipeData"))
                }
            }
        val sources = sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(sources.contains("assembleAndHandoff"))
        assertFalse(sources.contains("assembleAlreadyBoundDeviceFactoryReset"))
        assertFalse(sources.contains("Checkpoint19FDecision"))
        assertFalse(sources.contains("Checkpoint19EDecision"))
        assertFalse(sources.contains("Checkpoint19DDecision"))
        assertFalse(sources.contains("ProductionDestructiveRealChainOrchestrator"))
        assertFalse(sources.contains("ProductionDestructiveHumanConfirmationSource"))
        assertFalse(sources.contains("issueFromTrustedConfirmationSource"))
        assertFalse(sources.contains("issueFromTrustedValidationSource"))
        assertFalse(sources.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(sources.contains("destructive-validation-candidate.txt"))
        assertFalse(sources.contains("DestructiveValidationCandidateEvidence"))
        assertTrue(sources.contains("ProductionDestructiveRealChain.retainForProduction"))
        assertTrue(sources.contains("AndroidDestructiveSafetyPersistence.issueRuntimeDurability"))
        assertTrue(sources.contains("setScreenCaptureDisabled"))
        assertTrue(sources.contains("setCameraDisabled"))
        assertTrue(sources.contains("setStatusBarDisabled"))
    }

    @Test
    fun `19F freeze tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            File(requireNotNull(System.getProperty("deviceManagementSourceDir"))),
            "../test/java/com/example/devicemanagement/management/Checkpoint19FWipeBoundaryFreezeTest.kt",
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
