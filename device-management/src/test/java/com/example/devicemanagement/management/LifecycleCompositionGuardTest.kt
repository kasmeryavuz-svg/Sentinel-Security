package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class LifecycleCompositionGuardTest {
    @Test
    fun `composition reconstructs services and wires read-only recovery`() {
        val composition = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java/com/example/devicemanagement/management/DeviceManagementSensitiveActions.kt",
        ).readText()

        assertTrue(composition.contains("DeviceManagementRecoveryInspectionFactory.create"))
        assertTrue(composition.contains("override val recoveryInspection = recoveryInspection"))
        assertTrue(composition.contains("DeviceManagementSensitiveActionControllerFactory.create"))
        assertFalse(composition.contains(".submit("))
        assertFalse(composition.contains("BOOT_COMPLETED"))
        assertFalse(composition.contains("setCameraDisabled"))
        assertFalse(composition.contains("setScreenCaptureDisabled"))
        assertFalse(composition.contains("setStatusBarDisabled"))
        assertFalse(composition.contains("createFailSafeController"))
        assertFalse(composition.contains("SensitiveActionRegistry.failSafe"))
        assertFalse(composition.contains("SafeMockWipeAction"))
        assertFalse(composition.contains("MOCK_WIPE"))
        assertFalse(composition.contains("wipeData"))
        assertFalse(composition.contains("wipeDevice"))
        assertFalse(composition.contains("ApprovalAuthority"))
        assertFalse(composition.contains("ActionExecutor"))
        assertFalse(composition.contains("DestructiveArmingAuthority"))
        assertFalse(composition.contains("DestructiveAuthorizationAuthority"))
        assertFalse(composition.contains("DestructiveAttemptAdmissionAuthority"))
        assertFalse(composition.contains("DestructiveCapability"))
        assertFalse(composition.contains("FinalExecutionPermit"))
        assertFalse(composition.contains("SimulatedDestructiveExecutor"))
        assertFalse(composition.contains("Checkpoint17ASimulationSink"))
        assertFalse(composition.contains("DenyOnlyCooldownMarkerStore"))
        assertFalse(composition.contains("RuntimeDestructiveSafetyDurability"))
        assertFalse(composition.contains("issueRuntimeDurability"))
        assertFalse(composition.contains("AndroidDestructiveSafetyPersistence"))
        assertFalse(composition.contains("DestructiveArtifactIdentityAuthority"))
        assertFalse(composition.contains("DestructiveHumanApprovalAuthority"))
        assertFalse(composition.contains("DestructiveHumanConfirmationAuthority"))
        assertFalse(composition.contains("issueChallenge"))
        assertFalse(composition.contains("issueFromTrustedConfirmationSource"))
        assertFalse(composition.contains("issueFromTrustedValidationSource"))
        assertFalse(composition.contains("RuntimeDestructiveSafetyDurabilityMint"))
        assertFalse(composition.contains("TrustedDestructiveArtifactExpectationMint"))
        assertFalse(composition.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(composition.contains("assembleAndHandoff"))
        assertFalse(composition.contains("FutureDestructiveExecutorContract"))
        assertFalse(composition.contains("Checkpoint18Decision"))
    }

    @Test
    fun `implementation manifest has no boot receiver and does not widen DeviceAdmin actions`() {
        val manifest = parse(
            File(
                requireNotNull(System.getProperty("deviceManagementSourceDir")),
                "AndroidManifest.xml",
            ),
        )
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val actions = manifest.getElementsByTagName("action").asElements().map {
            it.getAttributeNS(androidNamespace, "name")
        }
        assertFalse(actions.contains("android.intent.action.BOOT_COMPLETED"))
        assertFalse(actions.contains("android.intent.action.LOCKED_BOOT_COMPLETED"))
        assertEquals(
            listOf(
                "android.app.action.DEVICE_ADMIN_ENABLED",
                "android.app.action.PROFILE_PROVISIONING_COMPLETE",
            ),
            actions,
        )
        assertEquals(1, manifest.getElementsByTagName("receiver").asElements().size)
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isNamespaceAware = true
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(file)

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length).map(::item).filterIsInstance<Element>()
    }
}
