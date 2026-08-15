package com.example.devicemanagement.destructive

import com.example.devicemanagement.integration.MonotonicTimeSource
import java.util.IdentityHashMap

/**
 * Build-purpose classification for artifact identity. Ordinary debug and
 * release artifacts are [ORDINARY_NON_DESTRUCTIVE] and can never become
 * eligible for a future destructive validation. [DISPOSABLE_DEVICE_VALIDATION]
 * exists as a classification only; this checkpoint does not create such a
 * build and does not record a real artifact digest.
 */
internal enum class DestructiveArtifactBuildPurpose {
    ORDINARY_NON_DESTRUCTIVE,
    DISPOSABLE_DEVICE_VALIDATION,
}

/**
 * Immutable artifact identity snapshot. Evidence and admission data only.
 * Never authorization, arming, a capability, a permit, or a resume token.
 *
 * This type is observed identity only. Expected digests are held by an
 * opaque [DestructiveArtifactIdentityExpectation] minted only by
 * [TrustedDestructiveArtifactExpectationMint]. An observed snapshot
 * cannot become a trusted expectation. There is no debug-key fallback.
 */
internal class DestructiveArtifactIdentity private constructor(
    val certificateSha256: String,
    val artifactSha256: String,
    val packageName: String,
    val adminComponent: String,
    val buildPurpose: DestructiveArtifactBuildPurpose,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DestructiveArtifactIdentity) return false
        return certificateSha256 == other.certificateSha256 &&
            artifactSha256 == other.artifactSha256 &&
            packageName == other.packageName &&
            adminComponent == other.adminComponent &&
            buildPurpose == other.buildPurpose
    }

    override fun hashCode(): Int {
        var result = certificateSha256.hashCode()
        result = 31 * result + artifactSha256.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + adminComponent.hashCode()
        result = 31 * result + buildPurpose.hashCode()
        return result
    }

    companion object {
        const val SHA256_HEX_CHARS = 64
        private val HEX = Regex("^[0-9a-f]{$SHA256_HEX_CHARS}$")
        private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        private val ZERO_DIGEST = "0".repeat(SHA256_HEX_CHARS)

        fun snapshot(
            certificateSha256: String,
            artifactSha256: String,
            packageName: String,
            adminComponent: String,
            buildPurpose: DestructiveArtifactBuildPurpose,
        ): DestructiveArtifactIdentity? {
            val certificate = normalizeDigest(certificateSha256) ?: return null
            val artifact = normalizeDigest(artifactSha256) ?: return null
            val pkg = packageName.trim()
            val admin = adminComponent.trim()
            if (!PACKAGE.matches(pkg)) {
                return null
            }
            if (!isAdminComponent(admin, pkg)) {
                return null
            }
            return DestructiveArtifactIdentity(
                certificateSha256 = certificate,
                artifactSha256 = artifact,
                packageName = pkg,
                adminComponent = admin,
                buildPurpose = buildPurpose,
            )
        }

        fun snapshotFromDigests(
            certificateSha256: ByteArray,
            artifactSha256: ByteArray,
            packageName: String,
            adminComponent: String,
            buildPurpose: DestructiveArtifactBuildPurpose,
        ): DestructiveArtifactIdentity? {
            if (certificateSha256.size != 32 || artifactSha256.size != 32) {
                return null
            }
            return snapshot(
                certificateSha256 = toHex(certificateSha256.copyOf()),
                artifactSha256 = toHex(artifactSha256.copyOf()),
                packageName = packageName,
                adminComponent = adminComponent,
                buildPurpose = buildPurpose,
            )
        }

        internal fun normalizeDigest(raw: String): String? {
            val hex = raw.trim().lowercase()
            if (!HEX.matches(hex) || hex == ZERO_DIGEST) {
                return null
            }
            return hex
        }

        internal fun isValidPackage(packageName: String): Boolean = PACKAGE.matches(packageName)

        internal fun isAdminComponent(admin: String, packageName: String): Boolean {
            val slash = admin.indexOf('/')
            if (slash <= 0 || slash == admin.lastIndex) {
                return false
            }
            val adminPackage = admin.substring(0, slash)
            val adminClass = admin.substring(slash + 1)
            return adminPackage == packageName && adminClass.isNotBlank() && !adminClass.contains(' ')
        }

        private fun toHex(bytes: ByteArray): String {
            return bytes.joinToString(separator = "") { byte ->
                val value = byte.toInt() and 0xff
                value.toString(16).padStart(2, '0')
            }
        }
    }
}

/**
 * Opaque trusted expected identity. This is not an observed
 * [DestructiveArtifactIdentity] and cannot be constructed from one.
 * The only mint path is
 * [TrustedDestructiveArtifactExpectationMint.issueFromTrustedValidationSource].
 */
internal class DestructiveArtifactIdentityExpectation private constructor(
    val certificateSha256: String,
    val artifactSha256: String,
    val packageName: String,
    val adminComponent: String,
    val buildPurpose: DestructiveArtifactBuildPurpose,
) {
    /**
     * Sole mint path for a trusted artifact expectation. Dedicated Kotlin
     * object with one JVM owner
     * (`DestructiveArtifactIdentityExpectation$TrustedDestructiveArtifactExpectationMint`).
     * Not a companion. Does not accept a caller-created or observed
     * [DestructiveArtifactIdentity]. Production bytecode allows this call
     * only from [TrustedDestructiveArtifactValidationSource.trustedExpectation].
     */
    object TrustedDestructiveArtifactExpectationMint {
        fun issueFromTrustedValidationSource(
            certificateSha256: String,
            artifactSha256: String,
            packageName: String,
            adminComponent: String,
            buildPurpose: DestructiveArtifactBuildPurpose,
        ): DestructiveArtifactIdentityExpectation? {
            if (buildPurpose != DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION) {
                return null
            }
            val certificate = DestructiveArtifactIdentity.normalizeDigest(certificateSha256) ?: return null
            val artifact = DestructiveArtifactIdentity.normalizeDigest(artifactSha256) ?: return null
            val pkg = packageName.trim()
            val admin = adminComponent.trim()
            if (!DestructiveArtifactIdentity.isValidPackage(pkg)) {
                return null
            }
            if (!DestructiveArtifactIdentity.isAdminComponent(admin, pkg)) {
                return null
            }
            return DestructiveArtifactIdentityExpectation(
                certificateSha256 = certificate,
                artifactSha256 = artifact,
                packageName = pkg,
                adminComponent = admin,
                buildPurpose = buildPurpose,
            )
        }
    }
}

