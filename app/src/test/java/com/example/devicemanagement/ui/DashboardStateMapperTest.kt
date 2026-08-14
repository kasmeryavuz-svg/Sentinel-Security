package com.example.devicemanagement.ui

import com.example.devicemanagement.management.CameraPolicyState
import com.example.devicemanagement.management.DeviceOwnerValidationResult
import com.example.devicemanagement.management.ManagementMode
import com.example.devicemanagement.management.ProvisioningAvailability
import com.example.devicemanagement.management.ScreenCapturePolicyState
import com.example.devicemanagement.management.StatusBarPolicyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStateMapperTest {
    @Test
    fun `verified Device Owner maps to verified dashboard header and management`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(),
            sessionEntries = emptyList(),
            pendingCapability = null,
        )

        assertEquals(
            VerificationPresentation.VERIFIED_DEVICE_OWNER,
            state.header.verification,
        )
        assertEquals(ManagementModePresentation.DEVICE_OWNER, state.management.mode)
        assertEquals(
            DashboardTestFixtures.EXPECTED_ADMIN,
            state.management.expectedAdminReceiver,
        )
        assertEquals(
            VerificationPresentation.VERIFIED_DEVICE_OWNER,
            state.management.verification,
        )
        assertEquals(
            ProvisioningPresentation.NOT_ALLOWED,
            state.management.deviceOwnerProvisioning,
        )
        assertFalse(state.operationInProgress)
    }

    @Test
    fun `not Device Owner warning state remains visible without inferring policy`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                validationResult = DeviceOwnerValidationResult.NOT_DEVICE_OWNER,
                mode = ManagementMode.ORDINARY_APP,
                screenCapture = ScreenCapturePolicyState.UNAVAILABLE,
                camera = CameraPolicyState.UNAVAILABLE,
                statusBar = StatusBarPolicyState.UNAVAILABLE,
                screenCaptureReasons = listOf("not owner"),
            ),
            sessionEntries = emptyList(),
            pendingCapability = null,
        )

        assertEquals(
            VerificationPresentation.NOT_DEVICE_OWNER,
            state.header.verification,
        )
        assertEquals(PolicyPresentationState.UNAVAILABLE, state.screenCapture.state)
        assertEquals(listOf("not owner"), state.screenCapture.reasons)
        assertTrue(state.screenCapture.actionsEnabled)
        assertTrue(state.camera.actionsEnabled)
        assertTrue(state.statusBar.actionsEnabled)
        assertFalse(state.statusBar.requiresApi34Notice)
    }

    @Test
    fun `configuration error and unavailable validation are distinct`() {
        val configuration = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                validationResult = DeviceOwnerValidationResult.CONFIGURATION_ERROR,
            ),
            sessionEntries = emptyList(),
            pendingCapability = null,
        )
        val unavailable = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                validationResult = DeviceOwnerValidationResult.UNAVAILABLE,
                mode = ManagementMode.UNAVAILABLE,
                isPolicyServiceAvailable = false,
            ),
            sessionEntries = emptyList(),
            pendingCapability = null,
        )

        assertEquals(
            VerificationPresentation.CONFIGURATION_ERROR,
            configuration.header.verification,
        )
        assertEquals(
            VerificationPresentation.UNAVAILABLE,
            unavailable.header.verification,
        )
        assertEquals(ManagementModePresentation.UNAVAILABLE, unavailable.management.mode)
        assertFalse(unavailable.management.isPolicyServiceAvailable)
    }

    @Test
    fun `policy cards copy provider state and do not infer from last result`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                screenCapture = ScreenCapturePolicyState.ENABLED,
                camera = CameraPolicyState.DISABLED,
                statusBar = StatusBarPolicyState.ENABLED,
            ),
            sessionEntries = listOf(
                DashboardTestFixtures.sessionEntry(
                    capability = PolicyCapability.SCREEN_CAPTURE,
                    requestedDisabled = true,
                    outcome = OperationOutcomePresentation.APPLIED,
                    correlationId = "screen-applied",
                ),
            ),
            pendingCapability = null,
        )

        assertEquals(PolicyPresentationState.ENABLED, state.screenCapture.state)
        assertEquals(PolicyPresentationState.DISABLED, state.camera.state)
        assertEquals(PolicyPresentationState.ENABLED, state.statusBar.state)
        assertEquals(OperationOutcomePresentation.APPLIED, state.screenCapture.latestOutcome)
        assertEquals("screen-applied", state.screenCapture.latestCorrelationId)
        assertEquals(OperationOutcomePresentation.NONE, state.camera.latestOutcome)
    }

    @Test
    fun `Applied Denied and Failed outcomes are presented distinctly`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(),
            sessionEntries = listOf(
                DashboardTestFixtures.sessionEntry(
                    capability = PolicyCapability.SCREEN_CAPTURE,
                    outcome = OperationOutcomePresentation.APPLIED,
                    correlationId = "applied-id",
                ),
                DashboardTestFixtures.sessionEntry(
                    capability = PolicyCapability.CAMERA,
                    outcome = OperationOutcomePresentation.DENIED,
                    correlationId = "denied-id",
                    reason = "decision_denied:DEVICE_OWNER_NOT_VERIFIED",
                ),
                DashboardTestFixtures.sessionEntry(
                    capability = PolicyCapability.STATUS_BAR,
                    outcome = OperationOutcomePresentation.FAILED,
                    correlationId = "failed-id",
                    reason = "post_write_read_back_mismatch",
                ),
            ),
            pendingCapability = null,
        )

        assertEquals(OperationOutcomePresentation.APPLIED, state.screenCapture.latestOutcome)
        assertNull(state.screenCapture.latestOutcomeDetail)
        assertEquals(OperationOutcomePresentation.DENIED, state.camera.latestOutcome)
        assertEquals(
            "decision_denied:DEVICE_OWNER_NOT_VERIFIED",
            state.camera.latestOutcomeDetail,
        )
        assertEquals(OperationOutcomePresentation.FAILED, state.statusBar.latestOutcome)
        assertEquals("post_write_read_back_mismatch", state.statusBar.latestOutcomeDetail)
        assertEquals("failed-id", state.statusBar.latestCorrelationId)
    }

    @Test
    fun `pending capability shows pending and disables all actions before a result`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(),
            sessionEntries = listOf(
                DashboardTestFixtures.sessionEntry(
                    capability = PolicyCapability.CAMERA,
                    outcome = OperationOutcomePresentation.APPLIED,
                    correlationId = "old-applied",
                ),
            ),
            pendingCapability = PolicyCapability.CAMERA,
        )

        assertTrue(state.operationInProgress)
        assertEquals(OperationOutcomePresentation.PENDING, state.camera.latestOutcome)
        assertNull(state.camera.latestCorrelationId)
        assertFalse(state.screenCapture.actionsEnabled)
        assertFalse(state.camera.actionsEnabled)
        assertFalse(state.statusBar.actionsEnabled)
    }

    @Test
    fun `status bar unavailable with API 34 reason renders unsupported notice`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                statusBar = StatusBarPolicyState.UNAVAILABLE,
                statusBarReasons = listOf(
                    "Status-bar policy requires Android 14 (API 34) or newer " +
                        "for verified setter/read-back.",
                ),
            ),
            sessionEntries = emptyList(),
            pendingCapability = null,
        )

        assertEquals(PolicyPresentationState.UNAVAILABLE, state.statusBar.state)
        assertTrue(state.statusBar.requiresApi34Notice)
        assertFalse(state.statusBar.actionsEnabled)
        assertTrue(state.screenCapture.actionsEnabled)
        assertTrue(state.camera.actionsEnabled)
    }

    @Test
    fun `status bar unavailable without API 34 reason keeps trusted actions available`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                validationResult = DeviceOwnerValidationResult.NOT_DEVICE_OWNER,
                mode = ManagementMode.ORDINARY_APP,
                statusBar = StatusBarPolicyState.UNAVAILABLE,
                statusBarReasons = listOf("Device Owner is not verified."),
            ),
            sessionEntries = emptyList(),
            pendingCapability = null,
        )

        assertTrue(state.statusBar.actionsEnabled)
        assertFalse(state.statusBar.requiresApi34Notice)
    }

    @Test
    fun `provisioning availability is copied from the readiness provider`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                validationResult = DeviceOwnerValidationResult.NOT_DEVICE_OWNER,
                mode = ManagementMode.ORDINARY_APP,
                deviceOwnerProvisioning = ProvisioningAvailability.ALLOWED,
                profileOwnerProvisioning = ProvisioningAvailability.UNAVAILABLE,
            ),
            sessionEntries = emptyList(),
            pendingCapability = null,
        )

        assertEquals(ProvisioningPresentation.ALLOWED, state.management.deviceOwnerProvisioning)
        assertEquals(
            ProvisioningPresentation.UNAVAILABLE,
            state.management.profileOwnerProvisioning,
        )
        assertEquals(
            listOf("device-owner-provisioning"),
            state.management.deviceOwnerProvisioningReasons,
        )
    }

    @Test
    fun `session activity is attached without treating it as current policy`() {
        val entries = listOf(
            DashboardTestFixtures.sessionEntry(
                capability = PolicyCapability.CAMERA,
                correlationId = "keep-me",
            ),
        )
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                camera = CameraPolicyState.ENABLED,
            ),
            sessionEntries = entries,
            pendingCapability = null,
        )

        assertEquals(entries, state.sessionActivity)
        assertEquals(PolicyPresentationState.ENABLED, state.camera.state)
        assertEquals("keep-me", state.camera.latestCorrelationId)
    }
}
