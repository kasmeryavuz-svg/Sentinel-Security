package com.example.devicemanagement.persistence

data class ManagementState(
    val serviceAvailable: Boolean,
    val sensitiveActionsEnabled: Boolean,
)

fun interface StateRepository {
    fun load(): ManagementState?
}

class InMemoryStateRepository(
    private var state: ManagementState?,
) : StateRepository {
    override fun load(): ManagementState? = state

    fun update(state: ManagementState?) {
        this.state = state
    }
}
