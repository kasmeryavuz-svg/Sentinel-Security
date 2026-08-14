import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class NetworkSecurityConfigVerifierTest {
    @Test
    fun `comments mentioning debug-overrides do not fail a hardened config`() {
        val file = File.createTempFile("network", ".xml").apply {
            writeText(
                """
                <network-security-config>
                  <!-- There is no debug-overrides block. -->
                  <base-config cleartextTrafficPermitted="false">
                    <trust-anchors>
                      <certificates src="system" />
                    </trust-anchors>
                  </base-config>
                </network-security-config>
                """.trimIndent(),
            )
        }
        val violations = NetworkSecurityConfigVerifier.verify(file)
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `debug-overrides element fails closed`() {
        val file = File.createTempFile("network", ".xml").apply {
            writeText(
                """
                <network-security-config>
                  <base-config cleartextTrafficPermitted="true">
                    <trust-anchors>
                      <certificates src="user" />
                    </trust-anchors>
                  </base-config>
                  <debug-overrides>
                    <trust-anchors>
                      <certificates src="user" />
                    </trust-anchors>
                  </debug-overrides>
                </network-security-config>
                """.trimIndent(),
            )
        }
        val violations = NetworkSecurityConfigVerifier.verify(file)
        assertTrue(violations.any { "debug-overrides" in it })
        assertTrue(violations.any { "cleartext" in it })
        assertTrue(violations.any { "user CAs" in it })
    }
}
