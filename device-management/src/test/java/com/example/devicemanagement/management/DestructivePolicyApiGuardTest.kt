package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DestructivePolicyApiGuardTest {
    @Test
    fun `DevicePolicyManager wrapper exposes query operations only`() {
        val exposedOperations = DevicePolicyReadService::class.java.declaredMethods
            .map { it.name }
            .toSet()

        assertEquals(
            setOf(
                "isDeviceOwnerApp",
                "isProfileOwnerApp",
                "isExpectedAdminActive",
                "isDeviceOwnerProvisioningAllowed",
                "isProfileOwnerProvisioningAllowed",
            ),
            exposedOperations,
        )
    }

    @Test
    fun `production module contains no destructive policy operation calls`() {
        val sourceDirectory = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
        )
        val forbiddenOperations = listOf(
            "wipeData",
            "wipeDevice",
            "reboot",
            "resetPassword",
            "resetPasswordWithToken",
            "clearApplicationUserData",
            "removeUser",
            "removeUserWhenPossible",
            "logoutUser",
            "lockNow",
            "setLockTaskPackages",
            "setFactoryResetProtectionPolicy",
        )
        val sourceFiles = sourceDirectory.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .toList()
        val destructiveCalls = sourceFiles.asSequence()
            .flatMap { source ->
                forbiddenOperations.asSequence().mapNotNull { operation ->
                    val callPattern = Regex("""\b${Regex.escape(operation)}\s*\(""")
                    if (callPattern.containsMatchIn(source.readText())) {
                        "${source.relativeTo(sourceDirectory)}: $operation"
                    } else {
                        null
                    }
                }
            }
            .toList()
        val importsOutsideBoundary = sourceFiles.mapNotNull { source ->
            if (
                source.readText().contains("import android.app.admin.DevicePolicyManager") &&
                source.name != "AndroidDeviceManagementInfrastructure.kt"
            ) {
                "${source.relativeTo(sourceDirectory)}: policy import outside boundary"
            } else {
                null
            }
        }
        val boundarySource = sourceFiles.single {
            it.name == "AndroidDeviceManagementInfrastructure.kt"
        }
        val allowedQueries = setOf(
            "isDeviceOwnerApp",
            "isProfileOwnerApp",
            "isAdminActive",
            "isProvisioningAllowed",
        )
        val nonQueryCalls = Regex(
            """\bmanager\s*\.\s*([A-Za-z][A-Za-z0-9_]*)\s*\(""",
        ).findAll(boundarySource.readText())
            .map { it.groupValues[1] }
            .filterNot(allowedQueries::contains)
            .map { "non-query DevicePolicyManager call: $it" }
            .toList()
        val violations = destructiveCalls + importsOutsideBoundary + nonQueryCalls

        assertTrue(
            "Only read-only policy queries are allowed: $violations",
            violations.isEmpty(),
        )
    }
}
