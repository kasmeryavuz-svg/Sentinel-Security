import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object EffectiveManifestSecurityVerifier {
    private val expectedExportedActivities = setOf(
        "com.example.devicemanagement.ui.MainActivity",
        "com.example.devicemanagement.provisioning.GetProvisioningModeActivity",
        "com.example.devicemanagement.provisioning.AdminPolicyComplianceActivity",
    )

    private val forbiddenActions = setOf(
        "android.app.action.PROVISION_MANAGED_DEVICE",
        "android.app.action.PROVISION_MANAGED_PROFILE",
        "android.app.action.PROVISION_MANAGED_USER",
        "android.app.action.PROVISION_MANAGED_SHARE_DEVICE",
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.LOCKED_BOOT_COMPLETED",
        "android.intent.action.QUICKBOOT_POWERON",
        "android.intent.action.VIEW",
        "android.hardware.usb.action.USB_DEVICE_ATTACHED",
        "android.hardware.usb.action.USB_ACCESSORY_ATTACHED",
    )

    private val forbiddenPermissions = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.BIND_VPN_SERVICE",
    )

    fun parse(file: java.io.File): Document {
        return DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isNamespaceAware = true
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(file)
    }

    fun verify(
        manifest: Document,
        androidNamespace: String,
        variantName: String,
        requireNonDebuggable: Boolean,
    ): List<String> {
        val violations = mutableListOf<String>()
        fun fail(message: String) {
            violations += "Effective $variantName manifest: $message"
        }

        val application = manifest.getElementsByTagName("application").elements().singleOrNull()
        if (application == null) {
            fail("must declare exactly one application element")
            return violations
        }

        if (application.getAttributeNS(androidNamespace, "allowBackup") != "false") {
            fail("allowBackup must be false")
        }
        if (application.getAttributeNS(androidNamespace, "fullBackupContent") != "@xml/backup_rules") {
            fail("fullBackupContent must reference @xml/backup_rules")
        }
        if (
            application.getAttributeNS(androidNamespace, "dataExtractionRules") !=
            "@xml/data_extraction_rules"
        ) {
            fail("dataExtractionRules must reference @xml/data_extraction_rules")
        }
        if (
            application.getAttributeNS(androidNamespace, "networkSecurityConfig") !=
            "@xml/network_security_config"
        ) {
            fail("networkSecurityConfig must reference @xml/network_security_config")
        }
        val cleartext = application.getAttributeNS(androidNamespace, "usesCleartextTraffic")
        if (cleartext.isNotEmpty() && cleartext != "false") {
            fail("usesCleartextTraffic must be false, found $cleartext")
        }
        val testOnly = application.getAttributeNS(androidNamespace, "testOnly")
        if (testOnly == "true") {
            fail("testOnly must not be true")
        }
        val debuggable = application.getAttributeNS(androidNamespace, "debuggable")
        if (requireNonDebuggable && debuggable == "true") {
            fail("release must not be debuggable")
        }
        if (application.getElementsByTagName("profileable").elements().isNotEmpty()) {
            fail("must not declare profileable")
        }

        val permissionNames = (
            manifest.getElementsByTagName("uses-permission").elements() +
                manifest.getElementsByTagName("uses-permission-sdk-23").elements()
            ).map { it.getAttributeNS(androidNamespace, "name") }.toSet()
        val unexpectedPermissions = permissionNames.intersect(forbiddenPermissions)
        if (unexpectedPermissions.isNotEmpty()) {
            fail("declares forbidden network permissions: $unexpectedPermissions")
        }

        val allActions = manifest.getElementsByTagName("action").elements()
            .map { it.getAttributeNS(androidNamespace, "name") }
            .toSet()
        val unexpectedActions = allActions.intersect(forbiddenActions)
        if (unexpectedActions.isNotEmpty()) {
            fail("declares forbidden intent actions: $unexpectedActions")
        }

        val categories = manifest.getElementsByTagName("category").elements()
            .map { it.getAttributeNS(androidNamespace, "name") }
            .toSet()
        if ("android.intent.category.BROWSABLE" in categories) {
            fail("must not declare BROWSABLE (deep-link) category")
        }

        val dataElements = manifest.getElementsByTagName("data").elements()
        if (dataElements.isNotEmpty()) {
            fail("must not declare intent-filter data / deep-link URI elements")
        }

        val exportedActivities = manifest.getElementsByTagName("activity").elements()
            .filter { it.getAttributeNS(androidNamespace, "exported") == "true" }
            .map { it.getAttributeNS(androidNamespace, "name") }
            .toSet()
        if (exportedActivities != expectedExportedActivities) {
            fail(
                "exported activities changed; expected $expectedExportedActivities, " +
                    "found $exportedActivities",
            )
        }

        val exportedServices = manifest.getElementsByTagName("service").elements()
            .filter { it.getAttributeNS(androidNamespace, "exported") == "true" }
        if (exportedServices.isNotEmpty()) {
            fail("must not export services")
        }
        val exportedProviders = manifest.getElementsByTagName("provider").elements()
            .filter { it.getAttributeNS(androidNamespace, "exported") == "true" }
        if (exportedProviders.isNotEmpty()) {
            fail("must not export providers")
        }
        val allProviders = manifest.getElementsByTagName("provider").elements()
        if (allProviders.isNotEmpty()) {
            fail("must not declare ContentProviders")
        }

        val exportedReceivers = manifest.getElementsByTagName("receiver").elements()
            .filter { it.getAttributeNS(androidNamespace, "exported") == "true" }
            .map { it.getAttributeNS(androidNamespace, "name") }
        if (
            exportedReceivers != listOf(
                "com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
            )
        ) {
            fail("exported receivers changed: $exportedReceivers")
        }

        return violations
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> {
        return (0 until length).map(::item).filterIsInstance<Element>()
    }
}
