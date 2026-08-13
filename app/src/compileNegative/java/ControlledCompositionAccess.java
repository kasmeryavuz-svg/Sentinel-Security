package attack;

import com.example.devicemanagement.action.ActionExecutor;
import com.example.devicemanagement.action.ApprovalAuthority;
import com.example.devicemanagement.action.DeviceManagementSensitiveActionControllerFactory;
import com.example.devicemanagement.internal.DeviceManagementImplementation;
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend;
import com.example.devicemanagement.management.DefaultCameraPolicy;
import com.example.devicemanagement.management.DefaultScreenCapturePolicy;
import com.example.devicemanagement.management.VerifiedPolicyMutationExecutor;

final class ControlledCompositionAccess {
    ActionExecutor executor;
    ApprovalAuthority approvalAuthority;
    SensitiveActionPolicyBackend backend;
    DefaultCameraPolicy cameraPolicyWriter;
    DefaultScreenCapturePolicy screenCapturePolicyWriter;
    VerifiedPolicyMutationExecutor mutationExecutor;
    DeviceManagementSensitiveActionControllerFactory controllerFactory;
    DeviceManagementImplementation implementationBootstrap;
}
