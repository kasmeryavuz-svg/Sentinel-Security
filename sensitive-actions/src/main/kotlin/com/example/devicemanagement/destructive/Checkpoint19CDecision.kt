package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 19C hardware-validation / real-chain
 * readiness review.
 *
 * This object records whether safe prerequisites are complete so a later
 * human may be asked for separate approvals. It does not assemble the
 * real chain, wire per-attempt confirmation, enable production signing,
 * record an artifact digest, or mark a hardware test as performed.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19CDecision {
    const val ARCHITECTURE_READY_RECONFIRMED = "YES"
    const val DESTRUCTIVE_IMPLEMENTATION_PRESENT = true
    const val REAL_CHAIN_ASSEMBLY_APPROVAL_REQUEST_READY = "YES"
    /**
     * Technical / device / artifact prerequisites only. A later YES would
     * mean a human may then be asked for the separate hardware-test
     * approval. Approval, execution, and GrapheneOS result verification
     * are later states and are not required for this flag.
     */
    const val HARDWARE_VALIDATION_PREPARATION_READY = "NO"
    const val READINESS_MODEL_NON_CIRCULAR = "YES"

    const val REAL_DESTRUCTIVE_CHAIN_ASSEMBLED = false
    const val REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION = false
    const val PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED = false
    const val TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HARDWARE_TEST_PERFORMED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT = false
    const val DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
    const val PRODUCTION_REACHABLE_SIMULATION = false
    const val REAL_CHAIN_ASSEMBLY_APPROVED = "NO"
    const val ARTIFACT_SIGNING_APPROVAL_GRANTED = "NO"
    const val HARDWARE_TEST_APPROVAL_GRANTED = "NO"

    const val REAL_DESTRUCTIVE_EXECUTOR_PRESENT = true
    const val DESTRUCTIVE_POLICY_WRAPPER_PRESENT = true
    const val DESTRUCTIVE_METADATA_PRESENT = true
    const val WIPE_ZERO_BYTECODE_ENFORCED = true
    const val FACTORY_RESET_ORIGIN_EXACT = true
    const val DEX_CONTROL_FLOW_ZERO_PROOF = true
    const val WIPE_DATA_METADATA_REVIEW_APPROVED = true
    const val DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED = true

    const val FUTURE_SCOPE_DEVICE_FACTORY_RESET_ONLY = true
    const val FUTURE_SCOPE_USER_SCOPED_WIPE_DENIED = true
    const val FUTURE_OPTION_SILENT_FORBIDDEN = true
    const val FUTURE_OPTION_RESET_PROTECTION_DENIED = true
    const val FUTURE_OPTION_EUICC_DENIED = true
    const val FUTURE_OPTION_UNKNOWN_DENIED = true
    const val FUTURE_EXTRA_FLAG_SET_MUST_BE_EMPTY = true
    const val FUTURE_FLAGS_MUST_BE_LITERAL_ZERO = true

    const val EXPECTED_PACKAGE_NAME = Checkpoint19ADecision.EXPECTED_PACKAGE_NAME
    const val EXPECTED_ADMIN_COMPONENT = Checkpoint19ADecision.EXPECTED_ADMIN_COMPONENT
    const val EXPECTED_BUILD_PURPOSE = Checkpoint19ADecision.EXPECTED_BUILD_PURPOSE

    val RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256: String? = null
    val RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256: String? = null
    val RECORDED_DISPOSABLE_DEVICE_SERIAL: String? = null
    val RECORDED_PER_ATTEMPT_CONFIRMATION: String? = null

    /**
     * Exact future production surface that a separately approved real-chain
     * assembly checkpoint would have to touch. This checkpoint does not
     * implement any of these changes.
     */
    val futureRealChainAssemblySurface = listOf(
        "TrustedDestructiveArtifactValidationSource.trustedExpectation",
        "DestructiveArtifactIdentityExpectation.TrustedDestructiveArtifactExpectationMint.issueFromTrustedValidationSource",
        "ProductionDestructiveRealChain.assembleIfPossible",
        "ProductionDestructiveRealChain.retainForProduction",
        "DeviceManagementComposition.retainProductionDestructiveImplementation",
        "DeviceManagementComposition.create",
        "UnwiredDestructiveHumanConfirmationSource.confirm",
        "DestructiveHumanConfirmationAuthority.confirm",
        "DestructiveHumanConfirmationMint.issueFromTrustedConfirmationSource",
        "DestructiveHumanApprovalAuthority.issueChallenge",
        "DestructiveHumanApprovalAuthority.redeem",
        "FutureDestructiveRealChainBoundary.assembleAndHandoff",
        "FutureDestructiveExecutorContract.execute",
        "AndroidFutureDestructiveExecutor.onAuthorizedHandoff",
        "AuthorizedFactoryResetPort.performAuthorizedFactoryReset",
        "AndroidDevicePolicyFactoryResetService.performAuthorizedFactoryReset",
        "ComposedDeviceManagementServices.productionDestructiveRetainer",
        "SensitiveActionRegistry.controlled",
        "AppContainer",
        "Checkpoint17BHardBlock ENFORCED flags",
    )

    val futureRequiredRuntimeOrder = listOf(
        "consume capability",
        "durable PRE_EXECUTION_COMMITTED append",
        "fresh live validation",
        "single-use final permit",
        "single-use execution bundle",
        "immediate synchronous executor handoff",
        "AndroidFutureDestructiveExecutor",
        "AuthorizedFactoryResetPort",
        "AndroidDevicePolicyFactoryResetService",
        "platform whole-device call with literal flags 0",
    )

    val futureFailClosedRequirements = listOf(
        "process_restart",
        "stale_lease",
        "replay",
        "wrong_target",
        "missing_device_owner",
        "inactive_admin",
        "api_below_34",
        "missing_trusted_artifact_expectation",
        "missing_per_attempt_human_confirmation",
        "durable_append_failure",
        "live_validation_failure",
    )

    val futurePerAttemptConfirmationRequiredFields = listOf(
        "operator_identity",
        "utc_timestamp",
        "exact_device_serial",
        "exact_package",
        "exact_device_admin_component",
        "exact_signing_certificate_sha256",
        "exact_apk_sha256",
        "exact_scope_DEVICE_FACTORY_RESET",
        "exact_flags_literal_0",
        "exact_build_revision",
        "one_attempt_only",
        "short_validity_window",
        "non_replayable_attempt_identifier",
    )

    val futureArtifactIdentityFlow = listOf(
        "exact_apk_used_for_installation_and_testing",
        "sha256_from_final_immutable_apk_bytes",
        "signing_certificate_sha256_extracted_from_that_apk",
        "package_identity_verified",
        "device_admin_receiver_identity_verified",
        "git_revision_and_build_provenance_recorded",
        "artifact_must_not_change_after_approval",
        "any_rebuild_invalidates_approval",
    )

    val disposableDeviceChecklist = listOf(
        "dedicated_disposable_android_device",
        "device_serial_explicitly_identified",
        "expected_os_build_recorded",
        "factory_reset_consequence_acknowledged",
        "device_owner_confirmed",
        "active_expected_device_admin_confirmed",
        "package_matches_approved_artifact",
        "signing_certificate_matches_approved_artifact",
        "artifact_sha256_matches_approval",
        "battery_usb_adb_state_recorded",
        "no_valuable_user_data",
        "recovery_provisioning_procedure_prepared",
        "abort_criteria_defined",
    )

    /**
     * Technical, device, artifact, and runtime-gate gaps that still block
     * [HARDWARE_VALIDATION_PREPARATION_READY]. Later approval, execution,
     * and result-verification flags are not members of this list.
     */
    val remainingHardwarePreparationBlockers = listOf(
        "REAL_DESTRUCTIVE_CHAIN_ASSEMBLED",
        "PER_ATTEMPT_HUMAN_CONFIRMATION_WIRED",
        "TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "DISPOSABLE_DEVICE_SERIAL_IDENTIFIED",
        "EXPECTED_OS_BUILD_RECORDED",
        "FACTORY_RESET_CONSEQUENCE_ACKNOWLEDGED",
        "DESTRUCTIVE_RECOVERY_PROCEDURE_PREPARED",
        "BATTERY_USB_ADB_STATE_RECORDED",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED",
    )

    /**
     * States that happen after preparation readiness. They must stay out
     * of [remainingHardwarePreparationBlockers] so a later
     * PREPARATION_READY = YES can exist while these remain false / NO.
     */
    val laterHardwareValidationStates = listOf(
        "HARDWARE_TEST_APPROVAL_GRANTED",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
        "DESTRUCTIVE_HARDWARE_TEST_PERFORMED",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
    )

    val gatesRequiringExplicitModification = listOf(
        "docs/WIPE_19C_HARDWARE_VALIDATION_READINESS.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19CDecision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19BDecision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/ProductionDestructiveRealChain.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/FutureDestructiveExecutorContract.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/DestructiveArtifactIdentity.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/DestructiveHumanApprovalAuthority.kt",
        "device-management/src/main/java/com/example/devicemanagement/management/DeviceManagementSensitiveActions.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint17BHardBlock.kt",
    )
}
