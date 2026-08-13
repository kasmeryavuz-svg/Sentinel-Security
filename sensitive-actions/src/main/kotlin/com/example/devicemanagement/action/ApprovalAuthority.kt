package com.example.devicemanagement.action

import java.util.IdentityHashMap

/**
 * Issues single-use, identity-bound approvals and retains the authoritative
 * request. The executor never trusts request data supplied with a decision.
 */
internal class ApprovalAuthority {
    private val issuedApprovals = IdentityHashMap<Approval, ActionRequest>()

    @Synchronized
    fun issue(request: ActionRequest): Approval {
        return Approval.create().also { approval ->
            issuedApprovals[approval] = request
        }
    }

    @Synchronized
    fun consume(approval: Approval): ActionRequest? = issuedApprovals.remove(approval)
}

internal class Approval private constructor() {
    companion object {
        internal fun create(): Approval = Approval()
    }
}
