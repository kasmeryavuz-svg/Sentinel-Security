package attack;

import android.content.Context;
import com.example.devicemanagement.management.AndroidDevicePolicyPlatform;

final class DirectDpmInfrastructureAccess {
    void bypass(Context context) {
        new AndroidDevicePolicyPlatform(context)
                .cameraPolicyService()
                .setCameraDisabled(true);
    }
}
