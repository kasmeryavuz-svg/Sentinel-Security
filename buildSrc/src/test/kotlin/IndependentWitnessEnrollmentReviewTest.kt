import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndependentWitnessEnrollmentReviewTest {
    @Test
    fun `valid review schema round-trips without becoming repository state`() {
        val record = validReview()
        val parsed = IndependentWitnessEnrollmentReview.parse(
            IndependentWitnessEnrollmentReview.render(record),
        )
        assertEquals(record, parsed)
        assertEquals(IndependentWitnessEnrollmentReview.FIELD_ORDER, parsedFieldOrder(record))
        IndependentWitnessEnrollmentReview.Decision.values().forEach { decision ->
            val typed = IndependentWitnessEnrollmentReview.parse(
                IndependentWitnessEnrollmentReview.render(record.copy(decision = decision)),
            )
            assertEquals(decision, typed.decision)
            assertFalse(
                IndependentWitnessEnrollmentReview.approveMechanicsImpliesEnrollment(decision),
            )
            assertFalse(
                IndependentWitnessEnrollmentReview.approveMechanicsImpliesRuntimeAuthorization(
                    decision,
                ),
            )
        }
        assertTrue(IndependentWitnessExternalEvidenceSource.repositoryWitnessReviews().isEmpty())
        assertTrue(IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isEmpty())
        assertFalse(
            IndependentWitnessEnrollmentReview.wellFormedReviewImpliesEnrollment(true),
        )
    }

    @Test
    fun `parser rejects duplicate missing unknown malformed and unsupported review fields`() {
        val valid = IndependentWitnessEnrollmentReview.render(validReview())
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace("review_version=1", "review_version=2"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace("decision=APPROVE_EVIDENCE_MECHANICS", "decision=APPROVE_ENROLLMENT"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace("decision=APPROVE_EVIDENCE_MECHANICS", "decision=AUTHORIZE"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace(REVISION, "not-a-commit"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace(REVIEW_TIME, "2026-08-16 20:00:00Z"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace(REVIEW_TIME, "2026-13-01T20:00:00Z"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace("\nrepository_revision=$REVISION\n", "\n"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace("reviewer_identifier=external-reviewer", "reviewer_identifier="),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace(
                    "reviewer_identifier=external-reviewer\n",
                    "reviewer_identifier=external-reviewer\nreviewer_identifier=other\n",
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(valid + "extra_critical=1\n")
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                valid.replace(
                    "reviewer_identifier=external-reviewer",
                    "reviewer_identifier=not a reviewer",
                ),
            )
        }
    }

    @Test
    fun `parser rejects overlapping and reserved reviewer witness and operator identities`() {
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                IndependentWitnessEnrollmentReview.render(
                    validReview().copy(reviewerIdentifier = "local-operator"),
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                IndependentWitnessEnrollmentReview.render(
                    validReview().copy(reviewerIdentifier = "external-witness"),
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                IndependentWitnessEnrollmentReview.render(
                    validReview().copy(operatorIdentifier = "external-witness"),
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessEnrollmentReview.parse(
                IndependentWitnessEnrollmentReview.render(
                    validReview().copy(reviewerIdentifier = "EXTERNAL-WITNESS"),
                ),
            )
        }
        val valid = IndependentWitnessEnrollmentReview.render(validReview())
        listOf(
            "ci",
            "github-actions",
            "cursor",
            "operator",
            "local-operator",
            "repository-owner",
            "19s-receipt",
            "19t-report",
        ).forEach { reserved ->
            assertFailsWith<IllegalStateException>("reviewer=$reserved") {
                IndependentWitnessEnrollmentReview.parse(
                    valid.replace(
                        "reviewer_identifier=external-reviewer",
                        "reviewer_identifier=$reserved",
                    ),
                )
            }
            assertFailsWith<IllegalStateException>("witness=$reserved") {
                IndependentWitnessEnrollmentReview.parse(
                    valid.replace(
                        "witness_identifier=external-witness",
                        "witness_identifier=$reserved",
                    ),
                )
            }
        }
        assertTrue(
            IndependentWitnessAuthorityEnrollment.isReservedWitnessIdentifier("ci"),
        )
        assertTrue(
            IndependentWitnessAuthorityEnrollment.isReservedWitnessIdentifier("cursor"),
        )
        assertTrue(
            IndependentWitnessAuthorityEnrollment.isReservedWitnessIdentifier(
                "repository-owner",
            ),
        )
        assertTrue(
            IndependentWitnessAuthorityEnrollment.isReservedWitnessIdentifier("19s-receipt"),
        )
        assertTrue(
            IndependentWitnessAuthorityEnrollment.isReservedWitnessIdentifier("19t-report"),
        )
    }

    private fun parsedFieldOrder(record: IndependentWitnessEnrollmentReview.Record): List<String> {
        return IndependentWitnessEnrollmentReview.render(record)
            .trim()
            .lines()
            .map { it.substringBefore('=') }
    }

    companion object {
        val REVISION = "12".repeat(20)
        const val REVIEW_TIME = "2026-08-16T20:00:00Z"

        fun validReview(): IndependentWitnessEnrollmentReview.Record {
            return IndependentWitnessEnrollmentReview.Record(
                reviewIdentifier = "desk-review-1",
                evidenceIdentifier = "org-separation-evidence",
                witnessIdentifier = "external-witness",
                reviewerIdentifier = "external-reviewer",
                operatorIdentifier = "local-operator",
                reviewTimestampUtc = REVIEW_TIME,
                repositoryRevision = REVISION,
                decision =
                    IndependentWitnessEnrollmentReview.Decision.APPROVE_EVIDENCE_MECHANICS,
            )
        }
    }
}
