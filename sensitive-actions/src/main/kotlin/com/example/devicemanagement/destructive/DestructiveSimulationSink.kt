package com.example.devicemanagement.destructive

/**
 * Checkpoint 17A non-destructive simulation boundary. Records that a
 * destructive action would execute. It has no Android policy-service
 * dependency and is not the fail-safe mock action.
 *
 * Structurally paired with the concrete [DestructiveFinalExecutionGate].
 * There is no injectable permit-consumer path.
 */
internal class Checkpoint17ASimulationSink(
    private val permitGate: DestructiveFinalExecutionGate,
) {
    private val invocations = mutableListOf<String>()

    fun invoke(
        permit: FinalExecutionPermit,
        expectedBinding: DestructiveTargetBinding,
    ): SimulationSinkResult {
        return when (val consumption = permitGate.consume(permit, expectedBinding)) {
            is PermitConsumption.Rejected -> SimulationSinkResult.Denied(consumption.reason)
            is PermitConsumption.Accepted -> {
                invocations += MESSAGE
                SimulationSinkResult.Invoked(MESSAGE)
            }
        }
    }

    fun invocationCount(): Int = invocations.size

    fun messages(): List<String> = invocations.toList()

    companion object {
        const val MESSAGE = "DESTRUCTIVE ACTION WOULD EXECUTE"
    }
}

internal sealed interface SimulationSinkResult {
    data class Invoked(val message: String) : SimulationSinkResult

    data class Denied(val reason: String) : SimulationSinkResult
}
