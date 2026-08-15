package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Current device-management wipe-boundary invariants.
 *
 * Checkpoint-specific historical documentation stays in thin app-module
 * 19G/19H/19J freeze tests. This class must not be cloned per checkpoint.
 */
class CurrentProductionWipeBoundaryInvariantTest {
    @Test
    fun `DeviceAdmin metadata remains exactly disable-camera and wipe-data`() {
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
    fun `exactly one production wipeDevice call uses literal zero flags`() {
        val sourceRoot = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java",
        )
        val productionFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
        val wipeDeviceFiles = productionFiles.filter { it.readText().contains("wipeDevice") }
        assertEquals(
            listOf("AndroidDevicePolicyFactoryResetService.kt"),
            wipeDeviceFiles.map { it.name },
        )
        val origin = wipeDeviceFiles.single().readText()
        assertTrue(origin.contains("wipeDevice(0)"))
        assertEquals(1, Regex("""wipeDevice\s*\(\s*0\s*\)""").findAll(origin).count())
        assertFalse(origin.contains("wipeDevice(1)"))
        assertFalse(origin.contains("wipeData"))
        assertFalse(origin.contains("WIPE_SILENTLY"))
        assertFalse(origin.contains("WIPE_RESET_PROTECTION_DATA"))
        assertFalse(origin.contains("WIPE_EUICC"))
        productionFiles.filter { it.name != "AndroidDevicePolicyFactoryResetService.kt" }
            .forEach { file ->
                val text = file.readText()
                assertFalse(file.path, text.contains("wipeDevice"))
                assertFalse(file.path, text.contains("wipeData"))
            }
    }

    @Test
    fun `implementation sources retain the production chain without checkpoint or proof types`() {
        val sources = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java",
        ).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .joinToString("\n") { it.readText() }

        assertTrue(sources.contains("ProductionDestructiveRealChain.retainForProduction"))
        listOf(
            "Checkpoint19DDecision",
            "Checkpoint19EDecision",
            "Checkpoint19FDecision",
            "Checkpoint19GDecision",
            "Checkpoint19HDecision",
            "Checkpoint19JDecision",
            "Checkpoint19PGovernanceObservation",
            "assembleAndHandoff",
            "assembleAlreadyBoundDeviceFactoryReset",
            "ProductionDestructiveRealChainOrchestrator",
            "ProductionDestructiveHumanConfirmationSource",
            "issueFromTrustedConfirmationSource",
            "issueFromTrustedValidationSource",
            "FutureDestructiveRealChainBoundary",
            "DESTRUCTIVE_VALIDATION_BUILD_PURPOSE",
            "checkUnsignedDisposableValidationBuildPurposeEvidence",
            "checkDestructiveSigningCeremonyPreparation",
            "DestructiveValidationCandidateEvidence",
            "DestructiveSigningCeremonyPreparation",
            "ProductionDistributionSigningGate",
            "ValidationOnlySigningGate",
            "inspectWriteAndAssertCleanup",
            "destructive-validation-candidate.txt",
        ).forEach { token ->
            assertFalse(token, sources.contains(token))
        }
    }

    @Test
    fun `current invariant tests do not invoke the platform whole-device call`() {
        val thisFile = File(
            File(requireNotNull(System.getProperty("deviceManagementSourceDir"))),
            "../test/java/com/example/devicemanagement/management/" +
                "CurrentProductionWipeBoundaryInvariantTest.kt",
        ).canonicalFile.readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("import android.app.admin." + "DevicePolicyManager"))
        assertFalse(thisFile.contains("Checkpoint19P" + "WipeBoundaryFreezeTest"))
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length)
            .map(::item)
            .filterIsInstance<Element>()
    }
}
