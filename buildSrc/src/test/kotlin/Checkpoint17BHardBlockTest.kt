import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class Checkpoint17BHardBlockTest {
    @Test
    fun `bytecode verifier hard-blocks destructive DPM method names independently of the allowlist`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        assertTrue(source.contains("checkpoint17BForbiddenDpmMethodNames"))
        assertTrue(source.contains("if (name in checkpoint17BForbiddenDpmMethodNames)"))
        val block = source
            .substringAfter("checkpoint17BForbiddenDpmMethodNames = setOf(")
            .substringBefore(")")
        assertTrue(block.contains("\"wipeData\""))
        assertTrue(block.contains("\"wipeDevice\""))
        assertTrue(source.contains("Checkpoint 17B-blocked"))
    }

    @Test
    fun `release DEX denylist still rejects both destructive tokens`() {
        val source = File("src/main/kotlin/ReleaseArtifactSecurityVerifier.kt").readText()
        val denylist = source
            .substringAfter("private val forbiddenDexTokens = listOf(")
            .substringBefore("private val debugCertMarkers")
        assertTrue(denylist.contains("\"wipeData\""))
        assertTrue(denylist.contains("\"wipeDevice\""))
    }

    @Test
    fun `production DPM allowlist still excludes destructive methods`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(!allowlistBlock.contains("wipeData"))
        assertTrue(!allowlistBlock.contains("wipeDevice"))
    }

    @Test
    fun `destructive-safety SQLite and mutation bindings exist without widening DPM`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
        assertTrue(source.contains("authorizedDestructiveSafetySqliteClasses"))
        assertTrue(source.contains("trustedDestructiveSafetyMutationOrigins"))
        assertTrue(source.contains("sentinel_deny_only_cooldown.db"))
        assertTrue(source.contains("sentinel_destructive_pre_execution_evidence.db"))
        assertTrue(source.contains("SqliteDenyOnlyMarkerStore"))
        assertTrue(source.contains("SqliteDestructivePreExecutionStore"))
        assertTrue(!source.contains("allowedDpmInvocations") || !source.substringAfter("allowedDpmInvocations").substringBefore("forbiddenLoaderOwners").contains("wipeDevice"))
    }
}
