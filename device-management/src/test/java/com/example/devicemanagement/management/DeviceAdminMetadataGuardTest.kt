package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class DeviceAdminMetadataGuardTest {
    @Test
    fun `DeviceAdmin metadata declares only approved policy capabilities`() {
        val metadataFile = File(
            requireNotNull(System.getProperty("deviceAdminMetadataFile")),
        )
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(metadataFile)
        val root = document.documentElement
        assertEquals("device-admin", root.tagName)

        val usesPoliciesElements = root.childNodes.asElements()
            .filter { it.tagName == "uses-policies" }
        assertEquals(1, usesPoliciesElements.size)

        val declaredCapabilities = usesPoliciesElements.single()
            .childNodes
            .asElements()
            .map { it.tagName }
            .toSet()
        val explicitlyForbidden = setOf(
            "wipe-data",
            "reset-password",
            "force-lock",
            "limit-password",
            "watch-login",
            "expire-password",
            "encrypted-storage",
            "disable-keyguard-features",
        )

        assertTrue(
            "Destructive or unapproved DeviceAdmin capabilities declared: " +
                declaredCapabilities.intersect(explicitlyForbidden),
            declaredCapabilities.intersect(explicitlyForbidden).isEmpty(),
        )
        assertEquals(
            "Only the camera capability is approved for current features",
            setOf("disable-camera"),
            declaredCapabilities,
        )
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length)
            .map(::item)
            .filterIsInstance<Element>()
    }
}
