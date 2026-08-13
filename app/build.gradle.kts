import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import org.gradle.api.tasks.testing.Test
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.util.jar.JarFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

fun String.isVerificationClasspath(): Boolean {
    return contains("UnitTest") ||
        contains("AndroidTest") ||
        contains("TestFixtures")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.devicemanagement"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.devicemanagement"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
            it.systemProperty(
                "appMainSourceDir",
                layout.projectDirectory.dir("src/main").asFile.absolutePath,
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class")
    }
}

dependencies {
    implementation(project(":device-management"))
    testImplementation("junit:junit:4.13.2")
}

val testKotlinCompileNegativeCompiler by configurations.creating

dependencies {
    testKotlinCompileNegativeCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.10")
}

val checkAppDependencyIsolation by tasks.registering {
    group = "verification"
    description =
        "Proves mutation implementations are absent from app compile classpaths and packaged at runtime."

    doLast {
        val forbiddenCompileProjects = setOf(
            ":device-management-impl",
            ":sensitive-actions",
        )
        val productionCompileConfigurations = configurations
            .filter {
                it.isCanBeResolved &&
                    it.name.endsWith("CompileClasspath", ignoreCase = true) &&
                    !it.name.isVerificationClasspath()
            }
        val implementationLeaks = productionCompileConfigurations
            .flatMap { configuration ->
                configuration.incoming.resolutionResult.allComponents.mapNotNull { component ->
                    val projectPath =
                        (component.id as? ProjectComponentIdentifier)?.projectPath
                    if (projectPath in forbiddenCompileProjects) {
                        "${configuration.name}: $projectPath"
                    } else {
                        null
                    }
                }
            }
        check(implementationLeaks.isEmpty()) {
            "Mutation implementation leaked onto app compile classpath:\n" +
                implementationLeaks.joinToString("\n")
        }
        productionCompileConfigurations.forEach { configuration ->
                val visibleProjects = configuration.incoming.resolutionResult.allComponents
                    .mapNotNull {
                        (it.id as? ProjectComponentIdentifier)?.projectPath
                    }
                    .filterNot { it == project.path }
                    .toSet()
                val expectedProjects = setOf(
                    ":device-management",
                    ":device-management-api",
                    ":sensitive-actions-api",
                )
                check(visibleProjects == expectedProjects) {
                    "${configuration.name} has unexpected project API surface; " +
                        "expected $expectedProjects, found $visibleProjects"
                }
            }

        val expectedRuntimeProjects = setOf(
            ":device-management",
            ":device-management-api",
            ":device-management-impl",
            ":sensitive-actions-api",
            ":sensitive-actions",
        )
        val productionRuntimeConfigurations = configurations.filter {
            it.isCanBeResolved &&
                it.name.endsWith("RuntimeClasspath", ignoreCase = true) &&
                !it.name.isVerificationClasspath()
        }
        productionRuntimeConfigurations.forEach { configuration ->
            val runtimeProjects = configuration.incoming.resolutionResult.allComponents
                .mapNotNull {
                    (it.id as? ProjectComponentIdentifier)?.projectPath
                }
                .filterNot { it == project.path }
                .toSet()
            check(runtimeProjects == expectedRuntimeProjects) {
                "${configuration.name} runtime project surface changed; " +
                    "expected $expectedRuntimeProjects, found $runtimeProjects"
            }
        }

        val allowedExternalModules = setOf(
            "org.jetbrains.kotlin:kotlin-stdlib",
            "org.jetbrains:annotations",
        )
        (productionCompileConfigurations + productionRuntimeConfigurations)
            .forEach { configuration ->
                val externalModules = configuration.incoming.resolutionResult.allComponents
                    .mapNotNull {
                        val id = it.id as? ModuleComponentIdentifier
                        id?.let { module -> "${module.group}:${module.module}" }
                    }
                    .toSet()
                check(externalModules == allowedExternalModules) {
                    "${configuration.name} has unapproved external production artifacts; " +
                        "expected $allowedExternalModules, found $externalModules"
                }
            }
    }
}

