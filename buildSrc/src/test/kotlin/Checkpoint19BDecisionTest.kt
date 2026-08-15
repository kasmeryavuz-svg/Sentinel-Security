import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint19BDecisionTest {
    @Test
    fun `wipeDevice is origin-bound and wipeData stays forbidden`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(allowlistBlock.contains("wipeDevice(I)V"))
        assertTrue(allowlistBlock.contains("AndroidDevicePolicyFactoryResetService"))
        assertTrue(allowlistBlock.contains("performAuthorizedFactoryReset"))
        assertTrue(!allowlistBlock.contains("wipeData"))
        assertTrue(source.contains("AndroidFutureDestructiveExecutor.onAuthorizedHandoff"))
        assertTrue(source.contains("DeviceManagementComposition"))
        val forbidden = source
            .substringAfter("checkpoint17BForbiddenDpmMethodNames = setOf(")
            .substringBefore(")")
        assertTrue(forbidden.contains("\"wipeData\""))
        assertTrue(!forbidden.contains("\"wipeDevice\""))
    }
}
