package com.example.devicemanagement.destructive

/**
 * Opaque in-chain hand-off issued only by [DestructiveFinalExecutionGate]
 * after live final validation. Consumable once, only by the paired sink
 * through that same concrete gate. There is no raw binding-only minting
 * API and no injectable permit-consumer interface.
 */
internal class FinalExecutionPermit private constructor() {
    companion object {
        fun create(): FinalExecutionPermit = FinalExecutionPermit()
    }
}

internal sealed interface PermitConsumption {
    data class Accepted(val binding: DestructiveTargetBinding) : PermitConsumption

    data class Rejected(val reason: String) : PermitConsumption
}
