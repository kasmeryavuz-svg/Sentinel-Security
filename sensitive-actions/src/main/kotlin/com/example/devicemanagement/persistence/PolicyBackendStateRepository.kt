@file:OptIn(com.example.devicemanagement.integration.SensitiveActionCompositionApi::class)

package com.example.devicemanagement.persistence

import com.example.devicemanagement.integration.SensitiveActionPolicyBackend

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
