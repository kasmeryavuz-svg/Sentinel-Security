package com.example.devicemanagement.destructive

import java.util.Collections
import java.util.LinkedHashSet
import java.util.UUID

/**
 * Authoritative correlation identity. Created only inside trusted code.
 * A caller [DestructiveSimulationRequest.callerRequestId] never becomes
 * this value and is never treated as authority.
 */
internal class DestructiveCorrelationId private constructor(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "correlation identity must not be blank" }
    }

    override fun equals(other: Any?): Boolean =
        other is DestructiveCorrelationId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        fun generate(generator: () -> String = { UUID.randomUUID().toString() }): DestructiveCorrelationId {
            val raw = generator().trim()
            require(raw.isNotEmpty()) { "correlation generator must not return a blank identity" }
            return DestructiveCorrelationId(raw)
        }
    }
}

internal enum class DestructiveActionType {
    FACTORY_RESET_SIMULATION,
}

enum class DestructiveManagementValidation {
    VERIFIED_DEVICE_OWNER,
    NOT_DEVICE_OWNER,
    CONFIGURATION_ERROR,
    UNAVAILABLE,
}

internal enum class DestructiveExecutionState {
    IDLE,
    REQUESTED,
    ASSESSED,
    ARMED,
    AUTHORIZED,
    PRE_EXECUTION_AUDIT,
    EXECUTION_COMMITTED,
    FINAL_VALIDATION,
    REJECTED,
    EXPIRED,
    CANCELLED,
    FAILED_PRE_EXECUTION,
    SIMULATED,
}

/**
 * Immutable snapshot of collection-valued admin identity. The constructor
 * is private; every factory copies the caller set and wraps it as
 * unmodifiable. A caller-owned mutable collection can never mutate an
 * already-created snapshot.
 */
internal class FrozenAdminSet private constructor(
    private val snapshot: Set<String>,
) : Set<String> by snapshot {
    override fun equals(other: Any?): Boolean {
        return when (other) {
            is FrozenAdminSet -> snapshot == other.snapshot
            is Set<*> -> snapshot == other
            else -> false
        }
    }

    override fun hashCode(): Int = snapshot.hashCode()

    override fun toString(): String = snapshot.toString()

    companion object {
        fun snapshot(values: Set<String>): FrozenAdminSet {
            return FrozenAdminSet(Collections.unmodifiableSet(LinkedHashSet(values)))
        }
    }
}

/**
 * Exact target context available to Sentinel today. Does not invent hardware
 * identifiers. Signing-certificate and artifact-digest binding live in
 * [DestructiveArtifactIdentity], not on this target snapshot.
 *
 * Not a data class: the constructor is private and every factory / wither
 * re-snapshots collection fields. There is no copy path that can retain a
 * caller-mutable collection reference.
 */
