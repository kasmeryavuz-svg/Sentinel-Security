import org.gradle.api.tasks.testing.Test
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
    implementation(project(":sensitive-actions"))
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
            "SensitiveActionPolicyBackend",
            "SensitiveActionCompositionApi",
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
    }
}

fun secureDocument(file: File) = DocumentBuilderFactory.newInstance().apply {
    setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    isExpandEntityReferences = false
}.newDocumentBuilder().parse(file)

fun org.w3c.dom.NodeList.elements(): List<Element> {
    return (0 until length).map(::item).filterIsInstance<Element>()
}

val effectiveDeviceAdminVariants = listOf("debug", "release")
val checkEffectiveDeviceAdminMetadata by tasks.registering {
    group = "verification"
    description = "Validates merged app DeviceAdmin metadata for every build type."
    dependsOn(
        effectiveDeviceAdminVariants.flatMap { variant ->
            val capitalized = variant.replaceFirstChar { it.uppercaseChar() }
            listOf(
                "process${capitalized}Manifest",
                "process${capitalized}Resources",
            )
        },
    )

    doLast {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val approvedPolicies = setOf("disable-camera")
        val expectedReceiver =
            "com.example.devicemanagement.management.SentinelDeviceAdminReceiver"
        val aapt2 = file(
            "${android.sdkDirectory}/build-tools/${android.buildToolsVersion}/aapt2",
        )
        check(aapt2.isFile) { "aapt2 is unavailable at $aapt2" }

        effectiveDeviceAdminVariants.forEach { variant ->
            val capitalized = variant.replaceFirstChar { it.uppercaseChar() }
            val mergedManifest = layout.buildDirectory.file(
                "intermediates/merged_manifests/$variant/" +
                    "process${capitalized}Manifest/AndroidManifest.xml",
            ).get().asFile
            check(mergedManifest.isFile) {
                "Merged $variant manifest is unavailable at $mergedManifest"
            }
            val manifest = secureDocument(mergedManifest)
            val metadataElements = manifest.getElementsByTagName("meta-data")
                .elements()
                .filter {
                    it.getAttributeNS(androidNamespace, "name") ==
                        "android.app.device_admin"
                }
            check(metadataElements.size == 1) {
                "Effective $variant manifest must contain exactly one " +
                    "android.app.device_admin metadata declaration"
            }
            val metadata = metadataElements.single()
            val receiver = checkNotNull(metadata.parentNode as? Element) {
                "Effective $variant DeviceAdmin metadata must belong to a receiver"
            }
            check(receiver.tagName == "receiver") {
                "Effective $variant DeviceAdmin metadata must belong to a receiver"
            }
            check(receiver.getAttributeNS(androidNamespace, "name") == expectedReceiver) {
                "Effective $variant DeviceAdmin metadata resolved to an unexpected receiver"
            }
            check(
                metadata.getAttributeNS(androidNamespace, "resource") ==
                    "@xml/device_admin_receiver",
            ) {
                "Effective $variant DeviceAdminReceiver must resolve " +
                    "@xml/device_admin_receiver"
            }

            val linkedResources = layout.buildDirectory.file(
                "intermediates/linked_resources_binary_format/$variant/" +
                    "process${capitalized}Resources/" +
                    "linked-resources-binary-format-$variant.ap_",
            ).get().asFile
            check(linkedResources.isFile) {
                "Linked $variant resources are unavailable at $linkedResources"
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
                    val match = Regex("""^(\s*)E:\s+([A-Za-z0-9_-]+)\b""").find(line)
                        ?: return@mapNotNull null
                    match.groupValues[1].length to match.groupValues[2]
                }
                .toList()
            check(elementLines.firstOrNull()?.second == "device-admin") {
                "Effective $variant metadata root must be device-admin"
            }
            val usesPoliciesIndex = elementLines.indexOfFirst {
                it.second == "uses-policies"
            }
            check(usesPoliciesIndex >= 0) {
                "Effective $variant metadata must contain uses-policies"
            }
            check(elementLines.count { it.second == "uses-policies" } == 1) {
                "Effective $variant metadata must contain exactly one uses-policies"
            }
            val usesPoliciesIndent = elementLines[usesPoliciesIndex].first
            val policyElements = elementLines
                .drop(usesPoliciesIndex + 1)
                .takeWhile { it.first > usesPoliciesIndent }
                .map { it.second }
            check(
                policyElements.toSet() == approvedPolicies &&
                    policyElements.size == approvedPolicies.size,
            ) {
                "Effective $variant DeviceAdmin policies must be exactly " +
                    "$approvedPolicies; found $policyElements"
            }
            val allApprovedElements = setOf(
                "device-admin",
                "uses-policies",
                "disable-camera",
            )
            val unapprovedElements = elementLines.map { it.second }.toSet() -
                allApprovedElements
            check(unapprovedElements.isEmpty()) {
                "Effective $variant metadata contains unapproved elements: " +
                    unapprovedElements
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(checkNoSensitiveActionComposition)
    dependsOn(checkEffectiveDeviceAdminMetadata)
    systemProperty(
        "appSourceDir",
        layout.projectDirectory.dir("src").asFile.absolutePath,
    )
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(checkNoSensitiveActionComposition)
    dependsOn(checkEffectiveDeviceAdminMetadata)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(checkNoSensitiveActionComposition)
}
