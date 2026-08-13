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
        "Rejects unauthorized DevicePolicyManager and dynamic invocation access."

    val productionSources = fileTree(rootDir) {
        include("**/*.kt", "**/*.java")
        exclude(
            "**/src/test/**",
            "**/src/androidTest/**",
            "**/src/testFixtures/**",
            "**/test/**",
            "**/androidTest/**",
            "**/testFixtures/**",
            "**/build/**",
            "**/.gradle/**",
            "**/.git/**",
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

        val forbiddenDynamicPatterns = mapOf(
            "Class.forName" to Regex("""\bClass\s*\.\s*forName\s*\("""),
            "runtime Class API" to Regex("""\bClass\b"""),
            "java.lang.reflect" to Regex("""\bjava\s*\.\s*lang\s*\.\s*reflect\b"""),
            "kotlin.reflect" to Regex("""\bkotlin\s*\.\s*reflect\b"""),
            "reflective method lookup" to
                Regex("""\bget(?:Declared)?(?:Method|Methods)\s*\("""),
            "reflective constructor lookup" to
                Regex("""\bget(?:Declared)?Constructor\s*\("""),
            "reflective field lookup" to
                Regex("""\bget(?:Declared)?Field\s*\("""),
            "reflective invocation" to Regex("""\binvoke\s*\("""),
            "method handles" to Regex(
                """\bjava\s*\.\s*lang\s*\.\s*invoke\b|""" +
                    """\b(?:MethodHandle|MethodHandles|MethodType|CallSite|""" +
                    """LambdaMetafactory)\b""",
            ),
            "dynamic class loader" to Regex(
                """\b(?:ClassLoader|URLClassLoader|DexClassLoader|PathClassLoader|""" +
                    """InMemoryDexClassLoader)\b|\bloadClass\s*\(|""" +
                    """\b(?:System|Runtime)\s*\.\s*(?:load|loadLibrary)\s*\(""",
            ),
        )
        val violations = sourceFiles.flatMap { source ->
            val relativePath = source.relativeTo(rootDir).invariantSeparatorsPath
            val rawSource = source.readText()
            val code = sourceCodeWithoutCommentsOrLiterals(rawSource)
            val dynamicViolations = forbiddenDynamicPatterns.mapNotNull {
                    (description, pattern) ->
                if (pattern.containsMatchIn(code)) {
                    "$relativePath: forbidden production $description"
                } else {
                    null
                }
            }
            val dpmViolation = if (relativePath != authorizedBoundary) {
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
            } else {
                null
            }
            dynamicViolations + listOfNotNull(dpmViolation)
        }
        check(violations.isEmpty()) {
            "Only typed, non-dynamic policy access through $authorizedBoundary is allowed:\n" +
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
