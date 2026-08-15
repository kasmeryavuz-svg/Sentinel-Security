package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 19H signing-ceremony preparation decision.
 *
 * This object records that a build-only signing-ceremony contract exists
 * and that the real repository state remains not ready for signing. It
 * is not runtime authorization. It does not generate a key, approve a
 * certificate, enable production signing, create a signed candidate,
 * mint a trusted artifact expectation, record a device identity, or
 * perform a wipe.
 *
 * Ceremony preparation is not key generation.
 * Key generation is not certificate approval.
 * Certificate approval is not production signing.
 * Production signing is not a signed validation candidate.
 * A signed validation candidate is not a trusted artifact.
 * A trusted artifact is not runtime authorization.
 * Runtime authorization is not hardware-test approval.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19HDecision {
    const val SIGNING_CEREMONY_CONTRACT_PRESENT = true
    const val SIGNING_CEREMONY_READY = false
    const val OFFLINE_KEY_GENERATED = false
    const val PUBLIC_CERTIFICATE_SUPPLIED = false
    const val EXPECTED_CERTIFICATE_RECORDED = false
    const val OPERATOR_APPROVAL_AVAILABLE = false
    const val WITNESS_APPROVAL_AVAILABLE = false
    const val KEY_CUSTODY_APPROVED = false
    const val RECOVERY_BACKUP_VERIFIED = false
    const val BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED = false
    const val PRODUCTION_ARTIFACT_SIGNED = false
    const val SIGNED_VALIDATION_CANDIDATE_PRODUCED = false
    const val TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
    const val PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
    const val CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = "NO"
    const val CHECKPOINT_19H_USED_AS_RUNTIME_AUTHORIZATION = false
    const val CEREMONY_PREPARATION_MINTS_TRUSTED_EXPECTATION = false

    const val DOCUMENT_RELATIVE_PATH =
        "docs/WIPE_19H_SIGNING_CEREMONY_PREPARATION.md"

    val recordedFlags = linkedMapOf(
        "CHECKPOINT_19H_SIGNING_CEREMONY_PREPARATION" to "YES",
        "19H_SIGNING_CEREMONY_CONTRACT_PRESENT" to "true",
        "19H_SIGNING_CEREMONY_READY" to "false",
        "19H_OFFLINE_KEY_GENERATED" to "false",
        "19H_PUBLIC_CERTIFICATE_SUPPLIED" to "false",
        "19H_EXPECTED_CERTIFICATE_RECORDED" to "false",
        "19H_OPERATOR_APPROVAL_AVAILABLE" to "false",
        "19H_WITNESS_APPROVAL_AVAILABLE" to "false",
        "19H_KEY_CUSTODY_APPROVED" to "false",
        "19H_RECOVERY_BACKUP_VERIFIED" to "false",
        "19H_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED" to "false",
        "19H_PRODUCTION_ARTIFACT_SIGNED" to "false",
        "19H_SIGNED_VALIDATION_CANDIDATE_PRODUCED" to "false",
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
        "19H_SIGNING_CEREMONY_READY",
        "19H_OFFLINE_KEY_GENERATED",
        "19H_PUBLIC_CERTIFICATE_SUPPLIED",
        "19H_EXPECTED_CERTIFICATE_RECORDED",
        "19H_OPERATOR_APPROVAL_AVAILABLE",
        "19H_WITNESS_APPROVAL_AVAILABLE",
        "19H_KEY_CUSTODY_APPROVED",
        "19H_RECOVERY_BACKUP_VERIFIED",
        "19H_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED",
        "19H_PRODUCTION_ARTIFACT_SIGNED",
        "19H_SIGNED_VALIDATION_CANDIDATE_PRODUCED",
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
