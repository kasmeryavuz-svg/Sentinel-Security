package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class DestructiveArtifactIdentityTest {
    @Test
    fun `wrong certificate digest is rejected`() {
        val authority = authorityFor(validIdentity())
        val observed = requireIdentity(
            certificateSha256 = OTHER_CERT,
            artifactSha256 = ARTIFACT_A,
        )
        val admitted = authority.admit(observed)
        assertTrue(admitted is ArtifactIdentityAdmitResult.Failed)
        assertEquals(
            "certificate_digest_mismatch",
            (admitted as ArtifactIdentityAdmitResult.Failed).reason,
        )
    }

    @Test
    fun `wrong artifact digest is rejected`() {
        val authority = authorityFor(validIdentity())
        val observed = requireIdentity(artifactSha256 = ARTIFACT_B)
        assertEquals(
            "artifact_digest_mismatch",
            (authority.admit(observed) as ArtifactIdentityAdmitResult.Failed).reason,
        )
    }

    @Test
    fun `wrong package is rejected`() {
        val authority = authorityFor(validIdentity())
        val observed = requireIdentity(
            packageName = "com.example.other",
            adminComponent = "com.example.other/com.example.other.Admin",
        )
        assertEquals(
            "package_mismatch",
            (authority.admit(observed) as ArtifactIdentityAdmitResult.Failed).reason,
        )
    }

    @Test
    fun `wrong admin component is rejected`() {
        val authority = authorityFor(validIdentity())
        val observed = requireIdentity(
            adminComponent = "$PACKAGE_NAME/com.example.devicemanagement.other.Admin",
        )
        assertEquals(
            "admin_component_mismatch",
            (authority.admit(observed) as ArtifactIdentityAdmitResult.Failed).reason,
        )
    }

    @Test
    fun `missing and malformed values fail closed`() {
        assertNull(DestructiveArtifactIdentity.snapshot("", ARTIFACT_A, PACKAGE_NAME, ADMIN, PURPOSE))
        assertNull(DestructiveArtifactIdentity.snapshot(CERT_A, "", PACKAGE_NAME, ADMIN, PURPOSE))
        assertNull(DestructiveArtifactIdentity.snapshot(CERT_A, ARTIFACT_A, "", ADMIN, PURPOSE))
        assertNull(DestructiveArtifactIdentity.snapshot(CERT_A, ARTIFACT_A, PACKAGE_NAME, "", PURPOSE))
        assertNull(
            DestructiveArtifactIdentity.snapshot("0".repeat(64), ARTIFACT_A, PACKAGE_NAME, ADMIN, PURPOSE),
        )
        assertNull(
            DestructiveArtifactIdentity.snapshot(CERT_A, "zz".repeat(32), PACKAGE_NAME, ADMIN, PURPOSE),
        )
        assertNull(
            DestructiveArtifactIdentity.snapshot(CERT_A, ARTIFACT_A, "not a package", ADMIN, PURPOSE),
        )
        val authority = authorityFor(validIdentity())
        assertEquals(
            "artifact_identity_missing",
            (authority.admit(null) as ArtifactIdentityAdmitResult.Failed).reason,
        )
    }

    @Test
    fun `mutable digest input cannot change a snapshot`() {
        val certificate = hexToBytes(CERT_A)
        val artifact = hexToBytes(ARTIFACT_A)
        val identity = DestructiveArtifactIdentity.snapshotFromDigests(
            certificateSha256 = certificate,
            artifactSha256 = artifact,
            packageName = PACKAGE_NAME,
            adminComponent = ADMIN,
            buildPurpose = PURPOSE,
        )
        assertNotNull(identity)
        certificate[0] = 0
        artifact[0] = 0
        assertEquals(CERT_A, identity!!.certificateSha256)
        assertEquals(ARTIFACT_A, identity.artifactSha256)
        val mutated = DestructiveArtifactIdentity.snapshotFromDigests(
            certificateSha256 = certificate,
            artifactSha256 = artifact,
            packageName = PACKAGE_NAME,
            adminComponent = ADMIN,
            buildPurpose = PURPOSE,
        )
        assertNotEquals(identity, mutated)
    }

    @Test
    fun `match proof cannot be replayed against a different artifact identity`() {
        val firstIdentity = validIdentity()
        val authority = authorityFor(firstIdentity)
        val admitted = authority.admit(firstIdentity) as ArtifactIdentityAdmitResult.Admitted
        val other = requireIdentity(artifactSha256 = ARTIFACT_B)
        assertTrue(
            authority.consume(admitted.proof, other) is ArtifactIdentityCheck.Rejected,
        )
        val secondAuthority = authorityFor(other)
        val second = secondAuthority.admit(other) as ArtifactIdentityAdmitResult.Admitted
        assertTrue(
            authority.consume(second.proof, other) is ArtifactIdentityCheck.Rejected,
        )
        assertTrue(
            secondAuthority.consume(admitted.proof, firstIdentity) is ArtifactIdentityCheck.Rejected,
        )
    }

    @Test
    fun `ordinary non-destructive builds cannot become future validation eligible`() {
        val ordinary = requireIdentity(buildPurpose = DestructiveArtifactBuildPurpose.ORDINARY_NON_DESTRUCTIVE)
        assertNull(
            DestructiveArtifactIdentityExpectation.TrustedDestructiveArtifactExpectationMint
                .issueFromTrustedValidationSource(
                certificateSha256 = ordinary.certificateSha256,
                artifactSha256 = ordinary.artifactSha256,
                packageName = ordinary.packageName,
                adminComponent = ordinary.adminComponent,
                buildPurpose = ordinary.buildPurpose,
            ),
        )
        val expected = requireExpectation(CERT_A, ARTIFACT_A)
        val authority = DestructiveArtifactIdentityAuthority(expected, MutableMonotonicClock(1_000L))
        assertEquals(
            "observed_build_purpose_not_disposable_validation",
            (authority.admit(ordinary) as ArtifactIdentityAdmitResult.Failed).reason,
        )
    }

    @Test
    fun `caller created identity cannot become a trusted expectation`() {
        val forged = requireIdentity(
            certificateSha256 = OTHER_CERT,
            artifactSha256 = ARTIFACT_B,
        )
        assertFalse(
            DestructiveArtifactIdentityExpectation::class.java.declaredMethods.any { method ->
                method.name == "fromTrustedSnapshot" ||
                    method.parameterTypes.contains(DestructiveArtifactIdentity::class.java)
            },
        )
        assertFalse(
            DestructiveArtifactIdentityExpectation::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.contains(DestructiveArtifactIdentity::class.java)
            },
        )
        assertFalse(
            DestructiveArtifactIdentityExpectation.TrustedDestructiveArtifactExpectationMint::class.java
                .declaredMethods.any { method ->
                    method.parameterTypes.contains(DestructiveArtifactIdentity::class.java)
                },
        )
        assertFalse(
            TrustedDestructiveArtifactValidationSource::class.java.declaredMethods.any { method ->
                method.parameterTypes.contains(DestructiveArtifactIdentity::class.java)
            },
        )
        assertFalse(
            UnwiredDestructiveArtifactIdentitySource::class.java.declaredMethods.any { method ->
                method.parameterTypes.contains(DestructiveArtifactIdentity::class.java)
            },
        )
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
        assertNull(UnwiredDestructiveArtifactIdentitySource.trustedExpectation())
        assertFalse(DestructiveArtifactIdentity::class.java.isAssignableFrom(
            DestructiveArtifactIdentityExpectation::class.java,
        ))
        assertFalse(forged::class.java == DestructiveArtifactIdentityExpectation::class.java)
        assertFalse(Checkpoint17BHardBlock.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED)
        assertFalse(Checkpoint18Decision.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertTrue(Checkpoint18Decision.REAL_CHAIN_ARTIFACT_IDENTITY_REQUIRED)
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        assertTrue(DestructiveArtifactIdentityMatchProof::class.java in handoff.parameterTypes)
        assertFalse(String::class.java in handoff.parameterTypes)
    }

    @Test
    fun `matching disposable identity can be admitted once and is not authorization`() {
        val identity = validIdentity()
        val clock = MutableMonotonicClock(1_000L)
        val authority = authorityFor(identity, clock)
        val admitted = authority.admit(identity) as ArtifactIdentityAdmitResult.Admitted
        assertTrue(authority.consume(admitted.proof, identity) is ArtifactIdentityCheck.Accepted)
        assertTrue(authority.consume(admitted.proof, identity) is ArtifactIdentityCheck.Rejected)
        assertFalse(identity::class.java.methods.any { it.name == "authorize" || it.name == "arm" })
        assertTrue(UnwiredDestructiveArtifactIdentitySource.trustedExpectation() == null)
        assertTrue(TrustedDestructiveArtifactValidationSource.trustedExpectation() == null)
    }

    @Test
    fun `caller cannot select the trusted digest at admit time`() {
        val methods = DestructiveArtifactIdentityAuthority::class.java.declaredMethods
            .filter { it.name == "admit" }
        assertEquals(1, methods.size)
        assertEquals(1, methods.single().parameterCount)
        assertEquals(DestructiveArtifactIdentity::class.java, methods.single().parameterTypes.single())
    }

    @Test
    fun `identity and match proof are process-local and not persistable authority`() {
        val types = listOf(
            DestructiveArtifactIdentity::class.java,
            DestructiveArtifactIdentityExpectation::class.java,
            DestructiveArtifactIdentityMatchProof::class.java,
            DestructiveArtifactIdentityAuthority::class.java,
            DestructiveArtifactIdentityExpectation.TrustedDestructiveArtifactExpectationMint::class.java,
            TrustedDestructiveArtifactValidationSource::class.java,
        )
        types.forEach { type ->
            assertFalse(Serializable::class.java.isAssignableFrom(type))
            assertFalse(type.interfaces.any { it.name == "android.os.Parcelable" })
        }
        val failed = try {
            ObjectOutputStream(ByteArrayOutputStream()).use {
                it.writeObject(DestructiveArtifactIdentityMatchProof.create())
            }
            false
        } catch (_: Exception) {
            true
        }
        assertTrue(failed)
    }

    @Test
    fun `negative monotonic delta and stale match fail closed`() {
        val identity = validIdentity()
        val clock = MutableMonotonicClock(5_000L)
        val authority = authorityFor(identity, clock)
        val admitted = authority.admit(identity) as ArtifactIdentityAdmitResult.Admitted
        assertTrue(
            authority.consume(admitted.proof, identity, nowMonotonicMillis = 4_000L)
                is ArtifactIdentityCheck.Rejected,
        )
        val second = authority.admit(identity) as ArtifactIdentityAdmitResult.Admitted
        clock.now = 5_000L + DestructiveArtifactIdentityAuthority.MAX_MATCH_AGE_MILLIS + 1
        assertEquals(
            "artifact_identity_stale",
            (authority.consume(second.proof, identity) as ArtifactIdentityCheck.Rejected).reason,
        )
    }

    private fun authorityFor(
        identity: DestructiveArtifactIdentity,
        clock: MutableMonotonicClock = MutableMonotonicClock(1_000L),
    ): DestructiveArtifactIdentityAuthority {
        val expected = requireExpectation(identity.certificateSha256, identity.artifactSha256)
        return DestructiveArtifactIdentityAuthority(expected, clock)
    }

    private fun validIdentity(): DestructiveArtifactIdentity = requireIdentity()

    private fun requireExpectation(
        certificateSha256: String,
        artifactSha256: String,
    ): DestructiveArtifactIdentityExpectation {
        return requireNotNull(
            DestructiveArtifactIdentityExpectation.TrustedDestructiveArtifactExpectationMint
                .issueFromTrustedValidationSource(
                certificateSha256 = certificateSha256,
                artifactSha256 = artifactSha256,
                packageName = PACKAGE_NAME,
                adminComponent = ADMIN,
                buildPurpose = PURPOSE,
            ),
        )
    }

    private fun requireIdentity(
        certificateSha256: String = CERT_A,
        artifactSha256: String = ARTIFACT_A,
        packageName: String = PACKAGE_NAME,
        adminComponent: String = ADMIN,
        buildPurpose: DestructiveArtifactBuildPurpose = PURPOSE,
    ): DestructiveArtifactIdentity {
        return requireNotNull(
            DestructiveArtifactIdentity.snapshot(
                certificateSha256 = certificateSha256,
                artifactSha256 = artifactSha256,
                packageName = packageName,
                adminComponent = adminComponent,
                buildPurpose = buildPurpose,
            ),
        )
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private companion object {
        const val CERT_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val OTHER_CERT = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        const val ARTIFACT_A = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val ARTIFACT_B = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        const val PACKAGE_NAME = "com.example.devicemanagement"
        const val ADMIN =
            "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver"
        val PURPOSE = DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION
    }
}
