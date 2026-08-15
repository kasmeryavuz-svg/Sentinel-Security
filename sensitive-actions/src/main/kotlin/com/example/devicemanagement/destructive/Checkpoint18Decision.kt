package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 18 destructive-boundary decision.
 *
 * REQUIRED flags mean the future real-chain type boundary structurally
 * demands that prerequisite. They are not ENFORCED flags. ENFORCED still
 * means a production-wired real destructive chain uses the component.
 *
 * PRESENT for the executor contract means the non-Android type boundary
 * exists. It does not mean an Android wipe executor exists.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint18Decision {
    const val DESTRUCTIVE_EXECUTOR_CONTRACT_PRESENT = true
    const val REAL_CHAIN_UNFORGEABLE_HANDOFF_PRESENT = true
    const val REAL_CHAIN_RUNTIME_DURABILITY_REQUIRED = true
    const val REAL_CHAIN_ARTIFACT_IDENTITY_REQUIRED = true
    const val REAL_CHAIN_HUMAN_APPROVAL_REQUIRED = true
    const val REAL_CHAIN_WIPE_OPTION_POLICY_REQUIRED = true
    const val REAL_CHAIN_FINAL_LIVE_VALIDATION_REQUIRED = true
    const val REAL_CHAIN_PRE_EXECUTION_APPEND_AFTER_CONSUME_REQUIRED = true
    const val REAL_CHAIN_RUNTIME_DURABLE_APPEND_PAIRED = false

    const val REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false
    const val DESTRUCTIVE_POLICY_WRAPPER_PRESENT = false
    const val DESTRUCTIVE_METADATA_PRESENT = false
    const val PRODUCTION_REACHABLE_SIMULATION = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val WIPE_DATA_METADATA_REVIEW_APPROVED = false
    const val DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED = false
    const val DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false

    const val ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL = "NO"

    val gatesRequiringExplicitModification = listOf(
        "docs/WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint18Decision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/FutureDestructiveExecutorContract.kt",
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt:checkpoint17BForbiddenDpmMethodNames",
        "device-management/src/main/res/xml/device_admin_receiver.xml",
    )

    val remainingSeparateApprovalBlockers = listOf(
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
        "REAL_CHAIN_RUNTIME_DURABLE_APPEND_PAIRED",
    )
}
