package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DestructivePolicyApiGuardTest {
    @Test
    fun `DevicePolicyManager wrapper exposes query operations only`() {
        val exposedOperations = DevicePolicyReadService::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf(
                "isDeviceOwnerApp",
                "isProfileOwnerApp",
                "isExpectedAdminActive",
                "isDeviceOwnerProvisioningAllowed",
                "isProfileOwnerProvisioningAllowed",
                "activeAdminComponentNames",
            ),
            exposedOperations,
        )
    }

    @Test
    fun `screen capture wrapper exposes the only approved policy mutator`() {
        val exposedOperations = DevicePolicyScreenCaptureService::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf("isScreenCaptureDisabled", "setScreenCaptureDisabled"),
            exposedOperations,
        )
    }

    @Test
    fun `public screen capture status provider is read only`() {
        assertEquals(
            setOf("currentStatus"),
            ScreenCapturePolicyStatusProvider::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
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
            "setLockTaskFeatures",
            "setFactoryResetProtectionPolicy",
            "installPackage",
            "installExistingPackage",
            "uninstallPackage",
            "setApplicationHidden",
            "setPackagesSuspended",
            "setUninstallBlocked",
            "setAccountManagementDisabled",
            "setAlwaysOnVpnPackage",
            "setRecommendedGlobalProxy",
            "setNetworkLoggingEnabled",
            "setSecurityLoggingEnabled",
            "addUserRestriction",
            "clearUserRestriction",
            "setPermissionGrantState",
            "createAndManageUser",
            "switchUser",
            "setProfileEnabled",
            "transferOwnership",
            "setDeviceOwner",
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
        assertTrue(
            "Screen-capture read-back must use the expected admin component",
            boundarySource.readText().contains(
                "manager.getScreenCaptureDisabled(adminComponent)",
            ),
        )
        assertTrue(
            "Screen-capture mutation must use the expected admin component",
            boundarySource.readText().contains(
                "manager.setScreenCaptureDisabled(adminComponent, disabled)",
            ),
        )
        val allowedQueries = setOf(
            "isDeviceOwnerApp",
            "isProfileOwnerApp",
            "isAdminActive",
            "isProvisioningAllowed",
            "getScreenCaptureDisabled",
        )
        val allowedMutators = setOf("setScreenCaptureDisabled")
        val nonQueryCalls = Regex(
            """\bmanager\s*\.\s*([A-Za-z][A-Za-z0-9_]*)\s*\(""",
        ).findAll(boundarySource.readText())
            .map { it.groupValues[1] }
            .filterNot { it in allowedQueries || it in allowedMutators }
            .map { "non-allowlisted DevicePolicyManager call: $it" }
            .toList()
        val violations = destructiveCalls + importsOutsideBoundary + nonQueryCalls

        assertTrue(
            "Only read-only policy queries are allowed: $violations",
            violations.isEmpty(),
        )
    }
}
