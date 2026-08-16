/**
 * Future immutable source of repository external witness evidence and
 * independent reviews.
 *
 * Checkpoint 19V keeps both collections empty. A later checkpoint may
 * populate them only after genuine external evidence exists outside this
 * file. Tests fail if a real evidence or review record appears here.
 */
object IndependentWitnessExternalEvidenceSource {
    const val LOCAL_EVIDENCE_RELATIVE_PATH =
        "local/independent-witness-external-evidence.txt"
    const val LOCAL_REVIEW_RELATIVE_PATH =
        "local/independent-witness-enrollment-review.txt"

    fun repositoryExternalWitnessEvidence():
        List<IndependentWitnessExternalEvidence.Record> {
        return emptyList()
    }

    fun repositoryWitnessReviews(): List<IndependentWitnessEnrollmentReview.Record> {
        return emptyList()
    }

    fun assertEmpty() {
        check(repositoryExternalWitnessEvidence().isEmpty()) {
            "19V repository external witness evidence must remain empty"
        }
        check(repositoryWitnessReviews().isEmpty()) {
            "19V repository witness reviews must remain empty"
        }
        IndependentWitnessAuthorityEnrollmentSource.assertNotEnrolled()
        check(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty()) {
            "establishedWitnessIdentifiers must remain empty"
        }
    }
}
