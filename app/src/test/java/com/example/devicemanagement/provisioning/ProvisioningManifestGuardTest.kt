package com.example.devicemanagement.provisioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class ProvisioningManifestGuardTest {
    @Test
    fun `source manifest declares exact protected provisioning activities`() {
        val manifest = parse(File(requireNotNull(System.getProperty("appMainSourceDir")), "AndroidManifest.xml"))
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val activities = manifest.getElementsByTagName("activity").asElements()

        val modeActivity = activities.single {
            it.getAttributeNS(androidNamespace, "name") ==
                ".provisioning.GetProvisioningModeActivity"
        }
        val complianceActivity = activities.single {
            it.getAttributeNS(androidNamespace, "name") ==
                ".provisioning.AdminPolicyComplianceActivity"
        }
        val launcher = activities.single {
            it.getAttributeNS(androidNamespace, "name") == ".ui.MainActivity"
        }

        assertProvisioningActivity(
            modeActivity,
            androidNamespace,
            "android.app.action.GET_PROVISIONING_MODE",
        )
        assertProvisioningActivity(
            complianceActivity,
            androidNamespace,
            "android.app.action.ADMIN_POLICY_COMPLIANCE",
        )

        assertEquals("", launcher.getAttributeNS(androidNamespace, "permission"))
        assertEquals(
            listOf("android.intent.action.MAIN"),
            launcher.actions(androidNamespace),
        )
        assertEquals(
            listOf("android.intent.category.LAUNCHER"),
            launcher.categories(androidNamespace),
        )

        val allActions = manifest.getElementsByTagName("action").asElements()
            .map { it.getAttributeNS(androidNamespace, "name") }
        assertFalse(allActions.contains("android.app.action.PROVISION_MANAGED_DEVICE"))
        assertFalse(allActions.contains("android.app.action.PROVISION_MANAGED_PROFILE"))
        assertEquals(3, activities.size)
    }

    @Test
    fun `application backup remains disabled`() {
        val manifest = parse(File(requireNotNull(System.getProperty("appMainSourceDir")), "AndroidManifest.xml"))
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val application = manifest.getElementsByTagName("application").asElements().single()
        assertEquals("false", application.getAttributeNS(androidNamespace, "allowBackup"))
    }

    @Test
    fun `enrollment diagnostics remain static read-only copy`() {
        val strings = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "res/values/strings.xml",
        ).readText()
        assertTrue(strings.contains("label_enrollment_contract"))
        assertTrue(strings.contains("Fully-managed Device Owner provisioning contract"))
        assertTrue(strings.contains("fully-managed Device Owner only"))
        assertTrue(strings.contains("Sentinel does not host or upload APKs"))
        assertFalse(strings.contains("ACTION_PROVISION_MANAGED_DEVICE"))
    }

    private fun assertProvisioningActivity(
        activity: Element,
        androidNamespace: String,
        expectedAction: String,
    ) {
        assertEquals("true", activity.getAttributeNS(androidNamespace, "exported"))
        assertEquals(
            "android.permission.BIND_DEVICE_ADMIN",
            activity.getAttributeNS(androidNamespace, "permission"),
        )
        assertEquals(listOf(expectedAction), activity.actions(androidNamespace))
        assertEquals(
            listOf("android.intent.category.DEFAULT"),
            activity.categories(androidNamespace),
        )
        assertFalse(
            activity.categories(androidNamespace)
                .contains("android.intent.category.LAUNCHER"),
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

    private fun Element.actions(androidNamespace: String): List<String> {
        return getElementsByTagName("action").asElements().map {
            it.getAttributeNS(androidNamespace, "name")
        }
    }

    private fun Element.categories(androidNamespace: String): List<String> {
        return getElementsByTagName("category").asElements().map {
            it.getAttributeNS(androidNamespace, "name")
        }
    }
}
