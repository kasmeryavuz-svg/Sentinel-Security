import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xlambdas=class")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":sensitive-actions-api"))
    testImplementation("junit:junit:4.13.2")
}

sourceSets.configureEach {
    if (!name.contains("test", ignoreCase = true)) {
        val sourceSet = this
        val guard = tasks.register<ProductionBytecodePolicyTask>(
            "check${name.replaceFirstChar { it.uppercaseChar() }}ProductionBytecodePolicy",
        ) {
            group = "verification"
            description = "Verifies compiled $name sensitive-action implementation classes."
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

tasks.test {
    useJUnit()
}
