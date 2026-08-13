import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.devicemanagement.management"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":sensitive-actions"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("reflect"))
}

val destructivePolicyOperations = listOf(
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

val verifiedPolicyMutationPairs = mapOf(
    "setScreenCaptureDisabled" to
        ("getScreenCaptureDisabled" to "isScreenCaptureDisabled"),
    "setCameraDisabled" to ("getCameraDisabled" to "isCameraDisabled"),
)

val allowedPolicyQueries = setOf(
    "isDeviceOwnerApp",
    "isProfileOwnerApp",
    "isAdminActive",
    "isProvisioningAllowed",
    "getScreenCaptureDisabled",
    "getCameraDisabled",
)

val allowedPolicyMutators = verifiedPolicyMutationPairs.keys

val checkNoDestructiveDevicePolicyApis by tasks.registering {
    group = "verification"
    description = "Rejects destructive policy APIs in production sources."
    val productionSources = fileTree("src/main") {
        include("**/*.kt", "**/*.java")
    }
    inputs.files(productionSources)

    doLast {
        val destructiveCalls = productionSources.files.flatMap { source ->
            destructivePolicyOperations.mapNotNull { operation ->
                val callPattern = Regex("""\b${Regex.escape(operation)}\s*\(""")
                if (callPattern.containsMatchIn(source.readText())) {
                    "${source.relativeTo(projectDir)}: $operation"
                } else {
                    null
                }
            }
        }
        val policyImportsOutsideBoundary = productionSources.files.mapNotNull { source ->
            val importsPolicyManager =
                source.readText().contains("import android.app.admin.DevicePolicyManager")
            if (
                importsPolicyManager &&
                source.name != "AndroidDeviceManagementInfrastructure.kt"
            ) {
                "${source.relativeTo(projectDir)}: DevicePolicyManager import outside boundary"
            } else {
                null
            }
        }
        val boundarySource = productionSources.files.singleOrNull {
            it.name == "AndroidDeviceManagementInfrastructure.kt"
        }
        val verifiedMutationSource = productionSources.files.singleOrNull {
            it.name == "VerifiedPolicyMutation.kt"
        }
        val nonQueryCalls = boundarySource?.let { source ->
            val sourceText = source.readText()
            val dpmReceivers = Regex(
                """\b(?:val|var)\s+([A-Za-z][A-Za-z0-9_]*)\s*:\s*DevicePolicyManager\b""",
            ).findAll(sourceText)
                .map { it.groupValues[1] }
                .toSet()
            check(dpmReceivers.isNotEmpty()) {
                "Authorized boundary must declare a typed DevicePolicyManager receiver"
            }
            dpmReceivers.asSequence()
                .flatMap { receiver ->
                    Regex(
                        """\b${Regex.escape(receiver)}\s*\.\s*""" +
                            """([A-Za-z][A-Za-z0-9_]*)\s*\(""",
                    ).findAll(sourceText)
                        .map { it.groupValues[1] }
                }
                .filterNot { call ->
                    call in allowedPolicyQueries || call in allowedPolicyMutators
                }
                .map {
                    "${source.relativeTo(projectDir)}: non-allowlisted DevicePolicyManager call $it"
                }
                .toList()
        }.orEmpty()
        val missingVerificationPairs = verifiedPolicyMutationPairs.mapNotNull {
                (mutator, readBackPair) ->
            val (dpmReadBack, typedReadBack) = readBackPair
            val text = verifiedMutationSource?.readText().orEmpty()
            if (
                !Regex("""\b${Regex.escape(mutator)}\s*\(""").containsMatchIn(text) ||
                !Regex("""\b${Regex.escape(typedReadBack)}\s*\(""").containsMatchIn(text)
            ) {
                "VerifiedPolicyMutation.kt: $mutator must be paired with " +
                    "$typedReadBack ($dpmReadBack)"
            } else {
                null
            }
        }
        val violations = destructiveCalls +
            policyImportsOutsideBoundary +
            nonQueryCalls +
            missingVerificationPairs
        check(violations.isEmpty()) {
            "Only allowlisted DevicePolicyManager queries and mutators are allowed:\n" +
                violations.joinToString("\n")
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(checkNoDestructiveDevicePolicyApis)
}

tasks.withType<Test>().configureEach {
    dependsOn(checkNoDestructiveDevicePolicyApis)
    systemProperty(
        "deviceManagementSourceDir",
        layout.projectDirectory.dir("src/main").asFile.absolutePath,
    )
    systemProperty(
        "deviceAdminMetadataFile",
        layout.projectDirectory
            .file("src/main/res/xml/device_admin_receiver.xml")
            .asFile
            .absolutePath,
    )
}
