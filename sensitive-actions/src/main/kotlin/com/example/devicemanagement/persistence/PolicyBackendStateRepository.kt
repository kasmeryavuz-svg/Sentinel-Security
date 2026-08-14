package com.example.devicemanagement.persistence

import com.example.devicemanagement.integration.SensitiveActionPolicyBackend

/**
 * Fresh Device Owner / admin / backend authorization on every load.
 *
 * This repository does not cache or persist authorization. Decision-time
 * state is always re-read from [SensitiveActionPolicyBackend].
 */
internal class PolicyBackendStateRepository(
    private val backend: SensitiveActionPolicyBackend,
) : StateRepository {
    override fun load(): ManagementState {
        val authorization = backend.currentAuthorization()
        return ManagementState(
            policyServiceAvailable = authorization.policyServiceAvailable,
            sensitiveActionsEnabled = authorization.sensitiveActionsEnabled,
            verifiedDeviceOwner = authorization.verifiedDeviceOwner,
            profileOwner = authorization.profileOwner,
            expectedAdminReceiverRegistered =
                authorization.expectedAdminReceiverRegistered,
            expectedAdminActive = authorization.expectedAdminActive,
            managementStateConsistent = authorization.managementStateConsistent,
        )
    }
}
