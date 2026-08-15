package com.example.devicemanagement.destructive

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
