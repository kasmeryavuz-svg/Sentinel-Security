package com.example.devicemanagement.persistence

internal data class ManagementState(
    val policyServiceAvailable: Boolean,
    val sensitiveActionsEnabled: Boolean,
    val verifiedDeviceOwner: Boolean,
    val profileOwner: Boolean,
    val expectedAdminReceiverRegistered: Boolean,
    val expectedAdminActive: Boolean,
    val managementStateConsistent: Boolean,
)

internal fun interface StateRepository {
    fun load(): ManagementState?
}

internal class InMemoryStateRepository(
    private var state: ManagementState?,
) : StateRepository {
    override fun load(): ManagementState? = state

    fun update(state: ManagementState?) {
        this.state = state
    }
}
