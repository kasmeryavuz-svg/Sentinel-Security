package approved;

import com.example.devicemanagement.action.SensitiveActionController;
import com.example.devicemanagement.audit.AuditHistoryProvider;
import com.example.devicemanagement.audit.AuditStorageStatusProvider;
import com.example.devicemanagement.management.DeviceManagementServices;
import com.example.devicemanagement.management.DeviceManagementStatusProvider;
import com.example.devicemanagement.recovery.RecoveryInspectionProvider;

final class PublicFacadeAccess {
    SensitiveActionController controller(DeviceManagementServices services) {
        return services.getSensitiveActions();
    }

    DeviceManagementStatusProvider status(DeviceManagementServices services) {
        return services.getDeviceManagementStatus();
    }

    AuditHistoryProvider auditHistory(DeviceManagementServices services) {
        return services.getAuditHistory();
    }

    AuditStorageStatusProvider auditStatus(DeviceManagementServices services) {
        return services.getAuditStorageStatus();
    }

    RecoveryInspectionProvider recoveryInspection(DeviceManagementServices services) {
        return services.getRecoveryInspection();
    }
}
