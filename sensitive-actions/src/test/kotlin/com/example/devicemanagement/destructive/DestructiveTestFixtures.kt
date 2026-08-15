package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import com.example.devicemanagement.logging.StructuredLogger

internal class MutableMonotonicClock(
    var now: Long,
) : MonotonicTimeSource {
    override fun nowMillis(): Long = now
}

internal class MutableDestructiveLiveFactsSource(
    var facts: DestructiveLiveFacts,
    var throwOnRead: Boolean = false,
) : DestructiveLiveFactsSource {
    override fun currentFacts(): DestructiveLiveFacts {
        if (throwOnRead) {
            error("live_facts_unavailable")
        }
        return facts
    }
}

internal class RecordingLogger : StructuredLogger {
    val events = mutableListOf<String>()

    override fun info(event: String, fields: Map<String, Any?>) {
        events += event
    }

    override fun warn(event: String, fields: Map<String, Any?>) {
        events += event
    }

    override fun error(
        event: String,
        fields: Map<String, Any?>,
        throwable: Throwable?,
    ) {
        events += event
    }
}

internal fun verifiedFacts(
    runningPackage: String = "com.example.devicemanagement",
    expectedAdminComponent: String =
        "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
): DestructiveLiveFacts {
    return DestructiveLiveFacts(
        runningPackage = runningPackage,
        expectedAdminComponent = expectedAdminComponent,
        registeredSentinelAdminSet = setOf(expectedAdminComponent),
        isDeviceOwner = true,
        isProfileOwner = false,
        isExpectedAdminActive = true,
        activeAdminComponentSet = setOf(expectedAdminComponent),
        managementValidationState = DestructiveManagementValidation.VERIFIED_DEVICE_OWNER,
        policyServiceAvailable = true,
    )
}

internal fun verifiedBinding(
    facts: DestructiveLiveFacts = verifiedFacts(),
    correlationId: DestructiveCorrelationId = DestructiveCorrelationId.generate { "authoritative-correlation" },
    scope: DestructiveScope = DestructiveScope.DEVICE_FACTORY_RESET,
): DestructiveTargetBinding {
    return DestructiveTargetRules.bindingFromAssessedFacts(facts, scope, correlationId)
}

internal class DestructiveAuthorityBundle(
    val clock: MutableMonotonicClock = MutableMonotonicClock(1_000L),
    store: DenyOnlyCooldownMarkerStore = InMemoryDenyOnlyCooldownMarkerStore(),
) {
    val cooldown = DestructiveDenyOnlyCooldown(store, clock)
    val admission = DestructiveAttemptAdmissionAuthority(cooldown, clock)
    val arming = DestructiveArmingAuthority(clock, admission)
    val authorization = DestructiveAuthorizationAuthority(arming, clock, admission)
    val cleanup = DestructiveTerminalCleanup(admission, arming, authorization)

    fun admitAndBind(binding: DestructiveTargetBinding): DestructiveAttemptLease {
        val admitted = admission.admit(binding.correlationId, binding.scope)
        check(admitted is AttemptAdmissionResult.Admitted) {
            (admitted as AttemptAdmissionResult.Rejected).reason
        }
        val bound = admission.bindTarget(admitted.lease, binding)
        check(bound is AttemptBindResult.Bound) {
            (bound as AttemptBindResult.Rejected).reason
        }
        return admitted.lease
    }

    fun arm(binding: DestructiveTargetBinding): Pair<DestructiveAttemptLease, DestructiveArmingToken> {
        val lease = admitAndBind(binding)
        val armed = arming.arm(binding, lease)
        check(armed is ArmingIssueResult.Armed) {
            (armed as ArmingIssueResult.Rejected).reason
        }
        return lease to armed.token
    }

    fun authorize(binding: DestructiveTargetBinding): DestructiveAuthorizationResult.Authorized {
        val (lease, token) = arm(binding)
        val authorized = authorization.authorize(token, binding, lease)
        check(authorized is DestructiveAuthorizationResult.Authorized) {
            (authorized as DestructiveAuthorizationResult.Rejected).reason
        }
        return authorized
    }
}

