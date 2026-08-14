package com.example.devicemanagement.provisioningqr

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipFile
import kotlin.system.exitProcess

/**
 * Local developer tooling that emits Android QR provisioning JSON for Sentinel.
 *
 * This module is not an Android production runtime dependency. It never reads
 * keystore files, passwords, or private keys. The SHA-256 checksum is always
 * the exact signed APK file digest, encoded as URL-safe Base64 without padding.
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

    private val APK_SIGNING_BLOCK_MAGIC: ByteArray =
        "APK Sig Block 42".toByteArray(StandardCharsets.UTF_8)

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
        val downloadUrl = request.apkDownloadUrl.trim()
        if (!downloadUrl.startsWith("https://")) {
            throw ProvisioningQrException("APK download URL must be HTTPS")
        }
        val apkPath = request.signedApkPath
        if (!Files.isRegularFile(apkPath)) {
            throw ProvisioningQrException("signed APK is missing: $apkPath")
        }
        if (!looksLikeSignedApk(apkPath)) {
            throw ProvisioningQrException(
                "APK is not a signed package; checksum must be computed after signing"
            )
        }
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

    fun looksLikeSignedApk(apkPath: Path): Boolean {
        if (!hasZipLocalFileHeader(apkPath)) {
            return false
        }
        return hasV1SignatureEntry(apkPath) || hasApkSigningBlock(apkPath)
    }

    private fun hasZipLocalFileHeader(apkPath: Path): Boolean {
        Files.newInputStream(apkPath).use { stream ->
            val header = ByteArray(4)
            if (stream.read(header) != 4) {
                return false
            }
            return header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        }
    }

    private fun hasV1SignatureEntry(apkPath: Path): Boolean {
        return try {
            ZipFile(apkPath.toFile()).use { zip ->
                zip.entries().asSequence().any { entry ->
                    val name = entry.name
                    name.startsWith("META-INF/") &&
                        (
                            name.endsWith(".RSA") ||
                                name.endsWith(".DSA") ||
                                name.endsWith(".EC")
                        )
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasApkSigningBlock(apkPath: Path): Boolean {
        val bytes = Files.readAllBytes(apkPath)
        if (bytes.size < APK_SIGNING_BLOCK_MAGIC.size + 32) {
            return false
        }
        val magic = APK_SIGNING_BLOCK_MAGIC
        var index = bytes.size - magic.size
        while (index >= 0) {
            var matched = true
            var offset = 0
            while (offset < magic.size) {
                if (bytes[index + offset] != magic[offset]) {
                    matched = false
                    break
                }
                offset++
            }
            if (matched) {
                return true
            }
            index--
        }
        return false
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
