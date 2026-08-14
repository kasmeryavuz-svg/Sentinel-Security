package com.example.devicemanagement.provisioningqr

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RepositorySigningSecretsGuardTest {
    @Test
    fun `repository does not contain signing secrets or keystore material`() {
        val repoRoot = File(requireNotNull(System.getProperty("repoRoot")))
        assertTrue(repoRoot.isDirectory)

        val forbiddenExtensions = setOf(
            "jks",
            "keystore",
            "p12",
            "pfx",
            "pk8",
            "bks",
        )
        val forbiddenContent = listOf(
            "-----BEGIN PRIVATE KEY-----",
            "-----BEGIN RSA PRIVATE KEY-----",
            "-----BEGIN EC PRIVATE KEY-----",
            "-----BEGIN DSA PRIVATE KEY-----",
            "-----BEGIN OPENSSH PRIVATE KEY-----",
            "-----BEGIN PGP PRIVATE KEY BLOCK-----",
            "storePassword=",
            "keyPassword=",
        )
        val skippedDirectoryNames = setOf(
            ".git",
            ".gradle",
            "build",
            ".idea",
        )
        val secretFiles = repoRoot.walkTopDown()
            .onEnter { directory -> directory.name !in skippedDirectoryNames }
            .filter { it.isFile }
            .mapNotNull { file ->
                val extension = file.extension.lowercase()
                if (extension in forbiddenExtensions) {
                    return@mapNotNull file.relativeTo(repoRoot).path
                }
                if (file.extension.lowercase() in setOf("png", "jpg", "jpeg", "webp", "apk", "aab", "so", "jar")) {
                    return@mapNotNull null
                }
                val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
                val matched = forbiddenContent.firstOrNull { marker -> marker in text }
                if (matched != null) {
                    "${file.relativeTo(repoRoot).path} ($matched)"
                } else {
                    null
                }
            }
            .toList()

        assertTrue(
            "Signing secrets must not be committed: $secretFiles",
            secretFiles.isEmpty(),
        )
    }
}
