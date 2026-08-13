package com.example.devicemanagement.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppCompositionBoundaryTest {
    @Test
    fun `app production sources cannot compose controlled sensitive actions`() {
        val appSourceDirectory = File(
            requireNotNull(System.getProperty("appSourceDir")),
        )
        val productionSources = appSourceDirectory.walkTopDown()
            .filter {
                it.isFile &&
                    it.extension in setOf("kt", "java") &&
                    it.relativeTo(appSourceDirectory).invariantSeparatorsPath
                        .substringBefore('/') !in
                    setOf("test", "androidTest", "testFixtures")
            }
            .toList()
        val forbiddenTokens = setOf(
            "SensitiveActionPolicyBackend",
            "SensitiveActionCompositionApi",
            "SensitiveActionAuthorization",
            "PolicyMutationResult",
            "MonotonicTimeSource",
            "createControlled",
            "createControlledInternal",
        )
        val violations = productionSources.flatMap { source ->
            forbiddenTokens.mapNotNull { token ->
                if (source.readText().contains(token)) {
                    "${source.relativeTo(appSourceDirectory)}: $token"
                } else {
                    null
                }
            }
        }

        assertTrue(
            "App production source must use the device-management facade: $violations",
            violations.isEmpty(),
        )
    }
}
