package com.example.devicemanagement.audit

/**
 * Immutable durable audit record.
 *
 * Wall-clock time is presentation/audit metadata only. It must never be used
 * for authorization, freshness, cooldowns, or replay protection.
 *
 * App-private SQLite storage is durable and architecture-controlled. It is not
 * cryptographically tamper-proof, anti-rollback, or remotely archived.
 */
data class AuditEvent(
    val sequence: Long,
    val eventId: String,
    val correlationId: String,
    val actionName: String,
    val phase: AuditEventPhase,
    val presentationWallClockMillis: Long,
    val reasonCode: AuditReasonCode?,
)

enum class AuditEventPhase {
    REQUESTED,
    APPLIED,
    REJECTED,
    FAILED,
    SIMULATED,
}

enum class AuditReasonCode {
    AUDIT_PERSISTENCE_UNAVAILABLE,
    INVALID_TRIGGER,
    MISSING_STATE,
    SERVICE_UNAVAILABLE,
    INCONSISTENT_MANAGEMENT_STATE,
    PROFILE_OWNER_NOT_ALLOWED,
    ADMIN_RECEIVER_NOT_REGISTERED,
    ADMIN_NOT_ACTIVE,
    DEVICE_OWNER_NOT_VERIFIED,
    SENSITIVE_ACTIONS_DISABLED,
    EVALUATION_ERROR,
    DECISION_NOT_APPROVED,
    APPROVAL_NOT_ISSUED_OR_ALREADY_CONSUMED,
    REQUEST_EXPIRED_BEFORE_EXECUTION,
    APPROVAL_STALE,
    ACTION_NOT_REGISTERED,
    POST_WRITE_READ_BACK_MISMATCH,
    POLICY_SERVICE_UNAVAILABLE,
    SETTER_REJECTED,
    SECURITY_EXCEPTION,
    VALIDATION_CHANGED,
    EXPECTED_ADMIN_NOT_ACTIVE,
    EXPECTED_ADMIN_COMPONENT_MISMATCH,
    MANAGEMENT_STATE_INCONSISTENT,
    DEVICE_OWNER_VALIDATION_FAILED,
    SIMULATED,
    SANITIZED_UNRECOGNIZED,
}

object AuditActionNames {
    const val DISABLE_SCREEN_CAPTURE = "disable_screen_capture"
    const val ENABLE_SCREEN_CAPTURE = "enable_screen_capture"
    const val DISABLE_CAMERA = "disable_camera"
    const val ENABLE_CAMERA = "enable_camera"
    const val DISABLE_STATUS_BAR = "disable_status_bar"
    const val ENABLE_STATUS_BAR = "enable_status_bar"
    const val MOCK_WIPE = "mock_wipe"
    const val UNRECOGNIZED = "unrecognized"

    private val known = setOf(
        DISABLE_SCREEN_CAPTURE,
        ENABLE_SCREEN_CAPTURE,
        DISABLE_CAMERA,
        ENABLE_CAMERA,
        DISABLE_STATUS_BAR,
        ENABLE_STATUS_BAR,
        MOCK_WIPE,
        UNRECOGNIZED,
    )

    fun canonicalize(rawCommand: String?): String {
        val command = rawCommand?.trim().orEmpty()
        return if (command in known && command != UNRECOGNIZED) {
            command
        } else {
            UNRECOGNIZED
        }
    }

    fun isKnown(actionName: String): Boolean = actionName in known
}

/**
 * Public audit presentation constants.
 *
 * Database filename and table identity are implementation artifacts and are
 * not part of the app/UI API surface.
 */
object AuditSchema {
    const val VERSION = 1
    const val RETENTION_BOUND = 8_000
    const val DASHBOARD_LIMIT = 20
}
