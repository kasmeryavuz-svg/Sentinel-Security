package com.example.devicemanagement.destructive

import java.util.IdentityHashMap

/**
 * Decision-domain defaults for future factory-reset options.
 *
 * These values are not Android policy-manager invocations and are not
 * wired to any executor. Unknown or unapproved options fail closed.
 *
 * Intended scope: [DestructiveScope.DEVICE_FACTORY_RESET] only.
 * [DestructiveScope.USER_SCOPED_WIPE] remains denied.
 */
internal enum class DestructiveWipeFlagOption(val policyName: String) {
    SILENT("WIPE_SILENTLY"),
    RESET_PROTECTION_DATA("WIPE_RESET_PROTECTION_DATA"),
    EUICC("WIPE_EUICC"),
}

internal enum class DestructiveWipeFlagDecision {
    FORBIDDEN,
    UNRESOLVED_DENY,
    DENY_UNKNOWN,
}

internal object DestructiveWipeOptionPolicy {
    fun decision(option: DestructiveWipeFlagOption): DestructiveWipeFlagDecision {
        return when (option) {
            DestructiveWipeFlagOption.SILENT -> DestructiveWipeFlagDecision.FORBIDDEN
            DestructiveWipeFlagOption.RESET_PROTECTION_DATA -> DestructiveWipeFlagDecision.UNRESOLVED_DENY
            DestructiveWipeFlagOption.EUICC -> DestructiveWipeFlagDecision.UNRESOLVED_DENY
        }
    }

    fun decisionForName(name: String): DestructiveWipeFlagDecision {
        val option = DestructiveWipeFlagOption.entries.firstOrNull { it.policyName == name }
            ?: return DestructiveWipeFlagDecision.DENY_UNKNOWN
        return decision(option)
    }

    fun allowsScope(scope: DestructiveScope): Boolean {
        return scope == DestructiveScope.DEVICE_FACTORY_RESET
    }

    fun isPermitted(option: DestructiveWipeFlagOption): Boolean {
        return false
    }

    fun isPermittedName(name: String): Boolean {
        return false
    }
}

/**
 * Process-local proof that a future real-chain request was checked against
 * the default-deny wipe-option policy. Not an Android flag integer. Not
 * authorization. Caller-constructed instances are not registered.
 */
internal class DestructiveWipeOptionPolicyProof private constructor() {
    companion object {
        fun create(): DestructiveWipeOptionPolicyProof = DestructiveWipeOptionPolicyProof()
    }
}

internal sealed interface WipeOptionPolicyVerifyResult {
    data class Verified(val proof: DestructiveWipeOptionPolicyProof) : WipeOptionPolicyVerifyResult

    data class Failed(val reason: String) : WipeOptionPolicyVerifyResult
}

internal sealed interface WipeOptionPolicyCheck {
    data object Accepted : WipeOptionPolicyCheck

    data class Rejected(val reason: String) : WipeOptionPolicyCheck
}

/**
 * Issues [DestructiveWipeOptionPolicyProof] only for
 * [DestructiveScope.DEVICE_FACTORY_RESET] with an empty extra-option set.
 * Unknown, silent, reset-protection, eUICC, and user-scoped requests fail
 * closed. No Android policy-manager constants are produced.
 */
internal class DestructiveWipeOptionPolicyAuthority {
    private val issued = IdentityHashMap<DestructiveWipeOptionPolicyProof, PolicyRecord>()

    @Synchronized
    fun verifyDefaultDeny(
        scope: DestructiveScope,
        requestedOptionNames: Set<String>,
    ): WipeOptionPolicyVerifyResult {
        if (scope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return WipeOptionPolicyVerifyResult.Failed("wipe_option_scope_denied")
        }
        if (!DestructiveWipeOptionPolicy.allowsScope(scope)) {
            return WipeOptionPolicyVerifyResult.Failed("wipe_option_scope_denied")
        }
        if (DestructiveWipeFlagOption.entries.any { DestructiveWipeOptionPolicy.isPermitted(it) }) {
            return WipeOptionPolicyVerifyResult.Failed("wipe_option_policy_not_default_deny")
        }
        if (requestedOptionNames.isNotEmpty()) {
            val first = requestedOptionNames.first()
            val reason = when (DestructiveWipeOptionPolicy.decisionForName(first)) {
                DestructiveWipeFlagDecision.FORBIDDEN -> "wipe_option_forbidden"
                DestructiveWipeFlagDecision.UNRESOLVED_DENY -> "wipe_option_unresolved_deny"
                DestructiveWipeFlagDecision.DENY_UNKNOWN -> "wipe_option_unknown_denied"
            }
            return WipeOptionPolicyVerifyResult.Failed(reason)
        }
        val proof = DestructiveWipeOptionPolicyProof.create()
        issued[proof] = PolicyRecord(scope = scope)
        return WipeOptionPolicyVerifyResult.Verified(proof)
    }

    @Synchronized
    fun consume(
        proof: DestructiveWipeOptionPolicyProof,
        expectedScope: DestructiveScope,
    ): WipeOptionPolicyCheck {
        val record = issued.remove(proof)
            ?: return WipeOptionPolicyCheck.Rejected("wipe_option_proof_not_issued_or_already_consumed")
        if (record.scope != expectedScope) {
            return WipeOptionPolicyCheck.Rejected("wipe_option_scope_mismatch")
        }
        if (expectedScope != DestructiveScope.DEVICE_FACTORY_RESET) {
            return WipeOptionPolicyCheck.Rejected("wipe_option_scope_denied")
        }
        return WipeOptionPolicyCheck.Accepted
    }

    private data class PolicyRecord(
        val scope: DestructiveScope,
    )
}
