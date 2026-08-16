import java.io.File
import java.security.MessageDigest

/**
 * Versioned witness-authority enrollment record.
 *
 * This schema binds cryptographic identity claims to a named witness.
 * A well-formed record is not independence, not approval, and not
 * enrollment. Software cannot prove social or organizational
 * independence from a self-asserted classification string.
 */
object IndependentWitnessAuthorityEnrollment {
    const val CHECKPOINT = "19U"
    const val ENROLLMENT_VERSION = "1"
    const val STATUS_NOT_ENROLLED = "NOT_ENROLLED"
    const val WITNESS_ROLE = "INDEPENDENT_WITNESS"

    val FIELD_ORDER = listOf(
        "checkpoint",
        "enrollment_version",
        "witness_identifier",
        "witness_display_name",
        "witness_verification_key_sha256",
        "witness_role",
        "independence_basis",
        "enrollment_timestamp_utc",
        "enrollment_repository_revision",
        "operator_identifier",
        "review_identifier",
    )

    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val GIT_REVISION = Regex("^[0-9a-f]{40}$")
    private val TIMESTAMP_UTC =
        Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")
    private val IDENTIFIER = Regex("^[A-Za-z0-9._:@/-]{1,128}$")
    private val DISPLAY_NAME = Regex("^[A-Za-z0-9 .,'-]{1,128}$")
    private val PRIVATE_KEY_EXTENSIONS = setOf("pk8", "p12", "pfx", "jks", "keystore")

    val RESERVED_WITNESS_IDENTIFIERS = setOf(
        "ci",
        "github",
        "github-actions",
        "github_actions",
        "actions",
        "cursor",
        "operator",
        "local-operator",
        "current-operator",
        "repository-owner",
        "19s-receipt",
        "19t-report",
        "signed-validation-candidate-receipt",
        "independent-witness-verification",
    )

    enum class IndependenceBasis {
        SEPARATE_NATURAL_PERSON,
        SEPARATE_ORGANIZATION,
        EXTERNAL_SECURITY_REVIEWER,
    }

    data class Record(
        val witnessIdentifier: String,
        val witnessDisplayName: String,
        val witnessVerificationKeySha256: String,
        val witnessRole: String,
        val independenceBasis: IndependenceBasis,
        val enrollmentTimestampUtc: String,
        val enrollmentRepositoryRevision: String,
        val operatorIdentifier: String,
        val reviewIdentifier: String,
    )

