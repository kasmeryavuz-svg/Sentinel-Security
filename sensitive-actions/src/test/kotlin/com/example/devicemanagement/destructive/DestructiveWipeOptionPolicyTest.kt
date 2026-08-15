package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveWipeOptionPolicyTest {
    @Test
    fun `known options have fail-closed defaults`() {
        assertEquals(
            DestructiveWipeFlagDecision.FORBIDDEN,
            DestructiveWipeOptionPolicy.decision(DestructiveWipeFlagOption.SILENT),
        )
        assertEquals(
            DestructiveWipeFlagDecision.UNRESOLVED_DENY,
            DestructiveWipeOptionPolicy.decision(DestructiveWipeFlagOption.RESET_PROTECTION_DATA),
        )
        assertEquals(
            DestructiveWipeFlagDecision.UNRESOLVED_DENY,
            DestructiveWipeOptionPolicy.decision(DestructiveWipeFlagOption.EUICC),
        )
        DestructiveWipeFlagOption.entries.forEach { option ->
            assertFalse(option.policyName, DestructiveWipeOptionPolicy.isPermitted(option))
        }
    }

    @Test
    fun `unknown and unapproved names fail closed`() {
        listOf(
            "",
            " ",
            "UNKNOWN",
            "WIPE_EXTERNAL_STORAGE",
            "WIPE_ALL",
            "ALLOW",
            "true",
            DestructiveWipeFlagOption.SILENT.name,
        ).forEach { name ->
            assertEquals(
                name,
                DestructiveWipeFlagDecision.DENY_UNKNOWN,
                DestructiveWipeOptionPolicy.decisionForName(name),
            )
            assertFalse(name, DestructiveWipeOptionPolicy.isPermittedName(name))
        }
        assertEquals(
            DestructiveWipeFlagDecision.FORBIDDEN,
            DestructiveWipeOptionPolicy.decisionForName("WIPE_SILENTLY"),
        )
        assertEquals(
            DestructiveWipeFlagDecision.UNRESOLVED_DENY,
            DestructiveWipeOptionPolicy.decisionForName("WIPE_RESET_PROTECTION_DATA"),
        )
        assertEquals(
            DestructiveWipeFlagDecision.UNRESOLVED_DENY,
            DestructiveWipeOptionPolicy.decisionForName("WIPE_EUICC"),
        )
        assertFalse(DestructiveWipeOptionPolicy.isPermittedName("WIPE_SILENTLY"))
        assertFalse(DestructiveWipeOptionPolicy.isPermittedName("WIPE_RESET_PROTECTION_DATA"))
        assertFalse(DestructiveWipeOptionPolicy.isPermittedName("WIPE_EUICC"))
    }

    @Test
    fun `only device factory reset scope is intended`() {
        assertTrue(DestructiveWipeOptionPolicy.allowsScope(DestructiveScope.DEVICE_FACTORY_RESET))
        assertFalse(DestructiveWipeOptionPolicy.allowsScope(DestructiveScope.USER_SCOPED_WIPE))
    }

    @Test
    fun `policy is exhaustive and is not an executor`() {
        val covered = DestructiveWipeFlagOption.entries.map {
            DestructiveWipeOptionPolicy.decision(it)
        }
        assertEquals(DestructiveWipeFlagOption.entries.size, covered.size)
        assertFalse(
            DestructiveWipeOptionPolicy::class.java.methods.any { method ->
                method.name == "execute" || method.name == "wipe" || method.name.contains("DevicePolicy")
            },
        )
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_POLICY_WRAPPER_PRESENT)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED)
    }
}
