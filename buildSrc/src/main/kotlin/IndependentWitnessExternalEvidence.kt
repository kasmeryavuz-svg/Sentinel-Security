import java.time.DateTimeException
import java.time.Instant

/**
 * Versioned external independence-evidence schema.
 *
 * A well-formed evidence record is not independence, not enrollment, and
 * not runtime authorization. The closed `evidence_type` enumeration is a
 * classification string only; software cannot prove social or
 * organizational independence from the enum value itself.
 *
 * Timestamp parsing is separate from freshness evaluation. Freshness is
 * decided against an injected evaluation instant, never against a hidden
 * local wall clock.
 */
object IndependentWitnessExternalEvidence {
    const val CHECKPOINT = "19V"
    const val EVIDENCE_VERSION = "1"

    val FIELD_ORDER = listOf(
        "checkpoint",
        "evidence_version",
        "evidence_identifier",
        "witness_identifier",
        "evidence_type",
        "evidence_issuer_identifier",
        "evidence_timestamp_utc",
        "evidence_expiry_utc",
        "repository_revision",
        "witness_verification_key_sha256",
        "evidence_digest_sha256",
        "review_required",
    )

    enum class EvidenceType {
        IDENTITY_AND_ROLE_ATTESTATION,
        ORGANIZATIONAL_SEPARATION_ATTESTATION,
        EXTERNAL_SECURITY_REVIEW_ATTESTATION,
    }

    data class Record(
        val evidenceIdentifier: String,
        val witnessIdentifier: String,
        val evidenceType: EvidenceType,
        val evidenceIssuerIdentifier: String,
        val evidenceTimestampUtc: String,
        val evidenceExpiryUtc: String,
        val repositoryRevision: String,
        val witnessVerificationKeySha256: String,
        val evidenceDigestSha256: String,
        val reviewRequired: Boolean,
    )

    fun render(record: Record): String {
        val normalized = normalize(record)
        return FIELD_ORDER.joinToString("") { key ->
            "$key=${fieldValue(normalized, key)}\n"
        }
    }

    fun parse(text: String): Record {
        val values = FailClosedStatusLines.requireExactKeys(
            FailClosedStatusLines.parseUnique(text),
            FIELD_ORDER.toSet(),
        )
        FIELD_ORDER.forEach { key ->
            check(values.getValue(key).isNotEmpty()) { "$key must not be blank" }
        }
        check(values.getValue("checkpoint") == CHECKPOINT) {
            "evidence checkpoint must be $CHECKPOINT"
        }
        check(values.getValue("evidence_version") == EVIDENCE_VERSION) {
            "unsupported evidence version"
        }
        val evidenceIdentifier = values.getValue("evidence_identifier")
        val witnessIdentifier = values.getValue("witness_identifier")
        val issuerIdentifier = values.getValue("evidence_issuer_identifier")
        requireNonReservedIdentifier(evidenceIdentifier, "evidence_identifier")
        requireNonReservedIdentifier(witnessIdentifier, "witness_identifier")
        requireNonReservedIdentifier(issuerIdentifier, "evidence_issuer_identifier")
        check(
            !IndependentWitnessAuthorityEnrollment.identifiersMatch(
                issuerIdentifier,
                witnessIdentifier,
            ),
        ) {
            "evidence_issuer_identifier must differ from witness_identifier"
        }
        val evidenceType = parseEvidenceType(values.getValue("evidence_type"))
        val issued = values.getValue("evidence_timestamp_utc")
        val expiry = values.getValue("evidence_expiry_utc")
        val issuedInstant = parseInstantUtc(issued, "evidence_timestamp_utc")
        val expiryInstant = parseInstantUtc(expiry, "evidence_expiry_utc")
        check(expiryInstant.isAfter(issuedInstant)) {
            "evidence_expiry_utc must be after evidence_timestamp_utc"
        }
        val revision = values.getValue("repository_revision").trim().lowercase()
        check(IndependentWitnessAuthorityEnrollment.isValidGitRevision(revision)) {
            "repository_revision must be an exact 40-hex commit"
        }
        val keyFingerprint = IndependentWitnessAuthorityEnrollment.requireSha256Hex(
            values.getValue("witness_verification_key_sha256"),
            "witness_verification_key_sha256",
        )
        val evidenceDigest = IndependentWitnessAuthorityEnrollment.requireSha256Hex(
            values.getValue("evidence_digest_sha256"),
            "evidence_digest_sha256",
        )
        check(keyFingerprint != evidenceDigest) {
            "evidence_digest_sha256 must not reuse the verification-key fingerprint"
        }
        val reviewRequired = parseBooleanFlag(
            values.getValue("review_required"),
            "review_required",
        )
        return normalize(
            Record(
                evidenceIdentifier = evidenceIdentifier,
                witnessIdentifier = witnessIdentifier,
                evidenceType = evidenceType,
                evidenceIssuerIdentifier = issuerIdentifier,
                evidenceTimestampUtc = issued,
                evidenceExpiryUtc = expiry,
                repositoryRevision = revision,
                witnessVerificationKeySha256 = keyFingerprint,
                evidenceDigestSha256 = evidenceDigest,
                reviewRequired = reviewRequired,
            ),
        )
    }