internal class DestructiveSimulationComposition(
    val pipeline: DestructiveSimulationPipeline,
    val admissionAuthority: DestructiveAttemptAdmissionAuthority,
    val armingAuthority: DestructiveArmingAuthority,
    val authorizationAuthority: DestructiveAuthorizationAuthority,
    val cooldown: DestructiveDenyOnlyCooldown,
    val evidenceWriter: InMemoryDestructiveSimulationEvidenceWriter,
    val sink: Checkpoint17ASimulationSink,
    val liveFacts: MutableDestructiveLiveFactsSource,
    val clock: MutableMonotonicClock,
    val executor: SimulatedDestructiveExecutor,
    val gate: DestructiveFinalExecutionGate,
    val cleanup: DestructiveTerminalCleanup,
    val markerStore: InMemoryDenyOnlyCooldownMarkerStore?,
) {
    fun admitBindAuthorize(
        binding: DestructiveTargetBinding,
    ): DestructiveAuthorizationResult.Authorized {
        val admitted = admissionAuthority.admit(binding.correlationId, binding.scope)
        check(admitted is AttemptAdmissionResult.Admitted) {
            (admitted as AttemptAdmissionResult.Rejected).reason
        }
        val bound = admissionAuthority.bindTarget(admitted.lease, binding)
        check(bound is AttemptBindResult.Bound) {
            (bound as AttemptBindResult.Rejected).reason
        }
        val armed = armingAuthority.arm(binding, admitted.lease)
        check(armed is ArmingIssueResult.Armed) {
            (armed as ArmingIssueResult.Rejected).reason
        }
        val authorized = authorizationAuthority.authorize(armed.token, binding, admitted.lease)
        check(authorized is DestructiveAuthorizationResult.Authorized) {
            (authorized as DestructiveAuthorizationResult.Rejected).reason
        }
        return authorized
    }

    companion object {
        fun create(
            store: DenyOnlyCooldownMarkerStore = InMemoryDenyOnlyCooldownMarkerStore(),
            facts: DestructiveLiveFacts = verifiedFacts(),
            nowMonotonicMillis: Long = 1_000L,
            logger: StructuredLogger = RecordingLogger(),
            evidenceWriter: InMemoryDestructiveSimulationEvidenceWriter =
                InMemoryDestructiveSimulationEvidenceWriter(),
        ): DestructiveSimulationComposition {
            val clock = MutableMonotonicClock(nowMonotonicMillis)
            val liveFacts = MutableDestructiveLiveFactsSource(facts)
            val cooldown = DestructiveDenyOnlyCooldown(
                store = store,
                monotonicTimeSource = clock,
            )
            val admissionAuthority = DestructiveAttemptAdmissionAuthority(cooldown, clock)
            val armingAuthority = DestructiveArmingAuthority(
                monotonicTimeSource = clock,
                admissionAuthority = admissionAuthority,
            )
            val authorizationAuthority = DestructiveAuthorizationAuthority(
                armingAuthority = armingAuthority,
                monotonicTimeSource = clock,
                admissionAuthority = admissionAuthority,
            )
            val cleanup = DestructiveTerminalCleanup(
                admissionAuthority = admissionAuthority,
                armingAuthority = armingAuthority,
                authorizationAuthority = authorizationAuthority,
            )
            val gate = DestructiveFinalExecutionGate(
                liveFactsSource = liveFacts,
                armingAuthority = armingAuthority,
                authorizationAuthority = authorizationAuthority,
                admissionAuthority = admissionAuthority,
                cooldown = cooldown,
                monotonicTimeSource = clock,
            )
            val sink = Checkpoint17ASimulationSink(gate)
            val executor = SimulatedDestructiveExecutor(
                authorizationAuthority = authorizationAuthority,
                gate = gate,
                evidenceWriter = evidenceWriter,
                sink = sink,
                cleanup = cleanup,
                monotonicTimeSource = clock,
                logger = logger,
            )
            val pipeline = DestructiveSimulationPipeline(
                liveFactsSource = liveFacts,
                admissionAuthority = admissionAuthority,
                armingAuthority = armingAuthority,
                authorizationAuthority = authorizationAuthority,
                cooldown = cooldown,
                executor = executor,
                evidenceWriter = evidenceWriter,
                cleanup = cleanup,
                logger = logger,
            )
            return DestructiveSimulationComposition(
                pipeline = pipeline,
                admissionAuthority = admissionAuthority,
                armingAuthority = armingAuthority,
                authorizationAuthority = authorizationAuthority,
                cooldown = cooldown,
                evidenceWriter = evidenceWriter,
                sink = sink,
                liveFacts = liveFacts,
                clock = clock,
                executor = executor,
                gate = gate,
                cleanup = cleanup,
                markerStore = store as? InMemoryDenyOnlyCooldownMarkerStore,
            )
        }
    }
}

internal fun validRequest() = DestructiveSimulationRequest(
    callerRequestId = "caller-diagnostic",
    requestedScope = DestructiveScope.DEVICE_FACTORY_RESET,
)
