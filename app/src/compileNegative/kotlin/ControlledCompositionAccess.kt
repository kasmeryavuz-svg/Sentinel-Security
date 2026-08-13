package attack

import com.example.devicemanagement.action.ActionExecutor
import com.example.devicemanagement.action.ApprovalAuthority
import com.example.devicemanagement.action.DeviceManagementSensitiveActionControllerFactory
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import com.example.devicemanagement.internal.DeviceManagementImplementation
import com.example.devicemanagement.management.DefaultCameraPolicy
import com.example.devicemanagement.management.DefaultScreenCapturePolicy
import com.example.devicemanagement.management.VerifiedPolicyMutationExecutor

class ControlledCompositionAccess(
    val executor: ActionExecutor,
    val approvalAuthority: ApprovalAuthority,
    val backend: SensitiveActionPolicyBackend,
    val cameraPolicyWriter: DefaultCameraPolicy,
    val screenCapturePolicyWriter: DefaultScreenCapturePolicy,
    val mutationExecutor: VerifiedPolicyMutationExecutor,
    val controllerFactory: DeviceManagementSensitiveActionControllerFactory,
    val implementationBootstrap: DeviceManagementImplementation,
)
