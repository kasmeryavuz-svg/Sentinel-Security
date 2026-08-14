package attack

import com.example.devicemanagement.action.ActionExecutor
import com.example.devicemanagement.action.ApprovalAuthority
import com.example.devicemanagement.action.DeviceManagementSensitiveActionControllerFactory
import com.example.devicemanagement.audit.AuditPersistedCodec
import com.example.devicemanagement.audit.AuditRecordStore
import com.example.devicemanagement.audit.AuditSqliteIdentity
import com.example.devicemanagement.audit.DurableAuditRepository
import com.example.devicemanagement.audit.SensitiveActionAuditWriter
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.recovery.AuditRecoveryInspector
import com.example.devicemanagement.recovery.DeviceManagementRecoveryInspectionFactory
import com.example.devicemanagement.internal.DeviceManagementImplementation
import com.example.devicemanagement.management.DefaultCameraPolicy
import com.example.devicemanagement.management.DefaultScreenCapturePolicy
import com.example.devicemanagement.management.DefaultStatusBarPolicy
import com.example.devicemanagement.management.VerifiedPolicyMutationExecutor

class ControlledCompositionAccess(
    val executor: ActionExecutor,
    val approvalAuthority: ApprovalAuthority,
    val backend: SensitiveActionPolicyBackend,
    val cameraPolicyWriter: DefaultCameraPolicy,
    val screenCapturePolicyWriter: DefaultScreenCapturePolicy,
    val statusBarPolicyWriter: DefaultStatusBarPolicy,
    val mutationExecutor: VerifiedPolicyMutationExecutor,
    val controllerFactory: DeviceManagementSensitiveActionControllerFactory,
    val implementationBootstrap: DeviceManagementImplementation,
    val auditWriter: SensitiveActionAuditWriter,
    val auditRepository: DurableAuditRepository,
    val auditRecordStore: AuditRecordStore,
    val auditPersistedCodec: AuditPersistedCodec,
    val auditSqliteIdentity: AuditSqliteIdentity,
    val auditRecoveryInspector: AuditRecoveryInspector,
    val recoveryInspectionFactory: DeviceManagementRecoveryInspectionFactory,
)
