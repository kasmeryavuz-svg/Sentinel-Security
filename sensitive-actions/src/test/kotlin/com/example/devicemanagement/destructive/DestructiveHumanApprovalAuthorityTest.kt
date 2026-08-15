package com.example.devicemanagement.destructive

import com.example.devicemanagement.action.Approval
import com.example.devicemanagement.action.ApprovalAuthority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class DestructiveHumanApprovalAuthorityTest {
    @Test
    fun `issued challenge is unpredictable and not a fixed magic string`() {
        val first = liveAuthority().issueChallenge()
        val second = liveAuthority().issueChallenge()
        val firstIssued = first as DestructiveChallengeIssueResult.Issued
        val secondIssued = second as DestructiveChallengeIssueResult.Issued
        assertFalse(firstIssued.challenge.nonceCopy().contentEquals(ByteArray(32)))
        assertFalse(firstIssued.challenge.nonceCopy().contentEquals(secondIssued.challenge.nonceCopy()))
        assertFalse(firstIssued.challenge.nonceCopy().contentEquals("APPROVE".toByteArray()))
        assertTrue(firstIssued.challenge !== secondIssued.challenge)
        assertTrue(firstIssued.challenge.identity !== secondIssued.challenge.identity)
    }

    @Test
    fun `issue challenge returns challenge material only and caller alone cannot redeem`() {
        val fixture = liveAuthority()
        val issued = fixture.issueChallenge() as DestructiveChallengeIssueResult.Issued
        assertEquals(
            listOf("challenge"),
            DestructiveChallengeIssueResult.Issued::class.java.declaredFields
                .filter { it.name != "Companion" }
                .map { it.name },
        )
        assertFalse(
            DestructiveChallengeIssueResult.Issued::class.java.declaredFields.any { field ->
                field.type == DestructiveHumanConfirmation::class.java ||
                    field.name.contains("response", ignoreCase = true) ||
                    field.name.contains("confirmation", ignoreCase = true) ||
                    field.name.contains("token", ignoreCase = true) ||
                    field.name.contains("approval", ignoreCase = true)
            },
        )
        assertFalse(
            DestructiveHumanApprovalAuthority::class.java.declaredMethods.any { method ->
                method.returnType == DestructiveHumanConfirmation::class.java ||
                    method.returnType == DestructiveHumanConfirmationResult::class.java ||
                    method.name.contains("confirm", ignoreCase = true) ||
                    method.name == "issueFromTrustedConfirmationSource"
            },
        )
        assertFalse(
            DestructiveHumanApprovalAuthority::class.java.declaredFields.any { field ->
                field.type == DestructiveHumanConfirmationAuthority::class.java
            },
        )
        assertNull(
            UnwiredDestructiveHumanConfirmationSource.confirm(
                challenge = issued.challenge,
                correlationId = fixture.binding.correlationId,
                binding = fixture.binding,
                scope = fixture.binding.scope,
                artifactIdentity = fixture.identity,
                attemptLease = fixture.lease,
            ),
        )
        assertTrue(
            fixture.authority.redeem(
                challenge = issued.challenge,
                confirmation = DestructiveHumanConfirmation.create(),
                correlationId = fixture.binding.correlationId,
                binding = fixture.binding,
                scope = fixture.binding.scope,
                artifactIdentity = fixture.identity,
                attemptLease = fixture.lease,
            ) is DestructiveHumanApprovalResult.Failed,
        )
        assertTrue(fixture.redeemWithoutConfirmation(issued) is DestructiveHumanApprovalResult.Failed)
    }

    @Test
    fun `redeem is bound to the exact pending attempt and identity`() {
        val fixture = liveAuthority()
        val issued = fixture.issueChallenge() as DestructiveChallengeIssueResult.Issued
        val confirmation = fixture.confirm(issued)
        val otherBinding = verifiedBinding(
            correlationId = DestructiveCorrelationId.generate { "other-correlation" },
        )
        assertEquals(
            "human_approval_correlation_mismatch",
            (
                fixture.authority.redeem(
                    challenge = issued.challenge,
                    confirmation = confirmation,
                    correlationId = otherBinding.correlationId,
                    binding = otherBinding,
                    scope = otherBinding.scope,
                    artifactIdentity = fixture.identity,
                    attemptLease = fixture.lease,
                ) as DestructiveHumanApprovalResult.Failed
                ).reason,
        )
    }

    @Test
    fun `challenge and approval replay fail`() {
        val fixture = liveAuthority()
        val issued = fixture.issueChallenge() as DestructiveChallengeIssueResult.Issued
        val confirmation = fixture.confirm(issued)
        val approved = fixture.redeem(issued, confirmation) as DestructiveHumanApprovalResult.Approved
        assertTrue(fixture.redeem(issued, confirmation) is DestructiveHumanApprovalResult.Failed)
        assertTrue(fixture.confirmResult(issued) is DestructiveHumanConfirmationResult.Confirmed)
        assertTrue(fixture.redeem(issued, confirmation) is DestructiveHumanApprovalResult.Failed)
        assertTrue(fixture.consume(approved.approval) is DestructiveHumanApprovalCheck.Accepted)
        assertTrue(fixture.consume(approved.approval) is DestructiveHumanApprovalCheck.Rejected)
    }

    @Test
    fun `caller constructed challenge and confirmation cannot mint approval`() {
        val fixture = liveAuthority()
        val forged = DestructiveOperatorChallenge.create(ByteArray(32) { 1 })
        val forgedConfirmation = DestructiveHumanConfirmation.create()
        assertTrue(
            fixture.authority.redeem(
                challenge = forged,
                confirmation = forgedConfirmation,
                correlationId = fixture.binding.correlationId,
                binding = fixture.binding,
                scope = fixture.binding.scope,
                artifactIdentity = fixture.identity,
                attemptLease = fixture.lease,
            ) is DestructiveHumanApprovalResult.Failed,
        )
        assertTrue(
            fixture.authority.consume(
                approval = DestructiveHumanApproval.create(),
                expectedCorrelationId = fixture.binding.correlationId,
                expectedBinding = fixture.binding,
                expectedScope = fixture.binding.scope,
                expectedIdentity = fixture.identity,
                expectedLease = fixture.lease,
            ) is DestructiveHumanApprovalCheck.Rejected,
        )
    }

    @Test
    fun `reconstructed authority cannot restore approval after process death`() {
        val first = liveAuthority()
        val issued = first.issueChallenge() as DestructiveChallengeIssueResult.Issued
        val confirmation = first.confirm(issued)
        val approved = first.redeem(issued, confirmation) as DestructiveHumanApprovalResult.Approved
        val reconstructed = liveAuthority()
        assertTrue(reconstructed.consume(approved.approval) is DestructiveHumanApprovalCheck.Rejected)
        assertTrue(reconstructed.redeem(issued, confirmation) is DestructiveHumanApprovalResult.Failed)
    }

    @Test
    fun `reversible Approval cannot satisfy destructive human approval`() {
        assertFalse(Approval::class.java == DestructiveHumanApproval::class.java)
        assertFalse(Approval::class.java == DestructiveHumanConfirmation::class.java)
        assertFalse(
            DestructiveHumanApprovalAuthority::class.java.methods.any { method ->
                method.parameterTypes.any { it == Approval::class.java } ||
                    method.returnType == Approval::class.java
            },
        )
        assertFalse(
            DestructiveHumanConfirmationAuthority::class.java.methods.any { method ->
                method.parameterTypes.any { it == Approval::class.java } ||
                    method.returnType == Approval::class.java
            },
        )
        assertFalse(
            ApprovalAuthority::class.java.methods.any { method ->
                method.returnType == DestructiveHumanApproval::class.java ||
                    method.parameterTypes.any { it == DestructiveHumanApproval::class.java } ||
                    method.returnType == DestructiveHumanConfirmation::class.java ||
                    method.parameterTypes.any { it == DestructiveHumanConfirmation::class.java }
            },
        )
    }

    @Test
    fun `no Boolean approved flag can authorize`() {
        assertFalse(
            DestructiveHumanApprovalAuthority::class.java.declaredMethods.any { method ->
                method.name.contains("approve", ignoreCase = true) &&
                    method.parameterTypes.any { it == java.lang.Boolean.TYPE || it == Boolean::class.java }
            },
        )
        assertFalse(
            DestructiveHumanApprovalAuthority::class.java.declaredMethods.any { method ->
                method.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE))
            },
        )
        assertFalse(
            DestructiveHumanConfirmationAuthority::class.java.declaredMethods.any { method ->
                method.parameterTypes.any { it == java.lang.Boolean.TYPE || it == Boolean::class.java }
            },
        )
    }

    @Test
    fun `negative monotonic delta and stale approval fail closed`() {
        val clock = MutableMonotonicClock(1_000L)
        val fixture = liveAuthority(clock)
        val issued = fixture.issueChallenge() as DestructiveChallengeIssueResult.Issued
        val confirmation = fixture.confirm(issued)
        assertTrue(
            fixture.authority.redeem(
                challenge = issued.challenge,
                confirmation = confirmation,
                correlationId = fixture.binding.correlationId,
                binding = fixture.binding,
                scope = fixture.binding.scope,
                artifactIdentity = fixture.identity,
                attemptLease = fixture.lease,
                nowMonotonicMillis = 500L,
            ) is DestructiveHumanApprovalResult.Failed,
        )
        val second = fixture.issueChallenge() as DestructiveChallengeIssueResult.Issued
        val secondConfirmation = fixture.confirm(second)
        val approved = fixture.redeem(second, secondConfirmation) as DestructiveHumanApprovalResult.Approved
        clock.now = 1_000L + DestructiveHumanApprovalAuthority.MAX_APPROVAL_AGE_MILLIS + 1
        assertEquals(
            "human_approval_stale",
            (fixture.consume(approved.approval) as DestructiveHumanApprovalCheck.Rejected).reason,
        )
    }

    @Test
    fun `wrong artifact identity cannot redeem or consume`() {
        val fixture = liveAuthority()
        val issued = fixture.issueChallenge() as DestructiveChallengeIssueResult.Issued
        val confirmation = fixture.confirm(issued)
        val otherIdentity = requireNotNull(
            DestructiveArtifactIdentity.snapshot(
                certificateSha256 = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
                artifactSha256 = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                packageName = "com.example.devicemanagement",
                adminComponent =
                    "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
                buildPurpose = DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
            ),
        )
        assertEquals(
            "human_approval_artifact_identity_mismatch",
            (
                fixture.authority.redeem(
                    challenge = issued.challenge,
                    confirmation = confirmation,
                    correlationId = fixture.binding.correlationId,
                    binding = fixture.binding,
                    scope = fixture.binding.scope,
                    artifactIdentity = otherIdentity,
                    attemptLease = fixture.lease,
                ) as DestructiveHumanApprovalResult.Failed
                ).reason,
        )
    }

    @Test
    fun `approval types are not persistable and production mint stays unwired`() {
        val types = listOf(
            DestructiveOperatorChallenge::class.java,
            DestructiveChallengeIdentity::class.java,
            DestructiveHumanConfirmation::class.java,
            DestructiveHumanApproval::class.java,
            DestructiveHumanApprovalAuthority::class.java,
            DestructiveHumanConfirmationAuthority::class.java,
        )
        types.forEach { type ->
            assertFalse(Serializable::class.java.isAssignableFrom(type))
            assertFalse(type.interfaces.any { it.name == "android.os.Parcelable" })
        }
        val failed = try {
            ObjectOutputStream(ByteArrayOutputStream()).use {
                it.writeObject(DestructiveHumanApproval.create())
            }
            false
        } catch (_: Exception) {
            true
        }
        assertTrue(failed)
        assertNotEquals("DeviceManagement", UnwiredDestructiveHumanApprovalMint::class.java.simpleName)
        assertNotEquals("DeviceManagement", UnwiredDestructiveHumanConfirmationSource::class.java.simpleName)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED)
        assertTrue(Checkpoint18Decision.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_HUMAN_APPROVAL_REQUIRED)
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        assertTrue(DestructiveHumanApproval::class.java in handoff.parameterTypes)
        assertFalse(DestructiveOperatorChallenge::class.java in handoff.parameterTypes)
        assertFalse(DestructiveHumanConfirmation::class.java in handoff.parameterTypes)
        assertFalse(java.lang.Boolean.TYPE in handoff.parameterTypes)
        assertFalse(Approval::class.java in handoff.parameterTypes)
    }

    @Test
    fun `distinct confirmation authority can redeem a live challenge once`() {
        val fixture = liveAuthority()
        val issued = fixture.issueChallenge() as DestructiveChallengeIssueResult.Issued
        val confirmation = fixture.confirm(issued)
        val approved = fixture.redeem(issued, confirmation) as DestructiveHumanApprovalResult.Approved
        assertTrue(fixture.consume(approved.approval) is DestructiveHumanApprovalCheck.Accepted)
    }

    @Test
    fun `abandoned unused challenge cannot be redeemed`() {
        val fixture = liveAuthority()
        val issued = fixture.issueChallenge() as DestructiveChallengeIssueResult.Issued
        val confirmation = fixture.confirm(issued)
        fixture.authority.abandon(issued.challenge)
        assertEquals(
            "human_approval_challenge_not_issued_or_already_used",
            (
                fixture.redeem(issued, confirmation) as DestructiveHumanApprovalResult.Failed
                ).reason,
        )
    }

    private fun liveAuthority(
        clock: MutableMonotonicClock = MutableMonotonicClock(1_000L),
    ): ApprovalFixture {
        val identity = requireNotNull(
            DestructiveArtifactIdentity.snapshot(
                certificateSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                artifactSha256 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                packageName = "com.example.devicemanagement",
                adminComponent =
                    "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
                buildPurpose = DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION,
            ),
        )
        val binding = verifiedBinding()
        return ApprovalFixture(
            authority = DestructiveHumanApprovalAuthority(clock),
            confirmationAuthority = DestructiveHumanConfirmationAuthority(),
            binding = binding,
            identity = identity,
            lease = DestructiveAttemptLease.create(),
            clock = clock,
        )
    }

    private class ApprovalFixture(
        val authority: DestructiveHumanApprovalAuthority,
        val confirmationAuthority: DestructiveHumanConfirmationAuthority,
        val binding: DestructiveTargetBinding,
        val identity: DestructiveArtifactIdentity,
        val lease: DestructiveAttemptLease,
        val clock: MutableMonotonicClock,
    ) {
        fun issueChallenge(): DestructiveChallengeIssueResult {
            return authority.issueChallenge(
                correlationId = binding.correlationId,
                binding = binding,
                scope = binding.scope,
                artifactIdentity = identity,
                attemptLease = lease,
            )
        }

        fun confirm(issued: DestructiveChallengeIssueResult.Issued): DestructiveHumanConfirmation {
            return (confirmResult(issued) as DestructiveHumanConfirmationResult.Confirmed).confirmation
        }

        fun confirmResult(
            issued: DestructiveChallengeIssueResult.Issued,
        ): DestructiveHumanConfirmationResult {
            return confirmationAuthority.confirm(
                challenge = issued.challenge,
                correlationId = binding.correlationId,
                binding = binding,
                scope = binding.scope,
                artifactIdentity = identity,
                attemptLease = lease,
                nowMonotonicMillis = clock.now,
            )
        }

        fun redeem(
            issued: DestructiveChallengeIssueResult.Issued,
            confirmation: DestructiveHumanConfirmation,
        ): DestructiveHumanApprovalResult {
            return authority.redeem(
                challenge = issued.challenge,
                confirmation = confirmation,
                correlationId = binding.correlationId,
                binding = binding,
                scope = binding.scope,
                artifactIdentity = identity,
                attemptLease = lease,
            )
        }

        fun redeemWithoutConfirmation(
            issued: DestructiveChallengeIssueResult.Issued,
        ): DestructiveHumanApprovalResult {
            return authority.redeem(
                challenge = issued.challenge,
                confirmation = DestructiveHumanConfirmation.create(),
                correlationId = binding.correlationId,
                binding = binding,
                scope = binding.scope,
                artifactIdentity = identity,
                attemptLease = lease,
            )
        }

        fun consume(approval: DestructiveHumanApproval): DestructiveHumanApprovalCheck {
            return authority.consume(
                approval = approval,
                expectedCorrelationId = binding.correlationId,
                expectedBinding = binding,
                expectedScope = binding.scope,
                expectedIdentity = identity,
                expectedLease = lease,
            )
        }
    }
}
