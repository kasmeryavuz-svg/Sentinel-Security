import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint19DDecisionTest {
    @Test
    fun `19D origin allowlists bind assembleAndHandoff to the production orchestrator`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        assertTrue(source.contains("private const val ASSEMBLE_AND_HANDOFF = \"assembleAndHandoff\""))
        assertTrue(
            source.contains(
                "com/example/devicemanagement/destructive/ProductionDestructiveRealChainOrchestrator",
            ),
        )
        assertTrue(source.contains("assembleAlreadyBoundDeviceFactoryReset"))
        assertTrue(
            source.contains(
                "no production trigger origin is authorized",
            ),
        )
        assertTrue(source.contains("constructs production real-chain type"))
        assertTrue(
            source.contains(
                "com/example/devicemanagement/destructive/ProductionDestructiveHumanConfirmationSource",
            ),
        )
        assertTrue(
            source.contains("com/example/devicemanagement/destructive/Checkpoint19DDecision"),
        )
        assertTrue(
            source.contains("com/example/devicemanagement/destructive/Checkpoint19EDecision"),
        )
        assertTrue(
            source.contains("com/example/devicemanagement/destructive/Checkpoint19FDecision"),
        )
        assertTrue(
            source.contains("com/example/devicemanagement/destructive/Checkpoint19GDecision"),
        )
        assertTrue(
            source.contains("com/example/devicemanagement/destructive/Checkpoint19HDecision"),
        )
        assertTrue(
            source.contains("com/example/devicemanagement/destructive/Checkpoint19JDecision"),
        )
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(allowlistBlock.contains("wipeDevice(I)V"))
        assertTrue(allowlistBlock.contains("AndroidDevicePolicyFactoryResetService"))
        assertTrue(allowlistBlock.contains("performAuthorizedFactoryReset"))
        assertTrue(!allowlistBlock.contains("wipeData"))
        val forbidden = source
            .substringAfter("checkpoint17BForbiddenDpmMethodNames = setOf(")
            .substringBefore(")")
        assertTrue(forbidden.contains("\"wipeData\""))
        assertTrue(!forbidden.contains("\"wipeDevice\""))
    }

    @Test
    fun `19D remaining runtime blockers exclude structural assembly and keep later approvals closed`() {
        val source = File(
            "../sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19DDecision.kt",
        ).readText()
        val blockers = source
            .substringAfter("val remainingRuntimeAvailabilityBlockers = listOf(")
            .substringBefore(")")
        assertTrue(source.contains("REAL_CHAIN_ASSEMBLY_IMPLEMENTATION_APPROVED = \"YES\""))
        assertTrue(source.contains("REAL_CHAIN_ASSEMBLY_PATH_PRESENT = true"))
        assertTrue(source.contains("REAL_DESTRUCTIVE_CHAIN_RUNTIME_AVAILABLE = false"))
        assertTrue(source.contains("REAL_CHAIN_STRUCTURAL_ASSEMBLY_COMPLETE = \"YES\""))
        assertTrue(source.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = \"NO\""))
        assertTrue(blockers.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED"))
        assertTrue(blockers.contains("PER_ATTEMPT_REAL_CONFIRMATION_AVAILABLE"))
        assertTrue(blockers.contains("DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED"))
        assertTrue(blockers.contains("DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED"))
        assertTrue(blockers.contains("DESTRUCTIVE_HARDWARE_TEST_PERFORMED"))
        assertTrue(blockers.contains("GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED"))
        assertTrue(!blockers.contains("REAL_CHAIN_ASSEMBLY_PATH_PRESENT"))
        assertTrue(!source.contains("wipeDevice"))
        assertTrue(!source.contains("wipeData"))
        val release = File("src/main/kotlin/ReleaseArtifactSecurityVerifier.kt").readText()
        assertTrue(release.contains("ProductionDestructiveRealChainOrchestrator"))
        assertTrue(release.contains("ProductionDestructiveHumanConfirmationSource"))
        val denylist = release
            .substringAfter("private val forbiddenDexTokens = listOf(")
            .substringBefore("private val debugCertMarkers")
        assertTrue(denylist.contains("\"wipeData\""))
        assertTrue(!denylist.contains("\"wipeDevice\""))
    }
}
