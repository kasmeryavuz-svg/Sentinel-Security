package com.example.devicemanagement.audit

import com.example.devicemanagement.action.ActionResult

internal object AuditReasonCodes {
    fun fromActionResult(result: ActionResult): AuditReasonCode? {
        return when (result) {
            is ActionResult.Applied -> null
            is ActionResult.Rejected -> sanitize(result.reason)
            is ActionResult.Failed -> sanitize(result.reason)
            is ActionResult.Simulated -> AuditReasonCode.SIMULATED
        }
    }

    fun sanitize(rawReason: String?): AuditReasonCode {
        val reason = rawReason?.trim().orEmpty()
        if (reason.isEmpty()) {
            return AuditReasonCode.SANITIZED_UNRECOGNIZED
        }
        exact[reason]?.let { return it }
        when {
            reason.startsWith("decision_denied:") -> {
                val suffix = reason.removePrefix("decision_denied:")
                return decisionReasons[suffix] ?: AuditReasonCode.SANITIZED_UNRECOGNIZED
            }
            reason.startsWith("device_owner_not_verified:") ->
                return AuditReasonCode.DEVICE_OWNER_NOT_VERIFIED
        }
        return AuditReasonCode.SANITIZED_UNRECOGNIZED
    }

    fun toPhase(result: ActionResult): AuditEventPhase {
        return when (result) {
            is ActionResult.Applied -> AuditEventPhase.APPLIED
            is ActionResult.Rejected -> AuditEventPhase.REJECTED
            is ActionResult.Failed -> AuditEventPhase.FAILED
            is ActionResult.Simulated -> AuditEventPhase.SIMULATED
        }
    }

    private val exact = mapOf(
        "audit_persistence_unavailable" to AuditReasonCode.AUDIT_PERSISTENCE_UNAVAILABLE,
        "decision_not_approved" to AuditReasonCode.DECISION_NOT_APPROVED,
        "approval_not_issued_or_already_consumed" to
            AuditReasonCode.APPROVAL_NOT_ISSUED_OR_ALREADY_CONSUMED,
        "request_expired_before_execution" to AuditReasonCode.REQUEST_EXPIRED_BEFORE_EXECUTION,
        "approval_stale" to AuditReasonCode.APPROVAL_STALE,
        "action_not_registered" to AuditReasonCode.ACTION_NOT_REGISTERED,
        "post_write_read_back_mismatch" to AuditReasonCode.POST_WRITE_READ_BACK_MISMATCH,
        "policy_service_unavailable" to AuditReasonCode.POLICY_SERVICE_UNAVAILABLE,
        "setter_rejected" to AuditReasonCode.SETTER_REJECTED,
        "security_exception" to AuditReasonCode.SECURITY_EXCEPTION,
        "validation_changed" to AuditReasonCode.VALIDATION_CHANGED,
        "expected_admin_not_active" to AuditReasonCode.EXPECTED_ADMIN_NOT_ACTIVE,
        "expected_admin_component_mismatch" to AuditReasonCode.EXPECTED_ADMIN_COMPONENT_MISMATCH,
        "management_state_inconsistent" to AuditReasonCode.MANAGEMENT_STATE_INCONSISTENT,
        "device_owner_validation_failed" to AuditReasonCode.DEVICE_OWNER_VALIDATION_FAILED,
        "admin_receiver_not_registered" to AuditReasonCode.ADMIN_RECEIVER_NOT_REGISTERED,
    )

    private val decisionReasons = mapOf(
        "INVALID_TRIGGER" to AuditReasonCode.INVALID_TRIGGER,
        "MISSING_STATE" to AuditReasonCode.MISSING_STATE,
        "SERVICE_UNAVAILABLE" to AuditReasonCode.SERVICE_UNAVAILABLE,
        "INCONSISTENT_MANAGEMENT_STATE" to AuditReasonCode.INCONSISTENT_MANAGEMENT_STATE,
        "PROFILE_OWNER_NOT_ALLOWED" to AuditReasonCode.PROFILE_OWNER_NOT_ALLOWED,
        "ADMIN_RECEIVER_NOT_REGISTERED" to AuditReasonCode.ADMIN_RECEIVER_NOT_REGISTERED,
        "ADMIN_NOT_ACTIVE" to AuditReasonCode.ADMIN_NOT_ACTIVE,
        "DEVICE_OWNER_NOT_VERIFIED" to AuditReasonCode.DEVICE_OWNER_NOT_VERIFIED,
        "SENSITIVE_ACTIONS_DISABLED" to AuditReasonCode.SENSITIVE_ACTIONS_DISABLED,
        "EVALUATION_ERROR" to AuditReasonCode.EVALUATION_ERROR,
    )
}
