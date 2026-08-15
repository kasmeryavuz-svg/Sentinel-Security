package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 19E independent CI decision.
 *
 * This object records whether the independent GitHub Actions verification
 * workflow is present and whether local validation of that suite passed.
 * It is not runtime authorization. It does not enable production signing,
 * record a trusted artifact digest, mint a real per-attempt confirmation,
 * configure branch protection, observe a GitHub run by itself, or mark a
 * hardware test as performed.
 *
 * Presence of the workflow file is not an observed GitHub run.
 * An observed GitHub run is not branch protection.
 * Branch protection is not production signing.
 * Production signing is not hardware-validation preparation.
 * Hardware-validation preparation is not hardware-test approval.
 * Hardware-test approval is not a performed hardware test.
 * A performed hardware test is not GrapheneOS verification.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19EDecision {
    const val INDEPENDENT_CI_WORKFLOW_PRESENT = true
    const val LOCAL_VALIDATION_PASSED = true
    const val GITHUB_CI_RUN_OBSERVED = false
    const val BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED = false
    const val REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
    const val TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
    const val PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = "NO"
    const val HARDWARE_VALIDATION_PREPARATION_READY = false
    const val HARDWARE_TEST_APPROVAL_GRANTED = false
    const val UNSIGNED_RELEASE_OUTPUT_IS_NOT_DISTRIBUTABLE = true
    const val CHECKPOINT_19E_USED_AS_RUNTIME_AUTHORIZATION = false

    const val WORKFLOW_RELATIVE_PATH =
        ".github/workflows/checkpoint-19e-independent-ci.yml"
    const val WORKFLOW_NAME = "Checkpoint 19E independent CI"
    const val WORKFLOW_JOB_NAME = "Independent safety verification"
    const val REQUIRED_JDK = "17"
    const val REQUIRED_COMPILE_SDK = "android-36"
    const val REQUIRED_BUILD_TOOLS = "35.0.0"

    val recordedFlags = linkedMapOf(
        "19E_INDEPENDENT_CI_WORKFLOW_PRESENT" to "true",
        "19E_LOCAL_VALIDATION_PASSED" to "true",
        "19E_GITHUB_CI_RUN_OBSERVED" to "false",
        "19E_BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED" to "false",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE" to "false",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED" to "false",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE" to "false",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED" to "false",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED" to "false",
        "DESTRUCTIVE_HARDWARE_TEST_PERFORMED" to "false",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED" to "false",
        "CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET" to "NO",
    )

    val separateStatesThatMustNotBeInferred = listOf(
        "CI workflow present",
        "actual GitHub CI run observed",
        "branch-protection required check configured",
        "production signing enabled",
        "hardware-validation preparation ready",
        "hardware-test approval granted",
        "hardware test performed",
        "GrapheneOS behavior verified",
    )

    val requiredGradleVerificationTasks = listOf(
        ":buildSrc:test",
        "test",
        "checkProductionBytecodePolicy",
        ":app:checkAppApiCompileNegative",
        ":app:checkAppDependencyIsolation",
        ":app:checkDebugEffectiveDeviceAdminMetadata",
        ":app:checkReleaseEffectiveDeviceAdminMetadata",
        ":app:checkDebugProductionBytecodePolicy",
        ":app:checkReleaseProductionBytecodePolicy",
        "assembleDebug",
        "assembleRelease",
        "bundleRelease",
        "checkReleaseProductionSecurity",
        "checkReleaseBundleProductionSecurity",
        ":sensitive-actions:test",
        ":sensitive-actions:checkMainProductionBytecodePolicy",
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

    val forbiddenWorkflowTriggers = listOf(
        "pull_request_target",
        "schedule",
        "workflow_run",
        "repository_dispatch",
        "deployment",
        "release",
        "create",
        "delete",
        "gollum",
        "page_build",
        "issue_comment",
        "issues",
    )

    val remainingIndependentCiBlockers = listOf(
        "19E_GITHUB_CI_RUN_OBSERVED",
        "19E_BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED",
    )

    val laterStatesThatMustStayClosed = listOf(
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "HARDWARE_VALIDATION_PREPARATION_READY",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
        "HARDWARE_TEST_APPROVAL_GRANTED",
        "DESTRUCTIVE_HARDWARE_TEST_PERFORMED",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE",
    )

    val gatesRequiringExplicitModification = listOf(
        "docs/WIPE_19E_INDEPENDENT_CI.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19EDecision.kt",
        ".github/workflows/checkpoint-19e-independent-ci.yml",
        "buildSrc/src/test/kotlin/Checkpoint19EIndependentCiFreezeTest.kt",
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt",
    )
}
