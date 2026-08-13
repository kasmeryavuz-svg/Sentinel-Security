import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
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
        freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class")
    }
}

dependencies {
    implementation(project(":device-management-api"))
    implementation(project(":sensitive-actions"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("reflect"))
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val capitalized = variant.name.replaceFirstChar { it.uppercaseChar() }
        val policyGuard = tasks.register<ProductionBytecodePolicyTask>(
            "check${capitalized}ProductionBytecodePolicy",
        ) {
            group = "verification"
            description =
                "Verifies compiled ${variant.name} implementation classes against policy boundaries."
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
        tasks.matching {
            it.name == "assemble$capitalized" ||
                it.name == "test${capitalized}UnitTest" ||
                it.name == "check$capitalized" ||
                it.name == "check"
        }.configureEach {
            dependsOn(policyGuard)
        }
    }
}

tasks.withType<Test>().configureEach {
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
