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
    testImplementation("junit:junit:4.13.2")
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

val allowedPolicyQueries = setOf(
    "isDeviceOwnerApp",
    "isProfileOwnerApp",
    "isAdminActive",
    "isProvisioningAllowed",
)

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
        val nonQueryCalls = boundarySource?.let { source ->
            Regex("""\bmanager\s*\.\s*([A-Za-z][A-Za-z0-9_]*)\s*\(""")
                .findAll(source.readText())
                .map { it.groupValues[1] }
                .filterNot(allowedPolicyQueries::contains)
                .map {
                    "${source.relativeTo(projectDir)}: non-query DevicePolicyManager call $it"
                }
                .toList()
        }.orEmpty()
        val violations = destructiveCalls + policyImportsOutsideBoundary + nonQueryCalls
        check(violations.isEmpty()) {
            "Only read-only DevicePolicyManager queries are allowed:\n" +
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
}
