import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.tasks.testing.Test
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

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
    }
}

dependencies {
    implementation(project(":device-management"))
    testImplementation("junit:junit:4.13.2")
}

val checkNoSensitiveActionComposition by tasks.registering {
    group = "verification"
    description = "Prevents app source from constructing controlled sensitive actions."
    val productionSources = fileTree("src") {
        include("**/*.kt", "**/*.java")
        exclude("test/**", "androidTest/**", "testFixtures/**")
    }
    inputs.files(productionSources)

    doLast {
        val forbiddenTokens = setOf(
            "DeviceManagementSensitiveActionControllerFactory",
            "SensitiveActionPolicyBackend",
            "SensitiveActionAuthorization",
            "PolicyMutationResult",
            "MonotonicTimeSource",
            "createControlled",
            "createControlledInternal",
        )
        val violations = productionSources.files.flatMap { source ->
            forbiddenTokens.mapNotNull { token ->
                if (source.readText().contains(token)) {
                    "${source.relativeTo(projectDir)}: forbidden composition token $token"
                } else {
                    null
                }
            }
        }
        check(violations.isEmpty()) {
            "App production source must use DeviceManagementSensitiveActions only:\n" +
                violations.joinToString("\n")
        }
        val implementationLeaks = configurations
            .filter {
                it.isCanBeResolved &&
                    it.name.endsWith("CompileClasspath", ignoreCase = true)
            }
            .flatMap { configuration ->
                configuration.incoming.resolutionResult.allComponents.mapNotNull { component ->
                    val identifier = component.id as? ProjectComponentIdentifier
                    if (identifier?.projectPath == ":sensitive-actions") {
                        "${configuration.name}: ${identifier.projectPath}"
                    } else {
                        null
                    }
                }
            }
        check(implementationLeaks.isEmpty()) {
            "Sensitive-action implementation leaked onto app compile classpath:\n" +
                implementationLeaks.joinToString("\n")
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
                it.name == "test${capitalized}UnitTest" ||
                it.name == "check"
        }.configureEach {
            dependsOn(guardTask)
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(checkNoSensitiveActionComposition)
    systemProperty(
        "appSourceDir",
        layout.projectDirectory.dir("src").asFile.absolutePath,
    )
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(checkNoSensitiveActionComposition)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(checkNoSensitiveActionComposition)
}
