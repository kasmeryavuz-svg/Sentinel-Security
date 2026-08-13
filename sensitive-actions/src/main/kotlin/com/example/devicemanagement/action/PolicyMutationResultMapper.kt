package com.example.devicemanagement.action

import com.example.devicemanagement.integration.PolicyMutationResult

internal fun PolicyMutationResult.toActionResult(
    operation: SensitiveActionOperation,
    correlationId: String,
): ActionResult {
    return when (this) {
        is PolicyMutationResult.Applied -> ActionResult.Applied(
            operation = operation,
            requestedDisabled = requestedDisabled,
            observedDisabled = observedDisabled,
            correlationId = correlationId,
        )
        is PolicyMutationResult.Denied -> ActionResult.Rejected(
            reason = reason,
            correlationId = correlationId,
        )
        is PolicyMutationResult.Failed -> ActionResult.Failed(
            reason = reason,
            correlationId = correlationId,
        )
    }
}
