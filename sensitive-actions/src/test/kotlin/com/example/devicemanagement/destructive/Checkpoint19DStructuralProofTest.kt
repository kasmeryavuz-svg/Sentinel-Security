package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Checkpoint19DStructuralProofTest {
    @Test
    fun `matching artifact exact challenge exact lease and fresh record reach the fake executor once`() {
        val port = RecordingNonAndroidFactoryResetPort()
        val recordSource = OneShotMatchingConfirmationRecordSource()
        val harness = StructuralHarness(port = port, recordSource = recordSource)
        val result = harness.orchestrator.assembleAlreadyBoundDeviceFactoryReset(harness.attempt)
        assertTrue(result is FutureDestructiveHandoffResult.Acknowledged)
        assertEquals(
            FutureDestructiveHandoffAcknowledgement.Initiated,
            (result as FutureDestructiveHandoffResult.Acknowledged).acknowledgement,
        )
        assertEquals(1, port.invocations.get())
        assertEquals(1, recordSource.consumeCount.get())
        assertEquals("YES", Checkpoint19DDecision.REAL_CHAIN_STRUCTURAL_ASSEMBLY_COMPLETE)
        assertFalse(Checkpoint19DDecision.REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE)
        assertEquals("NO", Checkpoint19DDecision.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
    }

    @Test
    fun `replay of a consumed one-attempt record fails on a fresh orchestrator`() {
        val firstPort = RecordingNonAndroidFactoryResetPort()
        val recordSource = OneShotMatchingConfirmationRecordSource()
        val first = StructuralHarness(port = firstPort, recordSource = recordSource)
        val firstResult = first.orchestrator.assembleAlreadyBoundDeviceFactoryReset(first.attempt)
        assertTrue(firstResult is FutureDestructiveHandoffResult.Acknowledged)
        val secondPort = RecordingNonAndroidFactoryResetPort()
        val second = StructuralHarness(port = secondPort, recordSource = recordSource)
        val replay = second.orchestrator.assembleAlreadyBoundDeviceFactoryReset(second.attempt)
        assertEquals(
            "replayed_confirmation",
            (replay as FutureDestructiveHandoffResult.Failed).reason,
        )
        assertEquals(1, firstPort.invocations.get())
        assertEquals(0, secondPort.invocations.get())
        assertEquals(2, recordSource.consumeCount.get())
    }

    @Test
    fun `different challenge fails and does not reach the fake executor`() {
        assertRejectedBeforeHandoff(
            factsFor = { challenge, lease, harness ->
                matchingFacts(
                    harness = harness,
                    challenge = DestructiveOperatorChallenge.create(ByteArray(32) { 2 }),
                    attemptLease = lease,
                ).also { check(it.challenge !== challenge) }
            },
            expectedReason = "confirmation_challenge_mismatch",
        )
    }

    @Test
    fun `different lease fails and does not reach the fake executor`() {
        assertRejectedBeforeHandoff(
            factsFor = { challenge, lease, harness ->
                matchingFacts(
                    harness = harness,
                    challenge = challenge,
                    attemptLease = DestructiveAttemptLease.create(),
                ).also { check(it.attemptLease !== lease) }
            },
            expectedReason = "confirmation_attempt_lease_mismatch",
        )
    }

    @Test
    fun `expired stale record fails even when monotonic time is current`() {
        assertRejectedBeforeHandoff(
            factsFor = { challenge, lease, harness ->
                matchingFacts(
                    harness = harness,
                    challenge = challenge,
                    attemptLease = lease,
                    utcTimestamp = "2026-08-15T16:59:00Z",
                    validUntilUtc = "2026-08-15T16:59:20Z",
                )
            },
            expectedReason = "stale_confirmation",
        )
    }

    @Test
    fun `wrong serial digest build and attempt each fail closed`() {
        assertRejectedBeforeHandoff(
            factsFor = { challenge, lease, harness ->
                matchingFacts(
                    harness = harness,
                    challenge = challenge,
                    attemptLease = lease,
                    deviceSerial = "WRONG-SERIAL",
                )
            },
            expectedReason = "confirmation_device_serial_mismatch",
        )
        assertRejectedBeforeHandoff(
            factsFor = { challenge, lease, harness ->
                matchingFacts(
                    harness = harness,
                    challenge = challenge,
                    attemptLease = lease,
                    certificateSha256 = OTHER_CERT,
                )
            },
            expectedReason = "confirmation_certificate_digest_mismatch",
        )
        assertRejectedBeforeHandoff(
            factsFor = { challenge, lease, harness ->
                matchingFacts(
                    harness = harness,
                    challenge = challenge,
                    attemptLease = lease,
                    artifactSha256 = OTHER_ARTIFACT,
                )
            },
            expectedReason = "confirmation_artifact_digest_mismatch",
        )
        assertRejectedBeforeHandoff(
            factsFor = { challenge, lease, harness ->
                matchingFacts(
                    harness = harness,
                    challenge = challenge,
                    attemptLease = lease,
                    buildRevision = "WRONG-BUILD",
                )
            },
            expectedReason = "confirmation_build_revision_mismatch",
        )
        assertRejectedBeforeHandoff(
            factsFor = { challenge, lease, harness ->
                matchingFacts(
                    harness = harness,
                    challenge = challenge,
                    attemptLease = lease,
                    attemptId = "wrong-attempt",
                )
            },
            expectedReason = "confirmation_attempt_id_mismatch",
        )
    }

    @Test
    fun `null production record remains fail-closed after other gates are populated`() {
        val port = RecordingNonAndroidFactoryResetPort()
        val artifactSource = FixedTrustedArtifactExpectationSource(requireNotNull(mintedExpectation()))
        val liveFacts = MutableDestructiveLiveFactsSource(verifiedFacts().copy(deviceSerial = TEST_SERIAL))
        val orchestrator = ProductionDestructiveRealChainOrchestrator(
            executor = AndroidFutureDestructiveExecutor(port),
            liveFacts = liveFacts,
            clock = MutableMonotonicClock(1_000L),
            durability = reflectRuntimeDurabilityForRejectPathTests(),
            artifactExpectationSource = artifactSource,
            confirmationSource = ProductionDestructiveHumanConfirmationSource(
                recordSource = ProductionDestructiveTrustedPerAttemptConfirmationRecordSource(),
                utcClock = ProductionDestructiveUtcClock(),
                approvedBuildRevision = ProductionDestructiveApprovedBuildRevisionSource(),
                liveFacts = liveFacts,
                artifactExpectationSource = artifactSource,
            ),
        )
        val result = orchestrator.assembleAlreadyBoundDeviceFactoryReset(
            requireNotNull(
                ProductionBoundDeviceFactoryResetAttempt.bindAlreadyAuthorizedDeviceFactoryReset(
                    binding = verifiedBinding(facts = liveFacts.facts),
                    observedIdentity = requireNotNull(disposableObservedIdentity()),
                ),
            ),
        )
        assertTrue(result is FutureDestructiveHandoffResult.Failed)
        assertEquals(
            "missing_per_attempt_human_confirmation",
            (result as FutureDestructiveHandoffResult.Failed).reason,
        )
        assertEquals(0, port.invocations.get())
        assertNull(TrustedPerAttemptDestructiveConfirmationRecord.current())
    }

    @Test
    fun `failure before handoff does not leak reusable confirmation state`() {
        val captured = mutableListOf<DestructiveOperatorChallenge>()
        val leftover = java.util.concurrent.atomic.AtomicReference<DestructiveHumanConfirmation?>(null)
        val port = RecordingNonAndroidFactoryResetPort()
        val recordSource = ScriptedConfirmationRecordSource { challenge, lease, harness ->
            captured += challenge
            matchingFacts(
                harness = harness,
                challenge = challenge,
                attemptLease = lease,
                deviceSerial = "WRONG-SERIAL",
            )
        }
        val harness = StructuralHarness(port = port, recordSource = recordSource)
        val first = harness.orchestrator.assembleAlreadyBoundDeviceFactoryReset(harness.attempt)
        assertTrue(first is FutureDestructiveHandoffResult.Failed)
        assertEquals(
            "confirmation_device_serial_mismatch",
            (first as FutureDestructiveHandoffResult.Failed).reason,
        )
        assertEquals(0, port.invocations.get())
        assertEquals(1, captured.size)
        leftover.set(DestructiveHumanConfirmation.create())
        assertNull(DestructiveHumanConfirmationMint.consume(leftover.get()!!))
        val replayPort = RecordingNonAndroidFactoryResetPort()
        val replayHarness = StructuralHarness(port = replayPort, recordSource = recordSource)
        val replay = replayHarness.orchestrator.assembleAlreadyBoundDeviceFactoryReset(replayHarness.attempt)
        assertEquals(
            "replayed_confirmation",
            (replay as FutureDestructiveHandoffResult.Failed).reason,
        )
        assertEquals(0, port.invocations.get())
        assertEquals(0, replayPort.invocations.get())
    }

    @Test
    fun `production sources do not include test-only trusted implementations`() {
        val main = java.io.File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        listOf(
            "OneShotMatchingConfirmationRecordSource",
            "ScriptedConfirmationRecordSource",
            "RecordingNonAndroidFactoryResetPort",
            "FixedTrustedArtifactExpectationSource",
            "TestUtcClock",
            "TestApprovedBuildRevisionSource",
            "var overrideConfirmation",
            "mutableProductionTestHook",
        ).forEach { token ->
            assertFalse(token, main.contains(token))
        }
        assertFalse(main.contains("confirmed: Boolean"))
        assertFalse(main.contains("fun confirm(confirmed: Boolean"))
        assertFalse(main.contains("= \"confirmed\""))
    }

    private fun assertRejectedBeforeHandoff(
        factsFor: (
            DestructiveOperatorChallenge,
            DestructiveAttemptLease,
            StructuralHarness,
        ) -> TrustedPerAttemptConfirmationFacts,
        expectedReason: String,
    ) {
        val port = RecordingNonAndroidFactoryResetPort()
        val harness = StructuralHarness(
            port = port,
            recordSource = ScriptedConfirmationRecordSource(factsFor),
        )
        val result = harness.orchestrator.assembleAlreadyBoundDeviceFactoryReset(harness.attempt)
        assertTrue(expectedReason, result is FutureDestructiveHandoffResult.Failed)
        assertEquals(expectedReason, (result as FutureDestructiveHandoffResult.Failed).reason)
        assertEquals(0, port.invocations.get())
    }

    private class StructuralHarness(
        val port: RecordingNonAndroidFactoryResetPort,
        recordSource: DestructiveTrustedPerAttemptConfirmationRecordSource,
        val facts: DestructiveLiveFacts = verifiedFacts().copy(deviceSerial = TEST_SERIAL),
        val identity: DestructiveArtifactIdentity = requireNotNull(disposableObservedIdentity()),
        val expected: DestructiveArtifactIdentityExpectation = requireNotNull(mintedExpectation()),
    ) {
        val liveFacts = MutableDestructiveLiveFactsSource(facts)
        val artifactSource = FixedTrustedArtifactExpectationSource(expected)
        val orchestrator = ProductionDestructiveRealChainOrchestrator(
            executor = AndroidFutureDestructiveExecutor(port),
            liveFacts = liveFacts,
            clock = MutableMonotonicClock(1_000L),
            durability = reflectRuntimeDurabilityForRejectPathTests(),
            artifactExpectationSource = artifactSource,
            confirmationSource = ProductionDestructiveHumanConfirmationSource(
                recordSource = recordSource.also { source ->
                    if (source is HarnessAwareConfirmationRecordSource) {
                        source.harness = this
                    }
                },
                utcClock = TestUtcClock(NOW),
                approvedBuildRevision = TestApprovedBuildRevisionSource(TEST_BUILD),
                liveFacts = liveFacts,
                artifactExpectationSource = artifactSource,
            ),
        )
        val attempt = requireNotNull(
            ProductionBoundDeviceFactoryResetAttempt.bindAlreadyAuthorizedDeviceFactoryReset(
                binding = verifiedBinding(facts = facts),
                observedIdentity = identity,
            ),
        )
    }

    private interface HarnessAwareConfirmationRecordSource :
        DestructiveTrustedPerAttemptConfirmationRecordSource {
        var harness: StructuralHarness?
    }

    private class OneShotMatchingConfirmationRecordSource :
        HarnessAwareConfirmationRecordSource {
        override var harness: StructuralHarness? = null
        val consumeCount = AtomicInteger(0)
        private val consumed = AtomicBoolean(false)

        override fun consumeMatching(
            challenge: DestructiveOperatorChallenge,
            attemptLease: DestructiveAttemptLease,
        ): DestructiveTrustedConfirmationRecordConsumeResult {
            consumeCount.incrementAndGet()
            if (!consumed.compareAndSet(false, true)) {
                return DestructiveTrustedConfirmationRecordConsumeResult.AlreadyConsumed
            }
            return DestructiveTrustedConfirmationRecordConsumeResult.Available(
                matchingFacts(
                    harness = requireNotNull(harness),
                    challenge = challenge,
                    attemptLease = attemptLease,
                ),
            )
        }
    }

    private class ScriptedConfirmationRecordSource(
        private val factsFor: (
            DestructiveOperatorChallenge,
            DestructiveAttemptLease,
            StructuralHarness,
        ) -> TrustedPerAttemptConfirmationFacts,
    ) : HarnessAwareConfirmationRecordSource {
        override var harness: StructuralHarness? = null
        private val consumed = AtomicBoolean(false)

        override fun consumeMatching(
            challenge: DestructiveOperatorChallenge,
            attemptLease: DestructiveAttemptLease,
        ): DestructiveTrustedConfirmationRecordConsumeResult {
            if (!consumed.compareAndSet(false, true)) {
                return DestructiveTrustedConfirmationRecordConsumeResult.AlreadyConsumed
            }
            return DestructiveTrustedConfirmationRecordConsumeResult.Available(
                factsFor(challenge, attemptLease, requireNotNull(harness)),
            )
        }
    }

    private class RecordingNonAndroidFactoryResetPort : AuthorizedFactoryResetPort {
        val invocations = AtomicInteger(0)

        override fun performAuthorizedFactoryReset(): AuthorizedFactoryResetResult {
            invocations.incrementAndGet()
            return AuthorizedFactoryResetResult.Initiated
        }
    }

    private class FixedTrustedArtifactExpectationSource(
        private val expected: DestructiveArtifactIdentityExpectation?,
    ) : DestructiveTrustedArtifactExpectationSource {
        override fun trustedExpectation(): DestructiveArtifactIdentityExpectation? = expected
    }

    private class TestUtcClock(
        private val now: Instant,
    ) : DestructiveUtcClock {
        override fun nowEpochMillis(): Long = now.toEpochMilli()
    }

    private class TestApprovedBuildRevisionSource(
        private val revision: String?,
    ) : DestructiveTrustedApprovedBuildRevisionSource {
        override fun recorded(): String? = revision
    }

    private companion object {
        const val TEST_SERIAL = "19D-TEST-DEVICE-SERIAL"
        const val TEST_BUILD = "19D-TEST-BUILD-REVISION"
        const val TEST_OPERATOR = "19D-TEST-OPERATOR"
        const val CERT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val ARTIFACT = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val OTHER_CERT = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        const val OTHER_ARTIFACT = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        val NOW: Instant = Instant.parse("2026-08-15T17:00:00Z")
        const val UTC_ISSUED = "2026-08-15T16:59:50Z"
        const val UTC_VALID_UNTIL = "2026-08-15T17:00:20Z"

        fun matchingFacts(
            harness: StructuralHarness,
            challenge: DestructiveOperatorChallenge,
            attemptLease: DestructiveAttemptLease,
            operatorIdentity: String = TEST_OPERATOR,
            utcTimestamp: String = UTC_ISSUED,
            deviceSerial: String = TEST_SERIAL,
            packageName: String = harness.identity.packageName,
            adminComponent: String = harness.identity.adminComponent,
            certificateSha256: String = CERT,
            artifactSha256: String = ARTIFACT,
            scope: DestructiveScope = DestructiveScope.DEVICE_FACTORY_RESET,
            flagsLiteralZero: Int = 0,
            buildRevision: String = TEST_BUILD,
            oneAttemptOnly: Boolean = true,
            attemptId: String = harness.attempt.binding.correlationId.value,
            validUntilUtc: String = UTC_VALID_UNTIL,
        ): TrustedPerAttemptConfirmationFacts {
            return reflectTrustedPerAttemptConfirmationFacts(
                operatorIdentity = operatorIdentity,
                utcTimestamp = utcTimestamp,
                deviceSerial = deviceSerial,
                packageName = packageName,
                adminComponent = adminComponent,
                certificateSha256 = certificateSha256,
                artifactSha256 = artifactSha256,
                scope = scope,
                flagsLiteralZero = flagsLiteralZero,
                buildRevision = buildRevision,
                oneAttemptOnly = oneAttemptOnly,
                attemptId = attemptId,
                validUntilUtc = validUntilUtc,
                challenge = challenge,
                correlationId = harness.attempt.binding.correlationId,
                binding = harness.attempt.binding,
                artifactIdentity = harness.identity,
                attemptLease = attemptLease,
                nowMonotonicMillis = 0L,
            )
        }
    }
}

internal fun mintedExpectation(): DestructiveArtifactIdentityExpectation? {
    val identity = disposableObservedIdentity() ?: return null
    return DestructiveArtifactIdentityExpectation.TrustedDestructiveArtifactExpectationMint
        .issueFromTrustedValidationSource(
            certificateSha256 = identity.certificateSha256,
            artifactSha256 = identity.artifactSha256,
            packageName = identity.packageName,
            adminComponent = identity.adminComponent,
            buildPurpose = identity.buildPurpose,
        )
}

internal fun reflectTrustedPerAttemptConfirmationFacts(
    operatorIdentity: String,
    utcTimestamp: String,
    deviceSerial: String,
    packageName: String,
    adminComponent: String,
    certificateSha256: String,
    artifactSha256: String,
    scope: DestructiveScope,
    flagsLiteralZero: Int,
    buildRevision: String,
    oneAttemptOnly: Boolean,
    attemptId: String,
    validUntilUtc: String,
    challenge: DestructiveOperatorChallenge,
    correlationId: DestructiveCorrelationId,
    binding: DestructiveTargetBinding,
    artifactIdentity: DestructiveArtifactIdentity,
    attemptLease: DestructiveAttemptLease,
    nowMonotonicMillis: Long,
): TrustedPerAttemptConfirmationFacts {
    val constructor = TrustedPerAttemptConfirmationFacts::class.java.declaredConstructors.single { candidate ->
        candidate.parameterTypes.count { type ->
            type.name != "kotlin.jvm.internal.DefaultConstructorMarker"
        } == 19
    }
    constructor.isAccessible = true
    val args: Array<Any?> = arrayOf(
        operatorIdentity,
        utcTimestamp,
        deviceSerial,
        packageName,
        adminComponent,
        certificateSha256,
        artifactSha256,
        scope,
        flagsLiteralZero,
        buildRevision,
        oneAttemptOnly,
        attemptId,
        validUntilUtc,
        challenge,
        correlationId,
        binding,
        artifactIdentity,
        attemptLease,
        nowMonotonicMillis,
    )
    val invocation = if (constructor.parameterCount == args.size + 1) {
        args.toList() + null
    } else {
        args.toList()
    }
    @Suppress("UNCHECKED_CAST")
    return constructor.newInstance(*invocation.toTypedArray()) as TrustedPerAttemptConfirmationFacts
}
