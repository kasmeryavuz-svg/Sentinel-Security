package com.example.devicemanagement.destructive

/**
 * Opaque in-chain hand-off issued only by [DestructiveFinalExecutionGate]
 * after live final validation. Consumable once, only by the paired sink.
 * There is no raw binding-only minting API.
 */
internal class FinalExecutionPermit private constructor() {
    companion object {
        fun create(): FinalExecutionPermit = FinalExecutionPermit()
    }
}

internal fun interface FinalExecutionPermitConsumer {
    fun consume(
        permit: FinalExecutionPermit,
        expectedBinding: DestructiveTargetBinding,
    ): PermitConsumption
}

internal sealed interface PermitConsumption {
    data class Accepted(val binding: DestructiveTargetBinding) : PermitConsumption

    data class Rejected(val reason: String) : PermitConsumption
}
