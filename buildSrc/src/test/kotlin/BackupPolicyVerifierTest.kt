import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BackupPolicyVerifierTest {
    private val requiredDomains = BackupPolicyVerifier.requiredExcludeDomains

    @Test
    fun `complete exclude rules are accepted`() {
        val backup = xmlFile(fullBackup(requiredDomains))
        val extraction = xmlFile(dataExtraction(requiredDomains, requiredDomains))

        val violations = BackupPolicyVerifier.verify(backup, extraction)
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `include rules fail closed`() {
        val backup = xmlFile(
            """
            <full-backup-content>
              <include domain="database" path="." />
            </full-backup-content>
            """.trimIndent(),
        )
        val extraction = xmlFile(
            """
            <data-extraction-rules>
              <cloud-backup>
                <include domain="database" path="sentinel_audit.db" />
              </cloud-backup>
              <device-transfer />
            </data-extraction-rules>
            """.trimIndent(),
        )

        val violations = BackupPolicyVerifier.verify(backup, extraction)
        assertTrue(violations.any { "must not include" in it })
    }

    @Test
    fun `missing any required domain fails closed`() {
        requiredDomains.forEach { missing ->
            val incomplete = requiredDomains - missing
            val backup = xmlFile(fullBackup(incomplete))
            val extraction = xmlFile(dataExtraction(requiredDomains, requiredDomains))
            val backupViolations = BackupPolicyVerifier.verify(backup, extraction)
            assertTrue(
                backupViolations.any { missing in it },
                "full-backup should fail closed without $missing: $backupViolations",
            )

            val completeBackup = xmlFile(fullBackup(requiredDomains))
            val cloudMissing = xmlFile(dataExtraction(incomplete, requiredDomains))
            val cloudViolations = BackupPolicyVerifier.verify(completeBackup, cloudMissing)
            assertTrue(
                cloudViolations.any { missing in it && "cloud-backup" in it },
                "cloud-backup should fail closed without $missing: $cloudViolations",
            )

            val transferMissing = xmlFile(dataExtraction(requiredDomains, incomplete))
            val transferViolations = BackupPolicyVerifier.verify(completeBackup, transferMissing)
            assertTrue(
                transferViolations.any { missing in it && "device-transfer" in it },
                "device-transfer should fail closed without $missing: $transferViolations",
            )
        }
    }

    @Test
    fun `missing device-protected domains fail closed`() {
        listOf(
            "device_root",
            "device_file",
            "device_database",
            "device_sharedpref",
        ).forEach { domain ->
            assertTrue(domain in requiredDomains)
            val incomplete = requiredDomains - domain
            val violations = BackupPolicyVerifier.verify(
                xmlFile(fullBackup(incomplete)),
                xmlFile(dataExtraction(incomplete, incomplete)),
            )
            assertTrue(violations.any { domain in it }, violations.joinToString("\n"))
        }
    }

    private fun fullBackup(domains: Set<String>): String {
        return buildString {
            appendLine("<full-backup-content>")
            domains.forEach { domain ->
                appendLine("""  <exclude domain="$domain" path="." />""")
            }
            appendLine("</full-backup-content>")
        }
    }

    private fun dataExtraction(
        cloud: Set<String>,
        transfer: Set<String>,
    ): String {
        return buildString {
            appendLine("<data-extraction-rules>")
            appendLine("  <cloud-backup>")
            cloud.forEach { domain ->
                appendLine("""    <exclude domain="$domain" path="." />""")
            }
            appendLine("  </cloud-backup>")
            appendLine("  <device-transfer>")
            transfer.forEach { domain ->
                appendLine("""    <exclude domain="$domain" path="." />""")
            }
            appendLine("  </device-transfer>")
            appendLine("</data-extraction-rules>")
        }
    }

    private fun xmlFile(contents: String): File {
        return File.createTempFile("backup", ".xml").apply { writeText(contents) }
    }
}
