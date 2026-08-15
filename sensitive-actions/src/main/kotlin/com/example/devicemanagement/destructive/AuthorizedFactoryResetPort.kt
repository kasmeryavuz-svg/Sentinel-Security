package com.example.devicemanagement.destructive

/**
 * Narrow production port for an already-authorized whole-device factory
 * reset. This type does not authorize, arm, or consume a real-chain
 * bundle. Production bytecode allows [performAuthorizedFactoryReset]
 * only from [AndroidFutureDestructiveExecutor.onAuthorizedHandoff].
 *
 * The Android policy-manager token lives only in the
 * device-management implementation of this port.
 */
interface AuthorizedFactoryResetPort {
    fun performAuthorizedFactoryReset(): AuthorizedFactoryResetResult
}

sealed interface AuthorizedFactoryResetResult {
    /**
     * The platform call returned. This is not proof that reset
     * completed; the process may die during reset.
     */
    data object Initiated : AuthorizedFactoryResetResult

    data class Refused(val reason: String) : AuthorizedFactoryResetResult
}

internal object UnavailableAuthorizedFactoryResetPort : AuthorizedFactoryResetPort {
    override fun performAuthorizedFactoryReset(): AuthorizedFactoryResetResult {
        return AuthorizedFactoryResetResult.Refused("policy_service_unavailable")
    }
}
