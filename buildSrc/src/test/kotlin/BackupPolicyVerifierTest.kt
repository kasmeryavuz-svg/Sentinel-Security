import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class BackupPolicyVerifierTest {
    @Test
    fun `complete exclude rules are accepted`() {
        val backup = xmlFile(
            """
            <full-backup-content>
              <exclude domain="root" path="." />
              <exclude domain="file" path="." />
              <exclude domain="database" path="." />
              <exclude domain="sharedpref" path="." />
              <exclude domain="external" path="." />
            </full-backup-content>
            """.trimIndent(),
        )
        val extraction = xmlFile(
            """
            <data-extraction-rules>
              <cloud-backup>
                <exclude domain="root" path="." />
                <exclude domain="file" path="." />
                <exclude domain="database" path="." />
                <exclude domain="sharedpref" path="." />
                <exclude domain="external" path="." />
              </cloud-backup>
              <device-transfer>
                <exclude domain="root" path="." />
                <exclude domain="file" path="." />
                <exclude domain="database" path="." />
                <exclude domain="sharedpref" path="." />
                <exclude domain="external" path="." />
              </device-transfer>
            </data-extraction-rules>
            """.trimIndent(),
        )

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

    private fun xmlFile(contents: String): File {
        return File.createTempFile("backup", ".xml").apply { writeText(contents) }
    }
}
