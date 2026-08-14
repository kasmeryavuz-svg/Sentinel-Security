import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class EffectiveManifestSecurityVerifierTest {
    @Test
    fun `release manifest accepts hardened application attributes`() {
        val file = xmlFile(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <application
                  android:allowBackup="false"
                  android:fullBackupContent="@xml/backup_rules"
                  android:dataExtractionRules="@xml/data_extraction_rules"
                  android:networkSecurityConfig="@xml/network_security_config"
                  android:usesCleartextTraffic="false"
                  android:debuggable="false">
                <activity android:name="com.example.devicemanagement.ui.MainActivity" android:exported="true">
                  <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                  </intent-filter>
                </activity>
                <activity android:name="com.example.devicemanagement.provisioning.GetProvisioningModeActivity" android:exported="true" android:permission="android.permission.BIND_DEVICE_ADMIN" />
                <activity android:name="com.example.devicemanagement.provisioning.AdminPolicyComplianceActivity" android:exported="true" android:permission="android.permission.BIND_DEVICE_ADMIN" />
                <receiver android:name="com.example.devicemanagement.management.SentinelDeviceAdminReceiver" android:exported="true" />
              </application>
            </manifest>
            """.trimIndent(),
        )

        val violations = EffectiveManifestSecurityVerifier.verify(
            manifest = EffectiveManifestSecurityVerifier.parse(file),
            androidNamespace = "http://schemas.android.com/apk/res/android",
            variantName = "release",
            requireNonDebuggable = true,
        )

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `INTERNET permission and boot receivers fail closed`() {
        val file = xmlFile(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <uses-permission android:name="android.permission.INTERNET" />
              <application android:allowBackup="true" android:debuggable="true">
                <activity android:name="com.example.devicemanagement.ui.MainActivity" android:exported="true">
                  <intent-filter>
                    <action android:name="android.intent.action.VIEW" />
                    <category android:name="android.intent.category.BROWSABLE" />
                    <data android:scheme="sentinel" />
                  </intent-filter>
                </activity>
                <receiver android:name=".Boot" android:exported="true">
                  <intent-filter>
                    <action android:name="android.intent.action.BOOT_COMPLETED" />
                  </intent-filter>
                </receiver>
              </application>
            </manifest>
            """.trimIndent(),
        )

        val violations = EffectiveManifestSecurityVerifier.verify(
            manifest = EffectiveManifestSecurityVerifier.parse(file),
            androidNamespace = "http://schemas.android.com/apk/res/android",
            variantName = "release",
            requireNonDebuggable = true,
        )

        assertTrue(violations.any { "INTERNET" in it })
        assertTrue(violations.any { "allowBackup" in it })
        assertTrue(violations.any { "debuggable" in it })
        assertTrue(violations.any { "BOOT_COMPLETED" in it })
        assertTrue(violations.any { "BROWSABLE" in it || "deep-link" in it })
    }

    private fun xmlFile(contents: String): File {
        return File.createTempFile("manifest", ".xml").apply { writeText(contents) }
    }
}
