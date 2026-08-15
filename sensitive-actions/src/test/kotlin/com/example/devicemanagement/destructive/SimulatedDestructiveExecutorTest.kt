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
    fun `capability freshness is revalidated after pre-execution evidence append`() {
        val evidence = InMemoryDestructiveSimulationEvidenceWriter()
        val composition = DestructiveSimulationComposition.create(
            evidenceWriter = evidence,
            nowMonotonicMillis = 1_000L,
        )
        evidence.appendHook = { ev ->
            if (ev.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED) {
                val advanced = 1_000L + DestructiveAuthorizationAuthority.MAX_CAPABILITY_AGE_MILLIS + 1L
                assertTrue(advanced - 1_000L > DestructiveAuthorizationAuthority.MAX_CAPABILITY_AGE_MILLIS)
                assertTrue(advanced - 1_000L < DestructiveArmingAuthority.MAX_ARM_AGE_MILLIS)
                composition.clock.now = advanced
            }
        }

        val result = composition.pipeline.submit(validRequest())

        assertEquals(DestructiveSimulationOutcome.FAILED_PRE_EXECUTION, result.outcome)
        assertEquals("capability_stale", result.reason)
        assertEquals(0, composition.sink.invocationCount())
        assertTrue(
            evidence.records().any { it.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED },
        )
    }

    @Test
    fun `negative monotonic delta after evidence append fails closed`() {
        val evidence = InMemoryDestructiveSimulationEvidenceWriter()
        val composition = DestructiveSimulationComposition.create(
            evidenceWriter = evidence,
            nowMonotonicMillis = 1_000L,
        )
        evidence.appendHook = { ev ->
            if (ev.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED) {
                composition.clock.now = 999L
            }
        }

        val result = composition.pipeline.submit(validRequest())

        assertEquals(DestructiveSimulationOutcome.FAILED_PRE_EXECUTION, result.outcome)
        assertEquals("capability_negative_monotonic_delta", result.reason)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `direct arm authorize execute without attempt admission cannot reach the sink`() {
        val composition = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val forgedLease = DestructiveAttemptLease.create()

        val armed = composition.armingAuthority.arm(binding, forgedLease)
        assertTrue(armed is ArmingIssueResult.Rejected)

        val authorized = composition.authorizationAuthority.authorize(
            DestructiveArmingToken.create(),
            binding,
            forgedLease,
        )
        assertTrue(authorized is DestructiveAuthorizationResult.Rejected)

        val executed = composition.executor.execute(
            DestructiveCapability.create(),
            binding,
            forgedLease,
        )
        assertTrue(executed.outcome != DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE)
        assertEquals(0, composition.sink.invocationCount())
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
        val authorized = composition.admitBindAuthorize(binding)
        val first = composition.executor.execute(
            authorized.capability,
            binding,
            authorized.attemptLease,
        )
        val replay = composition.executor.execute(
            authorized.capability,
            binding,
            authorized.attemptLease,
        )

        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, first.outcome)
        assertEquals(DestructiveSimulationOutcome.REJECTED, replay.outcome)
        assertEquals(1, composition.sink.invocationCount())
    }

    @Test
    fun `foreign permit cannot invoke the sink`() {
        val composition = DestructiveSimulationComposition.create()
        val denied = composition.sink.invoke(FinalExecutionPermit.create(), verifiedBinding())
        assertTrue(denied is SimulationSinkResult.Denied)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `raw issue then sink invoke path does not exist`() {
        val composition = DestructiveSimulationComposition.create()
        assertTrue(
            composition.gate.javaClass.methods.none { method ->
                method.name == "issue"
            },
        )
        val denied = composition.sink.invoke(FinalExecutionPermit.create(), verifiedBinding())
        assertTrue(denied is SimulationSinkResult.Denied)
        assertEquals(0, composition.sink.invocationCount())
        assertTrue(
            composition.pipeline.submit(validRequest()).outcome ==
                DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE,
        )
        assertEquals(1, composition.sink.invocationCount())
    }

    @Test
    fun `marker disappearance after admission fails closed at the final gate`() {
        val store = InMemoryDenyOnlyCooldownMarkerStore()
        val evidence = InMemoryDestructiveSimulationEvidenceWriter()
        val composition = DestructiveSimulationComposition.create(
            store = store,
            evidenceWriter = evidence,
        )
        evidence.appendHook = { ev ->
            if (ev.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED) {
                store.loseMarker()
            }
        }

        val result = composition.pipeline.submit(validRequest())

        assertEquals(DestructiveSimulationOutcome.FAILED_PRE_EXECUTION, result.outcome)
        assertEquals("cooldown_marker_missing_for_current_attempt", result.reason)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `terminal simulated success closes lease and arm`() {
        val composition = DestructiveSimulationComposition.create()
        val result = composition.pipeline.submit(validRequest())
        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, result.outcome)
        assertTrue(!composition.admissionAuthority.hasNonTerminalLease())
        val leftover = composition.armingAuthority.arm(
            verifiedBinding(),
            DestructiveAttemptLease.create(),
        )
        assertTrue(leftover is ArmingIssueResult.Rejected)
    }

    @Test
    fun `terminal pre-execution failure closes lease arm and proof`() {
        val evidence = InMemoryDestructiveSimulationEvidenceWriter()
        val composition = DestructiveSimulationComposition.create(evidenceWriter = evidence)
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        evidence.appendHook = { ev ->
            if (ev.phase == DestructiveEvidencePhase.PRE_EXECUTION_COMMITTED) {
                evidence.failNext = true
            }
        }

        val result = composition.executor.execute(
            authorized.capability,
            binding,
            authorized.attemptLease,
        )

        assertEquals(DestructiveSimulationOutcome.FAILED_PRE_EXECUTION, result.outcome)
        assertTrue(!composition.admissionAuthority.hasNonTerminalLease())
        assertEquals(
            "attempt_lease_not_issued_or_already_consumed",
            (
                composition.admissionAuthority.requireLive(authorized.attemptLease, binding)
                    as AttemptLeaseCheck.Dead
                ).reason,
        )
        assertEquals(
            "arm_not_issued_or_already_consumed",
            (
                composition.armingAuthority.requireLive(
                    authorized.armToken,
                    binding,
                    authorized.attemptLease,
                ) as ArmingCheck.Dead
                ).reason,
        )
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `after terminal close and cooldown expiry a new request may proceed`() {
        val composition = DestructiveSimulationComposition.create()
        val first = composition.pipeline.submit(validRequest())
        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, first.outcome)
        composition.clock.now = 1_000L + DestructiveDenyOnlyCooldown.DEFAULT_COOLDOWN_MILLIS
        val second = composition.pipeline.submit(validRequest())
        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, second.outcome)
        assertEquals(2, composition.sink.invocationCount())
    }

    @Test
    fun `old lease cannot be reused after terminal close`() {
        val composition = DestructiveSimulationComposition.create()
        val binding = verifiedBinding()
        val authorized = composition.admitBindAuthorize(binding)
        val executed = composition.executor.execute(
            authorized.capability,
            binding,
            authorized.attemptLease,
        )
        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, executed.outcome)
        val reused = composition.armingAuthority.arm(binding, authorized.attemptLease)
        assertEquals(
            "attempt_lease_not_issued_or_already_consumed",
            (reused as ArmingIssueResult.Rejected).reason,
        )
        val replayed = composition.executor.execute(
            authorized.capability,
            binding,
            authorized.attemptLease,
        )
        assertTrue(replayed.outcome != DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE)
        assertEquals(1, composition.sink.invocationCount())
    }

    @Test
    fun `malformed scope counts as a T19 attempt and never issues a lease`() {
        val composition = DestructiveSimulationComposition.create()
        val first = composition.pipeline.submit(
            DestructiveSimulationRequest(requestedScope = null),
        )
        assertEquals("unspecified_scope", first.reason)
        assertTrue(!composition.admissionAuthority.hasNonTerminalLease())
        val second = composition.pipeline.submit(
            DestructiveSimulationRequest(requestedScope = DestructiveScope.USER_SCOPED_WIPE),
        )
        assertEquals("cooldown_active", second.reason)
        assertTrue(!composition.admissionAuthority.hasNonTerminalLease())
        val valid = composition.pipeline.submit(validRequest())
        assertEquals("cooldown_active", valid.reason)
        assertEquals(0, composition.sink.invocationCount())
    }

    @Test
    fun `valid request issues exactly one lease after successful cooldown accounting`() {
        val composition = DestructiveSimulationComposition.create()
        val result = composition.pipeline.submit(validRequest())
        assertEquals(DestructiveSimulationOutcome.SIMULATED_WOULD_EXECUTE, result.outcome)
        assertTrue(!composition.admissionAuthority.hasNonTerminalLease())
        val secondAdmit = composition.admissionAuthority.admit(
            DestructiveCorrelationId.generate { "second" },
            DestructiveScope.DEVICE_FACTORY_RESET,
        )
        assertEquals(
            "cooldown_active",
            (secondAdmit as AttemptAdmissionResult.Rejected).reason,
        )
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
