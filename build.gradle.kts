import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.tasks.SourceSetContainer

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
}

val checkProductionBytecodePolicy by tasks.registering {
    group = "verification"
    description =
        "Verifies every compiled production output for DPM, dynamic, and native access."
}

val checkProductionPolicyCoverage by tasks.registering {
    group = "verification"
    description = "Fails when a production module or Android variant lacks a bytecode guard."
    doLast {
        val missing = subprojects.filter { project ->
            val isProductionProject =
                project.plugins.hasPlugin("com.android.application") ||
                    project.plugins.hasPlugin("com.android.library") ||
                    project.plugins.hasPlugin("java") ||
                    project.plugins.hasPlugin("org.jetbrains.kotlin.jvm") ||
                    project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
            isProductionProject &&
                project.tasks.withType(ProductionBytecodePolicyTask::class.java).isEmpty()
        }
        check(missing.isEmpty()) {
            "Production modules without compiled-bytecode policy guards: " +
                missing.joinToString { it.path }
        }

        val productionConfigurations = subprojects.flatMap { project ->
            project.configurations.filter { configuration ->
                val dependencyBucket = listOf(
                    "api",
                    "implementation",
                    "compileOnly",
                    "runtimeOnly",
                ).any { configuration.name.endsWith(it, ignoreCase = true) }
                dependencyBucket &&
                    !configuration.name.startsWith("test", ignoreCase = true) &&
                    !configuration.name.contains("UnitTest") &&
                    !configuration.name.contains("AndroidTest") &&
                    !configuration.name.contains("TestFixtures")
            }.map { project to it }
        }
        val fileDependencies = productionConfigurations.flatMap { (project, configuration) ->
            configuration.dependencies.withType(FileCollectionDependency::class.java).map {
                "${project.path}:${configuration.name}"
            }
        }
        check(fileDependencies.isEmpty()) {
            "Production file dependencies can bypass repository bytecode coverage: " +
                fileDependencies
        }

        val approvedExternalModules = setOf("org.jetbrains.kotlin:kotlin-stdlib")
        val unapprovedExternalDependencies =
            productionConfigurations.flatMap { (project, configuration) ->
                configuration.dependencies.withType(ExternalModuleDependency::class.java)
                    .mapNotNull { dependency ->
                        val coordinate = "${dependency.group}:${dependency.name}"
                        if (coordinate !in approvedExternalModules) {
                            "${project.path}:${configuration.name}:$coordinate"
                        } else {
                            null
                        }
                    }
            }
        check(unapprovedExternalDependencies.isEmpty()) {
            "Unapproved external production dependencies bypass repository bytecode coverage: " +
                unapprovedExternalDependencies
        }
    }
}

checkProductionBytecodePolicy.configure {
    dependsOn(checkProductionPolicyCoverage)
}

subprojects {
    pluginManager.withPlugin("java") {
        extensions.getByType(SourceSetContainer::class.java).configureEach {
            val sourceSet = this
            val isVerificationSourceSet =
                name == "test" || name == "testFixtures" || name.endsWith("Test")
            if (!isVerificationSourceSet) {
                val capitalized = name.replaceFirstChar { it.uppercaseChar() }
                val guard = tasks.register(
                    "check${capitalized}ProductionBytecodePolicy",
                    ProductionBytecodePolicyTask::class.java,
                ) {
                    group = "verification"
                    description =
                        "Verifies compiled ${sourceSet.name} production classes for ${project.path}."
                    artifactPath.set(project.path)
                    additionalClassFiles.from(sourceSet.output.classesDirs)
                    productionFiles.from(fileTree("src/${sourceSet.name}"))
                }
                rootProject.tasks.named("checkProductionBytecodePolicy").configure {
                    dependsOn(guard)
                }
                tasks.matching {
                    it.name == "check" ||
                        it.name == "test" ||
                        it.name == "assemble" ||
                        (sourceSet.name == "main" && it.name == "jar")
                }.configureEach {
                    dependsOn(guard)
                }
            }
        }
    }
}
