package com.example.devicemanagement.ui

import com.example.devicemanagement.audit.AuditActionNames
import com.example.devicemanagement.audit.AuditEventPhase
import com.example.devicemanagement.audit.AuditReasonCode
import com.example.devicemanagement.audit.AuditStorageHealth
import com.example.devicemanagement.audit.AuditStorageStatus
import com.example.devicemanagement.management.CameraPolicyState
import com.example.devicemanagement.management.DeviceOwnerValidationResult
import com.example.devicemanagement.management.ManagementMode
import com.example.devicemanagement.management.ProvisioningAvailability
import com.example.devicemanagement.management.ScreenCapturePolicyState
import com.example.devicemanagement.management.StatusBarPolicyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStateMapperTest {
    @Test
    fun `verified Device Owner maps to verified dashboard header and management`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(),
            auditHistory = DashboardTestFixtures.history(),
            auditStatus = DashboardTestFixtures.healthyStatus(),
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
            auditHistory = DashboardTestFixtures.history(),
            auditStatus = DashboardTestFixtures.healthyStatus(),
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
            auditHistory = DashboardTestFixtures.history(),
            auditStatus = DashboardTestFixtures.healthyStatus(),
            pendingCapability = null,
        )
        val unavailable = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                validationResult = DeviceOwnerValidationResult.UNAVAILABLE,
                mode = ManagementMode.UNAVAILABLE,
                isPolicyServiceAvailable = false,
            ),
            auditHistory = DashboardTestFixtures.history(),
            auditStatus = DashboardTestFixtures.healthyStatus(),
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
    fun `policy cards copy provider state and do not infer from last audit result`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                screenCapture = ScreenCapturePolicyState.ENABLED,
                camera = CameraPolicyState.DISABLED,
                statusBar = StatusBarPolicyState.ENABLED,
            ),
            auditHistory = DashboardTestFixtures.history(
                DashboardTestFixtures.auditEvent(
                    actionName = AuditActionNames.DISABLE_SCREEN_CAPTURE,
                    phase = AuditEventPhase.APPLIED,
                    correlationId = "screen-applied",
                ),
            ),
            auditStatus = DashboardTestFixtures.healthyStatus(),
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
            auditHistory = DashboardTestFixtures.history(
                DashboardTestFixtures.auditEvent(
                    actionName = AuditActionNames.DISABLE_SCREEN_CAPTURE,
                    phase = AuditEventPhase.APPLIED,
                    correlationId = "applied-id",
                    sequence = 3,
                ),
                DashboardTestFixtures.auditEvent(
                    actionName = AuditActionNames.DISABLE_CAMERA,
                    phase = AuditEventPhase.REJECTED,
                    correlationId = "denied-id",
                    sequence = 2,
                    reason = AuditReasonCode.DEVICE_OWNER_NOT_VERIFIED,
                ),
                DashboardTestFixtures.auditEvent(
                    actionName = AuditActionNames.DISABLE_STATUS_BAR,
                    phase = AuditEventPhase.FAILED,
                    correlationId = "failed-id",
                    sequence = 1,
                    reason = AuditReasonCode.POST_WRITE_READ_BACK_MISMATCH,
                ),
            ),
            auditStatus = DashboardTestFixtures.healthyStatus(),
            pendingCapability = null,
        )

        assertEquals(OperationOutcomePresentation.APPLIED, state.screenCapture.latestOutcome)
        assertNull(state.screenCapture.latestOutcomeDetail)
        assertEquals(OperationOutcomePresentation.DENIED, state.camera.latestOutcome)
        assertEquals(
            AuditReasonCode.DEVICE_OWNER_NOT_VERIFIED.name,
            state.camera.latestOutcomeDetail,
        )
        assertEquals(OperationOutcomePresentation.FAILED, state.statusBar.latestOutcome)
        assertEquals(
            AuditReasonCode.POST_WRITE_READ_BACK_MISMATCH.name,
            state.statusBar.latestOutcomeDetail,
        )
        assertEquals("failed-id", state.statusBar.latestCorrelationId)
    }

    @Test
    fun `incomplete REQUESTED is Interrupted and never Applied`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(),
            auditHistory = DashboardTestFixtures.history(
                DashboardTestFixtures.auditEvent(
                    actionName = AuditActionNames.DISABLE_CAMERA,
                    phase = AuditEventPhase.REQUESTED,
                    correlationId = "interrupted",
                ),
            ),
            auditStatus = DashboardTestFixtures.healthyStatus(),
            pendingCapability = null,
        )

        assertEquals(AuditLogStatus.INTERRUPTED, state.auditLog.single().status)
        assertEquals(OperationOutcomePresentation.INTERRUPTED, state.camera.latestOutcome)
        assertNotEquals(AuditLogStatus.APPLIED, state.auditLog.single().status)
        assertNotEquals(OperationOutcomePresentation.APPLIED, state.camera.latestOutcome)
        assertEquals("interrupted", state.auditLog.single().correlationId)
    }

    @Test
    fun `SIMULATED is never presented as Applied`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(),
            auditHistory = DashboardTestFixtures.history(
                DashboardTestFixtures.auditEvent(
                    actionName = AuditActionNames.MOCK_WIPE,
                    phase = AuditEventPhase.SIMULATED,
                    correlationId = "sim",
                    reason = AuditReasonCode.SIMULATED,
                ),
            ),
            auditStatus = DashboardTestFixtures.healthyStatus(),
            pendingCapability = null,
        )

        assertEquals(AuditLogStatus.SIMULATED, state.auditLog.single().status)
        assertNotEquals(AuditLogStatus.APPLIED, state.auditLog.single().status)
    }

    @Test
    fun `pending capability shows pending and disables all actions before a result`() {
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(),
            auditHistory = DashboardTestFixtures.history(
                DashboardTestFixtures.auditEvent(
                    actionName = AuditActionNames.DISABLE_CAMERA,
                    phase = AuditEventPhase.APPLIED,
                    correlationId = "old-applied",
                ),
            ),
            auditStatus = DashboardTestFixtures.healthyStatus(),
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
            auditHistory = DashboardTestFixtures.history(),
            auditStatus = DashboardTestFixtures.healthyStatus(),
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
            auditHistory = DashboardTestFixtures.history(),
            auditStatus = DashboardTestFixtures.healthyStatus(),
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
            auditHistory = DashboardTestFixtures.history(),
            auditStatus = DashboardTestFixtures.healthyStatus(),
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
    fun `audit history is attached without treating it as current policy`() {
        val history = DashboardTestFixtures.history(
            DashboardTestFixtures.auditEvent(
                actionName = AuditActionNames.DISABLE_CAMERA,
                correlationId = "keep-me",
            ),
        )
        val state = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(
                camera = CameraPolicyState.ENABLED,
            ),
            auditHistory = history,
            auditStatus = DashboardTestFixtures.healthyStatus(),
            pendingCapability = null,
        )

        assertEquals("keep-me", state.auditLog.single().correlationId)
        assertEquals(PolicyPresentationState.ENABLED, state.camera.state)
        assertEquals("keep-me", state.camera.latestCorrelationId)
        assertEquals(AuditStorageHealth.HEALTHY, state.auditStorageHealth)
    }

    @Test
    fun `degraded and unavailable audit health are visible presentation states`() {
        val degraded = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(),
            auditHistory = DashboardTestFixtures.history(),
            auditStatus = AuditStorageStatus(
                health = AuditStorageHealth.DEGRADED,
                reasonCode = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE,
            ),
            pendingCapability = null,
        )
        val unavailable = DashboardStateMapper.map(
            snapshot = DashboardTestFixtures.snapshot(),
            auditHistory = DashboardTestFixtures.history(),
            auditStatus = AuditStorageStatus(
                health = AuditStorageHealth.UNAVAILABLE,
                reasonCode = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE,
            ),
            pendingCapability = null,
        )

        assertEquals(AuditStorageHealth.DEGRADED, degraded.auditStorageHealth)
        assertEquals(AuditStorageHealth.UNAVAILABLE, unavailable.auditStorageHealth)
    }
}
