package com.example.devicemanagement.provisioningqr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64

class ProvisioningQrGeneratorTest {
    @Test
    fun `payload uses the exact Sentinel admin component and HTTPS URL`() {
        val apk = TestSignedApkFactory.signedApk()
        val payload = ProvisioningQrGenerator.generate(
            QrProvisioningRequest(
                signedApkPath = apk,
                apkDownloadUrl = "https://example.test/sentinel.apk",
            ),
        )

        assertEquals(
            ProvisioningQrGenerator.EXPECTED_ADMIN_COMPONENT,
            payload.adminComponent,
        )
        assertEquals(
            "com.example.devicemanagement/.management.SentinelDeviceAdminReceiver",
            payload.adminComponent,
        )
        assertEquals("https://example.test/sentinel.apk", payload.apkDownloadUrl)
        val json = payload.toJson()
        assertTrue(json.contains("\"${ProvisioningQrGenerator.KEY_ADMIN_COMPONENT}\""))
        assertTrue(json.contains("\"${ProvisioningQrGenerator.KEY_DOWNLOAD_LOCATION}\""))
        assertTrue(json.contains("\"${ProvisioningQrGenerator.KEY_PACKAGE_CHECKSUM}\""))
        assertFalse(json.contains(ProvisioningQrGenerator.KEY_WIFI_SSID))
        assertFalse(json.contains(ProvisioningQrGenerator.KEY_WIFI_PASSWORD))
    }

    @Test
    fun `checksum equals SHA-256 of the signed APK file encoded URL-safe without padding`() {
        val apk = TestSignedApkFactory.signedApk()
        val bytes = Files.readAllBytes(apk)
        val payload = ProvisioningQrGenerator.generate(
            QrProvisioningRequest(
                signedApkPath = apk,
                apkDownloadUrl = "https://example.test/sentinel.apk",
            ),
        )

        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        assertEquals(expected, payload.packageChecksum)
        assertEquals(expected, ProvisioningQrGenerator.encodePackageFileChecksum(bytes))
        assertFalse(payload.packageChecksum.contains("="))
        assertFalse(payload.packageChecksum.contains("+"))
        assertFalse(payload.packageChecksum.contains("/"))
        assertEquals(43, payload.packageChecksum.length)
    }

    @Test
    fun `checksum pins the exact APK file not a signature-only digest`() {
        val first = ProvisioningQrGenerator.generate(
            QrProvisioningRequest(
                signedApkPath = TestSignedApkFactory.signedApk("assets/a.txt" to byteArrayOf(1)),
                apkDownloadUrl = "https://example.test/sentinel.apk",
            ),
        )
        val second = ProvisioningQrGenerator.generate(
            QrProvisioningRequest(
                signedApkPath = TestSignedApkFactory.signedApk("assets/a.txt" to byteArrayOf(2)),
                apkDownloadUrl = "https://example.test/sentinel.apk",
            ),
        )

        assertNotEquals(first.packageChecksum, second.packageChecksum)
    }

