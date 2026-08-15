package com.example.devicemanagement.destructive

/**
 * Timestamped Checkpoint 19P external GitHub-state observation and
 * maintainability-cleanup record.
 *
 * This object is not runtime authorization. It does not configure
 * branch protection, approve a ceremony, mint a trusted artifact
 * expectation, enable production signing, authorize a merge, add a
 * trigger, or perform a wipe.
 *
 * Historical Checkpoint 19E constants remain the 19E snapshot.
 * Checkpoint 19H ceremony-approval verification remains false.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19PGovernanceObservation {
    const val MAINTAINABILITY_CLEANUP = "YES"
    const val CLONED_FREEZE_TESTS_REPAIRED = true
    const val CURRENT_GOVERNANCE_RECORDED = true
    const val RELEASE_DOCUMENTATION_CORRECTED = true
    const val SYNTHETIC_READY_TEST_ONLY = true
    const val PROOF_TASKS_ALWAYS_REEXECUTE = true
    const val BYTECODE_VERIFIER_REFACTOR_DEFERRED = true
    const val HISTORICAL_19E_STATE_UNCHANGED = true

    const val OBSERVATION_KIND = "EXTERNAL_GITHUB_STATE"
    const val OBSERVATION_MAY_DRIFT = true
    const val OBSERVATION_RECORDED_AT_UTC = "2026-08-15T23:18:47Z"
    const val RULESET_ID = "20897672"
    const val RULESET_NAME = "Protect main - Sentinel CI"
    const val RULESET_ENFORCEMENT = "active"
    const val RULESET_TARGET = "refs/heads/main"
    const val RULESET_TARGET_ONLY_MAIN = true
    const val REQUIRED_CHECK_NAME = "Independent safety verification"
    const val REQUIRED_CHECK_INTEGRATION_ID = "15368"
    const val STRICT_UP_TO_DATE_REQUIRED = true
    const val PULL_REQUEST_REQUIRED = true
    const val REQUIRED_APPROVING_REVIEW_COUNT = 0
    const val CONVERSATION_RESOLUTION_REQUIRED = true
    const val FORCE_PUSH_BLOCKED = true
    const val DELETION_BLOCKED = true
    const val BYPASS_ACTORS_EMPTY = true
    const val PR_35_GOVERNING_CHECK_SUCCESS = true
    const val PR_35_GOVERNING_CHECK_RUN = "31913510265"

    const val USED_AS_RUNTIME_AUTHORIZATION = false
    const val USED_AS_CEREMONY_APPROVAL = false
    const val USED_AS_ARTIFACT_TRUST = false
    const val USED_AS_SIGNING_AUTHORIZATION = false
    const val USED_AS_MERGE_AUTHORIZATION = false
    const val TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
    const val PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
    const val CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = "NO"
    const val SIGNED_VALIDATION_CANDIDATE_PRODUCED = false
    const val PRODUCTION_SIGNING_PERFORMED = false
    const val CEREMONY_READY = false
    const val NINETEEN_H_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED = false

    const val DOCUMENT_RELATIVE_PATH =
        "docs/WIPE_19P_MAINTAINABILITY_CLEANUP.md"

    val recordedFlags = linkedMapOf(
        "CHECKPOINT_19P_MAINTAINABILITY_CLEANUP" to "YES",
        "19O_1_CLONED_FREEZE_TESTS_REPAIRED" to "true",
        "19O_2_CURRENT_GOVERNANCE_RECORDED" to "true",
        "19O_3_RELEASE_DOCUMENTATION_CORRECTED" to "true",
        "19O_4_SYNTHETIC_READY_TEST_ONLY" to "true",
        "19O_5_PROOF_TASKS_ALWAYS_REEXECUTE" to "true",
        "19O_6_BYTECODE_VERIFIER_REFACTOR_DEFERRED" to "true",
        "HISTORICAL_19E_STATE_UNCHANGED" to "true",
        "19P_OBSERVATION_KIND" to OBSERVATION_KIND,
        "19P_OBSERVATION_MAY_DRIFT" to "true",
        "19P_RULESET_ID" to RULESET_ID,
        "19P_RULESET_NAME" to RULESET_NAME,
        "19P_RULESET_ENFORCEMENT" to RULESET_ENFORCEMENT,
        "19P_RULESET_TARGET" to RULESET_TARGET,
        "19P_REQUIRED_CHECK_NAME" to REQUIRED_CHECK_NAME,
        "19P_REQUIRED_CHECK_INTEGRATION_ID" to REQUIRED_CHECK_INTEGRATION_ID,
        "19P_STRICT_UP_TO_DATE_REQUIRED" to "true",
        "19P_PULL_REQUEST_REQUIRED" to "true",
        "19P_REQUIRED_APPROVING_REVIEW_COUNT" to "0",
        "19P_CONVERSATION_RESOLUTION_REQUIRED" to "true",
        "19P_FORCE_PUSH_BLOCKED" to "true",
        "19P_DELETION_BLOCKED" to "true",
        "19P_BYPASS_ACTORS_EMPTY" to "true",
        "19P_PR_35_GOVERNING_CHECK_SUCCESS" to "true",
        "19P_USED_AS_RUNTIME_AUTHORIZATION" to "false",
        "19P_USED_AS_CEREMONY_APPROVAL" to "false",
        "19P_USED_AS_MERGE_AUTHORIZATION" to "false",
        "19H_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED" to "false",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED" to "false",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE" to "false",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED" to "false",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE" to "false",
        "CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET" to "NO",
    )

    val laterStatesThatMustStayClosed = listOf(
        "19P_USED_AS_RUNTIME_AUTHORIZATION",
        "19P_USED_AS_CEREMONY_APPROVAL",
        "19P_USED_AS_ARTIFACT_TRUST",
        "19P_USED_AS_SIGNING_AUTHORIZATION",
        "19P_USED_AS_MERGE_AUTHORIZATION",
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
