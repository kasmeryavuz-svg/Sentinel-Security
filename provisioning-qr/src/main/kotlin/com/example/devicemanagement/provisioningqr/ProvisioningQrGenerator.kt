package com.example.devicemanagement.provisioningqr

import com.android.apksig.ApkVerifier
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import kotlin.system.exitProcess

/**
 * Local developer tooling that emits Android QR provisioning JSON for Sentinel.
 *
 * This module is not an Android production runtime dependency. It never reads
 * keystore files, passwords, or private keys. The SHA-256 checksum is always
 * the exact signed APK file digest, encoded as URL-safe Base64 without padding.
 *
 * APK authenticity is checked with Android's apksig verifier. Presence of
 * META-INF certificate files or an "APK Sig Block 42" marker is not accepted
 * as proof of signing.
 */
object ProvisioningQrGenerator {
    const val EXPECTED_ADMIN_COMPONENT: String =
        "com.example.devicemanagement/.management.SentinelDeviceAdminReceiver"

    const val KEY_ADMIN_COMPONENT: String =
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"
    const val KEY_DOWNLOAD_LOCATION: String =
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"
    const val KEY_PACKAGE_CHECKSUM: String =
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM"
    const val KEY_WIFI_SSID: String = "android.app.extra.PROVISIONING_WIFI_SSID"
    const val KEY_WIFI_SECURITY_TYPE: String = "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"
    const val KEY_WIFI_PASSWORD: String = "android.app.extra.PROVISIONING_WIFI_PASSWORD"

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            val request = parseArgs(args)
            val payload = generate(request)
            println(payload.toJson())
        } catch (failure: ProvisioningQrException) {
            System.err.println(failure.message)
            exitProcess(1)
        }
    }

    fun generate(request: QrProvisioningRequest): QrProvisioningPayload {
        val admin = request.adminComponent.trim()
        if (admin != EXPECTED_ADMIN_COMPONENT) {
            throw ProvisioningQrException(
                "admin component must be exactly $EXPECTED_ADMIN_COMPONENT"
            )
        }
        val downloadUrl = requireHttpsDownloadUrl(request.apkDownloadUrl)
        val apkPath = request.signedApkPath
        if (!Files.isRegularFile(apkPath)) {
            throw ProvisioningQrException("signed APK is missing: $apkPath")
        }
        verifySignedApk(apkPath)
        val wifiSsid = request.wifiSsid?.takeIf { it.isNotBlank() }
        val wifiSecurityType = request.wifiSecurityType?.takeIf { it.isNotBlank() }
        val wifiPassword = request.wifiPassword?.takeIf { it.isNotBlank() }
        if ((wifiSecurityType != null || wifiPassword != null) && wifiSsid == null) {
            throw ProvisioningQrException(
                "Wi-Fi security or password requires an explicit Wi-Fi SSID"
            )
        }
        val checksum = encodePackageFileChecksum(Files.readAllBytes(apkPath))
        return QrProvisioningPayload(
            adminComponent = admin,
            apkDownloadUrl = downloadUrl,
            packageChecksum = checksum,
            wifiSsid = wifiSsid,
            wifiSecurityType = wifiSecurityType,
            wifiPassword = wifiPassword,
        )
    }

    fun encodePackageFileChecksum(apkBytes: ByteArray): String {
        if (apkBytes.isEmpty()) {
            throw ProvisioningQrException("cannot checksum an empty APK")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(apkBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun requireHttpsDownloadUrl(raw: String): String {
        val trimmed = raw.trim()
        val uri = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            throw ProvisioningQrException("APK download URL must be a valid HTTPS URL")
        }
        if (!uri.isAbsolute || uri.isOpaque) {
            throw ProvisioningQrException("APK download URL must be a valid HTTPS URL")
        }
        val scheme = uri.scheme
        if (scheme == null || !scheme.equals("https", ignoreCase = true)) {
            throw ProvisioningQrException("APK download URL must be HTTPS")
        }
        if (uri.host.isNullOrBlank()) {
            throw ProvisioningQrException("APK download URL must include a non-empty host")
        }
        return trimmed
    }

    private fun verifySignedApk(apkPath: Path) {
        val result = try {
            ApkVerifier.Builder(apkPath.toFile()).build().verify()
        } catch (_: Exception) {
            throw ProvisioningQrException(
                "APK signature verification failed; checksum must be computed after signing"
            )
        }
        if (!result.isVerified || result.signerCertificates.isEmpty()) {
            throw ProvisioningQrException(
                "APK is not a signed package; checksum must be computed after signing"
            )
        }
    }

    internal fun parseArgs(args: Array<String>): QrProvisioningRequest {
        var apk: Path? = null
        var url: String? = null
        var admin: String = EXPECTED_ADMIN_COMPONENT
        var wifiSsid: String? = null
        var wifiSecurity: String? = null
        var wifiPassword: String? = null
        var index = 0
        while (index < args.size) {
            val flag = args[index]
            val value = args.getOrNull(index + 1)
            when (flag) {
                "--apk" -> apk = Path.of(requiredValue(flag, value))
                "--url" -> url = requiredValue(flag, value)
                "--admin" -> admin = requiredValue(flag, value)
                "--wifi-ssid" -> wifiSsid = requiredValue(flag, value)
                "--wifi-security" -> wifiSecurity = requiredValue(flag, value)
                "--wifi-password" -> wifiPassword = requiredValue(flag, value)
                else -> throw ProvisioningQrException("unknown argument: $flag")
            }
            index += 2
        }
        return QrProvisioningRequest(
            signedApkPath = apk ?: throw ProvisioningQrException("--apk is required"),
            apkDownloadUrl = url ?: throw ProvisioningQrException("--url is required"),
            adminComponent = admin,
            wifiSsid = wifiSsid,
            wifiSecurityType = wifiSecurity,
            wifiPassword = wifiPassword,
        )
    }

    private fun requiredValue(flag: String, value: String?): String {
        if (value == null || value.startsWith("--")) {
            throw ProvisioningQrException("$flag requires a value")
        }
        return value
    }
}

data class QrProvisioningRequest(
    val signedApkPath: Path,
    val apkDownloadUrl: String,
    val adminComponent: String = ProvisioningQrGenerator.EXPECTED_ADMIN_COMPONENT,
    val wifiSsid: String? = null,
    val wifiSecurityType: String? = null,
    val wifiPassword: String? = null,
)

data class QrProvisioningPayload(
    val adminComponent: String,
    val apkDownloadUrl: String,
    val packageChecksum: String,
    val wifiSsid: String? = null,
    val wifiSecurityType: String? = null,
    val wifiPassword: String? = null,
) {
    fun toJson(): String {
        val fields = linkedMapOf(
            ProvisioningQrGenerator.KEY_ADMIN_COMPONENT to adminComponent,
            ProvisioningQrGenerator.KEY_DOWNLOAD_LOCATION to apkDownloadUrl,
            ProvisioningQrGenerator.KEY_PACKAGE_CHECKSUM to packageChecksum,
        )
        wifiSsid?.let { fields[ProvisioningQrGenerator.KEY_WIFI_SSID] = it }
        wifiSecurityType?.let { fields[ProvisioningQrGenerator.KEY_WIFI_SECURITY_TYPE] = it }
        wifiPassword?.let { fields[ProvisioningQrGenerator.KEY_WIFI_PASSWORD] = it }
        return encodeJsonObject(fields)
    }

    private fun encodeJsonObject(fields: Map<String, String>): String {
        val builder = StringBuilder()
        builder.append('{')
        var first = true
        fields.forEach { (key, value) ->
            if (!first) {
                builder.append(',')
            }
            first = false
            builder.append('"').append(escape(key)).append('"')
            builder.append(':')
            builder.append('"').append(escape(value)).append('"')
        }
        builder.append('}')
        return builder.toString()
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

class ProvisioningQrException(message: String) : IllegalArgumentException(message)
