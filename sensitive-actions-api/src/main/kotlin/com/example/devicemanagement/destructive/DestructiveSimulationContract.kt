package com.example.devicemanagement.destructive

/**
 * Public Checkpoint 17A request / status / evidence contract.
 *
 * These types cannot arm, authorize, issue a capability, create a permit,
 * mutate cooldown state, or invoke any Android policy service. Application
 * and UI code may inspect them for non-destructive simulation testing only.
 * They are not a production mutation API.
 */
data class DestructiveSimulationRequest(
    val callerRequestId: String? = null,
    val requestedScope: DestructiveScope? = null,
)

enum class DestructiveScope {
    DEVICE_FACTORY_RESET,
    USER_SCOPED_WIPE,
}

enum class DestructiveSimulationOutcome {
    REJECTED,
    EXPIRED,
    CANCELLED,
    FAILED_PRE_EXECUTION,
    SIMULATED_WOULD_EXECUTE,
}

data class DestructiveSimulationStatus(
    val outcome: DestructiveSimulationOutcome,
    val reason: String,
    val correlationId: String?,
    val state: String,
)

enum class DestructiveEvidencePhase {
    REQUESTED,
    PRE_EXECUTION_COMMITTED,
    REJECTED,
    EXPIRED,
    CANCELLED,
    FAILED_PRE_EXECUTION,
    SIMULATED,
}

data class DestructiveSimulationEvidence(
    val eventId: String,
    val correlationId: String,
    val actionName: String,
    val phase: DestructiveEvidencePhase,
    val presentationWallClockMillis: Long,
    val reasonCode: String?,
    val boundPackage: String?,
    val boundAdminComponent: String?,
    val boundScope: DestructiveScope?,
    val callerRequestId: String?,
)
