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

internal class DestructiveSimulationComposition(
    val pipeline: DestructiveSimulationPipeline,
    val armingAuthority: DestructiveArmingAuthority,
    val authorizationAuthority: DestructiveAuthorizationAuthority,
    val cooldown: DestructiveDenyOnlyCooldown,
    val evidenceWriter: InMemoryDestructiveSimulationEvidenceWriter,
    val sink: Checkpoint17ASimulationSink,
    val liveFacts: MutableDestructiveLiveFactsSource,
    val clock: MutableMonotonicClock,
    val executor: SimulatedDestructiveExecutor,
    val permitAuthority: FinalExecutionPermitAuthority,
) {
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
            val armingAuthority = DestructiveArmingAuthority(clock)
            val authorizationAuthority = DestructiveAuthorizationAuthority(
                armingAuthority = armingAuthority,
                monotonicTimeSource = clock,
            )
            val cooldown = DestructiveDenyOnlyCooldown(
                store = store,
                monotonicTimeSource = clock,
            )
            val permitAuthority = FinalExecutionPermitAuthority(clock)
            val sink = Checkpoint17ASimulationSink(permitAuthority)
            val validator = DestructiveFinalValidator(
                liveFactsSource = liveFacts,
                armingAuthority = armingAuthority,
                cooldown = cooldown,
            )
            val executor = SimulatedDestructiveExecutor(
                authorizationAuthority = authorizationAuthority,
                validator = validator,
                evidenceWriter = evidenceWriter,
                permitAuthority = permitAuthority,
                sink = sink,
                monotonicTimeSource = clock,
                logger = logger,
            )
            val pipeline = DestructiveSimulationPipeline(
                liveFactsSource = liveFacts,
                armingAuthority = armingAuthority,
                authorizationAuthority = authorizationAuthority,
                cooldown = cooldown,
                executor = executor,
                evidenceWriter = evidenceWriter,
                logger = logger,
            )
            return DestructiveSimulationComposition(
                pipeline = pipeline,
                armingAuthority = armingAuthority,
                authorizationAuthority = authorizationAuthority,
                cooldown = cooldown,
                evidenceWriter = evidenceWriter,
                sink = sink,
                liveFacts = liveFacts,
                clock = clock,
                executor = executor,
                permitAuthority = permitAuthority,
            )
        }
    }
}

internal fun validRequest() = DestructiveSimulationRequest(
    callerRequestId = "caller-diagnostic",
    requestedScope = DestructiveScope.DEVICE_FACTORY_RESET,
)
