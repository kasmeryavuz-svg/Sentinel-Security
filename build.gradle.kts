import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
}

fun sourceCodeWithoutCommentsOrLiterals(source: String): String {
    return source
        .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), " ")
        .replace(Regex("\"(?:\\\\.|[^\"\\\\])*\""), " ")
        .replace(Regex("'(?:\\\\.|[^'\\\\])'"), " ")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
        .replace(Regex("//[^\\r\\n]*"), " ")
}

val checkDevicePolicyManagerBoundary by tasks.registering {
    group = "verification"
    description =
        "Rejects DevicePolicyManager references outside the sole infrastructure boundary."

    val productionSources = fileTree(rootDir) {
        include("**/src/**/*.kt", "**/src/**/*.java")
        exclude(
            "**/src/test/**",
            "**/src/androidTest/**",
            "**/src/testFixtures/**",
            "**/build/**",
            "**/.gradle/**",
        )
    }
    val authorizedBoundary =
        "device-management/src/main/java/com/example/devicemanagement/management/" +
            "AndroidDeviceManagementInfrastructure.kt"
    inputs.files(productionSources)

    doLast {
        val sourceFiles = productionSources.files.filter { it.isFile }
        val boundaryFiles = sourceFiles.filter {
            it.relativeTo(rootDir).invariantSeparatorsPath == authorizedBoundary
        }
        check(boundaryFiles.size == 1) {
            "Expected exactly one DevicePolicyManager boundary at $authorizedBoundary"
        }

        val violations = sourceFiles.mapNotNull { source ->
            val relativePath = source.relativeTo(rootDir).invariantSeparatorsPath
            if (relativePath == authorizedBoundary) {
                null
            } else {
                val rawSource = source.readText()
                val code = sourceCodeWithoutCommentsOrLiterals(rawSource)
                val directReference =
                    Regex("""\bDevicePolicyManager\b""").containsMatchIn(code)
                val reflectiveReference = rawSource.contains(
                    "android.app.admin.DevicePolicyManager",
                )
                if (directReference || reflectiveReference) {
                    "$relativePath: DevicePolicyManager reference outside authorized boundary"
                } else {
                    null
                }
            }
        }
        check(violations.isEmpty()) {
            "Only $authorizedBoundary may reference DevicePolicyManager:\n" +
                violations.joinToString("\n")
        }
    }
}

subprojects {
    fun wireAndroidBoundaryGuard() {
        tasks.matching { it.name == "preBuild" }.configureEach {
            dependsOn(rootProject.tasks.named("checkDevicePolicyManagerBoundary"))
        }
        tasks.withType<Test>().configureEach {
            dependsOn(rootProject.tasks.named("checkDevicePolicyManagerBoundary"))
        }
    }

    pluginManager.withPlugin("com.android.application") {
        wireAndroidBoundaryGuard()
    }
    pluginManager.withPlugin("com.android.library") {
        wireAndroidBoundaryGuard()
    }
}
