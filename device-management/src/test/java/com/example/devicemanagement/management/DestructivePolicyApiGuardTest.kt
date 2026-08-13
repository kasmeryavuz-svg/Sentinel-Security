package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `screen capture wrapper exposes only its approved policy surface`() {
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
    fun `camera wrapper exposes only its approved policy surface`() {
        val exposedOperations = DevicePolicyCameraService::class.java.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()

        assertEquals(
            setOf("isCameraDisabled", "setCameraDisabled"),
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
    fun `public camera status provider is read only`() {
        assertEquals(
            setOf("currentStatus"),
            CameraPolicyStatusProvider::class.java.declaredMethods
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
        assertTrue(
            "Camera read-back must use the expected admin component",
            boundarySource.readText().contains(
                "manager.getCameraDisabled(adminComponent)",
            ),
        )
        assertTrue(
            "Camera mutation must use the expected admin component",
            boundarySource.readText().contains(
                "manager.setCameraDisabled(adminComponent, disabled)",
            ),
        )
        val allowedQueries = setOf(
            "isDeviceOwnerApp",
            "isProfileOwnerApp",
            "isAdminActive",
            "isProvisioningAllowed",
            "getScreenCaptureDisabled",
            "getCameraDisabled",
        )
        val allowedMutators = setOf(
            "setScreenCaptureDisabled",
            "setCameraDisabled",
        )
        assertEquals(
            setOf("setScreenCaptureDisabled", "setCameraDisabled"),
            allowedMutators,
        )
        val requiredVerificationPairs = mapOf(
            "setScreenCaptureDisabled" to "isScreenCaptureDisabled",
            "setCameraDisabled" to "isCameraDisabled",
        )
        val verifiedMutationSource = sourceFiles.single {
            it.name == "VerifiedPolicyMutation.kt"
        }.readText()
        requiredVerificationPairs.forEach { (mutator, readBack) ->
            assertTrue(
                "$mutator must be explicitly paired with $readBack",
                verifiedMutationSource.contains("service.$mutator") &&
                    verifiedMutationSource.contains("service.$readBack"),
            )
        }
        assertFalse(verifiedMutationSource.contains("kotlin.reflect"))
        assertFalse(verifiedMutationSource.contains("java.lang.reflect"))
        assertFalse(verifiedMutationSource.contains("Function<"))
        assertFalse(verifiedMutationSource.contains("Map<String, Any"))
        val nonQueryCalls = Regex(
            """\bmanager\s*\.\s*([A-Za-z][A-Za-z0-9_]*)\s*\(""",
        ).findAll(boundarySource.readText())
            .map { it.groupValues[1] }
            .filterNot { it in allowedQueries || it in allowedMutators }
            .map { "non-allowlisted DevicePolicyManager call: $it" }
            .toList()
        val violations = destructiveCalls + importsOutsideBoundary + nonQueryCalls

        assertTrue(
            "Only allowlisted policy queries and mutators are allowed: $violations",
            violations.isEmpty(),
        )
    }
}
