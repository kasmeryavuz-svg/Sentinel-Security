package com.example.devicemanagement.ui

import com.example.devicemanagement.action.ActionResult
import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.action.SensitiveActionOperation
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
    fun `Applied result is shown only after the trusted controller returns`() {
        val pendingStates = mutableListOf<DashboardViewState>()
        var snapshot = DashboardTestFixtures.snapshot(
            screenCapture = ScreenCapturePolicyState.ENABLED,
        )
        val presenter = createPresenter(
            readSnapshot = { snapshot },
            result = ActionResult.Applied(
                operation = SensitiveActionOperation.DISABLE_SCREEN_CAPTURE,
                requestedDisabled = true,
                observedDisabled = true,
                correlationId = "authoritative-applied",
            ),
            afterSubmit = {
                snapshot = DashboardTestFixtures.snapshot(
                    screenCapture = ScreenCapturePolicyState.DISABLED,
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
        assertNull(pendingStates.single().screenCapture.latestCorrelationId)
        assertTrue(pendingStates.single().operationInProgress)
        assertEquals(OperationOutcomePresentation.APPLIED, completed.screenCapture.latestOutcome)
        assertEquals("authoritative-applied", completed.screenCapture.latestCorrelationId)
        assertEquals(PolicyPresentationState.DISABLED, completed.screenCapture.state)
        assertFalse(completed.operationInProgress)
    }

    @Test
    fun `Denied and Failed results are recorded without claiming Applied`() {
        val deniedPresenter = createPresenter(
            result = ActionResult.Rejected(
                reason = "decision_denied:DEVICE_OWNER_NOT_VERIFIED",
                correlationId = "denied-corr",
            ),
        )
        val failedPresenter = createPresenter(
            result = ActionResult.Failed(
                reason = "post_write_read_back_mismatch",
                correlationId = "failed-corr",
            ),
        )

        val denied = deniedPresenter.submitAction(PolicyCapability.CAMERA, disable = true)
        val failed = failedPresenter.submitAction(PolicyCapability.STATUS_BAR, disable = true)

        assertEquals(OperationOutcomePresentation.DENIED, denied.camera.latestOutcome)
        assertEquals("denied-corr", denied.camera.latestCorrelationId)
        assertEquals(
            "decision_denied:DEVICE_OWNER_NOT_VERIFIED",
            denied.sessionActivity.single().reason,
        )
        assertEquals(OperationOutcomePresentation.FAILED, failed.statusBar.latestOutcome)
        assertEquals("failed-corr", failed.statusBar.latestCorrelationId)
        assertEquals(
            "post_write_read_back_mismatch",
            failed.sessionActivity.single().reason,
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

        val completed = presenter.submitAction(PolicyCapability.CAMERA, disable = true)

        assertEquals(1, submitted.size)
        assertEquals(1, completed.sessionActivity.size)
        assertEquals("only-once", completed.sessionActivity.single().correlationId)
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
        assertEquals(OperationOutcomePresentation.APPLIED, completed.camera.latestOutcome)
    }

    private fun createPresenter(
        readSnapshot: () -> DashboardSnapshot = { DashboardTestFixtures.snapshot() },
        result: ActionResult,
        onSubmit: (Trigger?) -> Unit = {},
        afterSubmit: () -> Unit = {},
    ): DashboardPresenter {
        val controller = SensitiveActionController { trigger ->
            onSubmit(trigger)
            afterSubmit()
            result
        }
        return DashboardPresenter(
            readSnapshot = readSnapshot,
            sensitiveActions = controller,
            sessionActivity = SessionActivityStore(sessionTimestampMillis = { 100L }),
            nowEpochMillis = { 1_000L },
            requestIdGenerator = { "caller-request" },
        )
    }
}
