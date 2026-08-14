package approved;

import com.example.devicemanagement.action.SensitiveActionController;
import com.example.devicemanagement.management.DeviceManagementServices;
import com.example.devicemanagement.management.DeviceManagementStatusProvider;

final class PublicFacadeAccess {
    SensitiveActionController controller(DeviceManagementServices services) {
        return services.getSensitiveActions();
    }

    DeviceManagementStatusProvider status(DeviceManagementServices services) {
        return services.getDeviceManagementStatus();
    }
}
