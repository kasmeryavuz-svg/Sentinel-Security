import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductionDistributionSigningGateTest {
    @Test
    fun `ordinary release never attaches signing even when synthetic inputs are available`() {
        val unavailable = ProductionDistributionSigningGate.decide(
            distributionRequested = false,
            inputs = null,
        )
        val available = ProductionDistributionSigningGate.decide(
            distributionRequested = false,
            inputs = syntheticAvailableInputs(),
        )
        assertEquals(ProductionDistributionSigningGate.Decision.DO_NOT_ATTACH, unavailable)
        assertEquals(ProductionDistributionSigningGate.Decision.DO_NOT_ATTACH, available)
        assertFalse(ProductionDistributionSigningGate.mustAttach(available))
        assertFalse(ProductionDistributionSigningGate.mustRefuse(available))
    }

    @Test
    fun `explicit distribution without inputs fails closed`() {
        val missing = ProductionDistributionSigningGate.decide(
            distributionRequested = true,
            inputs = null,
        )
        val empty = ProductionDistributionSigningGate.decide(
            distributionRequested = true,
            inputs = ProductionDistributionSigningGate.ObservedSigningInputs(
                storeFilePresent = false,
                storePasswordPresent = false,
                keyAliasPresent = false,
                keyPasswordPresent = false,
                certificateFingerprintPresent = false,
                storeFileExists = false,
                storeFileLooksLikeDebugOrTest = false,
                certificateFingerprintValid = false,
            ),
        )
        assertEquals(ProductionDistributionSigningGate.Decision.REFUSE_MISSING_INPUTS, missing)
        assertEquals(ProductionDistributionSigningGate.Decision.REFUSE_MISSING_INPUTS, empty)
        assertTrue(ProductionDistributionSigningGate.mustRefuse(missing))
        assertFalse(ProductionDistributionSigningGate.mustAttach(missing))
    }

    @Test
    fun `explicit distribution with incomplete or debug material fails closed`() {
        val incomplete = ProductionDistributionSigningGate.decide(
            distributionRequested = true,
            inputs = syntheticAvailableInputs().copy(
                keyPasswordPresent = false,
            ),
        )
        val missingStore = ProductionDistributionSigningGate.decide(
            distributionRequested = true,
            inputs = syntheticAvailableInputs().copy(
                storeFileExists = false,
            ),
        )
        val debugMaterial = ProductionDistributionSigningGate.decide(
            distributionRequested = true,
            inputs = syntheticAvailableInputs().copy(
                storeFileLooksLikeDebugOrTest = true,
            ),
        )
        val invalidFingerprint = ProductionDistributionSigningGate.decide(
            distributionRequested = true,
            inputs = syntheticAvailableInputs().copy(
                certificateFingerprintValid = false,
            ),
        )
        assertEquals(
            ProductionDistributionSigningGate.Decision.REFUSE_INCOMPLETE_INPUTS,
            incomplete,
        )
        assertEquals(
            ProductionDistributionSigningGate.Decision.REFUSE_MISSING_INPUTS,
            missingStore,
        )
        assertEquals(
            ProductionDistributionSigningGate.Decision.REFUSE_DEBUG_OR_TEST_MATERIAL,
            debugMaterial,
        )
        assertEquals(
            ProductionDistributionSigningGate.Decision.REFUSE_INVALID_CERTIFICATE_FINGERPRINT,
            invalidFingerprint,
        )
    }

    @Test
    fun `explicit distribution with complete non-debug synthetic inputs is the only attach route`() {
        val attach = ProductionDistributionSigningGate.decide(
            distributionRequested = true,
            inputs = syntheticAvailableInputs(),
        )
        assertEquals(ProductionDistributionSigningGate.Decision.ATTACH, attach)
        assertTrue(ProductionDistributionSigningGate.mustAttach(attach))
        assertFalse(ProductionDistributionSigningGate.mustRefuse(attach))
    }

    @Test
    fun `gradle ordinary release remains unsigned and disposableValidation stays unsigned`() {
        val appGradle = java.io.File("../app/build.gradle.kts").readText()
        assertTrue(appGradle.contains("if (requestProductionDistribution)"))
        assertTrue(appGradle.contains("ordinary assembleRelease/bundleRelease must remain unsigned"))
        assertTrue(appGradle.contains("ordinary release must not create a production signing configuration"))
        assertTrue(appGradle.contains("signingConfig = null"))
        assertTrue(appGradle.contains("disposableValidation must remain unsigned even if production-signing"))
        assertTrue(appGradle.contains("release must never use the Android debug signing key"))
        assertTrue(appGradle.contains("ProductionDistributionSigningGate.decide"))
        val releaseBlock = appGradle
            .substringAfter("release {")
            .substringBefore("create(DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE)")
        assertTrue(releaseBlock.contains("if (requestProductionDistribution)"))
        assertFalse(
            releaseBlock.contains("if (secrets != null)"),
        )
        val ordinaryRead = appGradle
            .substringBefore("val requestProductionDistribution")
        assertFalse(ordinaryRead.contains("val productionSigningSecrets = readProductionSigningSecrets()"))
        assertFalse(appGradle.contains("apksigner sign"))
        assertFalse(appGradle.contains("keytool -genkeypair"))
    }

    private fun syntheticAvailableInputs(): ProductionDistributionSigningGate.ObservedSigningInputs {
        return ProductionDistributionSigningGate.ObservedSigningInputs(
            storeFilePresent = true,
            storePasswordPresent = true,
            keyAliasPresent = true,
            keyPasswordPresent = true,
            certificateFingerprintPresent = true,
            storeFileExists = true,
            storeFileLooksLikeDebugOrTest = false,
            certificateFingerprintValid = true,
        )
    }
}
