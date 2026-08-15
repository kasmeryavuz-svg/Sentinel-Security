package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 19B destructive-boundary implementation.
 *
 * This object records the Checkpoint 19A human approval and describes
 * the A–D package that approval authorized. It does not record a
 * disposable-device artifact hash, enable production signing, claim
 * GrapheneOS wipe-behavior verification, or mark a hardware test as
 * performed.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19BDecision {
    const val ARCHITECTURE_READY_RECONFIRMED = "YES"
    const val DESTRUCTIVE_IMPLEMENTATION_APPROVAL_REQUEST_READY = "YES"
    const val DESTRUCTIVE_IMPLEMENTATION_APPROVED = "YES"
    const val DESTRUCTIVE_IMPLEMENTATION_PRESENT = true
    const val DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT = false

    const val REAL_DESTRUCTIVE_EXECUTOR_PRESENT = true
    const val DESTRUCTIVE_POLICY_WRAPPER_PRESENT = true
    const val DESTRUCTIVE_METADATA_PRESENT = true
    const val PRODUCTION_REACHABLE_SIMULATION = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = true
    const val DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val WIPE_DATA_METADATA_REVIEW_APPROVED = true
    const val DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED = true
    const val REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION = false
    const val WIPE_ZERO_BYTECODE_ENFORCED = true
    const val FACTORY_RESET_ORIGIN_EXACT = true

    const val FUTURE_SCOPE_DEVICE_FACTORY_RESET_ONLY = true
    const val FUTURE_SCOPE_USER_SCOPED_WIPE_DENIED = true
    const val FUTURE_OPTION_SILENT_FORBIDDEN = true
    const val FUTURE_OPTION_RESET_PROTECTION_DENIED = true
    const val FUTURE_OPTION_EUICC_DENIED = true
    const val FUTURE_OPTION_UNKNOWN_DENIED = true
    const val FUTURE_EXTRA_FLAG_SET_MUST_BE_EMPTY = true

    const val EXPECTED_PACKAGE_NAME = Checkpoint19ADecision.EXPECTED_PACKAGE_NAME
    const val EXPECTED_ADMIN_COMPONENT = Checkpoint19ADecision.EXPECTED_ADMIN_COMPONENT
    const val EXPECTED_BUILD_PURPOSE = Checkpoint19ADecision.EXPECTED_BUILD_PURPOSE

    val RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256: String? = null
    val RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256: String? = null

    const val RECORDED_APPROVAL_OPERATOR = "Yavuz Kasmer <kasmeryavuz@gmail.com>"
    const val RECORDED_APPROVAL_TIMESTAMP = "2026-08-15T14:28:00Z"
    const val RECORDED_APPROVAL_GIT_REVISION =
        "b2c5cafe8f06074495e66dd35885693478f4ceba"
    const val RECORDED_APPROVAL_DEVICE_IDENTITY =
        "Checkpoint 19A hardware contract: dedicated operator-controlled " +
            "disposable Sentinel test device; serial not identified"
    const val RECORDED_APPROVAL_SENTENCE =
        Checkpoint19ADecision.REQUIRED_APPROVAL_SENTENCE

    val remainingHardwareValidationBlockers = listOf(
        "PRODUCTION_REACHABLE_SIMULATION",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
        "DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED",
        "REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED",
    )

    val gatesRequiringExplicitModification = listOf(
        "docs/WIPE_19B_DESTRUCTIVE_IMPLEMENTATION.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19BDecision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19ADecision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint18Decision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint17BHardBlock.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/FutureDestructiveExecutorContract.kt",
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt:checkpoint17BForbiddenDpmMethodNames",
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt:allowedDpmInvocations",
        "buildSrc/src/main/kotlin/ReleaseArtifactSecurityVerifier.kt:forbiddenDexTokens",
        "device-management/src/main/res/xml/device_admin_receiver.xml",
        "app/build.gradle.kts:checkpoint17BForbiddenPolicies",
    )
}
