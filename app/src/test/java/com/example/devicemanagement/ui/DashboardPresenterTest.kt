package com.example.devicemanagement.ui

import com.example.devicemanagement.action.ActionResult
import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.action.SensitiveActionOperation
import com.example.devicemanagement.audit.AuditActionNames
import com.example.devicemanagement.audit.AuditEvent
import com.example.devicemanagement.audit.AuditEventPhase
import com.example.devicemanagement.audit.AuditHistory
import com.example.devicemanagement.audit.AuditHistoryProvider
import com.example.devicemanagement.audit.AuditReasonCode
import com.example.devicemanagement.audit.AuditSchema
import com.example.devicemanagement.audit.AuditStorageHealth
import com.example.devicemanagement.audit.AuditStorageStatus
import com.example.devicemanagement.audit.AuditStorageStatusProvider
import com.example.devicemanagement.management.CameraPolicyState
import com.example.devicemanagement.management.ScreenCapturePolicyState
import com.example.devicemanagement.trigger.SensitiveActionCommands
import com.example.devicemanagement.trigger.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardPresenterTest {
    @Test
    fun `Applied is shown only after the trusted controller returns and audit records it`() {
        val pendingStates = mutableListOf<DashboardViewState>()
        var snapshot = DashboardTestFixtures.snapshot(
            screenCapture = ScreenCapturePolicyState.ENABLED,
        )
        val audit = RecordingAuditProviders()
        val presenter = createPresenter(
            readSnapshot = { snapshot },
            result = ActionResult.Applied(
                operation = SensitiveActionOperation.DISABLE_SCREEN_CAPTURE,
                requestedDisabled = true,
                observedDisabled = true,
                correlationId = "authoritative-applied",
            ),
            audit = audit,
            afterSubmit = {
                snapshot = DashboardTestFixtures.snapshot(
                    screenCapture = ScreenCapturePolicyState.DISABLED,
                )
                audit.record(
                    actionName = AuditActionNames.DISABLE_SCREEN_CAPTURE,
                    phase = AuditEventPhase.APPLIED,
                    correlationId = "authoritative-applied",
                )
            },
        )

        val completed = presenter.submitAction(
            capability = PolicyCapability.SCREEN_CAPTURE,
            disable = true,
            onPending = pendingStates::add,
        )

        assertEquals(1, pendingStates.size)
        assertEquals(OperationOutcomePresentation.PENDING, pendingStates.single().screenCapture.latestOutcome)
        assertNotEquals(OperationOutcomePresentation.APPLIED, pendingStates.single().screenCapture.latestOutcome)
        assertTrue(pendingStates.single().auditLog.isEmpty())
        assertEquals(OperationOutcomePresentation.APPLIED, completed.screenCapture.latestOutcome)
        assertEquals("authoritative-applied", completed.screenCapture.latestCorrelationId)
        assertEquals(PolicyPresentationState.DISABLED, completed.screenCapture.state)
        assertEquals(AuditLogStatus.APPLIED, completed.auditLog.single().status)
        assertFalse(completed.operationInProgress)
    }

    @Test
    fun `dashboard does not manufacture Applied from a controller result`() {
        val presenter = createPresenter(
            result = ActionResult.Applied(
                operation = SensitiveActionOperation.DISABLE_CAMERA,
                requestedDisabled = true,
                observedDisabled = true,
                correlationId = "not-written",
            ),
        )

        val completed = presenter.submitAction(PolicyCapability.CAMERA, disable = true)

        assertTrue(completed.auditLog.isEmpty())
        assertNotEquals(OperationOutcomePresentation.APPLIED, completed.camera.latestOutcome)
        assertEquals(OperationOutcomePresentation.NONE, completed.camera.latestOutcome)
    }

    @Test
    fun `Denied and Failed results are read from audit history`() {
        val deniedAudit = RecordingAuditProviders()
        deniedAudit.record(
            actionName = AuditActionNames.DISABLE_CAMERA,
            phase = AuditEventPhase.REJECTED,
            correlationId = "denied-corr",
            reason = AuditReasonCode.DEVICE_OWNER_NOT_VERIFIED,
        )
        val failedAudit = RecordingAuditProviders()
        failedAudit.record(
            actionName = AuditActionNames.DISABLE_STATUS_BAR,
            phase = AuditEventPhase.FAILED,
            correlationId = "failed-corr",
            reason = AuditReasonCode.POST_WRITE_READ_BACK_MISMATCH,
        )
        val denied = createPresenter(
            result = ActionResult.Rejected(
                reason = "decision_denied:DEVICE_OWNER_NOT_VERIFIED",
                correlationId = "denied-corr",
            ),
            audit = deniedAudit,
        ).submitAction(PolicyCapability.CAMERA, disable = true)
        val failed = createPresenter(
            result = ActionResult.Failed(
                reason = "post_write_read_back_mismatch",
                correlationId = "failed-corr",
            ),
            audit = failedAudit,
        ).submitAction(PolicyCapability.STATUS_BAR, disable = true)

        assertEquals(OperationOutcomePresentation.DENIED, denied.camera.latestOutcome)
        assertEquals("denied-corr", denied.camera.latestCorrelationId)
        assertEquals(AuditLogStatus.REJECTED, denied.auditLog.single().status)
        assertEquals(OperationOutcomePresentation.FAILED, failed.statusBar.latestOutcome)
        assertEquals("failed-corr", failed.statusBar.latestCorrelationId)
        assertEquals(
            AuditReasonCode.POST_WRITE_READ_BACK_MISMATCH.name,
            failed.auditLog.single().reasonCode,
        )
    }

    @Test
    fun `presenter submits only the trusted command for the selected card`() {
        val submitted = mutableListOf<Trigger?>()
        val presenter = createPresenter(
            result = ActionResult.Rejected(reason = "held", correlationId = "held"),
            onSubmit = submitted::add,
        )

        presenter.submitAction(PolicyCapability.CAMERA, disable = false)

        assertEquals(1, submitted.size)
        assertEquals(SensitiveActionCommands.ENABLE_CAMERA, submitted.single()?.command)
        assertTrue(DashboardCommands.trustedCommands().contains(submitted.single()?.command))
    }

    @Test
    fun `duplicate submit is ignored while an operation is pending`() {
        val submitted = mutableListOf<Trigger?>()
        lateinit var presenter: DashboardPresenter
        presenter = createPresenter(
            result = ActionResult.Applied(
                operation = SensitiveActionOperation.DISABLE_CAMERA,
                requestedDisabled = true,
                observedDisabled = true,
                correlationId = "only-once",
            ),
            onSubmit = { trigger ->
                submitted += trigger
                presenter.submitAction(PolicyCapability.CAMERA, disable = true)
            },
        )

        presenter.submitAction(PolicyCapability.CAMERA, disable = true)

        assertEquals(1, submitted.size)
    }

    @Test
    fun `status is refreshed from providers after the controller returns`() {
        var snapshot = DashboardTestFixtures.snapshot(
            camera = CameraPolicyState.ENABLED,
        )
        val presenter = createPresenter(
            readSnapshot = { snapshot },
            result = ActionResult.Applied(
                operation = SensitiveActionOperation.DISABLE_CAMERA,
                requestedDisabled = true,
                observedDisabled = true,
                correlationId = "refresh",
            ),
            afterSubmit = {
                snapshot = DashboardTestFixtures.snapshot(
                    camera = CameraPolicyState.DISABLED,
                )
            },
        )

        val completed = presenter.submitAction(PolicyCapability.CAMERA, disable = true)

        assertEquals(PolicyPresentationState.DISABLED, completed.camera.state)
        assertNotEquals(OperationOutcomePresentation.APPLIED, completed.camera.latestOutcome)
    }

    @Test
    fun `storage failure is visible without crashing`() {
        val audit = RecordingAuditProviders(
            status = AuditStorageStatus(
                health = AuditStorageHealth.UNAVAILABLE,
                reasonCode = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE,
            ),
        )
        val presenter = createPresenter(
            result = ActionResult.Rejected(
                reason = "audit_persistence_unavailable",
                correlationId = "blocked",
            ),
            audit = audit,
        )

        val state = presenter.currentState()

        assertEquals(AuditStorageHealth.UNAVAILABLE, state.auditStorageHealth)
        assertTrue(state.auditLog.isEmpty())
    }

    private fun createPresenter(
        readSnapshot: () -> DashboardSnapshot = { DashboardTestFixtures.snapshot() },
        result: ActionResult,
        onSubmit: (Trigger?) -> Unit = {},
        afterSubmit: () -> Unit = {},
        audit: RecordingAuditProviders = RecordingAuditProviders(),
    ): DashboardPresenter {
        val controller = SensitiveActionController { trigger ->
            onSubmit(trigger)
            afterSubmit()
            result
        }
        return DashboardPresenter(
            readSnapshot = readSnapshot,
            sensitiveActions = controller,
            auditHistory = audit,
            auditStorageStatus = audit,
            nowEpochMillis = { 1_000L },
            requestIdGenerator = { "caller-request" },
        )
    }
}

internal class RecordingAuditProviders(
    var events: MutableList<AuditEvent> = mutableListOf(),
    var status: AuditStorageStatus = AuditStorageStatus(
        health = AuditStorageHealth.HEALTHY,
        reasonCode = null,
    ),
) : AuditHistoryProvider, AuditStorageStatusProvider {
    override fun latest(limit: Int): AuditHistory {
        return AuditHistory(
            events = events.take(limit),
            storedCount = events.size,
            retentionBound = AuditSchema.RETENTION_BOUND,
        )
    }

    override fun currentStatus(): AuditStorageStatus = status

    fun record(
        actionName: String,
        phase: AuditEventPhase,
        correlationId: String,
        reason: AuditReasonCode? = null,
        sequence: Long = events.size + 1L,
    ) {
        events += AuditEvent(
            sequence = sequence,
            eventId = "event-$sequence",
            correlationId = correlationId,
            actionName = actionName,
            phase = phase,
            presentationWallClockMillis = 1_000L,
            reasonCode = reason,
        )
    }
}
