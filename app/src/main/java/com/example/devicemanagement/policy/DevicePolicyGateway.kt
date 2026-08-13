package com.example.devicemanagement.policy

/**
 * Boundary for future non-destructive device-policy queries.
 *
 * Destructive operations are intentionally absent.
 */
interface DevicePolicyGateway {
    fun isServiceAvailable(): Boolean
}

class NoOpDevicePolicyGateway : DevicePolicyGateway {
    override fun isServiceAvailable(): Boolean = false
}
