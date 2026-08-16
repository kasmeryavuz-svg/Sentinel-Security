/**
 * Repository contract for independently established witness authority.
 *
 * A cryptographically valid signature proves control of a signing key.
 * It does not enroll a person, organization, or CI run as an independent
 * witness. This repository currently recognizes no such authority.
 */
object IndependentWitnessAuthorityContract {
    fun establishedWitnessIdentifiers(): Set<String> = emptySet()

    fun independenceEstablished(witnessIdentifier: String): Boolean {
        val identifier = witnessIdentifier.trim()
        return identifier.isNotEmpty() &&
            identifier in establishedWitnessIdentifiers()
    }

    fun approval(
        statementPresent: Boolean,
        signatureVerified: Boolean,
        evidenceMatches: Boolean,
        independenceEstablished: Boolean,
    ): Boolean {
        return statementPresent &&
            signatureVerified &&
            evidenceMatches &&
            independenceEstablished
    }

    fun ciIsIndependentWitness(): Boolean = false

    fun localOperatorIsIndependentWitness(): Boolean = false

    fun localReceiptIsIndependentWitness(): Boolean = false
}
