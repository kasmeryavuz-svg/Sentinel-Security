package com.example.devicemanagement.management

import org.junit.Assert.assertEquals
import org.junit.Test

class NarrowPolicyServiceApiTest {
    @Test
    fun `DevicePolicyManager read wrapper exposes query operations only`() {
        assertEquals(
            setOf(
                "isDeviceOwnerApp",
                "isProfileOwnerApp",
                "isExpectedAdminActive",
                "isDeviceOwnerProvisioningAllowed",
                "isProfileOwnerProvisioningAllowed",
                "activeAdminComponentNames",
            ),
            DevicePolicyReadService::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `screen capture wrapper exposes only its approved policy surface`() {
        assertEquals(
            setOf("isScreenCaptureDisabled", "setScreenCaptureDisabled"),
            DevicePolicyScreenCaptureService::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `camera wrapper exposes only its approved policy surface`() {
        assertEquals(
            setOf("isCameraDisabled", "setCameraDisabled"),
            DevicePolicyCameraService::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `status bar wrapper exposes only its approved policy surface`() {
        assertEquals(
            setOf("isStatusBarDisabled", "setStatusBarDisabled"),
            DevicePolicyStatusBarService::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `public screen capture status provider is read only`() {
        assertEquals(
            setOf("currentStatus"),
            ScreenCapturePolicyStatusProvider::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `public camera status provider is read only`() {
        assertEquals(
            setOf("currentStatus"),
            CameraPolicyStatusProvider::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `public status bar status provider is read only`() {
        assertEquals(
            setOf("currentStatus"),
            StatusBarPolicyStatusProvider::class.java.declaredMethods
                .filterNot { it.isSynthetic }
                .map { it.name }
                .toSet(),
        )
    }
}
