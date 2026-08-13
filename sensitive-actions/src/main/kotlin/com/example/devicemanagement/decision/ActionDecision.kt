package com.example.devicemanagement.decision

import com.example.devicemanagement.action.Approval

internal sealed interface ActionDecision {
    data class Approved(
        val approval: Approval,
        val reason: DecisionReason = DecisionReason.APPROVED_BY_POLICY,
    ) : ActionDecision

    data class Denied(
        val reason: DecisionReason,
        val detail: String? = null,
    ) : ActionDecision
}
