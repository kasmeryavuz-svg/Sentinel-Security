package com.example.devicemanagement.persistence

internal data class ManagementState(
    val serviceAvailable: Boolean,
    val sensitiveActionsEnabled: Boolean,
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
