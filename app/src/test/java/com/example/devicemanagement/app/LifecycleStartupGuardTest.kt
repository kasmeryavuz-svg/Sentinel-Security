package com.example.devicemanagement.app

import com.example.devicemanagement.recovery.RecoveryInspectionProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class LifecycleStartupGuardTest {
    @Test
    fun `app startup reconstructs services and never submits or mutates DPM`() {
        val app = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/app/DeviceManagementApp.kt",
        ).readText()
        val container = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/app/AppContainer.kt",
        ).readText()

        assertTrue(app.contains("AppContainer("))
        assertTrue(container.contains("DeviceManagement.create"))
        assertTrue(container.contains("recoveryInspection"))
        assertFalse(app.contains(".submit("))
        assertFalse(container.contains(".submit("))
        assertFalse(app.contains("DevicePolicyManager"))
        assertFalse(container.contains("DevicePolicyManager"))
        assertFalse(app.contains("setCameraDisabled"))
        assertFalse(container.contains("setCameraDisabled"))
        assertFalse(app.contains("setScreenCaptureDisabled"))
        assertFalse(container.contains("setScreenCaptureDisabled"))
        assertFalse(container.contains("setStatusBarDisabled"))
        assertFalse(app.contains("ApprovalAuthority"))
        assertFalse(container.contains("ApprovalAuthority"))
        assertFalse(app.contains("ActionExecutor"))
        assertFalse(container.contains("ActionExecutor"))
        assertFalse(app.contains("BOOT_COMPLETED"))
        assertFalse(container.contains("BOOT_COMPLETED"))
        assertFalse(app.contains("wipeData"))
        assertFalse(container.contains("wipeData"))
        assertFalse(app.contains("wipeDevice"))
        assertFalse(container.contains("wipeDevice"))
        assertFalse(app.contains("AuditRecoveryInspector"))
        assertFalse(container.contains("DeviceManagementRecoveryInspectionFactory"))
        assertFalse(app.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(container.contains("FutureDestructiveRealChainBoundary"))
        assertFalse(app.contains("assembleAndHandoff"))
        assertFalse(container.contains("assembleAndHandoff"))
        assertFalse(app.contains("Checkpoint19ADecision"))
        assertFalse(container.contains("Checkpoint19ADecision"))
        assertFalse(app.contains("Checkpoint19BDecision"))
        assertFalse(container.contains("Checkpoint19BDecision"))
        assertFalse(app.contains("Checkpoint19CDecision"))
        assertFalse(container.contains("Checkpoint19CDecision"))
        assertFalse(app.contains("Checkpoint19DDecision"))
        assertFalse(container.contains("Checkpoint19DDecision"))
        assertFalse(app.contains("Checkpoint19EDecision"))
        assertFalse(container.contains("Checkpoint19EDecision"))
        assertFalse(app.contains("Checkpoint19FDecision"))
        assertFalse(container.contains("Checkpoint19FDecision"))
        assertFalse(app.contains("ProductionDestructiveRealChainOrchestrator"))
        assertFalse(container.contains("ProductionDestructiveRealChainOrchestrator"))
        assertFalse(app.contains("assembleAlreadyBoundDeviceFactoryReset"))
        assertFalse(container.contains("assembleAlreadyBoundDeviceFactoryReset"))
        assertFalse(app.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(container.contains("AndroidFutureDestructiveExecutor"))
        assertFalse(app.contains("AuthorizedFactoryResetPort"))
        assertFalse(container.contains("AuthorizedFactoryResetPort"))
    }

    @Test
    fun `source manifests declare no BOOT_COMPLETED receiver`() {
        val manifests = listOf(
            File(requireNotNull(System.getProperty("appMainSourceDir")), "AndroidManifest.xml"),
        )
        manifests.forEach { file ->
            val document = DocumentBuilderFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isNamespaceAware = true
                isExpandEntityReferences = false
            }.newDocumentBuilder().parse(file)
            val actions = document.getElementsByTagName("action")
            val names = (0 until actions.length).map { index ->
                (actions.item(index) as org.w3c.dom.Element)
                    .getAttributeNS("http://schemas.android.com/apk/res/android", "name")
            }
            assertFalse(names.contains("android.intent.action.BOOT_COMPLETED"))
            assertFalse(names.contains("android.intent.action.LOCKED_BOOT_COMPLETED"))
            assertFalse(names.contains("android.intent.action.QUICKBOOT_POWERON"))
        }
    }

    @Test
    fun `recovery inspection API is read-only`() {
        val forbidden = setOf(
            "append",
            "insert",
            "update",
            "delete",
            "clear",
            "execute",
            "approve",
            "retry",
            "submit",
        )
        RecoveryInspectionProvider::class.java.methods
            .filter { Modifier.isPublic(it.modifiers) }
            .forEach { method ->
                assertFalse(forbidden.contains(method.name))
            }
    }
}
