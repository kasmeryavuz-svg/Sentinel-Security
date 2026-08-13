package com.example.devicemanagement.decision

internal enum class DecisionReason {
    APPROVED_BY_POLICY,
    INVALID_TRIGGER,
    MISSING_STATE,
    SERVICE_UNAVAILABLE,
    SENSITIVE_ACTIONS_DISABLED,
    EVALUATION_ERROR,
}
