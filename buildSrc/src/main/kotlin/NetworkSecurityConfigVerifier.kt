import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object NetworkSecurityConfigVerifier {
    fun verify(file: File): List<String> {
        if (!file.isFile) {
            return listOf("network security config is missing at ${file.path}")
        }
        val document = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isNamespaceAware = true
            isExpandEntityReferences = false
            isIgnoringComments = true
        }.newDocumentBuilder().parse(file)
        val root = document.documentElement
        val violations = mutableListOf<String>()
        if (root == null || root.tagName != "network-security-config") {
            return listOf("${file.name} root must be network-security-config")
        }
        if (root.getElementsByTagName("debug-overrides").length > 0) {
            violations += "${file.name} must not include debug-overrides"
        }
        val baseConfigs = root.childNodes.elements().filter { it.tagName == "base-config" }
        if (baseConfigs.size != 1) {
            violations += "${file.name} must declare exactly one base-config"
            return violations
        }
        val base = baseConfigs.single()
        if (base.getAttribute("cleartextTrafficPermitted") != "false") {
            violations += "${file.name} must deny cleartext traffic by default"
        }
        val certificates = base.getElementsByTagName("certificates").elements()
        if (certificates.none { it.getAttribute("src") == "system" }) {
            violations += "${file.name} must trust system CAs"
        }
        if (certificates.any { it.getAttribute("src") == "user" }) {
            violations += "${file.name} must not trust user CAs"
        }
        return violations
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> {
        return (0 until length).map(::item).filterIsInstance<Element>()
    }
}
