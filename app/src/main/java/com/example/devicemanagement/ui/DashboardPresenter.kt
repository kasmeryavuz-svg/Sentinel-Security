package com.example.devicemanagement.ui

import com.example.devicemanagement.action.SensitiveActionController
import com.example.devicemanagement.audit.AuditHistory
import com.example.devicemanagement.audit.AuditHistoryProvider
import com.example.devicemanagement.audit.AuditReasonCode
import com.example.devicemanagement.audit.AuditSchema
import com.example.devicemanagement.audit.AuditStorageHealth
import com.example.devicemanagement.audit.AuditStorageStatus
import com.example.devicemanagement.audit.AuditStorageStatusProvider
import com.example.devicemanagement.trigger.Trigger
import java.util.UUID

/**
 * Presentation coordinator for the Sentinel Security dashboard.
 *
 * Mutation requests are submitted only through [SensitiveActionController].
 * This class does not approve, write, or verify policy changes.
 *
 * Audit history is loaded only from the read-only durable audit providers.
 * The presenter never appends audit events or infers Applied from a button press.
 */
class DashboardPresenter(
    private val readSnapshot: () -> DashboardSnapshot,
    private val sensitiveActions: SensitiveActionController,
    private val auditHistory: AuditHistoryProvider,
    private val auditStorageStatus: AuditStorageStatusProvider,
    private val requestLifetimeMillis: Long = REQUEST_LIFETIME_MILLIS,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val requestIdGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private var pendingCapability: PolicyCapability? = null

    fun currentState(): DashboardViewState {
        return DashboardStateMapper.map(
            snapshot = readSnapshot(),
            auditHistory = readAuditHistory(),
            auditStatus = readAuditStatus(),
            pendingCapability = pendingCapability,
        )
    }

    /**
     * Submits one trusted enable/disable command.
     *
     * [onPending] receives the in-progress presentation before the controller
     * returns. Audit results are never published from this method; they are
     * read back from the durable audit provider after the controller returns.
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
        sensitiveActions.submit(
            Trigger(
                command = DashboardCommands.command(capability, disable),
                requestId = requestIdGenerator(),
                expiresAtEpochMillis = nowEpochMillis() + requestLifetimeMillis,
            ),
        )
        pendingCapability = null
        return currentState()
    }

    private fun readAuditHistory(): AuditHistory {
        return try {
            auditHistory.latest(AuditSchema.DASHBOARD_LIMIT * 2)
        } catch (_: Throwable) {
            AuditHistory(
                events = emptyList(),
                storedCount = 0,
                retentionBound = AuditSchema.RETENTION_BOUND,
            )
        }
    }

    private fun readAuditStatus(): AuditStorageStatus {
        return try {
            auditStorageStatus.currentStatus()
        } catch (_: Throwable) {
            AuditStorageStatus(
                health = AuditStorageHealth.UNAVAILABLE,
                reasonCode = AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE,
            )
        }
    }

    private companion object {
        const val REQUEST_LIFETIME_MILLIS = 30_000L
    }
}