    data class ExternalEvidence(
        val independenceEvidencePresent: Boolean,
        val independentReviewPresent: Boolean,
        val enrollmentRepositoryBindingValid: Boolean,
    ) {
        companion object {
            fun none(): ExternalEvidence {
                return ExternalEvidence(
                    independenceEvidencePresent = false,
                    independentReviewPresent = false,
                    enrollmentRepositoryBindingValid = false,
                )
            }
        }
    }

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
            "enrollment checkpoint must be $CHECKPOINT"
        }
        check(values.getValue("enrollment_version") == ENROLLMENT_VERSION) {
            "unsupported enrollment version"
        }
        val witnessIdentifier = values.getValue("witness_identifier")
        val operatorIdentifier = values.getValue("operator_identifier")
        val reviewIdentifier = values.getValue("review_identifier")
        check(IDENTIFIER.matches(witnessIdentifier)) {
            "witness_identifier is malformed"
        }
        check(IDENTIFIER.matches(operatorIdentifier)) {
            "operator_identifier is malformed"
        }
        check(IDENTIFIER.matches(reviewIdentifier)) {
            "review_identifier is malformed"
        }
        check(!isReservedWitnessIdentifier(witnessIdentifier)) {
            "witness_identifier is reserved and cannot be an independent witness"
        }
        check(!sameIdentifier(operatorIdentifier, witnessIdentifier)) {
            "operator_identifier must differ from witness_identifier"
        }
        check(!sameIdentifier(reviewIdentifier, witnessIdentifier)) {
            "review_identifier must differ from witness_identifier"
        }
        check(!sameIdentifier(reviewIdentifier, operatorIdentifier)) {
            "review_identifier must differ from operator_identifier"
        }
        val displayName = values.getValue("witness_display_name")
        check(DISPLAY_NAME.matches(displayName)) {
            "witness_display_name is malformed"
        }
        val fingerprint = normalizeSha256(values.getValue("witness_verification_key_sha256"))
        check(values.getValue("witness_role") == WITNESS_ROLE) {
            "unsupported witness_role"
        }
        val basis = parseIndependenceBasis(values.getValue("independence_basis"))
        val timestamp = values.getValue("enrollment_timestamp_utc")
        check(TIMESTAMP_UTC.matches(timestamp)) {
            "enrollment_timestamp_utc must be YYYY-MM-DDTHH:MM:SSZ"
        }
        val revision = values.getValue("enrollment_repository_revision").trim().lowercase()
        check(GIT_REVISION.matches(revision)) {
            "enrollment_repository_revision must be an exact 40-hex commit"
        }
        return normalize(
            Record(
                witnessIdentifier = witnessIdentifier,
                witnessDisplayName = displayName,
                witnessVerificationKeySha256 = fingerprint,
                witnessRole = WITNESS_ROLE,
                independenceBasis = basis,
                enrollmentTimestampUtc = timestamp,
                enrollmentRepositoryRevision = revision,
                operatorIdentifier = operatorIdentifier,
                reviewIdentifier = reviewIdentifier,
            ),
        )
    }

    fun parseIndependenceBasis(raw: String): IndependenceBasis {
        return IndependenceBasis.values().firstOrNull { it.name == raw }
            ?: error("unsupported independence_basis")
    }

    fun isReservedWitnessIdentifier(identifier: String): Boolean {
        return identifier.trim().lowercase() in RESERVED_WITNESS_IDENTIFIERS
    }

    fun isValidFingerprint(value: String): Boolean {
        return SHA256.matches(value.trim().lowercase())
    }

    fun verificationKeySha256(file: File): String {
        check(!file.name.contains("private", ignoreCase = true)) {
            "witness enrollment must not read a private key"
        }
        check(file.extension.lowercase() !in PRIVATE_KEY_EXTENSIONS) {
            "witness enrollment must not read a private-key container"
        }
        val bytes = IndependentWitnessStatement.readStableBytes(
            file,
            "witness verification public key",
        )
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    fun independenceFromSignatureValidity(signatureVerified: Boolean): Boolean {
        return false && signatureVerified
    }

    fun independenceFromKeyFingerprint(fingerprint: String): Boolean {
        return false && isValidFingerprint(fingerprint)
    }

    private fun normalize(record: Record): Record {
        return record.copy(
            witnessVerificationKeySha256 = normalizeSha256(
                record.witnessVerificationKeySha256,
            ),
            enrollmentRepositoryRevision =
                record.enrollmentRepositoryRevision.trim().lowercase(),
        )
    }

    private fun fieldValue(record: Record, key: String): String {
        return when (key) {
            "checkpoint" -> CHECKPOINT
            "enrollment_version" -> ENROLLMENT_VERSION
            "witness_identifier" -> record.witnessIdentifier
            "witness_display_name" -> record.witnessDisplayName
            "witness_verification_key_sha256" -> record.witnessVerificationKeySha256
            "witness_role" -> record.witnessRole
            "independence_basis" -> record.independenceBasis.name
            "enrollment_timestamp_utc" -> record.enrollmentTimestampUtc
            "enrollment_repository_revision" -> record.enrollmentRepositoryRevision
            "operator_identifier" -> record.operatorIdentifier
            "review_identifier" -> record.reviewIdentifier
            else -> error("unknown enrollment field: $key")
        }
    }

    private fun normalizeSha256(value: String): String {
        val normalized = value.trim().lowercase()
        check(SHA256.matches(normalized)) {
            "witness_verification_key_sha256 is not a valid SHA-256 value"
        }
        return normalized
    }

    private fun sameIdentifier(left: String, right: String): Boolean {
        return left.trim().equals(right.trim(), ignoreCase = true)
    }
}
