/**
 * Build-only expected identity for untrusted candidate inspection.
 *
 * This is not a trusted artifact-identity expectation and must never be
 * minted from an observed APK. Expected values are the repository
 * contract, never copied from the candidate under inspection.
 */
data class DestructiveValidationExpectedIdentity(
    val packageName: String,
    val adminComponent: String,
    val policies: List<String>,
    val minSdk: Int,
    val targetSdk: Int,
    val expectedCertificateSha256: String?,
    val buildPurpose: String,
) {
    companion object {
        const val REPOSITORY_PACKAGE = "com.example.devicemanagement"
        const val REPOSITORY_ADMIN_COMPONENT =
            "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver"
        const val BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION = "DISPOSABLE_DEVICE_VALIDATION"
        const val BUILD_PURPOSE_METADATA_NAME =
            "com.example.devicemanagement.DESTRUCTIVE_VALIDATION_BUILD_PURPOSE"
        const val DISPOSABLE_VALIDATION_BUILD_TYPE = "disposableValidation"

        fun repositoryContract(): DestructiveValidationExpectedIdentity {
            return DestructiveValidationExpectedIdentity(
                packageName = REPOSITORY_PACKAGE,
                adminComponent = REPOSITORY_ADMIN_COMPONENT,
                policies = listOf("disable-camera", "wipe-data"),
                minSdk = 26,
                targetSdk = 36,
                expectedCertificateSha256 = null,
                buildPurpose = BUILD_PURPOSE_DISPOSABLE_DEVICE_VALIDATION,
            )
        }
    }
}
