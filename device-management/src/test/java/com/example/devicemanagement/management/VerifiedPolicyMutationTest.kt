package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedPolicyMutationTest {
    private val logger = NoOpLogger()

    @Test
    fun `screen capture dispatch is service then fresh validation then setter and read back`() {
        val operations = mutableListOf<String>()
        val executor = executor(operations, validationProvider(operations))

        val result = executor.execute(
            VerifiedPolicyMutation.ScreenCapture(disabled = true),
            CORRELATION_ID,
        )

        assertEquals(PolicyMutation.Applied(true, true), result)
        assertEquals(
            listOf("screen-service", "validate", "screen-set:true", "screen-get"),
            operations,
        )
    }

    @Test
    fun `camera dispatch is service then fresh validation then setter and read back`() {
        val operations = mutableListOf<String>()
        val executor = executor(operations, validationProvider(operations))

        val result = executor.execute(
            VerifiedPolicyMutation.Camera(disabled = true),
            CORRELATION_ID,
        )

        assertEquals(PolicyMutation.Applied(true, true), result)
        assertEquals(
            listOf("camera-service", "validate", "camera-set:true", "camera-get"),
            operations,
        )
    }

    @Test
    fun `Device Owner authorization is never cached between mutations`() {
        val operations = mutableListOf<String>()
        var validations = 0
        val provider = DeviceOwnerValidationProvider {
            operations += "validate"
            validations += 1
            if (validations == 1) verifiedValidation() else ordinaryAppValidation()
        }
        val executor = executor(operations, provider)

        val first = executor.execute(
            VerifiedPolicyMutation.ScreenCapture(true),
            "correlation-1",
        )
        val second = executor.execute(
            VerifiedPolicyMutation.ScreenCapture(false),
            "correlation-2",
        )

        assertEquals(PolicyMutation.Applied(true, true), first)
        assertTrue(second is PolicyMutation.Denied)
        assertEquals(2, validations)
        assertEquals(
            listOf(
                "screen-service",
                "validate",
                "screen-set:true",
                "screen-get",
                "screen-service",
                "validate",
            ),
            operations,
        )
    }

    @Test
    fun `validation exception after service acquisition fails before mutation`() {
        val operations = mutableListOf<String>()
        val executor = executor(
            operations,
            DeviceOwnerValidationProvider {
                operations += "validate"
                error("uncertain")
            },
        )

        val result = executor.execute(
            VerifiedPolicyMutation.Camera(true),
            CORRELATION_ID,
        )

        assertEquals(PolicyMutation.Failed("device_owner_validation_failed"), result)
        assertEquals(listOf("camera-service", "validate"), operations)
    }

    @Test
    fun `every sealed mutation variant has complete ordered behavioral verification`() {
        val variants = listOf<VerifiedPolicyMutation>(
            VerifiedPolicyMutation.ScreenCapture(disabled = true),
            VerifiedPolicyMutation.Camera(disabled = true),
        )
        assertEquals(
            VerifiedPolicyMutation::class.sealedSubclasses.toSet(),
            variants.map { it::class }.toSet(),
        )

        variants.forEach { mutation ->
            val successOperations = mutableListOf<String>()
            val success = executor(
                operations = successOperations,
                validationProvider = validationProvider(successOperations),
            ).execute(mutation, CORRELATION_ID)

            assertEquals(PolicyMutation.Applied(true, true), success)
            assertEquals(expectedOperations(mutation), successOperations)

            val mismatchOperations = mutableListOf<String>()
            val mismatchPlatform = when (mutation) {
                is VerifiedPolicyMutation.ScreenCapture -> RecordingPlatform(
                    operations = mismatchOperations,
                    ignoreScreenWrites = true,
                )
                is VerifiedPolicyMutation.Camera -> RecordingPlatform(
                    operations = mismatchOperations,
                    ignoreCameraWrites = true,
                )
            }
            val mismatch = executor(
                operations = mismatchOperations,
                validationProvider = validationProvider(mismatchOperations),
                platform = mismatchPlatform,
            ).execute(mutation, CORRELATION_ID)

            assertEquals(
                PolicyMutation.Failed("post_write_read_back_mismatch"),
                mismatch,
            )
            assertEquals(expectedOperations(mutation), mismatchOperations)
        }
    }

    private fun executor(
        operations: MutableList<String>,
        validationProvider: DeviceOwnerValidationProvider,
        platform: DevicePolicyPlatform = RecordingPlatform(operations),
    ): VerifiedPolicyMutationExecutor {
        return VerifiedPolicyMutationExecutor(
            deviceOwnerValidationProvider = validationProvider,
            platform = platform,
            logger = logger,
        )
    }

    private fun expectedOperations(
        mutation: VerifiedPolicyMutation,
    ): List<String> {
        return when (mutation) {
            is VerifiedPolicyMutation.ScreenCapture ->
                listOf("screen-service", "validate", "screen-set:true", "screen-get")
            is VerifiedPolicyMutation.Camera ->
                listOf("camera-service", "validate", "camera-set:true", "camera-get")
        }
    }

    private fun validationProvider(
        operations: MutableList<String>,
    ): DeviceOwnerValidationProvider {
        return DeviceOwnerValidationProvider {
            operations += "validate"
            verifiedValidation()
        }
    }

    private class RecordingPlatform(
        private val operations: MutableList<String>,
        private val ignoreScreenWrites: Boolean = false,
        private val ignoreCameraWrites: Boolean = false,
    ) : DevicePolicyPlatform {
        private var screenDisabled = false
        private var cameraDisabled = false

        override fun policyService(): DevicePolicyReadService? = null

        override fun screenCapturePolicyService(): DevicePolicyScreenCaptureService {
            operations += "screen-service"
            return object : DevicePolicyScreenCaptureService {
                override fun isScreenCaptureDisabled(): Boolean {
                    operations += "screen-get"
                    return screenDisabled
                }

                override fun setScreenCaptureDisabled(disabled: Boolean) {
                    operations += "screen-set:$disabled"
                    if (!ignoreScreenWrites) {
                        screenDisabled = disabled
                    }
                }
            }
        }

        override fun cameraPolicyService(): DevicePolicyCameraService {
            operations += "camera-service"
            return object : DevicePolicyCameraService {
                override fun isCameraDisabled(): Boolean {
                    operations += "camera-get"
                    return cameraDisabled
                }

                override fun setCameraDisabled(disabled: Boolean) {
                    operations += "camera-set:$disabled"
                    if (!ignoreCameraWrites) {
                        cameraDisabled = disabled
                    }
                }
            }
        }

        override fun isExpectedAdminReceiverRegistered(): Boolean = true
    }

    private class NoOpLogger : DeviceManagementLogger {
        override fun info(event: String, fields: Map<String, Any?>) = Unit
        override fun warn(event: String, fields: Map<String, Any?>) = Unit
        override fun error(
            event: String,
            fields: Map<String, Any?>,
            throwable: Throwable?,
        ) = Unit
    }

    private companion object {
        const val CORRELATION_ID = "authoritative-correlation"
        const val EXPECTED_COMPONENT =
            "com.example.devicemanagement/.management.SentinelDeviceAdminReceiver"

        fun verifiedValidation(): DeviceOwnerValidation {
            return validation(ManagementMode.DEVICE_OWNER)
        }

        fun ordinaryAppValidation(): DeviceOwnerValidation {
            return validation(ManagementMode.ORDINARY_APP)
        }

        fun validation(mode: ManagementMode): DeviceOwnerValidation {
            val isDeviceOwner = mode == ManagementMode.DEVICE_OWNER
            val status = DeviceManagementStatus(
                mode = mode,
                isPolicyServiceAvailable = true,
                isExpectedAdminReceiverRegistered = true,
                isAdminActive = true,
                isDeviceOwner = isDeviceOwner,
                isProfileOwner = false,
                availableCapabilities = emptySet(),
                diagnostics = emptyList(),
            )
            return DeviceOwnerValidation(
                result = if (isDeviceOwner) {
                    DeviceOwnerValidationResult.VERIFIED_DEVICE_OWNER
                } else {
                    DeviceOwnerValidationResult.NOT_DEVICE_OWNER
                },
                packageName = "com.example.devicemanagement",
                expectedAdminReceiverComponent = EXPECTED_COMPONENT,
                registeredSentinelAdminComponents = setOf(EXPECTED_COMPONENT),
                managementStatus = status,
                provisioningReadiness = ProvisioningReadiness(
                    managementStatus = status,
                    deviceOwnerProvisioning = ProvisioningOption(
                        ProvisioningAvailability.NOT_ALLOWED,
                        emptyList(),
                    ),
                    profileOwnerProvisioning = ProvisioningOption(
                        ProvisioningAvailability.NOT_ALLOWED,
                        emptyList(),
                    ),
                ),
                reasons = emptyList(),
            )
        }
    }
}
