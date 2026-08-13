import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":sensitive-actions-api"))
}

sourceSets.configureEach {
    if (!name.contains("test", ignoreCase = true)) {
        val sourceSet = this
        val guard = tasks.register<ProductionBytecodePolicyTask>(
            "check${name.replaceFirstChar { it.uppercaseChar() }}ProductionBytecodePolicy",
        ) {
            group = "verification"
            description = "Verifies compiled $name device-management API classes."
            artifactPath.set(project.path)
            additionalClassFiles.from(sourceSet.output.classesDirs)
            productionFiles.from(fileTree("src/${sourceSet.name}"))
        }
        rootProject.tasks.named("checkProductionBytecodePolicy").configure {
            dependsOn(guard)
        }
        tasks.named("check").configure {
            dependsOn(guard)
        }
        tasks.named("test").configure {
            dependsOn(guard)
        }
    }
}
