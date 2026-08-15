package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DestructiveTargetBindingTest {
    @Test
    fun `verified facts produce an exact matching binding`() {
        val facts = verifiedFacts()
        val binding = verifiedBinding(facts)
        assertNull(DestructiveTargetRules.denyReason(binding, facts))
        assertEquals(facts.runningPackage, binding.runningPackage)
        assertEquals(facts.expectedAdminComponent, binding.expectedAdminComponent)
        assertEquals(facts.registeredSentinelAdminSet, binding.registeredSentinelAdminSet)
        assertEquals(facts.activeAdminComponentSet, binding.activeAdminComponentSet)
        assertEquals(DestructiveManagementValidation.VERIFIED_DEVICE_OWNER, binding.managementValidationState)
        assertEquals(DestructiveScope.DEVICE_FACTORY_RESET, binding.scope)
    }

    @Test
    fun `every required field mismatch is denied`() {
        val facts = verifiedFacts()
        val binding = verifiedBinding(facts)
        assertEquals(
            "package_mismatch",
            DestructiveTargetRules.denyReason(
                binding,
                facts.copy(runningPackage = "com.example.other"),
            ),
        )
        assertEquals(
            "admin_component_mismatch",
            DestructiveTargetRules.denyReason(
                binding,
                facts.copy(
                    expectedAdminComponent = "other/other",
                    registeredSentinelAdminSet = setOf("other/other"),
                    activeAdminComponentSet = setOf("other/other"),
                ),
            ),
        )
        assertEquals(
            "device_owner_not_verified",
            DestructiveTargetRules.denyReason(binding, facts.copy(isDeviceOwner = false)),
        )
        assertEquals(
            "profile_owner_present",
            DestructiveTargetRules.denyReason(binding, facts.copy(isProfileOwner = true)),
        )
        assertEquals(
            "admin_not_active",
            DestructiveTargetRules.denyReason(binding, facts.copy(isExpectedAdminActive = false)),
        )
        assertEquals(
            "device_owner_not_verified",
            DestructiveTargetRules.denyReason(
                binding,
                facts.copy(managementValidationState = DestructiveManagementValidation.UNAVAILABLE),
            ),
        )
        assertEquals(
            "policy_service_unavailable",
            DestructiveTargetRules.denyReason(binding, facts.copy(policyServiceAvailable = false)),
        )
        assertEquals(
            "unsupported_scope",
            DestructiveTargetRules.denyReason(
                binding.copy(scope = DestructiveScope.USER_SCOPED_WIPE),
                facts,
            ),
        )
        assertEquals(
            "blank_package",
            DestructiveTargetRules.denyReason(binding.copy(runningPackage = " "), facts.copy(runningPackage = " ")),
        )
        assertEquals(
            "registered_admin_set_inconsistent",
            DestructiveTargetRules.denyReason(
                binding.copy(registeredSentinelAdminSet = setOf(binding.expectedAdminComponent, "dup/dup")),
                facts.copy(registeredSentinelAdminSet = setOf(binding.expectedAdminComponent, "dup/dup")),
            ),
        )
    }

    @Test
    fun `caller request id is never part of the binding`() {
        val binding = verifiedBinding()
        val fields = binding.toString()
        assertNull(DestructiveSimulationRequest(callerRequestId = "attacker-id").requestedScope)
        assertEquals(false, fields.contains("attacker-id"))
        assertNotNull(binding.correlationId.value)
    }
}
