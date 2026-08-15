package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19BDecisionRealityTest {
    @Test
    fun `recorded approval matches the required sentence and does not invent a hash`() {
        assertEquals("YES", Checkpoint19BDecision.DESTRUCTIVE_IMPLEMENTATION_APPROVED)
        assertTrue(Checkpoint19BDecision.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertTrue(Checkpoint19BDecision.DESTRUCTIVE_IMPLEMENTATION_PRESENT)
        assertFalse(Checkpoint19BDecision.DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT)
        assertEquals(
            Checkpoint19ADecision.REQUIRED_APPROVAL_SENTENCE,
            Checkpoint19BDecision.RECORDED_APPROVAL_SENTENCE,
        )
        assertEquals(
            "Yavuz Kasmer <kasmeryavuz@gmail.com>",
            Checkpoint19BDecision.RECORDED_APPROVAL_OPERATOR,
        )
        assertEquals("2026-08-15T14:28:00Z", Checkpoint19BDecision.RECORDED_APPROVAL_TIMESTAMP)
        assertEquals(
            "b2c5cafe8f06074495e66dd35885693478f4ceba",
            Checkpoint19BDecision.RECORDED_APPROVAL_GIT_REVISION,
        )
        assertTrue(
            Checkpoint19BDecision.RECORDED_APPROVAL_DEVICE_IDENTITY.contains("serial not identified"),
        )
        assertNull(Checkpoint19BDecision.RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256)
        assertNull(Checkpoint19BDecision.RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256)
        assertFalse(HEX_SHA256.containsMatchIn(File(
            "src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19BDecision.kt",
        ).readText()))
    }

    @Test
    fun `implementation flags match the A-D package without assembling the chain`() {
        assertTrue(Checkpoint19BDecision.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertTrue(Checkpoint19BDecision.DESTRUCTIVE_POLICY_WRAPPER_PRESENT)
        assertTrue(Checkpoint19BDecision.DESTRUCTIVE_METADATA_PRESENT)
        assertTrue(Checkpoint19BDecision.WIPE_DATA_METADATA_REVIEW_APPROVED)
        assertTrue(Checkpoint19BDecision.DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED)
        assertTrue(Checkpoint19BDecision.WIPE_ZERO_BYTECODE_ENFORCED)
        assertTrue(Checkpoint19BDecision.FACTORY_RESET_ORIGIN_EXACT)
        assertTrue(Checkpoint19BDecision.DEX_CONTROL_FLOW_ZERO_PROOF)
        assertFalse(Checkpoint19BDecision.PRODUCTION_REACHABLE_SIMULATION)
        assertFalse(Checkpoint19BDecision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint19BDecision.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertFalse(Checkpoint19BDecision.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertFalse(Checkpoint19BDecision.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertFalse(Checkpoint19BDecision.REAL_DESTRUCTIVE_CHAIN_ASSEMBLED_IN_PRODUCTION)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED)
        assertFalse(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED)
        assertTrue(Checkpoint19BDecision.FUTURE_SCOPE_DEVICE_FACTORY_RESET_ONLY)
        assertTrue(Checkpoint19BDecision.FUTURE_EXTRA_FLAG_SET_MUST_BE_EMPTY)
    }

    @Test
    fun `decision document records approval and refuses hardware validation`() {
        val docs = File("../docs/WIPE_19B_DESTRUCTIVE_IMPLEMENTATION.md").readText()
        assertTrue(docs.contains("DESTRUCTIVE_IMPLEMENTATION_APPROVED = YES"))
        assertTrue(docs.contains("DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = true"))
        assertTrue(docs.contains(Checkpoint19ADecision.REQUIRED_APPROVAL_SENTENCE))
        assertTrue(docs.contains("wipeDevice(0)"))
        assertTrue(docs.contains("<wipe-data>"))
        assertTrue(Checkpoint19BDecision.WIPE_ZERO_BYTECODE_ENFORCED)
        assertTrue(Checkpoint19BDecision.FACTORY_RESET_ORIGIN_EXACT)
        assertTrue(Checkpoint19BDecision.DEX_CONTROL_FLOW_ZERO_PROOF)
        assertTrue(docs.contains("exact integer constant 0"))
        assertTrue(docs.contains("control-flow predecessor"))
        assertTrue(docs.contains("DEX_CONTROL_FLOW_ZERO_PROOF = true"))
        assertTrue(docs.contains("NO DESTRUCTIVE HARDWARE TEST WAS PERFORMED"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertFalse(docs.contains("DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = true"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        Checkpoint19BDecision.remainingHardwareValidationBlockers.forEach { name ->
            assertTrue(name, name in Checkpoint19BDecision.remainingHardwareValidationBlockers)
        }
    }

    @Test
    fun `sensitive-actions production still does not spell Android wipe tokens`() {
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("import android.app.admin.DevicePolicyManager"))
        assertTrue(sources.contains("AndroidFutureDestructiveExecutor"))
        assertTrue(sources.contains("AuthorizedFactoryResetPort"))
        assertTrue(sources.contains("ProductionDestructiveRealChain"))
        assertTrue(sources.contains("Checkpoint19BDecision"))
        assertTrue(sources.contains("Checkpoint19CDecision"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}

class ProductionDestructiveRealChainTest {
    @Test
    fun `retain does not assemble the chain while artifact expectation is unrecorded`() {
        val retainer = ProductionDestructiveRealChain.retainForProduction(
            factoryReset = object : AuthorizedFactoryResetPort {
                override fun performAuthorizedFactoryReset(): AuthorizedFactoryResetResult {
                    return AuthorizedFactoryResetResult.Refused("test")
                }
            },
            liveFacts = DestructiveLiveFactsSource {
                error("live facts unused while chain unassembled")
            },
            clock = object : MonotonicTimeSource {
                override fun nowMillis(): Long = 0L
            },
            durability = null,
        )
        assertNull(retainer.boundary)
        assertTrue(retainer.executor is AndroidFutureDestructiveExecutor)
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
    }
}

class AndroidFutureDestructiveExecutorTest {
    @Test
    fun `handoff maps initiated and refused port results`() {
        val initiated = AndroidFutureDestructiveExecutor(
            factoryReset = object : AuthorizedFactoryResetPort {
                override fun performAuthorizedFactoryReset(): AuthorizedFactoryResetResult {
                    return AuthorizedFactoryResetResult.Initiated
                }
            },
        )
        val refused = AndroidFutureDestructiveExecutor(
            factoryReset = object : AuthorizedFactoryResetPort {
                override fun performAuthorizedFactoryReset(): AuthorizedFactoryResetResult {
                    return AuthorizedFactoryResetResult.Refused("not_device_owner")
                }
            },
        )
        assertEquals(
            FutureDestructiveHandoffAcknowledgement.Initiated,
            initiated.onAuthorizedHandoffForTest(),
        )
        val acknowledgement = refused.onAuthorizedHandoffForTest()
        assertTrue(acknowledgement is FutureDestructiveHandoffAcknowledgement.Refused)
        assertEquals(
            "not_device_owner",
            (acknowledgement as FutureDestructiveHandoffAcknowledgement.Refused).reason,
        )
    }
}

private fun AndroidFutureDestructiveExecutor.onAuthorizedHandoffForTest():
    FutureDestructiveHandoffAcknowledgement {
    val method = AndroidFutureDestructiveExecutor::class.java.getDeclaredMethod("onAuthorizedHandoff")
    method.isAccessible = true
    return method.invoke(this) as FutureDestructiveHandoffAcknowledgement
}