    fun parseEvidenceType(raw: String): EvidenceType {
        return EvidenceType.values().firstOrNull { it.name == raw }
            ?: error("unsupported evidence type")
    }

    fun parseInstantUtc(value: String, fieldName: String = "timestamp"): Instant {
        check(IndependentWitnessAuthorityEnrollment.isValidTimestampUtc(value)) {
            "$fieldName must be YYYY-MM-DDTHH:MM:SSZ"
        }
        return try {
            Instant.parse(value)
        } catch (ex: DateTimeException) {
            error("$fieldName is not a valid UTC instant")
        }
    }

    fun isFreshAt(issuedUtc: String, expiryUtc: String, evaluationInstant: Instant): Boolean {
        val issued = parseInstantUtc(issuedUtc, "evidence_timestamp_utc")
        val expiry = parseInstantUtc(expiryUtc, "evidence_expiry_utc")
        return !evaluationInstant.isBefore(issued) && evaluationInstant.isBefore(expiry)
    }

    fun isStructurallyValid(record: Record): Boolean {
        return try {
            parse(render(record))
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun evidenceTypeImpliesIndependence(type: EvidenceType): Boolean {
        return false && type.name.isNotEmpty()
    }

    fun wellFormedEvidenceImpliesIndependence(wellFormed: Boolean): Boolean {
        return false && wellFormed
    }

    fun digestMatchImpliesIndependence(digestMatches: Boolean): Boolean {
        return false && digestMatches
    }

    private fun requireNonReservedIdentifier(value: String, fieldName: String) {
        check(IndependentWitnessAuthorityEnrollment.isWellFormedIdentifier(value)) {
            "$fieldName is malformed"
        }
        check(!IndependentWitnessAuthorityEnrollment.isReservedWitnessIdentifier(value)) {
            "$fieldName is reserved and cannot support independent-witness evidence"
        }
    }

    private fun parseBooleanFlag(raw: String, fieldName: String): Boolean {
        return when (raw) {
            "true" -> true
            "false" -> false
            else -> error("$fieldName must be true or false")
        }
    }

    private fun normalize(record: Record): Record {
        return record.copy(
            repositoryRevision = record.repositoryRevision.trim().lowercase(),
            witnessVerificationKeySha256 =
                IndependentWitnessAuthorityEnrollment.requireSha256Hex(
                    record.witnessVerificationKeySha256,
                    "witness_verification_key_sha256",
                ),
            evidenceDigestSha256 =
                IndependentWitnessAuthorityEnrollment.requireSha256Hex(
                    record.evidenceDigestSha256,
                    "evidence_digest_sha256",
                ),
        )
    }

    private fun fieldValue(record: Record, key: String): String {
        return when (key) {
            "checkpoint" -> CHECKPOINT
            "evidence_version" -> EVIDENCE_VERSION
            "evidence_identifier" -> record.evidenceIdentifier
            "witness_identifier" -> record.witnessIdentifier
            "evidence_type" -> record.evidenceType.name
            "evidence_issuer_identifier" -> record.evidenceIssuerIdentifier
            "evidence_timestamp_utc" -> record.evidenceTimestampUtc
            "evidence_expiry_utc" -> record.evidenceExpiryUtc
            "repository_revision" -> record.repositoryRevision
            "witness_verification_key_sha256" -> record.witnessVerificationKeySha256
            "evidence_digest_sha256" -> record.evidenceDigestSha256
            "review_required" -> record.reviewRequired.toString()
            else -> error("unknown evidence field: $key")
        }
    }
}
