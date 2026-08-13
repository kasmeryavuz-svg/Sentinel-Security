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
                    project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")
            isProductionProject &&
                project.tasks.none {
                    it.name.matches(Regex("""check.*ProductionBytecodePolicy"""))
                }
        }
        check(missing.isEmpty()) {
            "Production modules without compiled-bytecode policy guards: " +
                missing.joinToString { it.path }
        }
    }
}

checkProductionBytecodePolicy.configure {
    dependsOn(checkProductionPolicyCoverage)
}
