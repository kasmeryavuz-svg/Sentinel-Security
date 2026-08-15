# Checkpoint 15: minimum keep rules for a minified release.
# Do not disable shrinking, optimization, or obfuscation here.
# Manifest components are also kept by the default Android rules.

# Application and launcher / platform-contract components instantiated by Android.
-keep class com.example.devicemanagement.app.DeviceManagementApp { <init>(); }
-keep class com.example.devicemanagement.ui.MainActivity { <init>(...); }
-keep class com.example.devicemanagement.provisioning.GetProvisioningModeActivity { <init>(...); }
-keep class com.example.devicemanagement.provisioning.AdminPolicyComplianceActivity { <init>(...); }
-keep class com.example.devicemanagement.management.SentinelDeviceAdminReceiver { <init>(...); }

# Runtime composition linkage across the facade / implementation artifacts.
-keep class com.example.devicemanagement.management.DeviceManagement {
    public static com.example.devicemanagement.management.DeviceManagementServices create(android.content.Context, com.example.devicemanagement.logging.StructuredLogger);
}
-keep class com.example.devicemanagement.internal.DeviceManagementImplementation {
    public static com.example.devicemanagement.management.DeviceManagementServices create(android.content.Context, com.example.devicemanagement.logging.StructuredLogger);
}
-keep interface com.example.devicemanagement.management.DeviceManagementServices { *; }
-keep class com.example.devicemanagement.app.AppContainer { *; }

# Public submit-only mutation API and read-only recovery / audit contracts.
-keep interface com.example.devicemanagement.action.SensitiveActionController { *; }
-keep interface com.example.devicemanagement.recovery.RecoveryInspectionProvider { *; }
-keep class com.example.devicemanagement.recovery.RecoveryInspection { *; }
-keep class com.example.devicemanagement.recovery.InterruptedRequest { *; }
-keep class com.example.devicemanagement.recovery.RecoveryInspectionHealth { *; }
-keep interface com.example.devicemanagement.audit.AuditHistoryProvider { *; }
-keep interface com.example.devicemanagement.audit.AuditStorageStatusProvider { *; }
-keep interface com.example.devicemanagement.logging.StructuredLogger { *; }
-keep class com.example.devicemanagement.logging.AndroidStructuredLogger { <init>(...); }

# Fail-safe MOCK_WIPE simulation types are intentionally not kept so R8 may
# strip them from the controlled production call graph.

# Checkpoint 19B disposable-device factory-reset origin. Kept so R8 cannot
# strip the sole wipeDevice wrapper while the real chain remains unassembled.
-keep class com.example.devicemanagement.management.AndroidDevicePolicyFactoryResetService { *; }
-keep class com.example.devicemanagement.management.ComposedDeviceManagementServices { *; }
-keep class com.example.devicemanagement.destructive.AndroidFutureDestructiveExecutor { *; }
-keep class com.example.devicemanagement.destructive.ProductionDestructiveRealChain { *; }
-keep class com.example.devicemanagement.destructive.ProductionDestructiveRetainer { *; }
-keep class com.example.devicemanagement.destructive.AuthorizedFactoryResetPort { *; }
-keep class com.example.devicemanagement.destructive.AuthorizedFactoryResetResult { *; }