/**
 * Dedicated trusted artifact-validation source. No disposable-device
 * artifact hash is recorded, so this source cannot mint an expectation.
 * Generic sensitive-actions code cannot promote an observed identity here.
 */
internal object TrustedDestructiveArtifactValidationSource {
    fun trustedExpectation(): DestructiveArtifactIdentityExpectation? = null
}

/**
 * Process-local proof that an observed identity matched the trusted
 * expectation. Not authorization. Dies with the process.
 */
internal class DestructiveArtifactIdentityMatchProof private constructor() {
    companion object {
        fun create(): DestructiveArtifactIdentityMatchProof = DestructiveArtifactIdentityMatchProof()
    }
}

internal sealed interface ArtifactIdentityAdmitResult {
    data class Admitted(val proof: DestructiveArtifactIdentityMatchProof) : ArtifactIdentityAdmitResult

    data class Failed(val reason: String) : ArtifactIdentityAdmitResult
}

internal sealed interface ArtifactIdentityCheck {
    data object Accepted : ArtifactIdentityCheck

    data class Rejected(val reason: String) : ArtifactIdentityCheck
}

/**
 * Admits an observed identity against a trusted expectation. Ordinary
 * non-destructive builds fail closed. Missing or mismatched fields fail
 * closed. There is no debug-key fallback and no caller-selected digest.
 */
internal class DestructiveArtifactIdentityAuthority(
    private val expected: DestructiveArtifactIdentityExpectation,
    private val monotonicTimeSource: MonotonicTimeSource,
    private val maxAgeMillis: Long = MAX_MATCH_AGE_MILLIS,
) {
    private val issued = IdentityHashMap<DestructiveArtifactIdentityMatchProof, MatchRecord>()

    @Synchronized
    fun admit(observed: DestructiveArtifactIdentity?): ArtifactIdentityAdmitResult {
        if (observed == null) {
            return ArtifactIdentityAdmitResult.Failed("artifact_identity_missing")
        }
        if (expected.buildPurpose !=
            DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION
        ) {
            return ArtifactIdentityAdmitResult.Failed("expected_build_purpose_not_disposable_validation")
        }
        if (observed.buildPurpose != DestructiveArtifactBuildPurpose.DISPOSABLE_DEVICE_VALIDATION) {
            return ArtifactIdentityAdmitResult.Failed("observed_build_purpose_not_disposable_validation")
        }
        mismatchReason(observed)?.let { reason ->
            return ArtifactIdentityAdmitResult.Failed(reason)
        }
        val proof = DestructiveArtifactIdentityMatchProof.create()
        issued[proof] = MatchRecord(
            identity = observed,
            issuedAtMonotonicMillis = monotonicTimeSource.nowMillis(),
        )
        return ArtifactIdentityAdmitResult.Admitted(proof)
    }

    @Synchronized
    fun consume(
        proof: DestructiveArtifactIdentityMatchProof,
        expectedIdentity: DestructiveArtifactIdentity,
        nowMonotonicMillis: Long = monotonicTimeSource.nowMillis(),
    ): ArtifactIdentityCheck {
        val record = issued.remove(proof)
            ?: return ArtifactIdentityCheck.Rejected("artifact_identity_not_admitted_or_already_consumed")
        if (record.identity != expectedIdentity) {
            return ArtifactIdentityCheck.Rejected("artifact_identity_mismatch")
        }
        val age = nowMonotonicMillis - record.issuedAtMonotonicMillis
        if (age < 0L) {
            return ArtifactIdentityCheck.Rejected("artifact_identity_negative_monotonic_delta")
        }
        if (age > maxAgeMillis) {
            return ArtifactIdentityCheck.Rejected("artifact_identity_stale")
        }
        return ArtifactIdentityCheck.Accepted
    }

    private fun mismatchReason(observed: DestructiveArtifactIdentity): String? {
        val trusted = expected
        if (observed.certificateSha256 != trusted.certificateSha256) {
            return "certificate_digest_mismatch"
        }
        if (observed.artifactSha256 != trusted.artifactSha256) {
            return "artifact_digest_mismatch"
        }
        if (observed.packageName != trusted.packageName) {
            return "package_mismatch"
        }
        if (observed.adminComponent != trusted.adminComponent) {
            return "admin_component_mismatch"
        }
        return null
    }

    private data class MatchRecord(
        val identity: DestructiveArtifactIdentity,
        val issuedAtMonotonicMillis: Long,
    )

    internal companion object {
        const val MAX_MATCH_AGE_MILLIS = 5_000L
    }
}

/**
 * Production expected-identity source. Not invoked by DeviceManagement.
 * Delegates to the dedicated validation source, which remains null because
 * no disposable-device artifact digest is recorded.
 */
internal object UnwiredDestructiveArtifactIdentitySource {
    fun trustedExpectation(): DestructiveArtifactIdentityExpectation? {
        return TrustedDestructiveArtifactValidationSource.trustedExpectation()
    }
}
