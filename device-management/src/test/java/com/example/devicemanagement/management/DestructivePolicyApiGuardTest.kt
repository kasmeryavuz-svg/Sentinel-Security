package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.net.URI
import java.util.jar.JarFile

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
        val boundaryText = boundarySource.readText()
        val dpmReceivers = Regex(
            """\b(?:val|var)\s+([A-Za-z][A-Za-z0-9_]*)\s*:\s*DevicePolicyManager\b""",
        ).findAll(boundaryText)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(
            "Authorized boundary must declare a typed DevicePolicyManager receiver",
            dpmReceivers.isNotEmpty(),
        )
        val nonQueryCalls = dpmReceivers.asSequence()
            .flatMap { receiver ->
                Regex(
                    """\b${Regex.escape(receiver)}\s*\.\s*""" +
                        """([A-Za-z][A-Za-z0-9_]*)\s*\(""",
                ).findAll(boundaryText)
                    .map { it.groupValues[1] }
            }
            .filterNot { it in allowedQueries || it in allowedMutators }
            .map { "non-allowlisted DevicePolicyManager call: $it" }
            .toList()
        val violations = destructiveCalls + importsOutsideBoundary + nonQueryCalls

        assertTrue(
            "Only allowlisted policy queries and mutators are allowed: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun `compiled DevicePolicyManager calls use the exact allowlist`() {
        val allowedQueries = setOf(
            "isDeviceOwnerApp",
            "isProfileOwnerApp",
            "isAdminActive",
            "isProvisioningAllowed",
            "getActiveAdmins",
            "getScreenCaptureDisabled",
            "getCameraDisabled",
        )
        val allowedMutators = setOf(
            "setScreenCaptureDisabled",
            "setCameraDisabled",
        )
        val compiledCalls = productionClassBytes().flatMap { (className, bytes) ->
            methodReferences(bytes)
                .filter { it.owner == DEVICE_POLICY_MANAGER_INTERNAL_NAME }
                .map { reference ->
                    CompiledPolicyCall(
                        className = className,
                        methodName = reference.name,
                        sourceBoundaryMarkerPresent = bytes.toString(Charsets.ISO_8859_1)
                            .contains("AndroidDeviceManagementInfrastructure.kt"),
                    )
                }
        }

        assertTrue("Expected compiled DevicePolicyManager calls", compiledCalls.isNotEmpty())
        assertTrue(
            "DPM calls must originate from the authorized source boundary: $compiledCalls",
            compiledCalls.all(CompiledPolicyCall::sourceBoundaryMarkerPresent),
        )
        val unapprovedCalls = compiledCalls.filter {
            it.methodName !in allowedQueries && it.methodName !in allowedMutators
        }
        assertTrue(
            "Compiled non-allowlisted DevicePolicyManager calls: $unapprovedCalls",
            unapprovedCalls.isEmpty(),
        )
        assertEquals(
            allowedMutators,
            compiledCalls.map(CompiledPolicyCall::methodName)
                .filter { it in allowedMutators }
                .toSet(),
        )
    }

    private fun productionClassBytes(): List<Pair<String, ByteArray>> {
        val location = File(
            URI(AndroidDevicePolicyPlatform::class.java.protectionDomain.codeSource.location.toString()),
        )
        val packagePath = "com/example/devicemanagement/management/"
        return if (location.isDirectory) {
            val packageDirectory = File(location, packagePath)
            packageDirectory.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .map { file ->
                    file.relativeTo(location).invariantSeparatorsPath to file.readBytes()
                }
                .toList()
        } else {
            JarFile(location).use { jar ->
                jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith(packagePath) }
                    .filter { it.name.endsWith(".class") }
                    .map { entry ->
                        entry.name to jar.getInputStream(entry).use { it.readBytes() }
                    }
                    .toList()
            }
        }
    }

    private fun methodReferences(classBytes: ByteArray): List<MethodReference> {
        DataInputStream(ByteArrayInputStream(classBytes)).use { input ->
            check(input.readInt() == CLASS_FILE_MAGIC)
            input.readUnsignedShort()
            input.readUnsignedShort()
            val constantPool = arrayOfNulls<ConstantPoolEntry>(input.readUnsignedShort())
            var index = 1
            while (index < constantPool.size) {
                when (val tag = input.readUnsignedByte()) {
                    1 -> constantPool[index] = Utf8Entry(input.readUTF())
                    3, 4 -> input.skipBytes(4)
                    5, 6 -> {
                        input.skipBytes(8)
                        index += 1
                    }
                    7 -> constantPool[index] = ClassEntry(input.readUnsignedShort())
                    8, 16, 19, 20 -> input.skipBytes(2)
                    9, 10, 11 -> constantPool[index] = ReferenceEntry(
                        tag = tag,
                        classIndex = input.readUnsignedShort(),
                        nameAndTypeIndex = input.readUnsignedShort(),
                    )
                    12 -> constantPool[index] = NameAndTypeEntry(
                        nameIndex = input.readUnsignedShort(),
                        descriptorIndex = input.readUnsignedShort(),
                    )
                    15 -> input.skipBytes(3)
                    17, 18 -> input.skipBytes(4)
                    else -> error("Unsupported class-file constant-pool tag: $tag")
                }
                index += 1
            }
            return constantPool.filterIsInstance<ReferenceEntry>()
                .filter { it.tag == 10 || it.tag == 11 }
                .map { reference ->
                    val ownerEntry = constantPool[reference.classIndex] as ClassEntry
                    val owner = (constantPool[ownerEntry.nameIndex] as Utf8Entry).value
                    val nameAndType =
                        constantPool[reference.nameAndTypeIndex] as NameAndTypeEntry
                    val name = (constantPool[nameAndType.nameIndex] as Utf8Entry).value
                    MethodReference(owner = owner, name = name)
                }
        }
    }

    private sealed interface ConstantPoolEntry

    private data class Utf8Entry(val value: String) : ConstantPoolEntry

    private data class ClassEntry(val nameIndex: Int) : ConstantPoolEntry

    private data class NameAndTypeEntry(
        val nameIndex: Int,
        val descriptorIndex: Int,
    ) : ConstantPoolEntry

    private data class ReferenceEntry(
        val tag: Int,
        val classIndex: Int,
        val nameAndTypeIndex: Int,
    ) : ConstantPoolEntry

    private data class MethodReference(
        val owner: String,
        val name: String,
    )

    private data class CompiledPolicyCall(
        val className: String,
        val methodName: String,
        val sourceBoundaryMarkerPresent: Boolean,
    )

    private companion object {
        const val CLASS_FILE_MAGIC = -889275714
        const val DEVICE_POLICY_MANAGER_INTERNAL_NAME =
            "android/app/admin/DevicePolicyManager"
    }
}