internal class DestructiveTargetBinding private constructor(
    val actionType: DestructiveActionType,
    val runningPackage: String,
    val expectedAdminComponent: String,
    val registeredSentinelAdminSet: FrozenAdminSet,
    val deviceOwnerExpected: Boolean,
    val profileOwnerMustBeFalse: Boolean,
    val activeAdminExpected: Boolean,
    val activeAdminComponentSet: FrozenAdminSet,
    val managementValidationState: DestructiveManagementValidation,
    val scope: DestructiveScope,
    val correlationId: DestructiveCorrelationId,
) {
    fun withScope(scope: DestructiveScope): DestructiveTargetBinding {
        return snapshot(
            actionType = actionType,
            runningPackage = runningPackage,
            expectedAdminComponent = expectedAdminComponent,
            registeredSentinelAdminSet = registeredSentinelAdminSet,
            deviceOwnerExpected = deviceOwnerExpected,
            profileOwnerMustBeFalse = profileOwnerMustBeFalse,
            activeAdminExpected = activeAdminExpected,
            activeAdminComponentSet = activeAdminComponentSet,
            managementValidationState = managementValidationState,
            scope = scope,
            correlationId = correlationId,
        )
    }

    fun withRunningPackage(runningPackage: String): DestructiveTargetBinding {
        return snapshot(
            actionType = actionType,
            runningPackage = runningPackage,
            expectedAdminComponent = expectedAdminComponent,
            registeredSentinelAdminSet = registeredSentinelAdminSet,
            deviceOwnerExpected = deviceOwnerExpected,
            profileOwnerMustBeFalse = profileOwnerMustBeFalse,
            activeAdminExpected = activeAdminExpected,
            activeAdminComponentSet = activeAdminComponentSet,
            managementValidationState = managementValidationState,
            scope = scope,
            correlationId = correlationId,
        )
    }

    fun withRegisteredSentinelAdminSet(values: Set<String>): DestructiveTargetBinding {
        return snapshot(
            actionType = actionType,
            runningPackage = runningPackage,
            expectedAdminComponent = expectedAdminComponent,
            registeredSentinelAdminSet = values,
            deviceOwnerExpected = deviceOwnerExpected,
            profileOwnerMustBeFalse = profileOwnerMustBeFalse,
            activeAdminExpected = activeAdminExpected,
            activeAdminComponentSet = activeAdminComponentSet,
            managementValidationState = managementValidationState,
            scope = scope,
            correlationId = correlationId,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DestructiveTargetBinding) return false
        return actionType == other.actionType &&
            runningPackage == other.runningPackage &&
            expectedAdminComponent == other.expectedAdminComponent &&
            registeredSentinelAdminSet == other.registeredSentinelAdminSet &&
            deviceOwnerExpected == other.deviceOwnerExpected &&
            profileOwnerMustBeFalse == other.profileOwnerMustBeFalse &&
            activeAdminExpected == other.activeAdminExpected &&
            activeAdminComponentSet == other.activeAdminComponentSet &&
            managementValidationState == other.managementValidationState &&
            scope == other.scope &&
            correlationId == other.correlationId
    }

    override fun hashCode(): Int {
        var result = actionType.hashCode()
        result = 31 * result + runningPackage.hashCode()
        result = 31 * result + expectedAdminComponent.hashCode()
        result = 31 * result + registeredSentinelAdminSet.hashCode()
        result = 31 * result + deviceOwnerExpected.hashCode()
        result = 31 * result + profileOwnerMustBeFalse.hashCode()
        result = 31 * result + activeAdminExpected.hashCode()
        result = 31 * result + activeAdminComponentSet.hashCode()
        result = 31 * result + managementValidationState.hashCode()
        result = 31 * result + scope.hashCode()
        result = 31 * result + correlationId.hashCode()
        return result
    }

    override fun toString(): String {
        return "DestructiveTargetBinding(" +
            "actionType=$actionType, " +
            "runningPackage=$runningPackage, " +
            "expectedAdminComponent=$expectedAdminComponent, " +
            "registeredSentinelAdminSet=$registeredSentinelAdminSet, " +
            "deviceOwnerExpected=$deviceOwnerExpected, " +
            "profileOwnerMustBeFalse=$profileOwnerMustBeFalse, " +
            "activeAdminExpected=$activeAdminExpected, " +
            "activeAdminComponentSet=$activeAdminComponentSet, " +
            "managementValidationState=$managementValidationState, " +
            "scope=$scope, " +
            "correlationId=$correlationId)"
    }

    companion object {
        fun snapshot(
            actionType: DestructiveActionType,
            runningPackage: String,
            expectedAdminComponent: String,
            registeredSentinelAdminSet: Set<String>,
            deviceOwnerExpected: Boolean,
            profileOwnerMustBeFalse: Boolean,
            activeAdminExpected: Boolean,
            activeAdminComponentSet: Set<String>,
            managementValidationState: DestructiveManagementValidation,
            scope: DestructiveScope,
            correlationId: DestructiveCorrelationId,
        ): DestructiveTargetBinding {
            return DestructiveTargetBinding(
                actionType = actionType,
                runningPackage = runningPackage,
                expectedAdminComponent = expectedAdminComponent,
                registeredSentinelAdminSet = FrozenAdminSet.snapshot(registeredSentinelAdminSet),
                deviceOwnerExpected = deviceOwnerExpected,
                profileOwnerMustBeFalse = profileOwnerMustBeFalse,
                activeAdminExpected = activeAdminExpected,
                activeAdminComponentSet = FrozenAdminSet.snapshot(activeAdminComponentSet),
                managementValidationState = managementValidationState,
                scope = scope,
                correlationId = correlationId,
            )
        }
    }
}

