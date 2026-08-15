package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class DeviceAdminReceiverProvisioningGuardTest {
    @Test
    fun `receiver completion is log-only and does not mutate policy`() {
        val source = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
            "java/com/example/devicemanagement/management/SentinelDeviceAdminReceiver.kt",
        ).readText()

        assertTrue(source.contains("onProfileProvisioningComplete"))
        assertTrue(source.contains("policy_mutation=false"))
        assertTrue(source.contains("persistence=false"))
        assertFalse(source.contains("DevicePolicyManager"))
        assertFalse(source.contains("getManager("))
        assertFalse(source.contains("setScreenCaptureDisabled"))
        assertFalse(source.contains("setCameraDisabled"))
        assertFalse(source.contains("setStatusBarDisabled"))
        assertFalse(source.contains("wipeData"))
        assertFalse(source.contains("wipeDevice"))
        assertFalse(source.contains("lockNow"))
        assertFalse(source.contains("reboot"))
        assertFalse(source.contains("resetPassword"))
        assertFalse(source.contains("removeUser"))
        assertFalse(source.contains("setLockTaskPackages"))
        assertFalse(source.contains("SharedPreferences"))
        assertFalse(source.contains("openFileOutput"))
        assertFalse(source.contains("VerifiedPolicyMutationExecutor"))
        assertFalse(source.contains("SensitiveActionController"))
        assertFalse(source.contains("BOOT_COMPLETED"))
        assertFalse(source.contains("ApprovalAuthority"))
        assertFalse(source.contains("ActionExecutor"))
    }

    @Test
    fun `receiver manifest keeps BIND_DEVICE_ADMIN and only approved actions`() {
        val manifest = parse(
            File(
                requireNotNull(System.getProperty("deviceManagementSourceDir")),
                "AndroidManifest.xml",
            ),
        )
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val receivers = manifest.getElementsByTagName("receiver").asElements()
        assertEquals(1, receivers.size)
        val receiver = receivers.single()
        assertEquals(
            ".SentinelDeviceAdminReceiver",
            receiver.getAttributeNS(androidNamespace, "name"),
        )
        assertEquals("true", receiver.getAttributeNS(androidNamespace, "exported"))
        assertEquals(
            "android.permission.BIND_DEVICE_ADMIN",
            receiver.getAttributeNS(androidNamespace, "permission"),
        )
        assertEquals(
            listOf(
                "android.app.action.DEVICE_ADMIN_ENABLED",
                "android.app.action.PROFILE_PROVISIONING_COMPLETE",
            ),
            receiver.getElementsByTagName("action").asElements().map {
                it.getAttributeNS(androidNamespace, "name")
            },
        )
        val metadata = receiver.getElementsByTagName("meta-data").asElements()
        assertEquals(1, metadata.size)
        assertEquals(
            "android.app.device_admin",
            metadata.single().getAttributeNS(androidNamespace, "name"),
        )
        assertEquals(
            "@xml/device_admin_receiver",
            metadata.single().getAttributeNS(androidNamespace, "resource"),
        )
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
