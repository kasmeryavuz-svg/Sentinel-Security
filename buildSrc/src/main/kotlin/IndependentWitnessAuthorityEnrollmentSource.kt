/**
 * Future immutable source of repository witness-authority enrollments.
 *
 * Checkpoint 19U keeps this source empty. A later checkpoint may populate
 * it only after the full enrollment contract is satisfied by evidence
 * outside this file. Tests fail if a real enrollment appears here.
 */
object IndependentWitnessAuthorityEnrollmentSource {
    const val LOCAL_RECORD_RELATIVE_PATH =
        "local/independent-witness-authority-enrollment.txt"

    fun repositoryEnrollments(): List<IndependentWitnessAuthorityEnrollment.Record> {
        return emptyList()
    }

    fun repositoryAcceptsEnrollment(): Boolean = false

    fun assertNotEnrolled() {
        check(repositoryEnrollments().isEmpty()) {
            "19U repository enrollments must remain empty"
        }
        check(IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty()) {
            "establishedWitnessIdentifiers must remain empty"
        }
        check(!repositoryAcceptsEnrollment()) {
            "19U must not accept a real witness-authority enrollment"
        }
    }
}
