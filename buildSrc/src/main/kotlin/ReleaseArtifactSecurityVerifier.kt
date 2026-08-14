import java.io.File

object ReleaseArtifactSecurityVerifier {
    private val requiredClassDescriptors = listOf(
        "Lcom/example/devicemanagement/management/SentinelDeviceAdminReceiver;",
        "Lcom/example/devicemanagement/provisioning/GetProvisioningModeActivity;",
        "Lcom/example/devicemanagement/provisioning/AdminPolicyComplianceActivity;",
        "Lcom/example/devicemanagement/ui/MainActivity;",
        "Lcom/example/devicemanagement/app/DeviceManagementApp;",
        "Lcom/example/devicemanagement/management/DeviceManagement;",
        "Lcom/example/devicemanagement/internal/DeviceManagementImplementation;",
        "Lcom/example/devicemanagement/recovery/RecoveryInspection;",
        "Lcom/example/devicemanagement/app/AppContainer;",
    )

    private val requiredMappingNames = listOf(
        "com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
        "com.example.devicemanagement.provisioning.GetProvisioningModeActivity",
        "com.example.devicemanagement.provisioning.AdminPolicyComplianceActivity",
        "com.example.devicemanagement.ui.MainActivity",
        "com.example.devicemanagement.app.DeviceManagementApp",
        "com.example.devicemanagement.management.DeviceManagement",
        "com.example.devicemanagement.internal.DeviceManagementImplementation",
        "com.example.devicemanagement.recovery.RecoveryInspection",
        "com.example.devicemanagement.action.SensitiveActionController",
    )

    private val forbiddenDexTokens = listOf(
        "wipeData",
        "wipeDevice",
        "lockNow",
        "resetPassword",
        "removeUser",
        "uninstallPackageWithActiveAdmins",
        "clearDeviceOwnerApp",
        "clearApplicationUserData",
        "setKeyguardDisabled",
        "setKeyguardDisabledFeatures",
        "installExistingPackage",
        "installPackage",
        "reboot",
    )

    private val debugCertMarkers = listOf(
        "CN=Android Debug",
        "OU=Android",
        "O=Android",
        "Android Debug",
    )

    enum class SigningClassification {
        UNSIGNED,
        TEST_SIGNED,
        PRODUCTION_SIGNED,
        UNKNOWN,
    }

    fun verifyPackagedDex(
        strings: Set<String>,
        sourceName: String,
    ): List<String> {
        val violations = mutableListOf<String>()
        requiredClassDescriptors.forEach { descriptor ->
            if (descriptor !in strings) {
                violations +=
                    "$sourceName is missing required class descriptor $descriptor after R8"
            }
        }
        forbiddenDexTokens.forEach { token ->
            if (token in strings) {
                violations +=
                    "$sourceName contains forbidden destructive API token $token"
            }
        }
        return violations
    }

    fun verifyMapping(mappingFile: File?): List<String> {
        if (mappingFile == null || !mappingFile.isFile) {
            return listOf("R8 mapping file is missing; release minification must produce one")
        }
        val text = mappingFile.readText()
        val violations = mutableListOf<String>()
        requiredMappingNames.forEach { className ->
            if (className !in text) {
                violations += "R8 mapping does not mention required class $className"
            }
        }
        return violations
    }

    fun classifySigning(certOutput: String, signed: Boolean): SigningClassification {
        if (!signed) {
            return SigningClassification.UNSIGNED
        }
        val debugSigned = debugCertMarkers.any { marker -> marker in certOutput }
        return if (debugSigned) {
            SigningClassification.TEST_SIGNED
        } else {
            SigningClassification.PRODUCTION_SIGNED
        }
    }

    fun verifySigningBoundary(
        classification: SigningClassification,
        productionDistributionRequested: Boolean,
    ): List<String> {
        if (!productionDistributionRequested) {
            return emptyList()
        }
        return when (classification) {
            SigningClassification.PRODUCTION_SIGNED -> emptyList()
            SigningClassification.TEST_SIGNED -> listOf(
                "Production distribution artifact is signed with the Android debug key",
            )
            SigningClassification.UNSIGNED -> listOf(
                "Production distribution artifact is unsigned",
            )
            SigningClassification.UNKNOWN -> listOf(
                "Production distribution artifact signing could not be classified",
            )
        }
    }

    fun signingReport(
        classification: SigningClassification,
        artifactName: String,
    ): String {
        return buildString {
            appendLine("artifact=$artifactName")
            appendLine("signing=$classification")
            appendLine(
                when (classification) {
                    SigningClassification.UNSIGNED ->
                        "This artifact is unsigned and is not a production distribution."
                    SigningClassification.TEST_SIGNED ->
                        "This artifact is test-signed (Android debug key) and is not a " +
                            "production distribution."
                    SigningClassification.PRODUCTION_SIGNED ->
                        "This artifact is signed with a non-debug certificate. Confirm the " +
                            "certificate fingerprint against the production keystore before " +
                            "distribution."
                    SigningClassification.UNKNOWN ->
                        "Signing classification is unknown. Do not distribute."
                },
            )
        }
    }
}
