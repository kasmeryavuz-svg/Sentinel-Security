package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 17B entry gate. Real destructive Android
 * policy APIs remain absent. Flags describe repository reality and must
 * not be flipped merely because documentation claims readiness.
 *
 * PRESENT flags mean the named component exists. ENFORCED flags mean a
 * future real destructive chain is structurally required to use that
 * component. The ENFORCED flags stay false until that pairing exists.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint17BHardBlock {
    const val REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false
    const val DESTRUCTIVE_POLICY_WRAPPER_PRESENT = false
    const val DESTRUCTIVE_METADATA_PRESENT = false
    const val PRODUCTION_REACHABLE_SIMULATION = false
    const val TRUSTED_RUNTIME_COOLDOWN_PERSISTENCE_ADAPTER_PRESENT = true
    const val REAL_DURABLE_DESTRUCTIVE_PRE_EXECUTION_AUDIT_PRESENT = true
    const val REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED = false
    const val REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED = false
    const val DESTRUCTIVE_ARTIFACT_IDENTITY_PRECONDITION_PRESENT = true
    const val DESTRUCTIVE_HUMAN_APPROVAL_AUTHORITY_PRESENT = true
    const val DESTRUCTIVE_WIPE_OPTION_POLICY_PRESENT = true
    const val REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED = false
    const val REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED = false
    const val REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val WIPE_DATA_METADATA_REVIEW_APPROVED = false
    const val DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED = false
    const val DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false

    val gatesRequiringExplicitModification = listOf(
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt:checkpoint17BForbiddenDpmMethodNames",
        "buildSrc/src/main/kotlin/ReleaseArtifactSecurityVerifier.kt:forbiddenDexTokens",
        "buildSrc/src/test/kotlin/Checkpoint16DpmAllowlistFreezeTest.kt",
        "buildSrc/src/test/kotlin/Checkpoint17BHardBlockTest.kt",
        "device-management/src/main/res/xml/device_admin_receiver.xml",
        "app/build.gradle.kts:checkpoint17BForbiddenPolicies",
        "device-management/src/test/java/com/example/devicemanagement/management/DeviceAdminMetadataGuardTest.kt",
        "docs/WIPE_17A_PREFLIGHT.md",
        "docs/WIPE_17B_ENTRY_REVIEW.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint17BHardBlock.kt",
    )

    val remainingDestructiveBoundaryBlockers = listOf(
        "REAL_DESTRUCTIVE_EXECUTOR_PRESENT",
        "DESTRUCTIVE_POLICY_WRAPPER_PRESENT",
        "DESTRUCTIVE_METADATA_PRESENT",
        "PRODUCTION_REACHABLE_SIMULATION",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
        "DESTRUCTIVE_HUMAN_APPROVAL_RECORDED",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
        "WIPE_DATA_METADATA_REVIEW_APPROVED",
        "DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED",
        "DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED",
    )
}
