package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 19D real-chain assembly decision.
 *
 * This object records the separate human approval to implement the
 * production assembly path, and whether that path is structurally
 * present. It does not claim runtime destructive availability, record a
 * trusted artifact digest, enable production signing, mint a real
 * per-attempt confirmation, or mark a hardware test as performed.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19DDecision {
    const val REAL_CHAIN_ASSEMBLY_IMPLEMENTATION_APPROVED = "YES"
    const val REAL_CHAIN_ASSEMBLY_PATH_PRESENT = true
    const val REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false
    const val TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
    const val PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val REAL_CHAIN_STRUCTURAL_ASSEMBLY_COMPLETE = "YES"
    const val CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = "NO"
    const val DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
    const val PRODUCTION_REACHABLE_SIMULATION = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT = false

    const val RECORDED_APPROVAL_OPERATOR = "Yavuz Kasmer <kasmeryavuz@gmail.com>"
    const val RECORDED_APPROVAL_TIMESTAMP = "2026-08-15T16:48:00Z"
    const val RECORDED_APPROVAL_GIT_REVISION =
        "167ef973eb06751ba56137c4b3fd57716da9e4b2"
    const val RECORDED_APPROVAL_SENTENCE =
        "The human explicitly approved Checkpoint 19D real-chain assembly " +
            "implementation."

    val RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256: String? = null
    val RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256: String? = null
    val RECORDED_DISPOSABLE_DEVICE_SERIAL: String? = null
    val RECORDED_PER_ATTEMPT_CONFIRMATION: String? = null

    /**
     * Exact production assembly call graph implemented by this checkpoint.
     * Presence of this graph is not runtime availability.
     */
    val productionAssemblyCallGraph = listOf(
        "ProductionDestructiveRealChain.retainForProduction",
        "ProductionDestructiveRealChainOrchestrator.assembleAlreadyBoundDeviceFactoryReset",
        "TrustedDestructiveArtifactValidationSource.trustedExpectation",
        "ProductionDestructiveHumanConfirmationSource.confirm",
        "TrustedPerAttemptDestructiveConfirmationRecord.current",
        "FutureDestructiveRealChainBoundary.assembleAndHandoff",
        "FutureDestructiveExecutorContract.execute",
        "AndroidFutureDestructiveExecutor.onAuthorizedHandoff",
        "AuthorizedFactoryResetPort.performAuthorizedFactoryReset",
        "AndroidDevicePolicyFactoryResetService.performAuthorizedFactoryReset",
        "platform whole-device call with literal flags 0",
    )

    val requiredRuntimeOrder = listOf(
        "consume capability",
        "durable PRE_EXECUTION_COMMITTED append",
        "fresh authoritative live validation",
        "single-use final permit",
        "single-use execution bundle",
        "immediate synchronous executor handoff",
        "AndroidFutureDestructiveExecutor",
        "AuthorizedFactoryResetPort",
        "AndroidDevicePolicyFactoryResetService",
        "platform whole-device call with literal flags 0",
    )

    val originAllowlists = listOf(
        "ProductionDestructiveRealChainOrchestrator.assembleAlreadyBoundDeviceFactoryReset " +
            "is the only production caller of " +
            "FutureDestructiveRealChainBoundary.assembleAndHandoff",
        "FutureDestructiveRealChainBoundary.assembleAndHandoff is the only caller " +
            "allowed to hand a real bundle to FutureDestructiveExecutorContract.execute",
        "AndroidFutureDestructiveExecutor.onAuthorizedHandoff is the only caller of " +
            "AuthorizedFactoryResetPort.performAuthorizedFactoryReset",
        "AndroidDevicePolicyFactoryResetService is the only platform whole-device origin",
        "ProductionDestructiveHumanConfirmationSource.confirm is the only caller of " +
            "DestructiveHumanConfirmationAuthority.confirm",
        "assembleAlreadyBoundDeviceFactoryReset has no authorized production trigger origin",
    )

    val runtimeFailClosedRequirements = listOf(
        "missing_trusted_artifact_expectation",
        "missing_per_attempt_human_confirmation",
        "stale_confirmation",
        "replayed_confirmation",
        "process_restart",
        "stale_attempt_admission_lease",
        "dead_arming_token",
        "durable_append_failure",
        "unavailable_live_facts",
        "wrong_package",
        "wrong_admin",
        "inactive_admin",
        "not_device_owner",
        "api_below_34",
        "target_mismatch",
        "artifact_mismatch",
        "approval_mismatch",
        "cooldown_denial",
    )

    val remainingRuntimeAvailabilityBlockers = listOf(
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED",
        "PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
        "DESTRUCTIVE_HARDWARE_TEST_PERFORMED",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
        "DISPOSABLE_DEVICE_SERIAL_IDENTIFIED",
    )

    val gatesRequiringExplicitModification = listOf(
        "docs/WIPE_19D_REAL_CHAIN_ASSEMBLY.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19DDecision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/ProductionDestructiveRealChain.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/ProductionDestructiveRealChainOrchestrator.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/FutureDestructiveExecutorContract.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/DestructiveArtifactIdentity.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/DestructiveHumanApprovalAuthority.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint17BHardBlock.kt",
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt",
        "device-management/src/main/java/com/example/devicemanagement/management/DeviceManagementSensitiveActions.kt",
    )
}
