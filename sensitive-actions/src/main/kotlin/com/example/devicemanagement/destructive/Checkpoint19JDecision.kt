package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 19J audit-findings repair decision.
 *
 * This object records that the three LOW findings from the separate
 * read-only Checkpoint 19I aggregate audit were repaired in
 * infrastructure only. It is not runtime authorization. It does not
 * enable production signing, mint a trusted artifact expectation,
 * record a device identity, add a trigger, or perform a wipe.
 *
 * Checkpoint 19I itself is not a committed repository decision.
 *
 * Isolated candidate-task paths are not candidate eligibility.
 * Enforced snapshot cleanup is not trusted-artifact enrollment.
 * An unsigned ordinary release is not a production distribution.
 * An explicit production-distribution request is not a performed
 * signing ceremony.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19JDecision {
    const val AUDIT_FINDINGS_REPAIRED = "YES"
    const val CANDIDATE_TASK_SNAPSHOT_PATHS_ISOLATED = true
    const val CANDIDATE_TASK_REPORT_PATHS_ISOLATED = true
    const val SNAPSHOT_CLEANUP_ENFORCED = true
    const val ORDINARY_RELEASE_REMAINS_UNSIGNED = true
    const val PRODUCTION_SIGNING_REQUIRES_EXPLICIT_DISTRIBUTION_REQUEST = true
    const val PRODUCTION_SIGNING_PERFORMED = false
    const val SIGNED_VALIDATION_CANDIDATE_PRODUCED = false
    const val TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
    const val PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
    const val CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = "NO"
    const val CHECKPOINT_19J_USED_AS_RUNTIME_AUTHORIZATION = false
    const val REPAIR_MINTS_TRUSTED_EXPECTATION = false

    const val DOCUMENT_RELATIVE_PATH =
        "docs/WIPE_19J_AUDIT_FINDINGS_REPAIR.md"

    val recordedFlags = linkedMapOf(
        "CHECKPOINT_19J_AUDIT_FINDINGS_REPAIRED" to "YES",
        "19J_CANDIDATE_TASK_SNAPSHOT_PATHS_ISOLATED" to "true",
        "19J_CANDIDATE_TASK_REPORT_PATHS_ISOLATED" to "true",
        "19J_SNAPSHOT_CLEANUP_ENFORCED" to "true",
        "19J_ORDINARY_RELEASE_REMAINS_UNSIGNED" to "true",
        "19J_PRODUCTION_SIGNING_REQUIRES_EXPLICIT_DISTRIBUTION_REQUEST" to "true",
        "19J_PRODUCTION_SIGNING_PERFORMED" to "false",
        "19J_SIGNED_VALIDATION_CANDIDATE_PRODUCED" to "false",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED" to "false",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE" to "false",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED" to "false",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED" to "false",
        "DESTRUCTIVE_HARDWARE_TEST_PERFORMED" to "false",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED" to "false",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE" to "false",
        "CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET" to "NO",
    )

    val laterStatesThatMustStayClosed = listOf(
        "19J_PRODUCTION_SIGNING_PERFORMED",
        "19J_SIGNED_VALIDATION_CANDIDATE_PRODUCED",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE",
        "DESTRUCTIVE_HARDWARE_TEST_PERFORMED",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED",
    )

    val forbiddenGradleTasks = listOf(
        "checkProductionDistributionSigning",
        "assembleProductionRelease",
        "bundleProductionRelease",
        "connectedAndroidTest",
        "connectedDebugAndroidTest",
        "connectedReleaseAndroidTest",
        "deviceCheck",
        "managedDevice",
    )
}
