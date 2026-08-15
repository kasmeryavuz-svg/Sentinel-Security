import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.util.jar.JarFile

internal data class PolicyVerificationTarget(
    val artifactPath: String,
    val displayPath: String,
    val bytes: ByteArray,
)

internal object ProductionBytecodePolicyVerifier {
    private const val DPM = "android/app/admin/DevicePolicyManager"

    private data class InvocationOrigin(
        val className: String,
        val methodName: String,
        val methodDescriptor: String,
    )

    private fun origins(vararg origins: InvocationOrigin): Set<InvocationOrigin> =
        origins.toSet()

    private val authorizedDpmCallers = setOf(
        "com/example/devicemanagement/management/AndroidDevicePolicyPlatform",
        "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
        "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService",
        "com/example/devicemanagement/management/AndroidDevicePolicyCameraService",
        "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService",
        "com/example/devicemanagement/management/AndroidDevicePolicyFactoryResetService",
    )

    private val checkpoint17BForbiddenDpmMethodNames = setOf(
        "wipeData",
    )

    private val allowedDpmInvocations = mapOf(
        "isDeviceOwnerApp(Ljava/lang/String;)Z" to origins(
            InvocationOrigin(
                "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
                "isDeviceOwnerApp",
                "()Z",
            ),
            InvocationOrigin(
                "com/example/devicemanagement/management/AndroidDevicePolicyFactoryResetService",
                "performAuthorizedFactoryReset",
                "()Lcom/example/devicemanagement/destructive/AuthorizedFactoryResetResult;",
            ),
        ),
        "isProfileOwnerApp(Ljava/lang/String;)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
            "isProfileOwnerApp",
            "()Z",
        )),
        "isAdminActive(Landroid/content/ComponentName;)Z" to origins(
            InvocationOrigin(
                "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
                "isExpectedAdminActive",
                "()Z",
            ),
            InvocationOrigin(
                "com/example/devicemanagement/management/AndroidDevicePolicyFactoryResetService",
                "performAuthorizedFactoryReset",
                "()Lcom/example/devicemanagement/destructive/AuthorizedFactoryResetResult;",
            ),
        ),
        "isProvisioningAllowed(Ljava/lang/String;)Z" to origins(
            InvocationOrigin(
                "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
                "isDeviceOwnerProvisioningAllowed",
                "()Z",
            ),
            InvocationOrigin(
                "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
                "isProfileOwnerProvisioningAllowed",
                "()Z",
            ),
        ),
        "getActiveAdmins()Ljava/util/List;" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyReadService",
            "activeAdminComponentNames",
            "()Ljava/util/Set;",
        )),
        "getScreenCaptureDisabled(Landroid/content/ComponentName;)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService",
            "isScreenCaptureDisabled",
            "()Z",
        )),
        "getCameraDisabled(Landroid/content/ComponentName;)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyCameraService",
            "isCameraDisabled",
            "()Z",
        )),
        "setScreenCaptureDisabled(Landroid/content/ComponentName;Z)V" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService",
            "setScreenCaptureDisabled",
            "(Z)V",
        )),
        "setCameraDisabled(Landroid/content/ComponentName;Z)V" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyCameraService",
            "setCameraDisabled",
            "(Z)V",
        )),
        "isStatusBarDisabled()Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService",
            "isStatusBarDisabled",
            "()Z",
        )),
        "setStatusBarDisabled(Landroid/content/ComponentName;Z)Z" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService",
            "setStatusBarDisabled",
            "(Z)Z",
        )),
        "wipeDevice(I)V" to origins(InvocationOrigin(
            "com/example/devicemanagement/management/AndroidDevicePolicyFactoryResetService",
            "performAuthorizedFactoryReset",
            "()Lcom/example/devicemanagement/destructive/AuthorizedFactoryResetResult;",
        )),
    )

    private val forbiddenLoaderOwners = setOf(
        "java/lang/ClassLoader",
        "java/net/URLClassLoader",
        "dalvik/system/BaseDexClassLoader",
        "dalvik/system/DexClassLoader",
        "dalvik/system/PathClassLoader",
        "dalvik/system/InMemoryDexClassLoader",
        "dalvik/system/DexFile",
    )

    private val authorizedAuditSqliteClasses = setOf(
        "com/example/devicemanagement/audit/SentinelAuditOpenHelper",
        "com/example/devicemanagement/audit/SqliteAuditRecordStore",
        "com/example/devicemanagement/audit/NonDestructiveAuditDatabaseErrorHandler",
        "com/example/devicemanagement/audit/AuditSqliteIdentity",
    )

    private val authorizedDestructiveSafetySqliteClasses = setOf(
        "com/example/devicemanagement/persistence/DenyOnlyCooldownOpenHelper",
        "com/example/devicemanagement/persistence/SqliteDenyOnlyMarkerStore",
        "com/example/devicemanagement/persistence/DestructivePreExecutionOpenHelper",
        "com/example/devicemanagement/persistence/SqliteDestructivePreExecutionStore",
        "com/example/devicemanagement/persistence/NonDestructiveSafetyDatabaseErrorHandler",
        "com/example/devicemanagement/persistence/DestructiveSafetySqliteIdentity",
        "com/example/devicemanagement/persistence/AndroidDestructiveSafetyPersistence",
        "com/example/devicemanagement/persistence/UnavailableDenyOnlyMarkerMedium",
    )

    private val forbiddenContextDatabaseMethods = setOf(
        "openOrCreateDatabase",
        "deleteDatabase",
        "getDatabasePath",
        "moveDatabaseFrom",
    )

    private val appForbiddenFileOwners = setOf(
        "java/io/File",
        "java/io/FileInputStream",
        "java/io/FileOutputStream",
        "java/io/RandomAccessFile",
        "java/io/FileWriter",
        "java/io/FileReader",
        "java/nio/file/Files",
        "java/nio/file/Paths",
        "java/nio/file/Path",
        "java/nio/channels/FileChannel",
    )

    private const val DATABASE_UTILS = "android/database/DatabaseUtils"

    private val lowLevelFileOwners = setOf(
        "android/system/Os",
        "libcore/io/Os",
        "libcore/io/Posix",
        "libcore/io/Linux",
        "libcore/io/ForwardingOs",
        "libcore/io/BlockGuardOs",
        "libcore/io/IoBridge",
    )

    /**
     * POSIX/Android syscall names that can open, replace, unlink, rename,
     * truncate, chmod/chown, or otherwise mutate app-private database files.
     * Unrelated Os helpers such as getpid remain unrestricted.
     */
    private val forbiddenOsFileMethods = setOf(
        "open",
        "openat",
        "creat",
        "write",
        "writev",
        "pwrite",
        "pwrite64",
        "pwritev",
        "lseek",
        "lseek64",
        "fcntl",
        "fcntlInt",
        "fcntlVoid",
        "fcntlLong",
        "flock",
        "fsync",
        "fdatasync",
        "ftruncate",
        "ftruncate64",
        "truncate",
        "unlink",
        "unlinkat",
        "remove",
        "rename",
        "renameat",
        "renameat2",
        "link",
        "linkat",
        "symlink",
        "symlinkat",
        "chmod",
        "fchmod",
        "fchmodat",
        "chown",
        "fchown",
        "lchown",
        "fchownat",
        "mkdir",
        "mkdirat",
        "mkfifo",
        "mkfifoat",
        "mknod",
        "mknodat",
        "rmdir",
        "mmap",
        "mmap64",
        "msync",
        "sendfile",
        "sendfile64",
        "copy_file_range",
        "posix_fallocate",
        "fallocate",
        "setxattr",
        "lsetxattr",
        "fsetxattr",
        "removexattr",
        "lremovexattr",
        "fremovexattr",
        "splice",
        "memfd_create",
        "ioctl",
        "ioctlInt",
        "ioctlint",
        "ioctlLong",
    )

    private const val AUDIT_DATABASE_FILE = "sentinel_audit.db"
    private const val DENY_ONLY_COOLDOWN_DATABASE_FILE = "sentinel_deny_only_cooldown.db"
    private const val DESTRUCTIVE_EVIDENCE_DATABASE_FILE =
        "sentinel_destructive_pre_execution_evidence.db"
    private const val SQLITE_PACKAGE = "android/database/sqlite/"

    private val verifiedMutationExecutorScreenCapture = InvocationOrigin(
        "com/example/devicemanagement/management/VerifiedPolicyMutationExecutor",
        "executeScreenCapture",
        "(Lcom/example/devicemanagement/management/VerifiedPolicyMutation" +
            "\$ScreenCapture;Ljava/lang/String;)" +
            "Lcom/example/devicemanagement/management/PolicyMutation;",
    )

    private val verifiedMutationExecutorCamera = InvocationOrigin(
        "com/example/devicemanagement/management/VerifiedPolicyMutationExecutor",
        "executeCamera",
        "(Lcom/example/devicemanagement/management/VerifiedPolicyMutation" +
            "\$Camera;Ljava/lang/String;)" +
            "Lcom/example/devicemanagement/management/PolicyMutation;",
    )

    private val verifiedMutationExecutorStatusBar = InvocationOrigin(
        "com/example/devicemanagement/management/VerifiedPolicyMutationExecutor",
        "executeStatusBar",
        "(Lcom/example/devicemanagement/management/VerifiedPolicyMutation" +
            "\$StatusBar;Ljava/lang/String;)" +
            "Lcom/example/devicemanagement/management/PolicyMutation;",
    )

    /**
     * Narrow policy setters are bound to VerifiedPolicyMutationExecutor whether the
     * bytecode call owner is the interface or the concrete Android implementation.
     * Restricting only the interface leaves a concrete-type / cast bypass that still
     * reaches the allowlisted DPM mutators inside those implementations.
     */
    private val verifiedMutationOrigins = mapOf(
        "com/example/devicemanagement/management/DevicePolicyScreenCaptureService." +
            "setScreenCaptureDisabled(Z)V" to verifiedMutationExecutorScreenCapture,
        "com/example/devicemanagement/management/AndroidDevicePolicyScreenCaptureService." +
            "setScreenCaptureDisabled(Z)V" to verifiedMutationExecutorScreenCapture,
        "com/example/devicemanagement/management/DevicePolicyCameraService." +
            "setCameraDisabled(Z)V" to verifiedMutationExecutorCamera,
        "com/example/devicemanagement/management/AndroidDevicePolicyCameraService." +
            "setCameraDisabled(Z)V" to verifiedMutationExecutorCamera,
        "com/example/devicemanagement/management/DevicePolicyStatusBarService." +
            "setStatusBarDisabled(Z)Z" to verifiedMutationExecutorStatusBar,
        "com/example/devicemanagement/management/AndroidDevicePolicyStatusBarService." +
            "setStatusBarDisabled(Z)Z" to verifiedMutationExecutorStatusBar,
    )

    private val trustedAuditAppendOrigin = InvocationOrigin(
        "com/example/devicemanagement/action/DefaultSensitiveActionController",
        "submit",
        "(Lcom/example/devicemanagement/trigger/Trigger;)" +
            "Lcom/example/devicemanagement/action/ActionResult;",
    )

    /**
     * Audit append is bound to DefaultSensitiveActionController whether the
     * bytecode call owner is the writer interface or the concrete repository.
     * Restricting only the interface leaves a concrete-type / cast bypass.
     */
    private val trustedAuditAppendOrigins = mapOf(
        "com/example/devicemanagement/audit/SensitiveActionAuditWriter." +
            "append(Lcom/example/devicemanagement/audit/AuditAppendRequest;)" +
            "Lcom/example/devicemanagement/audit/AuditAppendResult;" to
            trustedAuditAppendOrigin,
        "com/example/devicemanagement/audit/DurableAuditRepository." +
            "append(Lcom/example/devicemanagement/audit/AuditAppendRequest;)" +
            "Lcom/example/devicemanagement/audit/AuditAppendResult;" to
            trustedAuditAppendOrigin,
    )

    private val trustedAuditStoreMutationOrigin = InvocationOrigin(
        "com/example/devicemanagement/audit/DurableAuditRepository",
        "append",
        "(Lcom/example/devicemanagement/audit/AuditAppendRequest;)" +
            "Lcom/example/devicemanagement/audit/AuditAppendResult;",
    )

    /**
     * Audit record insert and retention deletion are bound to
     * DurableAuditRepository.append whether the bytecode call owner is the
     * store interface or a concrete production adapter. Restricting only the
     * interface leaves a concrete-type / cast bypass: a rogue class can still
     * instantiate SqliteAuditRecordStore and mutate rows while SQLite itself
     * remains allowlisted inside that adapter.
     */
    private const val RECOVERY_PACKAGE = "com/example/devicemanagement/recovery/"

    private val recoveryForbiddenTypeOwners = setOf(
        "com/example/devicemanagement/action/ApprovalAuthority",
        "com/example/devicemanagement/action/ActionExecutor",
        "com/example/devicemanagement/action/SensitiveActionController",
        "com/example/devicemanagement/action/DefaultSensitiveActionController",
        "com/example/devicemanagement/integration/SensitiveActionPolicyBackend",
        DPM,
        "com/example/devicemanagement/audit/SensitiveActionAuditWriter",
        "com/example/devicemanagement/audit/DurableAuditRepository",
        "com/example/devicemanagement/audit/AuditRecordStore",
        "com/example/devicemanagement/audit/SqliteAuditRecordStore",
        "com/example/devicemanagement/audit/InMemoryAuditRecordStore",
        "com/example/devicemanagement/audit/UnavailableAuditRecordStore",
        "com/example/devicemanagement/destructive/DestructiveArmingAuthority",
        "com/example/devicemanagement/destructive/DestructiveAuthorizationAuthority",
        "com/example/devicemanagement/destructive/DestructiveAttemptAdmissionAuthority",
        "com/example/devicemanagement/destructive/DestructiveFinalExecutionGate",
        "com/example/devicemanagement/destructive/DestructiveCapability",
        "com/example/devicemanagement/destructive/DestructiveAttemptLease",
        "com/example/devicemanagement/destructive/FinalExecutionPermit",
        "com/example/devicemanagement/destructive/SimulatedDestructiveExecutor",
        "com/example/devicemanagement/destructive/PreExecutionEvidenceCommitAuthority",
        "com/example/devicemanagement/destructive/DurableDestructivePreExecutionRepository",
        "com/example/devicemanagement/destructive/DestructivePreExecutionDurableStore",
        "com/example/devicemanagement/persistence/TrustedRuntimeDenyOnlyCooldownMarkerStore",
        "com/example/devicemanagement/persistence/DenyOnlyMarkerDurableMedium",
        "com/example/devicemanagement/persistence/SqliteDenyOnlyMarkerStore",
        "com/example/devicemanagement/persistence/SqliteDestructivePreExecutionStore",
        "com/example/devicemanagement/destructive/RuntimeDenyOnlyCooldownStore",
        "com/example/devicemanagement/destructive/RuntimeDestructivePreExecutionStore",
        "com/example/devicemanagement/destructive/RuntimeDestructiveSafetyDurability",
        "com/example/devicemanagement/destructive/DestructiveArtifactIdentity",
        "com/example/devicemanagement/destructive/DestructiveArtifactIdentityAuthority",
        "com/example/devicemanagement/destructive/DestructiveArtifactIdentityExpectation",
        "com/example/devicemanagement/destructive/TrustedDestructiveArtifactExpectationFactory",
        "com/example/devicemanagement/destructive/DestructiveArtifactIdentityExpectation\$TrustedDestructiveArtifactExpectationMint",
        "com/example/devicemanagement/destructive/TrustedDestructiveArtifactExpectationMint",
        "com/example/devicemanagement/destructive/TrustedDestructiveArtifactValidationSource",
        "com/example/devicemanagement/destructive/RuntimeDestructiveSafetyDurability\$RuntimeDestructiveSafetyDurabilityMint",
        "com/example/devicemanagement/destructive/RuntimeDestructiveSafetyDurabilityMint",
        "com/example/devicemanagement/destructive/DestructiveHumanApprovalAuthority",
        "com/example/devicemanagement/destructive/DestructiveHumanApproval",
        "com/example/devicemanagement/destructive/DestructiveHumanConfirmation",
        "com/example/devicemanagement/destructive/DestructiveHumanConfirmationAuthority",
        "com/example/devicemanagement/destructive/DestructiveHumanConfirmationMint",
        "com/example/devicemanagement/destructive/DestructiveOperatorChallenge",
        "com/example/devicemanagement/destructive/DestructiveChallengeIdentity",
        "com/example/devicemanagement/destructive/DestructiveWipeOptionPolicy",
        "com/example/devicemanagement/destructive/DestructiveWipeOptionPolicyProof",
        "com/example/devicemanagement/destructive/DestructiveWipeOptionPolicyAuthority",
        "com/example/devicemanagement/destructive/FutureDestructiveExecutorContract",
        "com/example/devicemanagement/destructive/FutureDestructiveExecutionBundle",
        "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary",
        "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary\$FutureDestructiveExecutorContract",
        "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary\$FutureDestructiveExecutionBundle",
        "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary\$FutureDestructiveExecutionBundle\$ExecutionBundleMint",
        "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary\$RealChainFinalLiveValidationPermit",
        "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary\$RealChainFinalLiveValidationPermit\$LiveValidationMint",
        "com/example/devicemanagement/destructive/RealChainHandoffRegistry",
        "com/example/devicemanagement/destructive/HandoffRegistry",
        "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary\$HandoffRegistry",
        "com/example/devicemanagement/destructive/IssuedRealChainFinalLiveValidationPermit",
        "com/example/devicemanagement/destructive/IssuedRealChainFinalLiveValidationPermit\$Companion",
        "com/example/devicemanagement/destructive/IssuedFutureDestructiveExecutionBundle",
        "com/example/devicemanagement/destructive/IssuedFutureDestructiveExecutionBundle\$Companion",
        "com/example/devicemanagement/destructive/RuntimeDurablePreExecutionCommitProof",
        "com/example/devicemanagement/destructive/RuntimeDurablePreExecutionCommitAuthority",
        "com/example/devicemanagement/destructive/RuntimeDurablePreExecutionCommitAuthority\$RuntimeDurablePreExecutionCommitProof",
        "com/example/devicemanagement/destructive/RealChainFinalLiveValidationPermit",
        "com/example/devicemanagement/destructive/Checkpoint18Decision",
        "com/example/devicemanagement/destructive/Checkpoint19ADecision",
        "com/example/devicemanagement/destructive/Checkpoint19BDecision",
        "com/example/devicemanagement/destructive/UnwiredFutureDestructiveExecutor",
        "com/example/devicemanagement/destructive/IssuedRuntimeDurablePreExecutionCommitProof",
        "com/example/devicemanagement/destructive/AuthorizedFactoryResetPort",
        "com/example/devicemanagement/destructive/AuthorizedFactoryResetResult",
        "com/example/devicemanagement/destructive/AndroidFutureDestructiveExecutor",
        "com/example/devicemanagement/destructive/ProductionDestructiveRealChain",
        "com/example/devicemanagement/destructive/ProductionDestructiveRetainer",
        "com/example/devicemanagement/destructive/UnavailableAuthorizedFactoryResetPort",
        "com/example/devicemanagement/management/AndroidDevicePolicyFactoryResetService",
        "com/example/devicemanagement/management/AndroidDestructiveLiveFactsSource",
        "com/example/devicemanagement/management/ComposedDeviceManagementServices",
    )

    private val recoveryForbiddenMethods = setOf(
        "submit",
        "issue",
        "consume",
        "execute",
        "append",
        "insert",
        "deleteOldest",
        "applyScreenCaptureDisabled",
        "applyCameraDisabled",
        "applyStatusBarDisabled",
        "setScreenCaptureDisabled",
        "setCameraDisabled",
        "setStatusBarDisabled",
        "issueFromTrustedAndroidStores",
        "issueRuntimeDurability",
        "issueFromTrustedValidationSource",
        "issueFromTrustedConfirmationSource",
        "mintFromTrustedAndroidMedium",
        "mintFromTrustedAndroidStore",
        "issueChallenge",
        "redeem",
        "admit",
        "assembleAndHandoff",
        "verifyDefaultDeny",
        "mintFinalLiveValidationPermit",
        "assembleBundleFromPermit",
        "commitAfterConsumedAuthorization",
        "onAuthorizedHandoff",
        "performAuthorizedFactoryReset",
        "retainForProduction",
        "requirePendingConsumption",
        "registerIssuedPermit",
        "registerIssuedBundle",
        "consumeIssuedPermit",
        "consumeIssuedBundle",
    )

    private val trustedAuditStoreMutationOrigins = mapOf(
        "com/example/devicemanagement/audit/AuditRecordStore." +
            "insert(Lcom/example/devicemanagement/audit/NewAuditRecord;)J" to
            trustedAuditStoreMutationOrigin,
        "com/example/devicemanagement/audit/SqliteAuditRecordStore." +
            "insert(Lcom/example/devicemanagement/audit/NewAuditRecord;)J" to
            trustedAuditStoreMutationOrigin,
        "com/example/devicemanagement/audit/InMemoryAuditRecordStore." +
            "insert(Lcom/example/devicemanagement/audit/NewAuditRecord;)J" to
            trustedAuditStoreMutationOrigin,
        "com/example/devicemanagement/audit/UnavailableAuditRecordStore." +
            "insert(Lcom/example/devicemanagement/audit/NewAuditRecord;)J" to
            trustedAuditStoreMutationOrigin,
        "com/example/devicemanagement/audit/AuditRecordStore." +
            "deleteOldest(I)V" to
            trustedAuditStoreMutationOrigin,
        "com/example/devicemanagement/audit/SqliteAuditRecordStore." +
            "deleteOldest(I)V" to
            trustedAuditStoreMutationOrigin,
        "com/example/devicemanagement/audit/InMemoryAuditRecordStore." +
            "deleteOldest(I)V" to
            trustedAuditStoreMutationOrigin,
            "com/example/devicemanagement/audit/UnavailableAuditRecordStore." +
            "deleteOldest(I)V" to
            trustedAuditStoreMutationOrigin,
    )

    private val trustedDenyOnlyMarkerWriteOrigin = InvocationOrigin(
        "com/example/devicemanagement/destructive/DestructiveDenyOnlyCooldown",
        "recordAttempt",
        "()Lcom/example/devicemanagement/destructive/CooldownRecordResult;",
    )

    private val trustedDenyOnlyMarkerAdapterWriteOrigin = InvocationOrigin(
        "com/example/devicemanagement/persistence/TrustedRuntimeDenyOnlyCooldownMarkerStore",
        "writeMarker",
        "([B)Lcom/example/devicemanagement/destructive/MarkerWriteResult;",
    )

    private val trustedDestructivePreExecutionStoreOrigin = InvocationOrigin(
        "com/example/devicemanagement/destructive/DurableDestructivePreExecutionRepository",
        "append",
        "(Lcom/example/devicemanagement/destructive/DestructivePreExecutionDurableRecord;)" +
            "Lcom/example/devicemanagement/destructive/DestructiveEvidenceAppendResult;",
    )

    private val trustedDestructivePreExecutionRepositoryOrigin = InvocationOrigin(
        "com/example/devicemanagement/destructive/PreExecutionEvidenceCommitAuthority",
        "commit",
        "(Lcom/example/devicemanagement/destructive/DestructiveSimulationEvidence;" +
            "Lcom/example/devicemanagement/destructive/DestructiveTargetBinding;" +
            "Lcom/example/devicemanagement/destructive/DestructiveAttemptLease;)" +
            "Lcom/example/devicemanagement/destructive/PreExecutionEvidenceCommitResult;",
    )

    private val trustedRuntimeDestructivePreExecutionRepositoryOrigin = InvocationOrigin(
        "com/example/devicemanagement/destructive/RuntimeDurablePreExecutionCommitAuthority",
        "commitAfterConsumedAuthorization",
        "(Lcom/example/devicemanagement/destructive/ConsumedDestructiveAuthorizationProof;" +
            "Lcom/example/devicemanagement/destructive/DestructiveTargetBinding;" +
            "Lcom/example/devicemanagement/destructive/DestructiveAttemptLease;" +
            "Lcom/example/devicemanagement/destructive/DestructiveArmingToken;" +
            "Lcom/example/devicemanagement/destructive/DestructiveAuthorizationAuthority;)" +
            "Lcom/example/devicemanagement/destructive/RuntimeDurablePreExecutionCommitResult;",
    )

    private val trustedDestructivePreExecutionCommitOrigin = InvocationOrigin(
        "com/example/devicemanagement/destructive/SimulatedDestructiveExecutor",
        "execute",
        "(Lcom/example/devicemanagement/destructive/DestructiveCapability;" +
            "Lcom/example/devicemanagement/destructive/DestructiveTargetBinding;" +
            "Lcom/example/devicemanagement/destructive/DestructiveAttemptLease;)" +
            "Lcom/example/devicemanagement/destructive/DestructiveSimulationStatus;",
    )

    private val trustedRuntimeDurabilityIssueOrigin = InvocationOrigin(
        "com/example/devicemanagement/persistence/AndroidDestructiveSafetyPersistence",
        "issueRuntimeDurability",
        "(Landroid/content/Context;Lcom/example/devicemanagement/logging/StructuredLogger;)" +
            "Lcom/example/devicemanagement/destructive/RuntimeDestructiveSafetyDurability;",
    )

    private val trustedArtifactExpectationIssueOrigin = InvocationOrigin(
        "com/example/devicemanagement/destructive/TrustedDestructiveArtifactValidationSource",
        "trustedExpectation",
        "()Lcom/example/devicemanagement/destructive/DestructiveArtifactIdentityExpectation;",
    )

    private val trustedHumanConfirmationAuthorityOwner =
        "com/example/devicemanagement/destructive/DestructiveHumanConfirmationAuthority"

    /**
     * Trusted mint operations whose security depends on bytecode origin.
     * Kotlin companions split a single source method across the outer
     * class, `$Companion`, and a named-companion owner. The verifier
     * therefore matches these methods by name on every JVM owner,
     * including method-handle forms.
     */
    private val trustedOriginBoundMintNames = setOf(
        "issueFromTrustedAndroidStores",
        "issueFromTrustedValidationSource",
        "issueFromTrustedConfirmationSource",
        "mintFromTrustedAndroidMedium",
        "mintFromTrustedAndroidStore",
    )

    private val realChainBoundaryOwner =
        "com/example/devicemanagement/destructive/FutureDestructiveRealChainBoundary"

    private val realChainExecutorContractOwners = setOf(
        "com/example/devicemanagement/destructive/FutureDestructiveExecutorContract",
        "$realChainBoundaryOwner\$FutureDestructiveExecutorContract",
        "com/example/devicemanagement/destructive/AndroidFutureDestructiveExecutor",
    )

    private val realChainHandoffRegistryOwner =
        "com/example/devicemanagement/destructive/RealChainHandoffRegistry"

    private val realChainNestedHandoffRegistryOwner =
        "$realChainBoundaryOwner\$HandoffRegistry"

    private val realChainHandoffMaterialOwners = setOf(
        "com/example/devicemanagement/destructive/FutureDestructiveExecutionBundle",
        "com/example/devicemanagement/destructive/RealChainFinalLiveValidationPermit",
        "com/example/devicemanagement/destructive/RuntimeDurablePreExecutionCommitProof",
        "com/example/devicemanagement/destructive/IssuedFutureDestructiveExecutionBundle",
        "com/example/devicemanagement/destructive/IssuedRealChainFinalLiveValidationPermit",
        "com/example/devicemanagement/destructive/IssuedRuntimeDurablePreExecutionCommitProof",
        "$realChainBoundaryOwner\$FutureDestructiveExecutionBundle",
        "$realChainBoundaryOwner\$RealChainFinalLiveValidationPermit",
        "com/example/devicemanagement/destructive/" +
            "RuntimeDurablePreExecutionCommitAuthority\$RuntimeDurablePreExecutionCommitProof",
    )

    private val realChainIssuedPermitOwner =
        "com/example/devicemanagement/destructive/IssuedRealChainFinalLiveValidationPermit"

    private val realChainIssuedBundleOwner =
        "com/example/devicemanagement/destructive/IssuedFutureDestructiveExecutionBundle"

    private val realChainHandoffConstructorOwners = setOf(
        realChainBoundaryOwner,
        "$realChainBoundaryOwner\$Companion",
        realChainHandoffRegistryOwner,
        realChainNestedHandoffRegistryOwner,
        "com/example/devicemanagement/destructive/HandoffRegistry",
        realChainIssuedPermitOwner,
        "$realChainIssuedPermitOwner\$Companion",
        realChainIssuedBundleOwner,
        "$realChainIssuedBundleOwner\$Companion",
        "com/example/devicemanagement/destructive/RuntimeDurablePreExecutionCommitAuthority",
        "com/example/devicemanagement/destructive/RuntimeDurablePreExecutionCommitAuthority\$Companion",
    )

    private val realChainHandoffConstructorMethods = setOf(
        "mintFinalLiveValidationPermit",
        "assembleBundleFromPermit",
        "commitAfterConsumedAuthorization",
        "assembleAndHandoff",
    )

    private val trustedRealChainHandoffOrigin = InvocationOrigin(
        realChainBoundaryOwner,
        "assembleAndHandoff",
        "(Lcom/example/devicemanagement/destructive/FutureDestructiveExecutorContract;" +
            "Lcom/example/devicemanagement/destructive/DestructiveTargetBinding;" +
            "Lcom/example/devicemanagement/destructive/DestructiveAttemptLease;" +
            "Lcom/example/devicemanagement/destructive/DestructiveCapability;" +
            "Lcom/example/devicemanagement/destructive/DestructiveArmingToken;" +
            "Lcom/example/devicemanagement/destructive/DestructiveArtifactIdentityMatchProof;" +
            "Lcom/example/devicemanagement/destructive/DestructiveArtifactIdentity;" +
            "Lcom/example/devicemanagement/destructive/DestructiveHumanApproval;" +
            "Lcom/example/devicemanagement/destructive/DestructiveWipeOptionPolicyProof;)" +
            "Lcom/example/devicemanagement/destructive/FutureDestructiveHandoffResult;",
    )

    private val trustedDestructiveSafetyMutationOrigins = mapOf(
        "com/example/devicemanagement/destructive/DenyOnlyCooldownMarkerStore." +
            "writeMarker([B)Lcom/example/devicemanagement/destructive/MarkerWriteResult;" to
            trustedDenyOnlyMarkerWriteOrigin,
        "com/example/devicemanagement/destructive/InMemoryDenyOnlyCooldownMarkerStore." +
            "writeMarker([B)Lcom/example/devicemanagement/destructive/MarkerWriteResult;" to
            trustedDenyOnlyMarkerWriteOrigin,
        "com/example/devicemanagement/persistence/TrustedRuntimeDenyOnlyCooldownMarkerStore." +
            "writeMarker([B)Lcom/example/devicemanagement/destructive/MarkerWriteResult;" to
            trustedDenyOnlyMarkerWriteOrigin,
        "com/example/devicemanagement/persistence/DenyOnlyMarkerDurableMedium." +
            "persistEncodedMarker([B)Lcom/example/devicemanagement/persistence/DenyOnlyMarkerPersistResult;" to
            trustedDenyOnlyMarkerAdapterWriteOrigin,
        "com/example/devicemanagement/persistence/SqliteDenyOnlyMarkerStore." +
            "persistEncodedMarker([B)Lcom/example/devicemanagement/persistence/DenyOnlyMarkerPersistResult;" to
            trustedDenyOnlyMarkerAdapterWriteOrigin,
        "com/example/devicemanagement/persistence/ReconstructableDenyOnlyMarkerMedium." +
            "persistEncodedMarker([B)Lcom/example/devicemanagement/persistence/DenyOnlyMarkerPersistResult;" to
            trustedDenyOnlyMarkerAdapterWriteOrigin,
        "com/example/devicemanagement/persistence/UnavailableDenyOnlyMarkerMedium." +
            "persistEncodedMarker([B)Lcom/example/devicemanagement/persistence/DenyOnlyMarkerPersistResult;" to
            trustedDenyOnlyMarkerAdapterWriteOrigin,
        "com/example/devicemanagement/destructive/DestructivePreExecutionDurableStore." +
            "insert(Lcom/example/devicemanagement/destructive/DestructivePreExecutionDurableRecord;)J" to
            trustedDestructivePreExecutionStoreOrigin,
        "com/example/devicemanagement/destructive/InMemoryDestructivePreExecutionDurableStore." +
            "insert(Lcom/example/devicemanagement/destructive/DestructivePreExecutionDurableRecord;)J" to
            trustedDestructivePreExecutionStoreOrigin,
        "com/example/devicemanagement/destructive/UnavailableDestructivePreExecutionDurableStore." +
            "insert(Lcom/example/devicemanagement/destructive/DestructivePreExecutionDurableRecord;)J" to
            trustedDestructivePreExecutionStoreOrigin,
        "com/example/devicemanagement/persistence/SqliteDestructivePreExecutionStore." +
            "insert(Lcom/example/devicemanagement/destructive/DestructivePreExecutionDurableRecord;)J" to
            trustedDestructivePreExecutionStoreOrigin,
        "com/example/devicemanagement/destructive/DurableDestructivePreExecutionRepository." +
            "append(Lcom/example/devicemanagement/destructive/DestructivePreExecutionDurableRecord;)" +
            "Lcom/example/devicemanagement/destructive/DestructiveEvidenceAppendResult;" to
            trustedDestructivePreExecutionRepositoryOrigin,
        "com/example/devicemanagement/destructive/PreExecutionEvidenceCommitAuthority." +
            "commit(Lcom/example/devicemanagement/destructive/DestructiveSimulationEvidence;" +
            "Lcom/example/devicemanagement/destructive/DestructiveTargetBinding;" +
            "Lcom/example/devicemanagement/destructive/DestructiveAttemptLease;)" +
            "Lcom/example/devicemanagement/destructive/PreExecutionEvidenceCommitResult;" to
            trustedDestructivePreExecutionCommitOrigin,
    )

    fun verify(targets: Iterable<PolicyVerificationTarget>): List<String> {
        return targets.flatMap(::verifyClass)
    }

    fun classTargets(
        artifactPath: String,
        roots: Iterable<File>,
    ): List<PolicyVerificationTarget> {
        return roots.flatMap { root ->
            when {
                root.isDirectory -> root.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .map {
                        PolicyVerificationTarget(
                            artifactPath = artifactPath,
                            displayPath = it.path,
                            bytes = it.readBytes(),
                        )
                    }
                    .toList()
                root.isFile && root.extension == "jar" -> JarFile(root).use { jar ->
                    jar.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") }
                        .map {
                            PolicyVerificationTarget(
                                artifactPath = artifactPath,
                                displayPath = "${root.path}!/${it.name}",
                                bytes = jar.getInputStream(it).readBytes(),
                            )
                        }
                        .toList()
                }
                root.isFile && root.extension == "class" -> listOf(
                    PolicyVerificationTarget(
                        artifactPath = artifactPath,
                        displayPath = root.path,
                        bytes = root.readBytes(),
                    ),
                )
                else -> emptyList()
            }
        }
    }

    private fun verifyClass(target: PolicyVerificationTarget): List<String> {
        val violations = mutableListOf<String>()
        ClassReader(target.bytes).accept(
            PolicyClassVisitor(target, violations),
            ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return violations
    }

    private class PolicyClassVisitor(
        private val target: PolicyVerificationTarget,
        private val violations: MutableList<String>,
    ) : ClassVisitor(Opcodes.ASM9) {
        private lateinit var className: String

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
        ) {
            className = name
            checkType(superName, "supertype")
            interfaces.orEmpty().forEach { checkType(it, "interface") }
            signature?.let { checkDescriptor(it, "class signature") }
        }

        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            checkDescriptor(descriptor, "annotation")
            return null
        }

        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?,
        ): org.objectweb.asm.FieldVisitor? {
            checkDescriptor(descriptor, "field $name")
            signature?.let { checkDescriptor(it, "field $name signature") }
            checkConstant(value, "field $name")
            return null
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor {
            checkDescriptor(descriptor, "method $name")
            signature?.let { checkDescriptor(it, "method $name signature") }
            exceptions.orEmpty().forEach { checkType(it, "method $name exception") }
            if (access and Opcodes.ACC_NATIVE != 0) {
                violation("$className.$name$descriptor declares a native/JNI entry point")
            }
            return PolicyMethodVisitor(name, descriptor)
        }

        private inner class PolicyMethodVisitor(
            private val methodName: String,
            private val methodDescriptor: String,
        ) : MethodVisitor(Opcodes.ASM9) {
            private val location: String
                get() = "$className.$methodName$methodDescriptor"

            override fun visitTypeInsn(opcode: Int, type: String) {
                checkType(type, "$location type instruction")
                checkRecoveryIsolation(type, "<type>", location)
                checkForbiddenOwner(type, "<type>", location)
            }

            override fun visitFieldInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
            ) {
                checkDpmOwner(owner, "$location field $name")
                checkRecoveryIsolation(owner, name, "$location field $name")
                checkSqliteOwner(owner, "$location field $name")
                checkDatabaseUtilsOwner(owner, "$location field $name")
                checkLowLevelFileApi(owner, name, "$location field $name")
                checkAppFileOwner(owner, "$location field $name")
                checkDiagnosticOutput(owner, name, descriptor, "$location field $name")
                checkForbiddenOwner(owner, name, "$location field $name")
                checkDescriptor(descriptor, "$location field $name")
            }

            override fun visitMethodInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
                isInterface: Boolean,
            ) {
                checkDpmInvocation(owner, name, descriptor, location)
                checkVerifiedMutationInvocation(owner, name, descriptor, location)
                checkTrustedAuditAppendInvocation(owner, name, descriptor, location)
                checkTrustedAuditStoreMutationInvocation(owner, name, descriptor, location)
                checkTrustedDestructiveSafetyMutationInvocation(owner, name, descriptor, location)
                checkTrustedRuntimeDurabilityIssueInvocation(owner, name, descriptor, location)
                checkTrustedArtifactExpectationIssueInvocation(owner, name, descriptor, location)
                checkTrustedHumanConfirmationMintInvocation(owner, name, descriptor, location)
                checkTrustedHumanConfirmationConfirmInvocation(owner, name, descriptor, location)
                checkRealChainHandoffInvocation(owner, name, descriptor, location)
                checkAuthorizedFactoryResetPortInvocation(owner, name, location)
                checkProductionDestructiveRetainInvocation(owner, name, location)
                checkRecoveryIsolation(owner, name, location)
                checkSqliteInvocation(owner, name, descriptor, location)
                checkContextDatabaseInvocation(owner, name, location)
                checkDatabaseUtilsOwner(owner, "$location invocation $owner.$name$descriptor")
                checkLowLevelFileApi(owner, name, location)
                checkForbiddenOwner(owner, name, location)
                checkDiagnosticOutput(owner, name, descriptor, location)
                checkAppFileOwner(owner, location)
                checkDescriptor(descriptor, "$location invocation")
            }

            override fun visitInvokeDynamicInsn(
                name: String,
                descriptor: String,
                bootstrapMethodHandle: Handle,
                vararg bootstrapMethodArguments: Any,
            ) {
                val isCompilerStringConcatenation =
                    bootstrapMethodHandle.owner == "java/lang/invoke/StringConcatFactory" &&
                        bootstrapMethodHandle.name in
                        setOf("makeConcat", "makeConcatWithConstants") &&
                        bootstrapMethodArguments.none { it is Handle }
                if (isCompilerStringConcatenation) {
                    checkDescriptor(descriptor, "$location string concatenation")
                    bootstrapMethodArguments.forEach {
                        checkConstant(it, "$location string concatenation argument")
                    }
                    return
                }
                violation("$location uses invokedynamic ($name$descriptor)")
                checkHandle(bootstrapMethodHandle, location)
                bootstrapMethodArguments.forEach {
                    checkConstant(it, "$location invokedynamic argument")
                }
            }

            override fun visitLdcInsn(value: Any?) {
                checkConstant(value, "$location constant")
            }

            override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
                checkDescriptor(descriptor, "$location array")
            }
        }

        private fun checkConstant(value: Any?, location: String) {
            when (value) {
                is Type -> checkDescriptor(value.descriptor, location)
                is Handle -> checkHandle(value, location)
                is String -> checkAuditDatabaseFilename(value, location)
            }
        }

        private fun checkHandle(handle: Handle, location: String) {
            checkDpmInvocation(handle.owner, handle.name, handle.desc, "$location method handle")
            checkVerifiedMutationInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkTrustedAuditAppendInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkTrustedAuditStoreMutationInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkTrustedDestructiveSafetyMutationInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkTrustedRuntimeDurabilityIssueInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkTrustedArtifactExpectationIssueInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkTrustedHumanConfirmationMintInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkTrustedHumanConfirmationConfirmInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkRealChainHandoffInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkAuthorizedFactoryResetPortInvocation(
                handle.owner,
                handle.name,
                "$location method handle",
            )
            checkProductionDestructiveRetainInvocation(
                handle.owner,
                handle.name,
                "$location method handle",
            )
            checkRecoveryIsolation(handle.owner, handle.name, "$location method handle")
            checkSqliteInvocation(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkContextDatabaseInvocation(handle.owner, handle.name, "$location method handle")
            checkDatabaseUtilsOwner(
                handle.owner,
                "$location method handle invocation ${handle.owner}.${handle.name}${handle.desc}",
            )
            checkLowLevelFileApi(handle.owner, handle.name, "$location method handle")
            checkForbiddenOwner(handle.owner, handle.name, "$location method handle")
            checkDiagnosticOutput(
                handle.owner,
                handle.name,
                handle.desc,
                "$location method handle",
            )
            checkAppFileOwner(handle.owner, "$location method handle")
            checkDescriptor(handle.desc, "$location method handle")
        }

        private fun checkDpmInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            if (owner != DPM) return
            val invocation = "$name$descriptor"
            val actualOrigin = InvocationOrigin(className, methodName(location), methodDescriptor(location))
            val approvedOrigins = allowedDpmInvocations[invocation].orEmpty()
            if (target.artifactPath != ":device-management-impl" || actualOrigin !in approvedOrigins) {
                violation(
                    "$location invokes $DPM.$invocation outside the explicitly " +
                        "authorized implementation method",
                )
            }
            if (invocation !in allowedDpmInvocations.keys) {
                violation("$location invokes non-allowlisted $DPM.$invocation")
            }
            if (name in checkpoint17BForbiddenDpmMethodNames) {
                violation(
                    "$location invokes Checkpoint 17B-blocked $DPM.$invocation",
                )
            }
        }

        private fun checkVerifiedMutationInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            val invocation = "$owner.$name$descriptor"
            val approvedOrigin = verifiedMutationOrigins[invocation] ?: return
            val actualOrigin = InvocationOrigin(
                className,
                methodName(location),
                methodDescriptor(location),
            )
            if (
                target.artifactPath != ":device-management-impl" ||
                actualOrigin != approvedOrigin
            ) {
                violation(
                    "$location invokes narrow policy mutation $invocation outside " +
                        "VerifiedPolicyMutationExecutor",
                )
            }
        }

        private fun checkTrustedAuditAppendInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            val invocation = "$owner.$name$descriptor"
            val approvedOrigin = trustedAuditAppendOrigins[invocation] ?: return
            val actualOrigin = InvocationOrigin(
                className,
                methodName(location),
                methodDescriptor(location),
            )
            if (
                target.artifactPath != ":sensitive-actions" ||
                actualOrigin != approvedOrigin
            ) {
                violation(
                    "$location invokes audit append $invocation outside " +
                        "DefaultSensitiveActionController",
                )
            }
        }

        private fun isRecoveryClass(): Boolean = className.startsWith(RECOVERY_PACKAGE)

        private fun checkRecoveryIsolation(owner: String, name: String, location: String) {
            if (!isRecoveryClass()) {
                return
            }
            if (owner in recoveryForbiddenTypeOwners) {
                violation(
                    "$location recovery code references forbidden type $owner",
                )
            }
            if (name in recoveryForbiddenMethods) {
                violation(
                    "$location recovery code invokes forbidden method $owner.$name",
                )
            }
        }

        private fun checkTrustedAuditStoreMutationInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            val invocation = "$owner.$name$descriptor"
            val approvedOrigin = trustedAuditStoreMutationOrigins[invocation] ?: return
            val actualOrigin = InvocationOrigin(
                className,
                methodName(location),
                methodDescriptor(location),
            )
            if (
                target.artifactPath != ":sensitive-actions" ||
                actualOrigin != approvedOrigin
            ) {
                violation(
                    "$location invokes audit store mutation $invocation outside " +
                        "DurableAuditRepository",
                )
            }
        }

        private fun checkTrustedRuntimeDurabilityIssueInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            checkTrustedOriginBoundMint(owner, name, descriptor, location)
        }

        private fun checkTrustedArtifactExpectationIssueInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            checkTrustedOriginBoundMint(owner, name, descriptor, location)
        }

        private fun checkTrustedHumanConfirmationMintInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            checkTrustedOriginBoundMint(owner, name, descriptor, location)
        }

        private fun checkTrustedOriginBoundMint(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            if (name !in trustedOriginBoundMintNames) {
                return
            }
            val invocation = "$owner.$name$descriptor"
            val actualOrigin = InvocationOrigin(
                className,
                methodName(location),
                methodDescriptor(location),
            )
            val callerMethod = methodName(location)
            val authorized = isKotlinMintBridge(owner, name, className, callerMethod) ||
                when (name) {
                    "issueFromTrustedAndroidStores" -> {
                        target.artifactPath == ":device-management-impl" &&
                            actualOrigin == trustedRuntimeDurabilityIssueOrigin
                    }
                    "mintFromTrustedAndroidMedium",
                    "mintFromTrustedAndroidStore",
                    -> isRuntimeDurabilityMintMethod(className, callerMethod)
                    "issueFromTrustedValidationSource" -> {
                        target.artifactPath == ":sensitive-actions" &&
                            actualOrigin == trustedArtifactExpectationIssueOrigin
                    }
                    "issueFromTrustedConfirmationSource" -> {
                        target.artifactPath == ":sensitive-actions" &&
                            className == trustedHumanConfirmationAuthorityOwner &&
                            callerMethod == "confirm"
                    }
                    else -> false
                }
            if (authorized) {
                return
            }
            val label = when (name) {
                "issueFromTrustedAndroidStores",
                "mintFromTrustedAndroidMedium",
                "mintFromTrustedAndroidStore",
                -> "runtime durability issuance"
                "issueFromTrustedValidationSource" -> "trusted artifact expectation issuance"
                "issueFromTrustedConfirmationSource" -> "human confirmation issuance"
                else -> "trusted mint issuance"
            }
            val required = when (name) {
                "issueFromTrustedAndroidStores",
                "mintFromTrustedAndroidMedium",
                "mintFromTrustedAndroidStore",
                -> "AndroidDestructiveSafetyPersistence"
                "issueFromTrustedValidationSource" -> "TrustedDestructiveArtifactValidationSource"
                "issueFromTrustedConfirmationSource" -> "DestructiveHumanConfirmationAuthority"
                else -> "the trusted mint origin"
            }
            violation("$location invokes $label $invocation outside $required")
        }

        private fun isKotlinMintBridge(
            calleeOwner: String,
            calleeName: String,
            callerClass: String,
            callerMethod: String,
        ): Boolean {
            if (calleeName != callerMethod) {
                return false
            }
            return calleeOwner.startsWith("$callerClass\$")
        }

        private fun isRuntimeDurabilityMintMethod(owner: String, method: String): Boolean {
            return method == "issueFromTrustedAndroidStores" &&
                (
                    owner == "com/example/devicemanagement/destructive/" +
                        "RuntimeDestructiveSafetyDurability\$RuntimeDestructiveSafetyDurabilityMint" ||
                        owner == "com/example/devicemanagement/destructive/" +
                        "RuntimeDestructiveSafetyDurabilityMint"
                    )
        }

        private fun checkRealChainHandoffInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            checkRealChainExecutorExecute(owner, name, descriptor, location)
            checkRealChainOnAuthorizedHandoff(owner, name, location)
            checkRealChainHandoffMint(owner, name, descriptor, location)
            checkRealChainHandoffConstructor(owner, name, location)
            checkRealChainCompanionCreate(owner, name, descriptor, location)
        }

        private fun checkAuthorizedFactoryResetPortInvocation(
            owner: String,
            name: String,
            location: String,
        ) {
            if (name != "performAuthorizedFactoryReset") {
                return
            }
            val factoryResetOwners = setOf(
                "com/example/devicemanagement/destructive/AuthorizedFactoryResetPort",
                "com/example/devicemanagement/destructive/UnavailableAuthorizedFactoryResetPort",
                "com/example/devicemanagement/management/AndroidDevicePolicyFactoryResetService",
            )
            if (owner !in factoryResetOwners &&
                !owner.endsWith("AuthorizedFactoryResetPort")
            ) {
                return
            }
            val callerMethod = methodName(location)
            val authorized = className ==
                "com/example/devicemanagement/destructive/AndroidFutureDestructiveExecutor" &&
                callerMethod == "onAuthorizedHandoff"
            if (authorized) {
                return
            }
            violation(
                "$location invokes authorized factory-reset $owner.$name outside " +
                    "AndroidFutureDestructiveExecutor.onAuthorizedHandoff",
            )
        }

        private fun checkProductionDestructiveRetainInvocation(
            owner: String,
            name: String,
            location: String,
        ) {
            if (name != "retainForProduction") {
                return
            }
            val retainOwners = setOf(
                "com/example/devicemanagement/destructive/ProductionDestructiveRealChain",
                "com/example/devicemanagement/destructive/ProductionDestructiveRealChain\$Companion",
            )
            if (owner !in retainOwners) {
                return
            }
            val callerMethod = methodName(location)
            val authorized = target.artifactPath == ":device-management-impl" &&
                className ==
                "com/example/devicemanagement/management/DeviceManagementComposition" &&
                callerMethod in setOf("create", "retainProductionDestructiveImplementation")
            if (authorized) {
                return
            }
            violation(
                "$location invokes production destructive retainer $owner.$name outside " +
                    "DeviceManagementComposition",
            )
        }

        private fun checkRealChainExecutorExecute(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            if (!isRealChainExecutorExecute(owner, name, descriptor)) {
                return
            }
            val callerMethod = methodName(location)
            val authorized = target.artifactPath == ":sensitive-actions" &&
                className == realChainBoundaryOwner &&
                callerMethod == "assembleAndHandoff"
            if (authorized) {
                return
            }
            val invocation = "$owner.$name$descriptor"
            violation(
                "$location invokes future executor $invocation outside " +
                    "FutureDestructiveRealChainBoundary.assembleAndHandoff",
            )
        }

        private fun isRealChainExecutorExecute(
            owner: String,
            name: String,
            descriptor: String,
        ): Boolean {
            if (name != "execute") {
                return false
            }
            if (owner in realChainExecutorContractOwners) {
                return true
            }
            return realChainHandoffMaterialOwners.any { material ->
                descriptor.contains("L$material;")
            } && "FutureDestructiveExecutionBundle" in descriptor
        }

        private fun checkRealChainOnAuthorizedHandoff(
            owner: String,
            name: String,
            location: String,
        ) {
            if (name != "onAuthorizedHandoff") {
                return
            }
            val callerMethod = methodName(location)
            val authorized = className in realChainExecutorContractOwners &&
                callerMethod == "execute"
            if (authorized) {
                return
            }
            violation(
                "$location invokes future executor $owner.$name outside " +
                    "FutureDestructiveExecutorContract.execute",
            )
        }

        private fun checkRealChainHandoffMint(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            val mintNames = setOf(
                "mintFinalLiveValidationPermit",
                "assembleBundleFromPermit",
                "commitAfterConsumedAuthorization",
                "registerIssuedPermit",
                "registerIssuedBundle",
                "consumeIssuedPermit",
                "consumeIssuedBundle",
            )
            if (name !in mintNames) {
                return
            }
            val callerMethod = methodName(location)
            val authorized = when (name) {
                "mintFinalLiveValidationPermit" -> {
                    target.artifactPath == ":sensitive-actions" &&
                        className == realChainBoundaryOwner &&
                        callerMethod in setOf("assembleAndHandoff", "mintFinalLiveValidationPermit")
                }
                "assembleBundleFromPermit" -> {
                    target.artifactPath == ":sensitive-actions" &&
                        className == realChainBoundaryOwner &&
                        callerMethod in setOf("assembleAndHandoff", "assembleBundleFromPermit")
                }
                "commitAfterConsumedAuthorization" -> {
                    val fromBoundary = target.artifactPath == ":sensitive-actions" &&
                        className == realChainBoundaryOwner &&
                        callerMethod == "assembleAndHandoff"
                    val fromAuthority = className ==
                        "com/example/devicemanagement/destructive/" +
                        "RuntimeDurablePreExecutionCommitAuthority" &&
                        callerMethod == "commitAfterConsumedAuthorization"
                    fromBoundary || fromAuthority
                }
                "registerIssuedPermit" -> {
                    callerMethod == "mintFinalLiveValidationPermit" &&
                        (
                            className == realChainBoundaryOwner ||
                                className == realChainIssuedPermitOwner ||
                                className == "$realChainIssuedPermitOwner\$Companion" ||
                                className == realChainHandoffRegistryOwner ||
                                className == realChainNestedHandoffRegistryOwner ||
                                className == "com/example/devicemanagement/destructive/HandoffRegistry" ||
                                className.endsWith("\$LiveValidationMint")
                            )
                }
                "registerIssuedBundle",
                "consumeIssuedPermit",
                -> {
                    callerMethod == "assembleBundleFromPermit" &&
                        (
                            className == realChainBoundaryOwner ||
                                className == realChainIssuedBundleOwner ||
                                className == "$realChainIssuedBundleOwner\$Companion" ||
                                className == realChainHandoffRegistryOwner ||
                                className == realChainNestedHandoffRegistryOwner ||
                                className == "com/example/devicemanagement/destructive/HandoffRegistry" ||
                                className.endsWith("\$ExecutionBundleMint")
                            )
                }
                "consumeIssuedBundle" -> {
                    className in realChainExecutorContractOwners &&
                        callerMethod == "execute"
                }
                else -> false
            } || isKotlinMintBridge(owner, name, className, callerMethod)
            if (authorized) {
                return
            }
            val invocation = "$owner.$name$descriptor"
            violation(
                "$location invokes real-chain handoff mint $invocation outside " +
                    "FutureDestructiveRealChainBoundary",
            )
        }

        private fun checkRealChainHandoffConstructor(
            owner: String,
            name: String,
            location: String,
        ) {
            if (name != "<init>" || owner !in realChainHandoffMaterialOwners) {
                return
            }
            val callerMethod = methodName(location)
            val authorizedOwner = className in realChainHandoffConstructorOwners ||
                className.startsWith("$realChainBoundaryOwner$") ||
                className.startsWith(
                    "com/example/devicemanagement/destructive/" +
                        "RuntimeDurablePreExecutionCommitAuthority$",
                )
            val authorizedMethod = callerMethod in realChainHandoffConstructorMethods ||
                callerMethod.startsWith("access\$")
            if (authorizedOwner && authorizedMethod) {
                return
            }
            if (className == owner && callerMethod == "<init>") {
                return
            }
            violation(
                "$location constructs real-chain handoff material $owner outside " +
                    "the FutureDestructiveRealChainBoundary mint",
            )
        }

        private fun checkRealChainCompanionCreate(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            if (name != "create") {
                return
            }
            val isHandoffOwner = owner in realChainHandoffMaterialOwners ||
                owner.endsWith("\$Companion") &&
                realChainHandoffMaterialOwners.any { owner.startsWith("$it$") }
            if (!isHandoffOwner) {
                return
            }
            val invocation = "$owner.$name$descriptor"
            violation(
                "$location invokes forbidden real-chain companion mint $invocation",
            )
        }

        private fun checkTrustedHumanConfirmationConfirmInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            if (owner != trustedHumanConfirmationAuthorityOwner || name != "confirm") {
                return
            }
            val invocation = "$owner.$name$descriptor"
            violation(
                "$location invokes human confirmation $invocation outside an unwired " +
                    "production confirmation path",
            )
        }

        private fun checkTrustedDestructiveSafetyMutationInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            val invocation = "$owner.$name$descriptor"
            val approvedOrigin = trustedDestructiveSafetyMutationOrigins[invocation] ?: return
            val actualOrigin = InvocationOrigin(
                className,
                methodName(location),
                methodDescriptor(location),
            )
            val runtimePreExecutionAppend =
                invocation ==
                    "com/example/devicemanagement/destructive/" +
                    "DurableDestructivePreExecutionRepository." +
                    "append(Lcom/example/devicemanagement/destructive/" +
                    "DestructivePreExecutionDurableRecord;)" +
                    "Lcom/example/devicemanagement/destructive/" +
                    "DestructiveEvidenceAppendResult;" &&
                    actualOrigin == trustedRuntimeDestructivePreExecutionRepositoryOrigin
            if (
                target.artifactPath != ":sensitive-actions" ||
                actualOrigin != approvedOrigin && !runtimePreExecutionAppend
            ) {
                violation(
                    "$location invokes destructive-safety persistence mutation " +
                        "$invocation outside the paired Checkpoint 17B authority",
                )
            }
        }

        private fun methodName(location: String): String {
            return location.removePrefix("$className.").substringBefore('(')
        }

        private fun methodDescriptor(location: String): String {
            return location.substringAfter("$className.${methodName(location)}")
        }

        private fun checkDpmOwner(owner: String, location: String) {
            if (
                owner == DPM &&
                (
                    target.artifactPath != ":device-management-impl" ||
                        className !in authorizedDpmCallers
                    )
            ) {
                violation("$location references $DPM outside the authorized implementation")
            }
        }

        private fun checkType(type: String?, location: String) {
            if (type == null) return
            checkDpmOwner(type, location)
            checkRecoveryIsolation(type, "<type>", location)
            checkSqliteOwner(type, location)
            checkDatabaseUtilsOwner(type, location)
            checkLowLevelFileApi(type, "<type>", location)
            checkAppFileOwner(type, location)
            checkForbiddenOwner(type, "<type>", location)
        }

        private fun checkDescriptor(descriptor: String, location: String) {
            if (descriptor.contains("L$DPM;")) {
                checkDpmOwner(DPM, location)
            }
            recoveryForbiddenTypeOwners.forEach { owner ->
                if (descriptor.contains("L$owner;")) {
                    checkRecoveryIsolation(owner, "<type>", location)
                }
            }
            sqliteTypesIn(descriptor).forEach { owner ->
                checkSqliteOwner(owner, location)
            }
            if (descriptor.contains("L$DATABASE_UTILS")) {
                checkDatabaseUtilsOwner(DATABASE_UTILS, location)
            }
            if (descriptor.contains("Landroid/system/OsConstants")) {
                checkLowLevelFileApi("android/system/OsConstants", "<type>", location)
            }
            appForbiddenFileOwners.forEach { owner ->
                if (descriptor.contains("L$owner;")) {
                    checkAppFileOwner(owner, location)
                }
            }
            forbiddenLoaderOwners.forEach { owner ->
                if (descriptor.contains("L$owner;")) {
                    violation("$location references forbidden dynamic loader $owner")
                }
            }
            if (
                descriptor.contains("Ljava/lang/reflect/") ||
                descriptor.contains("Lkotlin/reflect/") ||
                descriptor.contains("Ljava/lang/invoke/")
            ) {
                violation("$location references a forbidden reflection or method-handle type")
            }
        }

        private fun sqliteTypesIn(descriptor: String): List<String> {
            val prefix = "L$SQLITE_PACKAGE"
            val owners = mutableListOf<String>()
            var start = 0
            while (true) {
                val index = descriptor.indexOf(prefix, start)
                if (index < 0) {
                    return owners
                }
                val end = descriptor.indexOf(';', index)
                if (end < 0) {
                    return owners
                }
                owners += descriptor.substring(index + 1, end)
                start = end + 1
            }
        }

        private fun isFrameworkClass(): Boolean {
            return className.startsWith("android/") ||
                className.startsWith("java/") ||
                className.startsWith("javax/") ||
                className.startsWith("dalvik/")
        }

        private fun checkSqliteOwner(owner: String, location: String) {
            if (isFrameworkClass() || !owner.startsWith(SQLITE_PACKAGE)) {
                return
            }
            if (!authorizedAuditSqliteAccess() && !authorizedDestructiveSafetySqliteAccess()) {
                violation(
                    "$location references $owner outside the trusted audit SQLite implementation",
                )
            }
        }

        private fun checkSqliteInvocation(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            checkSqliteOwner(owner, "$location invocation $owner.$name$descriptor")
        }

        /**
         * Identify Context database APIs by method name, never by a closed owner
         * allowlist. invokevirtual/invokespecial on an application-defined
         * ContextWrapper (or other Context-derived) subclass records that subclass
         * as the call owner, so matching only Context/ContextWrapper/Activity/
         * Application/Service leaves an inherited open/delete/path bypass.
         */
        private fun checkContextDatabaseInvocation(
            owner: String,
            name: String,
            location: String,
        ) {
            if (isFrameworkClass() || name !in forbiddenContextDatabaseMethods) {
                return
            }
            if (!authorizedAuditSqliteAccess() && !authorizedDestructiveSafetySqliteAccess()) {
                violation(
                    "$location invokes $owner.$name, which can open, locate, move, or delete " +
                        "the Sentinel audit database outside the trusted audit pipeline",
                )
            }
        }

        private fun checkDatabaseUtilsOwner(owner: String, location: String) {
            if (
                isFrameworkClass() ||
                (owner != DATABASE_UTILS && !owner.startsWith("${DATABASE_UTILS}$"))
            ) {
                return
            }
            if (!authorizedAuditSqliteAccess() && !authorizedDestructiveSafetySqliteAccess()) {
                violation(
                    "$location references $owner, which can create or populate the " +
                        "Sentinel audit database outside the trusted audit pipeline",
                )
            }
        }

        /**
         * Low-level file syscalls can open, replace, or chmod the audit DB
         * without touching java.io.File or Context database helpers.
         */
        private fun checkLowLevelFileApi(owner: String, name: String, location: String) {
            if (isFrameworkClass()) {
                return
            }
            val osConstants = owner == "android/system/OsConstants" ||
                owner.startsWith("android/system/OsConstants\$")
            val fileSyscall = owner in lowLevelFileOwners && name in forbiddenOsFileMethods
            if (!osConstants && !fileSyscall) {
                return
            }
            if (!authorizedAuditSqliteAccess() && !authorizedDestructiveSafetySqliteAccess()) {
                violation(
                    "$location uses $owner.$name, which can open, unlink, rename, " +
                        "replace, truncate, chmod, or chown Sentinel private " +
                        "databases outside the trusted audit or destructive-safety pipeline",
                )
            }
        }

        private fun checkAppFileOwner(owner: String, location: String) {
            if (
                isFrameworkClass() ||
                target.artifactPath != ":app" ||
                owner !in appForbiddenFileOwners
            ) {
                return
            }
            violation(
                "$location uses $owner, which can directly access the Sentinel audit " +
                    "database file from app/UI code",
            )
        }

        private fun checkAuditDatabaseFilename(value: String, location: String) {
            if (isFrameworkClass()) {
                return
            }
            when (value) {
                AUDIT_DATABASE_FILE -> if (!authorizedAuditSqliteAccess()) {
                    violation(
                        "$location embeds the Sentinel audit database filename " +
                            "$AUDIT_DATABASE_FILE outside the trusted audit SQLite implementation",
                    )
                }
                DENY_ONLY_COOLDOWN_DATABASE_FILE,
                DESTRUCTIVE_EVIDENCE_DATABASE_FILE,
                -> if (!authorizedDestructiveSafetySqliteAccess()) {
                    violation(
                        "$location embeds the destructive-safety database filename " +
                            "$value outside the trusted destructive-safety SQLite implementation",
                    )
                }
            }
        }

        private fun authorizedAuditSqliteAccess(): Boolean {
            return target.artifactPath == ":device-management-impl" &&
                className in authorizedAuditSqliteClasses
        }

        private fun authorizedDestructiveSafetySqliteAccess(): Boolean {
            return target.artifactPath == ":device-management-impl" &&
                className in authorizedDestructiveSafetySqliteClasses
        }

        private fun diagnosticOutputRestricted(): Boolean {
            return target.artifactPath != ":provisioning-qr"
        }

        /**
         * Production diagnostic streams must not dump secrets, extras, or
         * exception traces. Structured audit persistence is a separate path
         * and is not written through System.out / printStackTrace.
         */
        private fun checkDiagnosticOutput(
            owner: String,
            name: String,
            descriptor: String,
            location: String,
        ) {
            if (isFrameworkClass() || !diagnosticOutputRestricted()) {
                return
            }
            if (
                owner == "java/lang/System" &&
                name in setOf("out", "err")
            ) {
                violation("$location uses forbidden diagnostic stream System.$name")
            }
            if (
                owner == "java/io/PrintStream" &&
                name in setOf("print", "println", "printf", "format", "append", "write")
            ) {
                violation("$location uses forbidden diagnostic stream $owner.$name")
            }
            if (
                name == "printStackTrace" &&
                (
                    descriptor == "()V" ||
                        descriptor.startsWith("(Ljava/io/PrintStream;)") ||
                        descriptor.startsWith("(Ljava/io/PrintWriter;)")
                    )
            ) {
                violation("$location uses forbidden $owner.printStackTrace")
            }
        }

        private fun checkForbiddenOwner(owner: String, name: String, location: String) {
            val reason = when {
                owner.startsWith("java/lang/reflect/") -> "Java reflection"
                owner.startsWith("kotlin/reflect/") -> "Kotlin reflection"
                owner.startsWith("java/lang/invoke/") -> "method handles"
                owner in forbiddenLoaderOwners -> "dynamic class loading"
                owner == "java/lang/Class" &&
                    name in setOf(
                        "forName",
                        "newInstance",
                        "getClassLoader",
                        "getMethod",
                        "getMethods",
                        "getDeclaredMethod",
                        "getDeclaredMethods",
                        "getConstructor",
                        "getConstructors",
                        "getDeclaredConstructor",
                        "getDeclaredConstructors",
                        "getField",
                        "getFields",
                        "getDeclaredField",
                        "getDeclaredFields",
                    ) -> "reflective lookup"
                owner == "java/lang/System" && name in setOf("load", "loadLibrary") ->
                    "native library loading"
                owner == "java/lang/Runtime" &&
                    name in setOf("load", "loadLibrary", "exec") ->
                    "native library or process loading"
                owner == "java/lang/ProcessBuilder" && name == "start" ->
                    "process execution"
                owner.startsWith("javax/tools/") -> "runtime code compilation"
                else -> null
            }
            if (reason != null) {
                violation("$location uses forbidden $reason via $owner.$name")
            }
        }

        private fun violation(message: String) {
            violations += "${target.artifactPath}:${target.displayPath}: $message"
        }
    }
}
