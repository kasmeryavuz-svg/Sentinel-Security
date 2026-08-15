/**
 * Build-only decision for whether ordinary or explicit production-distribution
 * release work may attach a production signing configuration.
 *
 * This object never reads environment secrets, never opens a keystore, and
 * never signs an artifact. Tests may supply synthetic input presence.
 */
object ProductionDistributionSigningGate {
    enum class Decision {
        DO_NOT_ATTACH,
        ATTACH,
        REFUSE_MISSING_INPUTS,
        REFUSE_INCOMPLETE_INPUTS,
        REFUSE_DEBUG_OR_TEST_MATERIAL,
        REFUSE_INVALID_CERTIFICATE_FINGERPRINT,
    }

    /**
     * Observed presence of production-signing inputs. Values are booleans
     * and synthetic flags only. Callers must not place keystore bytes,
     * passwords, or certificate material here.
     */
    data class ObservedSigningInputs(
        val storeFilePresent: Boolean,
        val storePasswordPresent: Boolean,
        val keyAliasPresent: Boolean,
        val keyPasswordPresent: Boolean,
        val certificateFingerprintPresent: Boolean,
        val storeFileExists: Boolean,
        val storeFileLooksLikeDebugOrTest: Boolean,
        val certificateFingerprintValid: Boolean,
    ) {
        val anySecretPresent: Boolean =
            storeFilePresent ||
                storePasswordPresent ||
                keyAliasPresent ||
                keyPasswordPresent ||
                certificateFingerprintPresent

        val allSecretsPresent: Boolean =
            storeFilePresent &&
                storePasswordPresent &&
                keyAliasPresent &&
                keyPasswordPresent
    }

    fun decide(
        distributionRequested: Boolean,
        inputs: ObservedSigningInputs?,
    ): Decision {
        if (!distributionRequested) {
            return Decision.DO_NOT_ATTACH
        }
        val observed = inputs ?: return Decision.REFUSE_MISSING_INPUTS
        if (!observed.anySecretPresent) {
            return Decision.REFUSE_MISSING_INPUTS
        }
        if (!observed.allSecretsPresent || !observed.certificateFingerprintPresent) {
            return Decision.REFUSE_INCOMPLETE_INPUTS
        }
        if (!observed.storeFileExists) {
            return Decision.REFUSE_MISSING_INPUTS
        }
        if (observed.storeFileLooksLikeDebugOrTest) {
            return Decision.REFUSE_DEBUG_OR_TEST_MATERIAL
        }
        if (!observed.certificateFingerprintValid) {
            return Decision.REFUSE_INVALID_CERTIFICATE_FINGERPRINT
        }
        return Decision.ATTACH
    }

    fun mustAttach(decision: Decision): Boolean = decision == Decision.ATTACH

    fun mustRefuse(decision: Decision): Boolean =
        decision != Decision.DO_NOT_ATTACH && decision != Decision.ATTACH
}
