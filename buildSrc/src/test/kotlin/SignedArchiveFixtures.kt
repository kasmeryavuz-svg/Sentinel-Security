import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object SignedArchiveFixtures {
    fun signedJar(): File {
        val javaHome = File(System.getProperty("java.home"))
        val keytool = tool(javaHome, "keytool")
        val jarsigner = tool(javaHome, "jarsigner")
        val directory = Files.createTempDirectory("sentinel-signing-fixture").toFile()
        val keystore = File(directory, "arbitrary-developer.p12")
        val unsigned = File(directory, "unsigned.jar")
        ZipOutputStream(unsigned.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zip.write("Manifest-Version: 1.0\n".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("payload.txt"))
            zip.write("sentinel-signing-fixture\n".toByteArray())
            zip.closeEntry()
        }
        run(
            keytool.absolutePath,
            "-genkeypair",
            "-alias",
            "arbitrary",
            "-keystore",
            keystore.absolutePath,
            "-storetype",
            "PKCS12",
            "-storepass",
            "fixture-pass",
            "-keypass",
            "fixture-pass",
            "-dname",
            "CN=Arbitrary Developer, OU=QA, O=Example Corp, C=US",
            "-keyalg",
            "RSA",
            "-keysize",
            "2048",
            "-validity",
            "1",
        )
        run(
            jarsigner.absolutePath,
            "-keystore",
            keystore.absolutePath,
            "-storepass",
            "fixture-pass",
            "-keypass",
            "fixture-pass",
            unsigned.absolutePath,
            "arbitrary",
        )
        return unsigned
    }

    fun tamperPayloadKeepingSignatures(signed: File): File {
        val tampered = File(signed.parentFile, "tampered-${signed.name}")
        ZipFile(signed).use { source ->
            ZipOutputStream(tampered.outputStream()).use { destination ->
                source.entries().asSequence().forEach { entry ->
                    val original = source.getInputStream(entry).use { it.readBytes() }
                    destination.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "payload.txt") {
                        destination.write("tampered-payload-after-signing\n".toByteArray())
                    } else {
                        destination.write(original)
                    }
                    destination.closeEntry()
                }
            }
        }
        return tampered
    }

    fun hasSignatureBlockFiles(archive: File): Boolean {
        return ZipFile(archive).use { zip ->
            zip.entries().asSequence().any { entry ->
                val name = entry.name.uppercase()
                name.startsWith("META-INF/") &&
                    (
                        name.endsWith(".RSA") ||
                            name.endsWith(".DSA") ||
                            name.endsWith(".EC")
                        )
            }
        }
    }

    private fun tool(javaHome: File, name: String): File {
        val pathHits = System.getenv("PATH").orEmpty()
            .split(File.pathSeparator)
            .flatMap { directory ->
                listOf(File(directory, name), File(directory, "$name.exe"))
            }
        val candidates = listOf(
            File(javaHome, "bin/$name"),
            File(javaHome, "bin/$name.exe"),
            File(javaHome.parentFile, "bin/$name"),
            File(javaHome.parentFile, "bin/$name.exe"),
        ) + pathHits
        return candidates.firstOrNull { it.isFile }
            ?: error("$name is required to generate an ephemeral test certificate")
    }

    private fun run(vararg command: String) {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText()
        check(finished && process.exitValue() == 0) {
            "Command failed: ${command.joinToString(" ")}\n$output"
        }
    }
}