    @Test
    fun `tampered signed APK fails closed`() {
        val apk = TestSignedApkFactory.signedApk()
        Files.write(apk, byteArrayOf(0x0A), java.nio.file.StandardOpenOption.APPEND)
        val error = runCatching {
            ProvisioningQrGenerator.generate(
                QrProvisioningRequest(
                    signedApkPath = apk,
                    apkDownloadUrl = "https://example.test/sentinel.apk",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is ProvisioningQrException)
        assertTrue(error?.message?.contains("signed") == true || error?.message?.contains("verification") == true)
    }

    @Test
    fun `HTTP download URL is rejected`() {
        val error = runCatching {
            ProvisioningQrGenerator.generate(
                QrProvisioningRequest(
                    signedApkPath = TestSignedApkFactory.signedApk(),
                    apkDownloadUrl = "http://example.test/sentinel.apk",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is ProvisioningQrException)
        assertTrue(error?.message?.contains("HTTPS") == true)
    }

    @Test
    fun `HTTPS URL without host is rejected`() {
        listOf("https://", "https:///apk", "https:example.test", "not-a-url").forEach { url ->
            val error = runCatching {
                ProvisioningQrGenerator.requireHttpsDownloadUrl(url)
            }.exceptionOrNull()
            assertTrue("expected rejection for $url", error is ProvisioningQrException)
        }
    }

    @Test
    fun `HTTPS scheme is accepted case-insensitively when host is present`() {
        assertEquals(
            "HTTPS://Example.TEST/sentinel.apk",
            ProvisioningQrGenerator.requireHttpsDownloadUrl(
                "HTTPS://Example.TEST/sentinel.apk",
            ),
        )
    }

    @Test
    fun `missing APK fails closed`() {
        val missing = Files.createTempDirectory("sentinel-qr").resolve("missing.apk")
        val error = runCatching {
            ProvisioningQrGenerator.generate(
                QrProvisioningRequest(
                    signedApkPath = missing,
                    apkDownloadUrl = "https://example.test/sentinel.apk",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is ProvisioningQrException)
        assertTrue(error?.message?.contains("missing") == true)
    }

    @Test
    fun `unsigned APK fails closed`() {
        val error = runCatching {
            ProvisioningQrGenerator.generate(
                QrProvisioningRequest(
                    signedApkPath = TestSignedApkFactory.unsignedApk(),
                    apkDownloadUrl = "https://example.test/sentinel.apk",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is ProvisioningQrException)
        assertTrue(error?.message?.contains("signed") == true || error?.message?.contains("verification") == true)
    }

    @Test
    fun `ZIP with fake CERT RSA is rejected`() {
        val error = runCatching {
            ProvisioningQrGenerator.generate(
                QrProvisioningRequest(
                    signedApkPath = TestSignedApkFactory.zipWithFakeCertRsa(),
                    apkDownloadUrl = "https://example.test/sentinel.apk",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is ProvisioningQrException)
        assertTrue(error?.message?.contains("signed") == true || error?.message?.contains("verification") == true)
    }

    @Test
    fun `ZIP containing APK Sig Block 42 text is rejected`() {
        val error = runCatching {
            ProvisioningQrGenerator.generate(
                QrProvisioningRequest(
                    signedApkPath = TestSignedApkFactory.zipContainingApkSigningBlockMarker(),
                    apkDownloadUrl = "https://example.test/sentinel.apk",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is ProvisioningQrException)
        assertTrue(error?.message?.contains("signed") == true || error?.message?.contains("verification") == true)
    }

    @Test
    fun `empty APK checksum generation fails closed`() {
        val error = runCatching {
            ProvisioningQrGenerator.encodePackageFileChecksum(ByteArray(0))
        }.exceptionOrNull()

        assertTrue(error is ProvisioningQrException)
        assertTrue(error?.message?.contains("empty") == true)
    }

    @Test
    fun `wrong admin component is rejected`() {
        val error = runCatching {
            ProvisioningQrGenerator.generate(
                QrProvisioningRequest(
                    signedApkPath = TestSignedApkFactory.signedApk(),
                    apkDownloadUrl = "https://example.test/sentinel.apk",
                    adminComponent = "com.example.other/.OtherReceiver",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is ProvisioningQrException)
        assertTrue(error?.message?.contains("admin component") == true)
    }

    @Test
    fun `Wi-Fi extras are omitted unless supplied explicitly`() {
        val withWifi = ProvisioningQrGenerator.generate(
            QrProvisioningRequest(
                signedApkPath = TestSignedApkFactory.signedApk(),
                apkDownloadUrl = "https://example.test/sentinel.apk",
                wifiSsid = "lab-net",
                wifiSecurityType = "WPA2",
                wifiPassword = "not-logged",
            ),
        )
        val json = withWifi.toJson()
        assertTrue(json.contains("\"${ProvisioningQrGenerator.KEY_WIFI_SSID}\":\"lab-net\""))
        assertTrue(json.contains("\"${ProvisioningQrGenerator.KEY_WIFI_SECURITY_TYPE}\":\"WPA2\""))
        assertTrue(json.contains("\"${ProvisioningQrGenerator.KEY_WIFI_PASSWORD}\":\"not-logged\""))
    }

    @Test
    fun `Wi-Fi password without SSID fails closed`() {
        val error = runCatching {
            ProvisioningQrGenerator.generate(
                QrProvisioningRequest(
                    signedApkPath = TestSignedApkFactory.signedApk(),
                    apkDownloadUrl = "https://example.test/sentinel.apk",
                    wifiPassword = "secret",
                ),
            )
        }.exceptionOrNull()

        assertTrue(error is ProvisioningQrException)
        assertTrue(error?.message?.contains("SSID") == true)
    }

    @Test
    fun `CLI parse requires apk and https url arguments`() {
        val apk = TestSignedApkFactory.signedApk()
        val request = ProvisioningQrGenerator.parseArgs(
            arrayOf(
                "--apk",
                apk.toString(),
                "--url",
                "https://example.test/sentinel.apk",
            ),
        )
        assertEquals(apk, request.signedApkPath)
        assertEquals("https://example.test/sentinel.apk", request.apkDownloadUrl)
        assertEquals(
            ProvisioningQrGenerator.EXPECTED_ADMIN_COMPONENT,
            request.adminComponent,
        )
    }

    @Test
    fun `unknown CLI arguments fail closed`() {
        val error = runCatching {
            ProvisioningQrGenerator.parseArgs(arrayOf("--keystore", "secret.jks"))
        }.exceptionOrNull()
        assertTrue(error is ProvisioningQrException)
    }
}
