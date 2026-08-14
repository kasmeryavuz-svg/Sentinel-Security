pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SentinelSecurity"
include(":app")
include(":device-management")
project(":device-management").projectDir = file("device-management-facade")
include(":device-management-api")
include(":device-management-impl")
project(":device-management-impl").projectDir = file("device-management")
include(":sensitive-actions-api")
include(":sensitive-actions")
