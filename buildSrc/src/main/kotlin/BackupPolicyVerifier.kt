import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

object BackupPolicyVerifier {
    val requiredExcludeDomains = setOf(
        "root",
        "file",
        "database",
        "sharedpref",
        "external",
        "device_root",
        "device_file",
        "device_database",
        "device_sharedpref",
    )

    fun verify(backupRules: File, dataExtractionRules: File): List<String> {
        val violations = mutableListOf<String>()
        violations += verifyFullBackup(backupRules)
        violations += verifyDataExtraction(dataExtractionRules)
        return violations
    }

    private fun verifyFullBackup(file: File): List<String> {
        val violations = mutableListOf<String>()
        if (!file.isFile) {
            return listOf("full-backup rules file is missing at ${file.path}")
        }
        val document = parse(file)
        val root = document.documentElement
        if (root == null || root.tagName != "full-backup-content") {
            return listOf("${file.name} root must be full-backup-content")
        }
        if (root.getElementsByTagName("include").length > 0) {
            violations += "${file.name} must not include any backup domain"
        }
        val excluded = excludedDomains(root)
        if (excluded != requiredExcludeDomains) {
            violations +=
                "${file.name} must exclude exactly $requiredExcludeDomains; found $excluded"
        }
        return violations
    }

    private fun verifyDataExtraction(file: File): List<String> {
        val violations = mutableListOf<String>()
        if (!file.isFile) {
            return listOf("data-extraction rules file is missing at ${file.path}")
        }
        val document = parse(file)
        val root = document.documentElement
        if (root == null || root.tagName != "data-extraction-rules") {
            return listOf("${file.name} root must be data-extraction-rules")
        }
        listOf("cloud-backup", "device-transfer").forEach { sectionName ->
            val sections = root.childNodes.elements().filter { it.tagName == sectionName }
            if (sections.size != 1) {
                violations += "${file.name} must declare exactly one <$sectionName> section"
                return@forEach
            }
            val section = sections.single()
            if (section.getElementsByTagName("include").length > 0) {
                violations += "${file.name} <$sectionName> must not include any domain"
            }
            val excluded = excludedDomains(section)
            if (excluded != requiredExcludeDomains) {
                violations +=
                    "${file.name} <$sectionName> must exclude exactly " +
                    "$requiredExcludeDomains; found $excluded"
            }
        }
        return violations
    }

    private fun excludedDomains(parent: Element): Set<String> {
        return parent.getElementsByTagName("exclude").elements()
            .map { it.getAttribute("domain") }
            .toSet()
    }

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isNamespaceAware = true
        isExpandEntityReferences = false
    }.newDocumentBuilder().parse(file)

    private fun org.w3c.dom.NodeList.elements(): List<Element> {
        return (0 until length).map(::item).filterIsInstance<Element>()
    }
}
