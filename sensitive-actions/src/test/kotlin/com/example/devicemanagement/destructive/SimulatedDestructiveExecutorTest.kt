package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedDestructiveExecutorTest {
    @Test
    fun `happy path consumes then appends then validates then invokes the sink`() {
        val composition = DestructiveSimulationComposition.create()
        val order = mutableListOf<String>()
        composition.evidenceWriter.appendHook = { evidence ->
            order += evidence.phase.name
        }

        val result = composition.pipeline.submit(validRequest())

        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, result.outcome)
        assertEquals(Checkpoint17ASimulationSink.MESSAGE, result.reason)
        assertEquals(1, composition.sink.invocationCount())
        val phases = composition.evidenceWriter.records().map { it.phase }
        assertTrue(phases.contains(DestructiveEvidencePhase.REQUESTED))
        assertTrue(phases.contains(DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED))
        assertTrue(phases.contains(DestructiveEvidencePhase.SIMULATED))
        assertTrue(!phases.contains(DestructiveEvidencePhase.FAILED_PRE_EXECUTION))
        assertTrue(order.indexOf("PRE_EXECUTION_COMMITTED") < order.indexOf("SIMULATED"))
    }

    @Test
    fun `audit append failure prevents simulated execution`() {
        val evidence = InMemoryDestructiveSimulationEvidenceWriter()
        val composition = DestructiveSimulationComposition.create(evidenceWriter = evidence)
        evidence.appendHook = { ev ->
            if (ev.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED) {
                evidence.failNext = true
            }
        }

        val result = composition.pipeline.submit(validRequest())

        assertEquals(DestructiveSimulationOutcome.FAILED_PRE_EXECUTION, result.outcome)
        assertEquals("audit_persistence_unavailable", result.reason)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `final validation happens after append and sees live fact changes`() {
        val evidence = InMemoryDestructiveSimulationEvidenceWriter()
        val composition = DestructiveSimulationComposition.create(evidenceWriter = evidence)
        val original = composition.liveFacts.facts
        evidence.appendHook = { ev ->
            if (ev.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED) {
                composition.liveFacts.facts = original.copy(isDeviceOwner = false)
            }
        }

        val result = composition.pipeline.submit(validRequest())

        assertEquals(DestructiveSimulationOutcome.FAILED_PRE_EXECUTION, result.outcome)
        assertEquals("device_owner_not_verified", result.reason)
        assertEquals(0, composition.sink.invocationCount())
        assertTrue(
            evidence.records().any { it.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED },
        )
    }

    @Test
    fun `wrong device owner admin target scope freshness or cooldown denies`() {
        assertDenied(facts = verifiedFacts().copy(isDeviceOwner = false), reason = "device_owner_not_verified")
        assertDenied(facts = verifiedFacts().copy(isProfileOwner = true), reason = "profile_owner_present")
        assertDenied(
            facts = verifiedFacts(runningPackage = " "),
            reason = "blank_package",
        )
        assertDenied(
            request = DestructiveSimulationRequest(requestedScope = null),
            reason = "unspecified_scope",
        )
        assertDenied(
            request = DestructiveSimulationRequest(requestedScope = DestructiveScope.USER_SCOPED_WIPE),
            reason = "unsupported_scope",
        )
        assertDenied(
            facts = verifiedFacts().copy(
                managementValidationState = DestructiveManagementValidation.CONFIGURATION_ERROR,
            ),
            reason = "device_owner_not_verified",
        )
        assertDenied(
            facts = verifiedFacts().copy(policyServiceAvailable = false),
            reason = "policy_service_unavailable",
        )
    }

    @Test
    fun `exception during live facts is deny`() {
        val composition = DestructiveSimulationComposition.create()
        composition.liveFacts.throwOnRead = true
        val result = composition.pipeline.submit(validRequest())
        assertEquals(DestructiveSimulationOutcome.REJECTED, result.outcome)
        assertEquals("live_facts_unavailable", result.reason)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `replayed capability cannot reach the sink`() {
        val composition = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val armed = composition.armingAuthority.arm(binding) as ArmingIssueResult.Armed
        val authorized = composition.authorizationAuthority.authorize(armed.token, binding)
            as DestructiveAuthorizationResult.Authorized
        val first = composition.executor.execute(authorized.capability, binding)
        val replay = composition.executor.execute(authorized.capability, binding)

        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, first.outcome)
        assertEquals(DestructiveSimulationOutcome.REJECTED, replay.outcome)
        assertEquals(1, composition.sink.invocationCount())
    }

    @Test
    fun `foreign permit cannot invoke the sink`() {
        val composition = DestructiveSimulationComposition.create()
        val foreign = FinalExecutionPermitAuthority(composition.clock).issue(verifiedBinding())
        val denied = composition.sink.invoke(foreign, verifiedBinding())
        assertTrue(denied is SimulationSinkResult.Denied)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `duplicate in-flight submit is rejected`() {
        val composition = DestructiveSimulationComposition.create()
        var nested: DestructiveSimulationStatus? = null
        composition.evidenceWriter.appendHook = {
            if (nested == null) {
                nested = composition.pipeline.submit(validRequest())
            }
        }
        val first = composition.pipeline.submit(validRequest())
        assertEquals("duplicate_in_flight", nested?.reason)
        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, first.outcome)
        assertEquals(1, composition.sink.invocationCount())
    }

    @Test
    fun `second submit in the same process is denied by cooldown`() {
        val composition = DestructiveSimulationComposition.create()
        val first = composition.pipeline.submit(validRequest())
        val second = composition.pipeline.submit(validRequest())
        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, first.outcome)
        assertEquals("cooldown_active", second.reason)
        assertEquals(1, composition.sink.invocationCount())
    }

    @Test
    fun `simulated evidence never uses APPLIED`() {
        val composition = DestructiveSimulationComposition.create()
        composition.pipeline.submit(validRequest())
        assertTrue(
            composition.evidenceWriter.records().none {
                it.phase.name == "APPLIED"
            },
        )
        assertTrue(
            composition.evidenceWriter.records().none {
                it.actionName == "mock_wipe"
            },
        )
    }

    private fun assertDenied(
        facts: DestructiveLiveFacts = verifiedFacts(),
        request: DestructiveSimulationRequest = validRequest(),
        reason: String,
    ) {
        val composition = DestructiveSimulationComposition.create(facts = facts)
        val result = composition.pipeline.submit(request)
        assertEquals(reason, result.reason)
        assertEquals(0, composition.sink.invocationCount())
        assertTrue(result.outcome != DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE)
    }
}
