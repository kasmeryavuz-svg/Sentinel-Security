package com.example.devicemanagement.destructive

/**
 * Production [FutureDestructiveExecutorContract] implementor.
 *
 * This class does not mention Android policy-manager APIs. After a
 * registered bundle is consumed, [onAuthorizedHandoff] calls the
 * injected [AuthorizedFactoryResetPort]. Production bytecode allows
 * that port call only from this method.
 */
internal class AndroidFutureDestructiveExecutor(
    private val factoryReset: AuthorizedFactoryResetPort,
) : FutureDestructiveExecutorContract() {
    override fun onAuthorizedHandoff(): FutureDestructiveHandoffAcknowledgement {
        return when (val result = factoryReset.performAuthorizedFactoryReset()) {
            AuthorizedFactoryResetResult.Initiated -> {
                FutureDestructiveHandoffAcknowledgement.Initiated
            }
            is AuthorizedFactoryResetResult.Refused -> {
                FutureDestructiveHandoffAcknowledgement.Refused(result.reason)
            }
        }
    }
}
