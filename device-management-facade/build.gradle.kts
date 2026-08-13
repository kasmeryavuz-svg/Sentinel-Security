import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.devicemanagement.facade"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class")
    }
}

dependencies {
    api(project(":device-management-api"))
    implementation(project(":device-management-impl"))
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val capitalized = variant.name.replaceFirstChar { it.uppercaseChar() }
        val policyGuard = tasks.register<ProductionBytecodePolicyTask>(
            "check${capitalized}ProductionBytecodePolicy",
        ) {
            group = "verification"
            description =
                "Verifies compiled ${variant.name} facade classes against policy boundaries."
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
                it.name == "bundle${capitalized}Aar" ||
                it.name == "test${capitalized}UnitTest" ||
                it.name == "check$capitalized" ||
                it.name == "check"
        }.configureEach {
            dependsOn(policyGuard)
        }
    }
}
