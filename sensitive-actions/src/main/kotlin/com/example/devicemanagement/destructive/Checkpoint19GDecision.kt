package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 19G validation build-purpose provenance
 * decision.
 *
 * This object records that a dedicated unsigned disposable-device
 * validation variant can expose an independently observed build purpose.
 * It is not runtime authorization. It does not enable production signing,
 * mint a trusted artifact expectation, record a real device serial,
 * approve a hardware test, or perform a wipe.
 *
 * An observed build purpose is not candidate eligibility.
 * Candidate eligibility is not production signing.
 * Production signing is not a frozen trusted artifact.
 * A frozen artifact is not a recorded disposable-device identity.
 * Device identity is not hardware-validation preparation.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19GDecision {
    const val DISPOSABLE_VALIDATION_VARIANT_PRESENT = true
    const val BUILD_PURPOSE_OBSERVABLE = true
    const val CANDIDATE_ARTIFACT_ELIGIBLE = false
    const val REAL_DEVICE_IDENTITY_RECORDED = false
    const val HARDWARE_VALIDATION_PREPARATION_READY = false
    const val TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
    const val PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
    const val CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = "NO"
    const val CHECKPOINT_19G_USED_AS_RUNTIME_AUTHORIZATION = false
    const val OBSERVED_BUILD_PURPOSE_IS_RUNTIME_AUTHORIZATION = false
    const val CANDIDATE_EVIDENCE_MINTS_TRUSTED_EXPECTATION = false

    const val DOCUMENT_RELATIVE_PATH =
        "docs/WIPE_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE.md"

    val recordedFlags = linkedMapOf(
        "CHECKPOINT_19G_VALIDATION_BUILD_PURPOSE_PROVENANCE" to "YES",
        "19G_DISPOSABLE_VALIDATION_VARIANT_PRESENT" to "true",
        "19G_BUILD_PURPOSE_OBSERVABLE" to "true",
        "19G_CANDIDATE_ARTIFACT_ELIGIBLE" to "false",
        "19G_REAL_DEVICE_IDENTITY_RECORDED" to "false",
        "19G_HARDWARE_VALIDATION_PREPARATION_READY" to "false",
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
        "19G_CANDIDATE_ARTIFACT_ELIGIBLE",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "19G_HARDWARE_VALIDATION_PREPARATION_READY",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE",
        "DESTRUCTIVE_HARDWARE_TEST_PERFORMED",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED",
        "19G_REAL_DEVICE_IDENTITY_RECORDED",
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
