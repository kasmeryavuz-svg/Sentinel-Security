package attack;

import com.example.devicemanagement.action.ActionExecutor;
import com.example.devicemanagement.action.ApprovalAuthority;
import com.example.devicemanagement.action.DeviceManagementSensitiveActionControllerFactory;
import com.example.devicemanagement.audit.AuditPersistedCodec;
import com.example.devicemanagement.audit.AuditRecordStore;
import com.example.devicemanagement.audit.AuditSqliteIdentity;
import com.example.devicemanagement.audit.DurableAuditRepository;
import com.example.devicemanagement.audit.SensitiveActionAuditWriter;
import com.example.devicemanagement.internal.DeviceManagementImplementation;
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend;
import com.example.devicemanagement.management.DefaultCameraPolicy;
import com.example.devicemanagement.management.DefaultScreenCapturePolicy;
import com.example.devicemanagement.management.DefaultStatusBarPolicy;
import com.example.devicemanagement.management.VerifiedPolicyMutationExecutor;

final class ControlledCompositionAccess {
    ActionExecutor executor;
    ApprovalAuthority approvalAuthority;
    SensitiveActionPolicyBackend backend;
    DefaultCameraPolicy cameraPolicyWriter;
    DefaultScreenCapturePolicy screenCapturePolicyWriter;
    DefaultStatusBarPolicy statusBarPolicyWriter;
    VerifiedPolicyMutationExecutor mutationExecutor;
    DeviceManagementSensitiveActionControllerFactory controllerFactory;
    DeviceManagementImplementation implementationBootstrap;
    SensitiveActionAuditWriter auditWriter;
    DurableAuditRepository auditRepository;
    AuditRecordStore auditRecordStore;
    AuditPersistedCodec auditPersistedCodec;
    AuditSqliteIdentity auditSqliteIdentity;
}
