package com.example.devicemanagement.release

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class ReleaseHardeningGuardTest {
    @Test
    fun `source manifest hardens backup network and cleartext policy`() {
        val manifest = parse(
            File(requireNotNull(System.getProperty("appMainSourceDir")), "AndroidManifest.xml"),
        )
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val application = manifest.getElementsByTagName("application").asElements().single()

        assertEquals("false", application.getAttributeNS(androidNamespace, "allowBackup"))
        assertEquals(
            "@xml/backup_rules",
            application.getAttributeNS(androidNamespace, "fullBackupContent"),
        )
        assertEquals(
            "@xml/data_extraction_rules",
            application.getAttributeNS(androidNamespace, "dataExtractionRules"),
        )
        assertEquals(
            "@xml/network_security_config",
            application.getAttributeNS(androidNamespace, "networkSecurityConfig"),
        )
        assertEquals("false", application.getAttributeNS(androidNamespace, "usesCleartextTraffic"))
        assertFalse(application.getAttributeNS(androidNamespace, "testOnly") == "true")
        assertEquals(0, manifest.getElementsByTagName("profileable").length)
        assertEquals(0, manifest.getElementsByTagName("provider").length)
        assertEquals(0, manifest.getElementsByTagName("uses-permission").length)
        val actions = manifest.getElementsByTagName("action").asElements()
            .map { it.getAttributeNS(androidNamespace, "name") }
        assertFalse(actions.contains("android.permission.INTERNET"))
        assertFalse(actions.contains("android.intent.action.BOOT_COMPLETED"))
        assertFalse(actions.contains("android.intent.action.LOCKED_BOOT_COMPLETED"))
        assertFalse(actions.contains("android.intent.action.QUICKBOOT_POWERON"))
        assertFalse(actions.contains("android.intent.action.VIEW"))
        assertEquals(0, manifest.getElementsByTagName("data").length)
    }

    @Test
    fun `backup extraction and network xml exclude all private data and deny cleartext`() {
        val main = File(requireNotNull(System.getProperty("appMainSourceDir")))
        val backup = File(main, "res/xml/backup_rules.xml").readText()
        val extraction = File(main, "res/xml/data_extraction_rules.xml").readText()
        val network = File(main, "res/xml/network_security_config.xml").readText()

        listOf("root", "file", "database", "sharedpref", "external").forEach { domain ->
            assertTrue(backup.contains("domain=\"$domain\""))
            assertTrue(extraction.contains("domain=\"$domain\""))
        }
        assertTrue(extraction.contains("<cloud-backup>"))
        assertTrue(extraction.contains("<device-transfer>"))
        assertFalse(backup.contains("<include"))
        assertFalse(extraction.contains("<include"))
        assertTrue(network.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(network.contains("<debug-overrides"))
        assertFalse(network.contains("src=\"user\""))
        assertFalse(backup.contains("sentinel_audit.db") && backup.contains("<include"))
    }

    @Test
    fun `production logger redacts secrets and does not dump throwables`() {
        val logger = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/logging/AndroidStructuredLogger.kt",
        ).readText()
        val sanitizer = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/logging/ProductionLogSanitizer.kt",
        ).readText()

        assertTrue(logger.contains("ProductionLogSanitizer.sanitize"))
        assertTrue(logger.contains("exception_class"))
        assertFalse(logger.contains("Log.e(tag, format(event, fields), throwable)"))
        assertFalse(logger.contains("printStackTrace"))
        assertFalse(logger.contains("System.out"))
        assertTrue(sanitizer.contains("keystore_password"))
        assertTrue(sanitizer.contains("intent_extras"))
        assertTrue(sanitizer.contains("approval"))
    }

    @Test
    fun `MainActivity ignores externally supplied extras`() {
        val activity = File(
            requireNotNull(System.getProperty("appMainSourceDir")),
            "java/com/example/devicemanagement/ui/MainActivity.kt",
        ).readText()

        assertTrue(activity.contains("Incoming extras, data, or actions cannot"))
        assertTrue(activity.contains("override fun onNewIntent"))
        assertFalse(activity.contains("getStringExtra"))
        assertFalse(activity.contains("getExtras"))
        assertFalse(activity.contains("intent.data"))
        assertFalse(activity.contains("intent.extras"))
        assertFalse(activity.contains("getParcelableExtra"))
        assertFalse(activity.contains("sensitiveActions.submit"))
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
