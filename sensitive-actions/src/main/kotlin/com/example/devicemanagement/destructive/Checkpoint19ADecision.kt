package com.example.devicemanagement.destructive

/**
 * Machine-readable Checkpoint 19A destructive-implementation approval
 * review, including the later recorded human approval.
 *
 * Checkpoint 19A prepared the question. The human later issued the
 * required sentence; this object now records that answer. Implementation
 * presence is Checkpoint 19B: these PRESENT flags stay false because
 * 19A itself did not add wipe-capable code.
 *
 * Production source in this module must not spell Android destructive
 * method names; freeze tests reject those tokens.
 */
internal object Checkpoint19ADecision {
    const val ARCHITECTURE_READY_RECONFIRMED = "YES"
    const val DESTRUCTIVE_IMPLEMENTATION_APPROVAL_REQUEST_READY = "YES"
    const val DESTRUCTIVE_IMPLEMENTATION_APPROVED = "YES"
    const val DESTRUCTIVE_IMPLEMENTATION_PRESENT = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT = false

    const val REAL_DESTRUCTIVE_EXECUTOR_PRESENT = false
    const val DESTRUCTIVE_POLICY_WRAPPER_PRESENT = false
    const val DESTRUCTIVE_METADATA_PRESENT = false
    const val PRODUCTION_REACHABLE_SIMULATION = false
    const val DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED = false
    const val DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED = false
    const val DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = true
    const val DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false
    const val GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED = false
    const val WIPE_DATA_METADATA_REVIEW_APPROVED = false
    const val DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED = false

    const val FUTURE_SCOPE_DEVICE_FACTORY_RESET_ONLY = true
    const val FUTURE_SCOPE_USER_SCOPED_WIPE_DENIED = true
    const val FUTURE_OPTION_SILENT_FORBIDDEN = true
    const val FUTURE_OPTION_RESET_PROTECTION_DENIED = true
    const val FUTURE_OPTION_EUICC_DENIED = true
    const val FUTURE_OPTION_UNKNOWN_DENIED = true
    const val FUTURE_EXTRA_FLAG_SET_MUST_BE_EMPTY = true

    const val EXPECTED_PACKAGE_NAME = "com.example.devicemanagement"
    const val EXPECTED_ADMIN_COMPONENT =
        "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver"
    const val EXPECTED_BUILD_PURPOSE = "DISPOSABLE_DEVICE_VALIDATION"

    /**
     * Exact sentence the human issued. Holding this string was not itself
     * a recorded approval; [RECORDED_APPROVAL_SENTENCE] and
     * [DESTRUCTIVE_HUMAN_APPROVAL_RECORDED] are the record.
     */
    const val REQUIRED_APPROVAL_SENTENCE =
        "I, the human operator responsible for Sentinel Security, " +
            "explicitly approve starting a separately scoped " +
            "destructive-boundary implementation that will create " +
            "production-reachable code capable of factory-resetting the " +
            "dedicated disposable Sentinel test device identified by the " +
            "Checkpoint 19A hardware contract."

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
        REQUIRED_APPROVAL_SENTENCE

    val futureAndroidApiImplementationFiles = listOf(
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/FutureDestructiveExecutorContract.kt",
        "device-management/src/main/java/com/example/devicemanagement/management/AndroidDeviceManagementInfrastructure.kt",
        "device-management/src/main/java/com/example/devicemanagement/management/SentinelDeviceAdminReceiver.kt",
        "device-management/src/main/java/com/example/devicemanagement/management/AndroidDevicePolicyFactoryResetService.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/AndroidFutureDestructiveExecutor.kt",
    )

    val futureDeviceAdminMetadataFiles = listOf(
        "device-management/src/main/res/xml/device_admin_receiver.xml",
        "device-management/src/test/java/com/example/devicemanagement/management/DeviceAdminMetadataGuardTest.kt",
        "app/build.gradle.kts:approvedPolicies",
        "app/build.gradle.kts:checkpoint17BForbiddenPolicies",
    )

    val futureDpmAllowlistWrapperFiles = listOf(
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt:checkpoint17BForbiddenDpmMethodNames",
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt:allowedDpmInvocations",
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt:authorizedDpmCallers",
        "buildSrc/src/main/kotlin/ReleaseArtifactSecurityVerifier.kt:forbiddenDexTokens",
        "buildSrc/src/test/kotlin/Checkpoint16DpmAllowlistFreezeTest.kt",
        "buildSrc/src/test/kotlin/Checkpoint17BHardBlockTest.kt",
    )

    val futureTrustedProductionCompositionFiles = listOf(
        "device-management/src/main/java/com/example/devicemanagement/management/DeviceManagementSensitiveActions.kt",
        "device-management/src/main/java/com/example/devicemanagement/internal/DeviceManagementImplementation.kt",
        "device-management-facade/src/main/java/com/example/devicemanagement/management/DeviceManagement.kt",
        "device-management/src/main/java/com/example/devicemanagement/persistence/SqliteDenyOnlyMarkerStore.kt",
        "app/src/main/java/com/example/devicemanagement/app/AppContainer.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint17BHardBlock.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint18Decision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19ADecision.kt",
    )

    val futureProductionSigningIdentityFiles = listOf(
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/DestructiveArtifactIdentity.kt",
        "docs/RELEASE_SECURITY.md",
        "app/build.gradle.kts:SENTINEL_RELEASE_CERT_SHA256",
        "buildSrc/src/main/kotlin/ReleaseArtifactSecurityVerifier.kt",
    )

    val futureHumanApprovalRecordingFiles = listOf(
        "docs/WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19ADecision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/DestructiveHumanApprovalAuthority.kt",
    )

    val futureDisposableHardwareValidationFiles = listOf(
        "docs/DEVICE_OWNER_TEST_DEVICE.md",
        "docs/WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19ADecision.kt",
    )

    val futureGrapheneOsValidationFiles = listOf(
        "docs/WIPE_PLATFORM_PREFLIGHT.md",
        "docs/GRAPHENEOS_ENROLLMENT.md",
        "docs/WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md",
    )

    val gatesRequiringExplicitModification = listOf(
        "docs/WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19ADecision.kt",
        "docs/WIPE_18_DESTRUCTIVE_BOUNDARY_DECISION.md",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint18Decision.kt",
        "sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/FutureDestructiveExecutorContract.kt",
        "buildSrc/src/main/kotlin/ProductionBytecodePolicyVerifier.kt:checkpoint17BForbiddenDpmMethodNames",
        "buildSrc/src/main/kotlin/ReleaseArtifactSecurityVerifier.kt:forbiddenDexTokens",
        "device-management/src/main/res/xml/device_admin_receiver.xml",
        "app/build.gradle.kts:checkpoint17BForbiddenPolicies",
    )

    val remainingImplementationBlockers = listOf(
        "REAL_DESTRUCTIVE_EXECUTOR_PRESENT",
        "DESTRUCTIVE_POLICY_WRAPPER_PRESENT",
        "DESTRUCTIVE_METADATA_PRESENT",
        "PRODUCTION_REACHABLE_SIMULATION",
        "DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED",
        "DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED",
        "GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED",
        "WIPE_DATA_METADATA_REVIEW_APPROVED",
        "DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED",
        "DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED",
        "REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED",
        "REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED",
    )
}