val checkAppApiCompileNegative by tasks.registering {
    group = "verification"
    description = "Compiles adversarial Java and Kotlin app snippets against the app compile classpath."
    val javaNegative = fileTree("src/compileNegative/java") { include("**/*.java") }
    val kotlinNegative = fileTree("src/compileNegative/kotlin") { include("**/*.kt") }
    val javaPositive = fileTree("src/compilePositive/java") { include("**/*.java") }
    val debugJavaCompile = tasks.named<JavaCompile>("compileDebugJavaWithJavac")
    inputs.files(
        javaNegative,
        kotlinNegative,
        javaPositive,
        debugJavaCompile.map { it.classpath },
    )

    doLast {
        val compileFiles = debugJavaCompile.get().classpath.files
        val compileClasspath = (compileFiles + android.bootClasspath)
            .joinToString(File.pathSeparator) { it.absolutePath }
        val outputRoot = layout.buildDirectory.dir("compile-negative").get().asFile
        outputRoot.deleteRecursively()
        outputRoot.mkdirs()
        val javac = File(System.getProperty("java.home"), "bin/javac")

        val approvedTopLevelClasses = setOf(
            "com/example/devicemanagement/logging/StructuredLogger",
            "com/example/devicemanagement/action/ActionResult",
            "com/example/devicemanagement/action/SensitiveActionOperation",
            "com/example/devicemanagement/action/SensitiveActionController",
            "com/example/devicemanagement/trigger/SensitiveActionCommands",
            "com/example/devicemanagement/trigger/Trigger",
            "com/example/devicemanagement/management/DeviceManagement",
            "com/example/devicemanagement/management/DeviceManagementServices",
            "com/example/devicemanagement/management/ManagementMode",
            "com/example/devicemanagement/management/ManagementCapability",
            "com/example/devicemanagement/management/DeviceManagementStatus",
            "com/example/devicemanagement/management/DeviceManagementStatusProvider",
            "com/example/devicemanagement/management/ProvisioningAvailability",
            "com/example/devicemanagement/management/ProvisioningOption",
            "com/example/devicemanagement/management/ProvisioningReadiness",
            "com/example/devicemanagement/management/ProvisioningReadinessProvider",
            "com/example/devicemanagement/management/DeviceOwnerValidationResult",
            "com/example/devicemanagement/management/DeviceOwnerValidation",
            "com/example/devicemanagement/management/DeviceOwnerValidationProvider",
            "com/example/devicemanagement/management/ScreenCapturePolicyState",
            "com/example/devicemanagement/management/ScreenCapturePolicyStatus",
            "com/example/devicemanagement/management/ScreenCapturePolicyStatusProvider",
            "com/example/devicemanagement/management/CameraPolicyState",
            "com/example/devicemanagement/management/CameraPolicyStatus",
            "com/example/devicemanagement/management/CameraPolicyStatusProvider",
            "com/example/devicemanagement/facade/R",
        )
        val appBuildDirectory = layout.buildDirectory.get().asFile.canonicalFile
        val appVisibleProjectClasses = compileFiles
            .filterNot { root ->
                root.canonicalFile.toPath().startsWith(appBuildDirectory.toPath())
            }
            .flatMap { root ->
                when {
                    root.isDirectory -> root.walkTopDown()
                        .filter { it.isFile && it.extension == "class" }
                        .map {
                            it.relativeTo(root).invariantSeparatorsPath
                                .removeSuffix(".class")
                        }
                        .toList()
                    root.isFile && root.extension == "jar" -> JarFile(root).use { jar ->
                        jar.entries().asSequence()
                            .filter { !it.isDirectory && it.name.endsWith(".class") }
                            .map { it.name.removeSuffix(".class") }
                            .toList()
                    }
                    else -> emptyList()
                }
            }
            .filter { it.startsWith("com/example/devicemanagement/") }
        val unapprovedClasses = appVisibleProjectClasses.filter { className ->
            className.substringBefore('$') !in approvedTopLevelClasses
        }
        check(unapprovedClasses.isEmpty()) {
            "App compile classpath exposes non-allowlisted JVM classes:\n" +
                unapprovedClasses.sorted().joinToString("\n")
        }

        fun compileJava(source: File): Pair<Int, String> {
            val output = ByteArrayOutputStream()
            val result = project.exec {
                commandLine(
                    javac.absolutePath,
                    "-proc:none",
                    "-source",
                    "17",
                    "-target",
                    "17",
                    "-classpath",
                    compileClasspath,
                    "-d",
                    File(outputRoot, source.nameWithoutExtension).absolutePath,
                    source.absolutePath,
                )
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
            return result.exitValue to output.toString(Charsets.UTF_8)
        }

        javaPositive.files.forEach { source ->
            val (exitCode, output) = compileJava(source)
            check(exitCode == 0) {
                "Public facade control failed to compile: ${source.path}\n$output"
            }
        }
        javaNegative.files.forEach { source ->
            val (exitCode, output) = compileJava(source)
            check(exitCode != 0) {
                "Forbidden Java app composition unexpectedly compiled: ${source.path}\n$output"
            }
        }
        kotlinNegative.files.forEach { source ->
            val output = ByteArrayOutputStream()
            val result = project.javaexec {
                classpath = testKotlinCompileNegativeCompiler
                mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
                args(
                    "-classpath",
                    compileClasspath,
                    "-d",
                    File(outputRoot, source.nameWithoutExtension).absolutePath,
                    source.absolutePath,
                )
                standardOutput = output
                errorOutput = output
                isIgnoreExitValue = true
            }
            check(result.exitValue != 0) {
                "Forbidden Kotlin app composition unexpectedly compiled: ${source.path}\n" +
                    output.toString(Charsets.UTF_8)
            }
        }
    }
}

fun secureDocument(file: File) = DocumentBuilderFactory.newInstance().apply {
    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    isNamespaceAware = true
    isExpandEntityReferences = false
}.newDocumentBuilder().parse(file)

fun org.w3c.dom.NodeList.elements(): List<Element> {
    return (0 until length).map(::item).filterIsInstance<Element>()
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val variantName = variant.name
        val capitalized = variantName.replaceFirstChar { it.uppercaseChar() }
        val policyGuard = tasks.register<ProductionBytecodePolicyTask>(
            "check${capitalized}ProductionBytecodePolicy",
        ) {
            group = "verification"
            description =
                "Verifies compiled $variantName app classes against production policy boundaries."
            artifactPath.set(project.path)
            mergedNativeLibraries.from(
                variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS),
            )
            productionFiles.from(fileTree("src") {
                exclude("test/**", "androidTest/**", "testFixtures/**")
            })
        }
        variant.artifacts
            .forScope(ScopedArtifacts.Scope.PROJECT)
            .use(policyGuard)
            .toGet(
                ScopedArtifact.CLASSES,
                ProductionBytecodePolicyTask::classJars,
                ProductionBytecodePolicyTask::classDirectories,
            )
        rootProject.tasks.named("checkProductionBytecodePolicy").configure {
            dependsOn(policyGuard)
        }
        val mergedManifestArtifact =
            variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val guardTask = tasks.register(
            "check${capitalized}EffectiveDeviceAdminMetadata",
        ) {
            group = "verification"
            description =
                "Validates effective DeviceAdmin metadata for $variantName."
            dependsOn("process${capitalized}Resources")
            inputs.file(mergedManifestArtifact)

            doLast {
                val androidNamespace =
                    "http://schemas.android.com/apk/res/android"
                val approvedPolicies = setOf("disable-camera")
                val expectedReceiver =
                    "com.example.devicemanagement.management." +
                        "SentinelDeviceAdminReceiver"
                val aapt2 = file(
                    "${android.sdkDirectory}/build-tools/" +
                        "${android.buildToolsVersion}/aapt2",
                )
                check(aapt2.isFile) { "aapt2 is unavailable at $aapt2" }

                val mergedManifest = mergedManifestArtifact.get().asFile
                check(mergedManifest.isFile) {
                    "Merged $variantName manifest is unavailable at $mergedManifest"
                }
                val manifest = secureDocument(mergedManifest)
                val deviceAdminReceivers = manifest.getElementsByTagName("receiver")
                    .elements()
                    .filter { candidate ->
                        candidate.getAttributeNS(androidNamespace, "permission") ==
                            "android.permission.BIND_DEVICE_ADMIN" ||
                            candidate.getElementsByTagName("meta-data")
                                .elements()
                                .any {
                                    it.getAttributeNS(androidNamespace, "name") ==
                                        "android.app.device_admin"
                                } ||
                            candidate.getElementsByTagName("action")
                                .elements()
                                .any {
                                    it.getAttributeNS(androidNamespace, "name") ==
                                        "android.app.action.DEVICE_ADMIN_ENABLED"
                                }
                    }
                check(deviceAdminReceivers.size == 1) {
                    "Effective $variantName manifest must contain exactly one " +
                        "DeviceAdmin receiver; found ${deviceAdminReceivers.size}"
                }
                val metadataElements = manifest.getElementsByTagName("meta-data")
                    .elements()
                    .filter {
                        it.getAttributeNS(androidNamespace, "name") ==
                            "android.app.device_admin"
                    }
                check(metadataElements.size == 1) {
                    "Effective $variantName manifest must contain exactly one " +
                        "android.app.device_admin metadata declaration"
                }
                val metadata = metadataElements.single()
                val receiver = checkNotNull(metadata.parentNode as? Element) {
                    "Effective $variantName DeviceAdmin metadata must belong " +
                        "to a receiver"
                }
                check(receiver.tagName == "receiver")
                check(
                    receiver.getAttributeNS(androidNamespace, "name") ==
                        expectedReceiver,
                ) {
                    "Effective $variantName DeviceAdmin metadata resolved to " +
                        "an unexpected receiver"
                }
                check(receiver == deviceAdminReceivers.single()) {
                    "Effective $variantName DeviceAdmin metadata belongs to an " +
                        "unapproved receiver"
                }
                check(
                    receiver.getAttributeNS(androidNamespace, "permission") ==
                        "android.permission.BIND_DEVICE_ADMIN",
                ) {
                    "Effective $variantName DeviceAdmin receiver permission changed"
                }
                check(
                    receiver.getAttributeNS(androidNamespace, "exported") == "true" &&
                        receiver.getAttributeNS(androidNamespace, "enabled") != "false",
                ) {
                    "Effective $variantName DeviceAdmin receiver is disabled or not exported"
                }
                val enableActions = receiver.getElementsByTagName("action")
                    .elements()
                    .map { it.getAttributeNS(androidNamespace, "name") }
                check(
                    enableActions ==
                        listOf("android.app.action.DEVICE_ADMIN_ENABLED"),
                ) {
                    "Effective $variantName DeviceAdmin receiver actions changed: " +
                        enableActions
                }
                check(
                    metadata.getAttributeNS(androidNamespace, "resource") ==
                        "@xml/device_admin_receiver",
                ) {
                    "Effective $variantName DeviceAdminReceiver must resolve " +
                        "@xml/device_admin_receiver"
                }

                val linkedResources = layout.buildDirectory.file(
                    "intermediates/linked_resources_binary_format/$variantName/" +
                        "process${capitalized}Resources/" +
                        "linked-resources-binary-format-$variantName.ap_",
                ).get().asFile
                check(linkedResources.isFile) {
                    "Linked $variantName resources are unavailable at " +
                        linkedResources
                }
                val output = ByteArrayOutputStream()
                project.exec {
                    commandLine(
                        aapt2.absolutePath,
                        "dump",
                        "xmltree",
                        linkedResources.absolutePath,
                        "--file",
                        "res/xml/device_admin_receiver.xml",
                    )
                    standardOutput = output
                }
                val elementLines = output.toString(Charsets.UTF_8)
                    .lineSequence()
                    .mapNotNull { line ->
                        val match = Regex(
                            """^(\s*)E:\s+([A-Za-z0-9_-]+)\b""",
                        ).find(line) ?: return@mapNotNull null
                        match.groupValues[1].length to match.groupValues[2]
                    }
                    .toList()
                check(elementLines.firstOrNull()?.second == "device-admin") {
                    "Effective $variantName metadata root must be device-admin"
                }
                val usesPoliciesIndex = elementLines.indexOfFirst {
                    it.second == "uses-policies"
                }
                check(usesPoliciesIndex >= 0)
                check(elementLines.count { it.second == "uses-policies" } == 1)
                val usesPoliciesIndent = elementLines[usesPoliciesIndex].first
                val policyElements = elementLines
                    .drop(usesPoliciesIndex + 1)
                    .takeWhile { it.first > usesPoliciesIndent }
                    .map { it.second }
                check(
                    policyElements.toSet() == approvedPolicies &&
                        policyElements.size == approvedPolicies.size,
                ) {
                    "Effective $variantName DeviceAdmin policies must be " +
                        "exactly $approvedPolicies; found $policyElements"
                }
                val approvedElements = setOf(
                    "device-admin",
                    "uses-policies",
                    "disable-camera",
                )
                val unapprovedElements =
                    elementLines.map { it.second }.toSet() - approvedElements
                check(unapprovedElements.isEmpty()) {
                    "Effective $variantName metadata contains unapproved " +
                        "elements: $unapprovedElements"
                }
            }
        }

        tasks.matching {
            it.name == "assemble$capitalized" ||
                it.name == "bundle$capitalized" ||
                it.name == "package$capitalized" ||
                it.name == "test${capitalized}UnitTest" ||
                it.name == "check$capitalized" ||
                it.name == "check"
        }.configureEach {
            dependsOn(guardTask, policyGuard)
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(checkAppDependencyIsolation, checkAppApiCompileNegative)
    systemProperty(
        "appSourceDir",
        layout.projectDirectory.dir("src").asFile.absolutePath,
    )
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(checkAppDependencyIsolation, checkAppApiCompileNegative)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(checkAppDependencyIsolation)
}
