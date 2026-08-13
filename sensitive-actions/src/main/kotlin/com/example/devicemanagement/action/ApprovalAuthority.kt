package com.example.devicemanagement.action

import java.util.IdentityHashMap

/**
 * Issues single-use, identity-bound approvals and retains the authoritative
 * request. The executor never trusts request data supplied with a decision.
 */
internal class ApprovalAuthority {
    private val issuedApprovals = IdentityHashMap<Approval, ApprovalRecord>()

    @Synchronized
    fun issue(
        request: ActionRequest,
        issuedAtMonotonicMillis: Long,
    ): Approval {
        return Approval.create().also { approval ->
            issuedApprovals[approval] = ApprovalRecord(
                request = request,
                issuedAtMonotonicMillis = issuedAtMonotonicMillis,
            )
        }
    }

    @Synchronized
    fun consume(
        approval: Approval,
        nowEpochMillis: Long,
        nowMonotonicMillis: Long,
    ): ApprovalConsumption {
        val record = issuedApprovals.remove(approval)
            ?: return ApprovalConsumption.Rejected("approval_not_issued_or_already_consumed")
        if (record.request.expiresAtEpochMillis <= nowEpochMillis) {
            return ApprovalConsumption.Rejected(
                reason = "request_expired_before_execution",
                correlationId = record.request.correlationId,
            )
        }
        val approvalAge = nowMonotonicMillis - record.issuedAtMonotonicMillis
        if (approvalAge < 0L || approvalAge > MAX_APPROVAL_AGE_MILLIS) {
            return ApprovalConsumption.Rejected(
                reason = "approval_stale",
                correlationId = record.request.correlationId,
            )
        }
        return ApprovalConsumption.Accepted(record.request)
    }

    private data class ApprovalRecord(
        val request: ActionRequest,
        val issuedAtMonotonicMillis: Long,
    )

    private companion object {
        const val MAX_APPROVAL_AGE_MILLIS = 5_000L
    }
}

internal sealed interface ApprovalConsumption {
    data class Accepted(val request: ActionRequest) : ApprovalConsumption

    data class Rejected(
        val reason: String,
        val correlationId: String? = null,
    ) : ApprovalConsumption
}

internal class Approval private constructor() {
    companion object {
        internal fun create(): Approval = Approval()
    }
}
