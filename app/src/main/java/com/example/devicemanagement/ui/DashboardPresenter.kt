package com.example.devicemanagement.ui

import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.trigger.Trigger
import java.util.UUID

/**
 * Presentation coordinator for the Sentinel Security dashboard.
 *
 * Mutation requests are submitted only through [SensitiveActionController].
 * This class does not approve, write, or verify policy changes.
 *
 * Session activity recorded here is NON-PERSISTENT in-memory history.
 */
class DashboardPresenter(
    private val readSnapshot: () -> DashboardSnapshot,
    private val sensitiveActions: SensitiveActionController,
    private val sessionActivity: SessionActivityStore,
    private val requestLifetimeMillis: Long = REQUEST_LIFETIME_MILLIS,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val requestIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private var pendingCapability: PolicyCapability? = null

    fun currentState(): DashboardViewState {
        return DashboardStateMapper.map(
            snapshot = readSnapshot(),
            sessionEntries = sessionActivity.entries(),
            pendingCapability = pendingCapability,
        )
    }

    /**
     * Submits one trusted enable/disable command.
     *
     * [onPending] receives the in-progress presentation before the controller
     * returns. Success is never published from this method until the controller
     * result has been recorded.
     */
    fun submitAction(
        capability: PolicyCapability,
        disable: Boolean,
        onPending: (DashboardViewState) -> Unit = {},
    ): DashboardViewState {
        if (pendingCapability != null) {
            return currentState()
        }
        pendingCapability = capability
        onPending(currentState())
        val result = sensitiveActions.submit(
            Trigger(
                command = DashboardCommands.command(capability, disable),
                requestId = requestIdGenerator(),
                expiresAtEpochMillis = nowEpochMillis() + requestLifetimeMillis,
            ),
        )
        sessionActivity.record(
            capability = capability,
            requestedDisabled = disable,
            result = result,
        )
        pendingCapability = null
        return currentState()
    }

    private companion object {
        const val REQUEST_LIFETIME_MILLIS = 30_000L
    }
}
