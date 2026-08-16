/**
 * Versioned independent review of external witness evidence.
 *
 * `APPROVE_EVIDENCE_MECHANICS` means only that the evidence record is
 * mechanically well-formed and bound. It is not witness enrollment, not
 * independent-witness approval, and not runtime authorization.
 */
object IndependentWitnessEnrollmentReview {
    const val CHECKPOINT = "19V"
    const val REVIEW_VERSION = "1"

    val FIELD_ORDER = listOf(
        "checkpoint",
        "review_version",
        "review_identifier",
        "evidence_identifier",
        "witness_identifier",
        "reviewer_identifier",
        "operator_identifier",
        "review_timestamp_utc",
        "repository_revision",
        "decision",
    )

    enum class Decision {
        APPROVE_EVIDENCE_MECHANICS,
        REJECT,
    }

    data class Record(
        val reviewIdentifier: String,
        val evidenceIdentifier: String,
        val witnessIdentifier: String,
        val reviewerIdentifier: String,
        val operatorIdentifier: String,
        val reviewTimestampUtc: String,
        val repositoryRevision: String,
        val decision: Decision,
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
            "review checkpoint must be $CHECKPOINT"
        }
        check(values.getValue("review_version") == REVIEW_VERSION) {
            "unsupported review version"
        }
        val reviewIdentifier = values.getValue("review_identifier")
        val evidenceIdentifier = values.getValue("evidence_identifier")
        val witnessIdentifier = values.getValue("witness_identifier")
        val reviewerIdentifier = values.getValue("reviewer_identifier")
        val operatorIdentifier = values.getValue("operator_identifier")
        requireNonReservedIdentifier(reviewIdentifier, "review_identifier")
        requireNonReservedIdentifier(evidenceIdentifier, "evidence_identifier")
        requireNonReservedIdentifier(witnessIdentifier, "witness_identifier")
        requireNonReservedIdentifier(reviewerIdentifier, "reviewer_identifier")
        check(
            IndependentWitnessAuthorityEnrollment.isWellFormedIdentifier(operatorIdentifier),
        ) {
            "operator_identifier is malformed"
        }
        check(
            !IndependentWitnessAuthorityEnrollment.identifiersMatch(
                reviewerIdentifier,
                operatorIdentifier,
            ),
        ) {
            "reviewer_identifier must differ from operator_identifier"
        }
        check(
            !IndependentWitnessAuthorityEnrollment.identifiersMatch(
                reviewerIdentifier,
                witnessIdentifier,
            ),
        ) {
            "reviewer_identifier must differ from witness_identifier"
        }
        check(
            !IndependentWitnessAuthorityEnrollment.identifiersMatch(
                operatorIdentifier,
                witnessIdentifier,
            ),
        ) {
            "operator_identifier must differ from witness_identifier"
        }
        check(
            !IndependentWitnessAuthorityEnrollment.identifiersMatch(
                reviewIdentifier,
                reviewerIdentifier,
            ),
        ) {
            "review_identifier must differ from reviewer_identifier"
        }
        check(
            !IndependentWitnessAuthorityEnrollment.identifiersMatch(
                reviewIdentifier,
                operatorIdentifier,
            ),
        ) {
            "review_identifier must differ from operator_identifier"
        }
        check(
            !IndependentWitnessAuthorityEnrollment.identifiersMatch(
                reviewIdentifier,
                witnessIdentifier,
            ),
        ) {
            "review_identifier must differ from witness_identifier"
        }
        IndependentWitnessExternalEvidence.parseInstantUtc(
            values.getValue("review_timestamp_utc"),
            "review_timestamp_utc",
        )
        val revision = values.getValue("repository_revision").trim().lowercase()
        check(IndependentWitnessAuthorityEnrollment.isValidGitRevision(revision)) {
            "repository_revision must be an exact 40-hex commit"
        }
        val decision = parseDecision(values.getValue("decision"))
        return normalize(
            Record(
                reviewIdentifier = reviewIdentifier,
                evidenceIdentifier = evidenceIdentifier,
                witnessIdentifier = witnessIdentifier,
                reviewerIdentifier = reviewerIdentifier,
                operatorIdentifier = operatorIdentifier,
                reviewTimestampUtc = values.getValue("review_timestamp_utc"),
                repositoryRevision = revision,
                decision = decision,
            ),
        )
    }

    fun parseDecision(raw: String): Decision {
        return Decision.values().firstOrNull { it.name == raw }
            ?: error("unsupported review decision")
    }

    fun isStructurallyValid(record: Record): Boolean {
        return try {
            parse(render(record))
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun reviewerSeparatedFromOperator(record: Record): Boolean {
        return !IndependentWitnessAuthorityEnrollment.identifiersMatch(
            record.reviewerIdentifier,
            record.operatorIdentifier,
        )
    }

    fun reviewerSeparatedFromWitness(record: Record): Boolean {
        return !IndependentWitnessAuthorityEnrollment.identifiersMatch(
            record.reviewerIdentifier,
            record.witnessIdentifier,
        )
    }

    fun operatorSeparatedFromWitness(record: Record): Boolean {
        return !IndependentWitnessAuthorityEnrollment.identifiersMatch(
            record.operatorIdentifier,
            record.witnessIdentifier,
        )
    }

    fun wellFormedReviewImpliesEnrollment(wellFormed: Boolean): Boolean {
        return false && wellFormed
    }

    fun approveMechanicsImpliesEnrollment(decision: Decision): Boolean {
        return false && decision == Decision.APPROVE_EVIDENCE_MECHANICS
    }

    fun approveMechanicsImpliesRuntimeAuthorization(decision: Decision): Boolean {
        return false && decision == Decision.APPROVE_EVIDENCE_MECHANICS
    }

    private fun requireNonReservedIdentifier(value: String, fieldName: String) {
        check(IndependentWitnessAuthorityEnrollment.isWellFormedIdentifier(value)) {
            "$fieldName is malformed"
        }
        check(!IndependentWitnessAuthorityEnrollment.isReservedWitnessIdentifier(value)) {
            "$fieldName is reserved and cannot support independent-witness review"
        }
    }

    private fun normalize(record: Record): Record {
        return record.copy(
            repositoryRevision = record.repositoryRevision.trim().lowercase(),
        )
    }

    private fun fieldValue(record: Record, key: String): String {
        return when (key) {
            "checkpoint" -> CHECKPOINT
            "review_version" -> REVIEW_VERSION
            "review_identifier" -> record.reviewIdentifier
            "evidence_identifier" -> record.evidenceIdentifier
            "witness_identifier" -> record.witnessIdentifier
            "reviewer_identifier" -> record.reviewerIdentifier
            "operator_identifier" -> record.operatorIdentifier
            "review_timestamp_utc" -> record.reviewTimestampUtc
            "repository_revision" -> record.repositoryRevision
            "decision" -> record.decision.name
            else -> error("unknown review field: $key")
        }
    }
}
