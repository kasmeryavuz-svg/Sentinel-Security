package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveWipeOptionPolicyProofTest {
    @Test
    fun `device factory reset with no extra options can be verified once`() {
        val authority = DestructiveWipeOptionPolicyAuthority()
        val verified = authority.verifyDefaultDeny(
            DestructiveScope.DEVICE_FACTORY_RESET,
            emptySet(),
        )
        assertTrue(verified is WipeOptionPolicyVerifyResult.Verified)
        val proof = (verified as WipeOptionPolicyVerifyResult.Verified).proof
        assertTrue(
            authority.consume(proof, DestructiveScope.DEVICE_FACTORY_RESET)
                is WipeOptionPolicyCheck.Accepted,
        )
        assertTrue(
            authority.consume(proof, DestructiveScope.DEVICE_FACTORY_RESET)
                is WipeOptionPolicyCheck.Rejected,
        )
    }

    @Test
    fun `user scoped wipe and extra options remain denied`() {
        val authority = DestructiveWipeOptionPolicyAuthority()
        assertEquals(
            "wipe_option_scope_denied",
            (
                authority.verifyDefaultDeny(DestructiveScope.USER_SCOPED_WIPE, emptySet())
                    as WipeOptionPolicyVerifyResult.Failed
                ).reason,
        )
        assertEquals(
            "wipe_option_forbidden",
            (
                authority.verifyDefaultDeny(
                    DestructiveScope.DEVICE_FACTORY_RESET,
                    setOf("WIPE_SILENTLY"),
                ) as WipeOptionPolicyVerifyResult.Failed
                ).reason,
        )
        assertEquals(
            "wipe_option_unresolved_deny",
            (
                authority.verifyDefaultDeny(
                    DestructiveScope.DEVICE_FACTORY_RESET,
                    setOf("WIPE_RESET_PROTECTION_DATA"),
                ) as WipeOptionPolicyVerifyResult.Failed
                ).reason,
        )
        assertEquals(
            "wipe_option_unresolved_deny",
            (
                authority.verifyDefaultDeny(
                    DestructiveScope.DEVICE_FACTORY_RESET,
                    setOf("WIPE_EUICC"),
                ) as WipeOptionPolicyVerifyResult.Failed
                ).reason,
        )
        assertEquals(
            "wipe_option_unknown_denied",
            (
                authority.verifyDefaultDeny(
                    DestructiveScope.DEVICE_FACTORY_RESET,
                    setOf("WIPE_EXTERNAL_STORAGE"),
                ) as WipeOptionPolicyVerifyResult.Failed
                ).reason,
        )
        assertTrue(
            authority.consume(
                DestructiveWipeOptionPolicyProof.create(),
                DestructiveScope.DEVICE_FACTORY_RESET,
            ) is WipeOptionPolicyCheck.Rejected,
        )
    }

    @Test
    fun `future executor boundary requires the policy proof type`() {
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        assertTrue(DestructiveWipeOptionPolicyProof::class.java in handoff.parameterTypes)
        assertFalse(
            handoff.parameterTypes.any {
                it == Int::class.javaPrimitiveType || it == Integer::class.java
            },
        )
        assertFalse(
            DestructiveWipeOptionPolicyAuthority::class.java.methods.any { method ->
                method.name == "execute" || method.name.contains("DevicePolicy")
            },
        )
    }
}