data class DestructiveLiveFacts(
    val runningPackage: String,
    val expectedAdminComponent: String,
    val registeredSentinelAdminSet: Set<String>,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val isExpectedAdminActive: Boolean,
    val activeAdminComponentSet: Set<String>,
    val managementValidationState: DestructiveManagementValidation,
    val policyServiceAvailable: Boolean,
)

fun interface DestructiveLiveFactsSource {
    fun currentFacts(): DestructiveLiveFacts
}

internal object DestructiveTargetRules {
    fun denyReason(
        binding: DestructiveTargetBinding,
        live: DestructiveLiveFacts,
    ): String? {
        if (binding.actionType != DestructiveActionType.FACTORY_RESET_SIMULATION) {
            return "unsupported_action_type"
        }
        if (binding.scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return "unsupported_scope"
        }
        if (binding.runningPackage.isBlank() || live.runningPackage.isBlank()) {
            return "blank_package"
        }
        if (binding.expectedAdminComponent.isBlank() || live.expectedAdminComponent.isBlank()) {
            return "blank_admin_component"
        }
        if (binding.runningPackage != live.runningPackage) {
            return "package_mismatch"
        }
        if (binding.expectedAdminComponent != live.expectedAdminComponent) {
            return "admin_component_mismatch"
        }
        if (binding.registeredSentinelAdminSet != live.registeredSentinelAdminSet) {
            return "registered_admin_set_mismatch"
        }
        if (binding.registeredSentinelAdminSet != setOf(binding.expectedAdminComponent)) {
            return "registered_admin_set_inconsistent"
        }
        if (live.registeredSentinelAdminSet != setOf(live.expectedAdminComponent)) {
            return "live_admin_set_inconsistent"
        }
        if (!binding.deviceOwnerExpected || !live.isDeviceOwner) {
            return "device_owner_not_verified"
        }
        if (!binding.profileOwnerMustBeFalse || live.isProfileOwner) {
            return "profile_owner_present"
        }
        if (!binding.activeAdminExpected || !live.isExpectedAdminActive) {
            return "admin_not_active"
        }
        if (binding.activeAdminComponentSet != live.activeAdminComponentSet) {
            return "active_admin_set_mismatch"
        }
        if (binding.expectedAdminComponent !in binding.activeAdminComponentSet) {
            return "expected_admin_not_in_active_set"
        }
        if (live.expectedAdminComponent !in live.activeAdminComponentSet) {
            return "live_expected_admin_not_in_active_set"
        }
        if (binding.managementValidationState != DestructiveManagementValidation.VERIFIED_DEVICE_OWNER) {
            return "device_owner_not_verified"
        }
        if (live.managementValidationState != DestructiveManagementValidation.VERIFIED_DEVICE_OWNER) {
            return "device_owner_not_verified"
        }
        if (!live.policyServiceAvailable) {
            return "policy_service_unavailable"
        }
        return null
    }

    fun bindingFromAssessedFacts(
        facts: DestructiveLiveFacts,
        scope: DestructiveScope,
        correlationId: DestructiveCorrelationId,
    ): DestructiveTargetBinding {
        return DestructiveTargetBinding.snapshot(
            actionType = DestructiveActionType.FACTORY_RESET_SIMULATION,
            runningPackage = facts.runningPackage,
            expectedAdminComponent = facts.expectedAdminComponent,
            registeredSentinelAdminSet = facts.registeredSentinelAdminSet,
            deviceOwnerExpected = true,
            profileOwnerMustBeFalse = true,
            activeAdminExpected = true,
            activeAdminComponentSet = facts.activeAdminComponentSet,
            managementValidationState = facts.managementValidationState,
            scope = scope,
            correlationId = correlationId,
        )
    }
}

internal object DestructiveSimulationActionNames {
    const val FACTORY_RESET_SIMULATION = "destructive_factory_reset_simulation"
}

internal object DestructiveRuntimeActionNames {
    const val FUTURE_REAL_CHAIN_FACTORY_RESET = "future_real_chain_factory_reset"
}

internal fun closedSimulationStatus(
    outcome: DestructiveSimulationOutcome,
    reason: String,
    correlationId: String?,
    state: DestructiveExecutionState,
): DestructiveSimulationStatus {
    return DestructiveSimulationStatus(
        outcome = outcome,
        reason = reason,
        correlationId = correlationId,
        state = state.name,
    )
}
