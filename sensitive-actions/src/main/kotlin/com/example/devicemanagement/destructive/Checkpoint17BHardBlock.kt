package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 17B hard block. Real destructive Android
 * policy APIs remain absent. Explicit review must change these flags and
 * the listed gates before a later 17B implementation may exist.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint17BHardBlock {
    const val REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false
    const val DESTRUCTIVE_POLICY_WRAPPER_PRESENT = false
    const val DESTRUCTIVE_METADATA_PRESENT = false
    const val PRODUCTION_REACHABLE_SIMULATION = false
    const val TRUSTED_RUNTIME_COOLDOWN_PERSISTENCE_ADAPTER_PRESENT = false
    const val REAL_DURABLE_DESTRUCTIVE_PRE_EXECUTION_AUDIT_PRESENT = false

    val gatesRequiringExplicitModification = listOf(
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt:checkpoint17BForbiddenDpmMethodNames",
        "buildSrc/src/main/kotlin/ReleaseArtifactSecurityVerifier.kt:forbiddenDexTokens",
        "buildSrc/src/test/kotlin/Checkpoint16DpmAllowlistFreezeTest.kt",
        "buildSrc/src/test/kotlin/Checkpoint17BHardBlockTest.kt",
        "device-management/src/main/res/xml/device_admin_receiver.xml",
        "app/build.gradle.kts:checkpoint17BForbiddenPolicies",
        "device-management/src/test/java/com/example/devicemanagement/management/DeviceAdminMetadataGuardTest.kt",
        "docs/WIPE_17A_PREFLIGHT.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint17BHardBlock.kt",
    )
}
