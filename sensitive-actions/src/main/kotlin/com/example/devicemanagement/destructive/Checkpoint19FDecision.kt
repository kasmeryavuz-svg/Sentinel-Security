package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 19F validation-evidence preparation decision.
 *
 * This object records that build-only candidate-artifact evidence tooling
 * and an unfilled disposable-device contract template are present. It is
 * not runtime authorization. It does not enable production signing, mint
 * a trusted artifact expectation, record a real device serial, approve a
 * hardware test, or perform a wipe.
 *
 * Candidate evidence tooling present is not a generated trusted report.
 * A generated candidate report is not candidate eligibility.
 * Candidate eligibility is not production signing.
 * Production signing is not a frozen trusted artifact.
 * A frozen artifact is not a recorded disposable-device identity.
 * Device identity is not hardware-validation preparation.
 * Preparation is not hardware-test approval.
 * Approval is not per-attempt confirmation.
 * Confirmation is not a performed hardware test.
 * A performed hardware test is not GrapheneOS verification.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19FDecision {
    const val ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT = true
    const val DISPOSABLE_DEVICE_CONTRACT_TEMPLATE_PRESENT = true
    const val UNSIGNED_CANDIDATE_PROOF_PASSED = true
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
    const val CHECKPOINT_19F_USED_AS_RUNTIME_AUTHORIZATION = false
    const val CANDIDATE_REPORT_IS_RUNTIME_AUTHORIZATION = false
    const val CANDIDATE_EVIDENCE_MINTS_TRUSTED_EXPECTATION = false

    const val CONTRACT_RELATIVE_PATH =
        "docs/WIPE_19F_DISPOSABLE_DEVICE_EVIDENCE_CONTRACT.md"

    val recordedFlags = linkedMapOf(
        "19F_ARTIFACT_CANDIDATE_EVIDENCE_TOOLING_PRESENT" to "true",
        "19F_DISPOSABLE_DEVICE_CONTRACT_TEMPLATE_PRESENT" to "true",
        "19F_UNSIGNED_CANDIDATE_PROOF_PASSED" to "true",
        "19F_CANDIDATE_ARTIFACT_ELIGIBLE" to "false",
        "19F_REAL_DEVICE_IDENTITY_RECORDED" to "false",
        "19F_HARDWARE_VALIDATION_PREPARATION_READY" to "false",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED" to "false",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE" to "false",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED" to "false",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED" to "false",
        "DESTRUCTIVE_HARDWARE_TEST_PERFORMED" to "false",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED" to "false",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE" to "false",
        "CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET" to "NO",
    )

    val separateStatesThatMustNotBeInferred = listOf(
        "Candidate evidence tooling present",
        "Candidate report generated",
        "Candidate eligible",
        "Production signing approved",
        "Production signing enabled",
        "Exact artifact frozen and trusted",
        "Disposable device identified",
        "Hardware-validation preparation ready",
        "Hardware-test approval granted",
        "Per-attempt confirmation available",
        "Hardware test performed",
        "GrapheneOS behavior verified",
    )

    val remainingValidationEvidenceBlockers = listOf(
        "19F_CANDIDATE_ARTIFACT_ELIGIBLE",
        "19F_REAL_DEVICE_IDENTITY_RECORDED",
        "19F_HARDWARE_VALIDATION_PREPARATION_READY",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE",
    )

    val laterStatesThatMustStayClosed = listOf(
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "19F_HARDWARE_VALIDATION_PREPARATION_READY",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE",
        "DESTRUCTIVE_HARDWARE_TEST_PERFORMED",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED",
        "19F_CANDIDATE_ARTIFACT_ELIGIBLE",
        "19F_REAL_DEVICE_IDENTITY_RECORDED",
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

    val gatesRequiringExplicitModification = listOf(
        "docs/WIPE_19F_DISPOSABLE_DEVICE_EVIDENCE_CONTRACT.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19FDecision.kt",
        "buildSrc/src/test/kotlin/Checkpoint19FValidationEvidenceFreezeTest.kt",
        ".github/workflows/checkpoint-19e-independent-ci.yml",
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt",
    )
}
