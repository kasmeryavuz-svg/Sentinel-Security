package com.example.devicemanagement.provisioningqr

import com.android.apksig.ApkSigner
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Test-only helper that signs a real aapt2-linked unsigned APK with an
 * ephemeral keystore created in a temp directory. Nothing is committed.
 */
internal object TestSignedApkFactory {
    private val unsignedApkBytes: ByteArray = Base64.getDecoder().decode(
        "UEsDBAAAAAAIAAAAIQBK0cw13gEAALQEAAATAAAAQW5kcm9pZE1hbmlmZXN0LnhtbJWTO28T" +
            "QRSFz3hjvCEOOIGCh0tXKNjQIERBARIVQkJB1DFxHlZis9p1IpQqBb+GioJfQk1BRU" +
            "FJRQffXF+TiYMlmNXxXJ+5j3Pv7GbK9WlJCmrrS026prO1n9hXwAZ4BrbAe/ABfAXf" +
            "wQ+wHqQOeAxOwGfwDfwEG+TeAnUdqq832mGXVjXSUGNtaqADvYYtVcG8hZNWdHyOec" +
            "rvAObiyQtyjuykpQl2qT3+TRbkXdM21kgFzCFnf/e6+Q9eM0XjP/Uz3VdX97BqWA9s" +
            "cpvmMbFOY98vyfAKq4KTGsSOyVKSa8geu+tTtcBziIa+RU41tV1Tl+h3VrMwbV1TcW" +
            "z+O7B9U7Rn9rS2dIf7nPAUeqQeT4XvvntXZDivouuVeqblgL00xb0LanOvN9Ru0lMB" +
            "t03cVIV0w5TGXnaJLIl5oiOb7GDBTP8n5uz+czwq03EXxBuTTkOujuI7HkKzFsJt0A" +
            "YFKOshVEChRb14a9Iv1mV7O/kP/zHh44rfwnWehn8bq8l5zisYnMtsElGTsoZz9cSv" +
            "49ylqY/55X5+lb3pXNO51lxstNcSruV6n3vOmd5111tL9IYk7pZzS3P5M5/JfK5Y46" +
            "H3NuNXvEZIasS17D0sJ/nm47K52c9mHBbcyW9QSwMEAAAAAAAAAAAhAAtQNhMoAAAA" +
            "KAAAAA4AAQByZXNvdXJjZXMuYXJzYwACAAwAKAAAAAAAAAABABwAHAAAAAAAAAAAAA" +
            "AAAAEAABwAAAAAAAAAUEsBAgAAAAAAAAgAAAAhAErRzDXeAQAAtAQAABMAAAAAAAAA" +
            "AAAAAAAAAAAAAEFuZHJvaWRNYW5pZmVzdC54bWxQSwECAAAAAAAAAAAAACEAC1A2Ey" +
            "gAAAAoAAAADgAAAAAAAAAAAAAAAAAPAgAAcmVzb3VyY2VzLmFyc2NQSwUGAAAAAAIA" +
            "AgB9AAAAZAIAAAAA",
    )

    private val signerConfig: ApkSigner.SignerConfig by lazy { createSignerConfig() }

    fun signedApk(extraEntry: Pair<String, ByteArray>? = null): Path {
        val directory = Files.createTempDirectory("sentinel-signed-apk")
        val unsigned = directory.resolve("unsigned.apk")
        if (extraEntry == null) {
            Files.write(unsigned, unsignedApkBytes)
        } else {
            copyZipAddingEntry(unsignedApkBytes, unsigned, extraEntry.first, extraEntry.second)
        }
        val signed = directory.resolve("signed.apk")
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsigned.toFile())
            .setOutputApk(signed.toFile())
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setV4SigningEnabled(false)
            .build()
            .sign()
        return signed
    }

    fun unsignedApk(): Path = writeBytes("unsigned.apk", unsignedApkBytes)

    fun zipWithFakeCertRsa(): Path {
        return writeZip(
            "AndroidManifest.xml" to byteArrayOf(0x01, 0x02, 0x03),
            "META-INF/CERT.RSA" to byteArrayOf(0x30, 0x01, 0x02),
        )
    }

    fun zipContainingApkSigningBlockMarker(): Path {
        return writeZip(
            "AndroidManifest.xml" to byteArrayOf(0x01, 0x02, 0x03),
            "assets/note.txt" to "APK Sig Block 42".toByteArray(),
        )
    }

    @Suppress("DEPRECATION")
    private fun createSignerConfig(): ApkSigner.SignerConfig {
        val directory = Files.createTempDirectory("sentinel-test-signer")
        val keystoreFile = directory.resolve("test-signer.p12").toFile()
        val keytool = File(System.getProperty("java.home"), "bin/keytool")
        val process = ProcessBuilder(
            keytool.absolutePath,
            "-genkeypair",
            "-alias",
            "test",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "2",
            "-keystore",
            keystoreFile.absolutePath,
            "-storetype",
            "PKCS12",
            "-storepass",
            "test-only",
            "-dname",
            "CN=SentinelQrTest",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor() == 0) { "test keytool failed: $output" }
        val keyStore = KeyStore.getInstance("PKCS12")
        keystoreFile.inputStream().use { stream ->
            keyStore.load(stream, "test-only".toCharArray())
        }
        val privateKey = keyStore.getKey("test", "test-only".toCharArray()) as PrivateKey
        val certificate = keyStore.getCertificate("test") as X509Certificate
        return ApkSigner.SignerConfig.Builder("test", privateKey, listOf(certificate)).build()
    }

    private fun copyZipAddingEntry(
        source: ByteArray,
        destination: Path,
        name: String,
        bytes: ByteArray,
    ) {
        ZipInputStream(source.inputStream()).use { input ->
            ZipOutputStream(Files.newOutputStream(destination)).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    output.putNextEntry(ZipEntry(entry.name))
                    input.copyTo(output)
                    output.closeEntry()
                }
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
    }

    private fun writeZip(vararg entries: Pair<String, ByteArray>): Path {
        val apk = Files.createTempDirectory("sentinel-qr-zip").resolve("artifact.apk")
        ZipOutputStream(Files.newOutputStream(apk)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return apk
    }

    private fun writeBytes(name: String, bytes: ByteArray): Path {
        val path = Files.createTempDirectory("sentinel-qr-bytes").resolve(name)
        Files.write(path, bytes)
        return path
    }
}
