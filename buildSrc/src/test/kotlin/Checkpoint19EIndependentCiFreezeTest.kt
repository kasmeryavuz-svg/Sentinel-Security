import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Checkpoint19EIndependentCiFreezeTest {
    @Test
    fun `independent CI workflow stays pinned fail-closed and complete`() {
        val workflowDir = File("../.github/workflows")
        assertTrue(workflowDir.isDirectory, "independent CI workflow directory must exist")
        val workflows = workflowDir.listFiles().orEmpty()
            .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        assertEquals(
            listOf("checkpoint-19e-independent-ci.yml"),
            workflows.map { it.name }.sorted(),
        )
        val workflow = workflows.single()
        val text = workflow.readText()
        val onBlock = yamlBlockAfter(text, "on:")
        val permissionsBlock = yamlBlockAfter(text, "permissions:")

        assertTrue(text.contains("name: Checkpoint 19E independent CI"))
        assertTrue(text.contains("name: Independent safety verification"))
        assertTrue(onBlock.contains("pull_request:"))
        assertTrue(onBlock.contains("workflow_dispatch:"))
        assertFalse(text.contains("pull_request_target"))
        assertFalse(onBlock.contains("pull_request_target"))
        Checkpoint19EDecisionSource.forbiddenWorkflowTriggers.forEach { trigger ->
            assertFalse(
                Regex("""^\s*${Regex.escape(trigger)}\s*:""", RegexOption.MULTILINE)
                    .containsMatchIn(onBlock),
                trigger,
            )
        }
        assertTrue(permissionsBlock.contains("contents: read"))
        assertFalse(Regex(""":\s*write\b""").containsMatchIn(permissionsBlock))
        assertFalse(permissionsBlock.contains("id-token: write"))
        assertTrue(text.contains("timeout-minutes:"))
        val timeout = Regex("""timeout-minutes:\s*(\d+)""").find(text)
        assertTrue((timeout?.groupValues?.get(1)?.toInt() ?: 0) >= 30)
        assertTrue(text.contains("cancel-in-progress: true"))

        val uses = Regex("""^\s+uses:\s*(\S+)""", RegexOption.MULTILINE)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(3, uses.size)
        uses.forEach { ref ->
            assertTrue(
                ref.matches(
                    Regex("""(?:actions|gradle)/[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)?@[0-9a-f]{40}"""),
                ),
                ref,
            )
        }
        assertTrue(
            text.contains(
                "uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1",
            ),
        )
        assertTrue(
            text.contains(
                "uses: gradle/actions/wrapper-validation@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6.3.0",
            ),
        )
        assertTrue(
            text.contains(
                "uses: actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961 # v5.7.0",
            ),
        )
        assertTrue(text.contains("java-version: \"17\""))
        assertTrue(text.contains("\"platforms;android-36\""))
        assertTrue(text.contains("\"build-tools;35.0.0\""))
        assertTrue(text.contains("./gradlew"))
        assertFalse(Regex("""^\s+gradle\s""", RegexOption.MULTILINE).containsMatchIn(text))

        Checkpoint19EDecisionSource.requiredGradleVerificationTasks.forEach { task ->
            if (task == "test") {
                assertTrue(
                    Regex("""(?m)^[ \t]+test[ \t]*\\?$""").containsMatchIn(text),
                    "standalone Gradle test task must remain in the workflow",
                )
            } else {
                assertTrue(text.contains(task), task)
            }
        }
        Checkpoint19EDecisionSource.forbiddenGradleTasks.forEach { task ->
            assertFalse(text.contains(task), task)
        }
        assertTrue(text.contains("signing=UNSIGNED"))
        assertTrue(text.contains("This artifact is unsigned and is not a production distribution."))
        assertTrue(text.contains("Unexpected production-signing environment variable is populated"))

        assertFalse(text.contains("\${{ secrets"))
        assertFalse(Regex("""^\s*secrets\s*:""", RegexOption.MULTILINE).containsMatchIn(text))
        listOf(
            "SENTINEL_RELEASE_STORE_FILE:",
            "SENTINEL_RELEASE_STORE_PASSWORD:",
            "SENTINEL_RELEASE_KEY_ALIAS:",
            "SENTINEL_RELEASE_KEY_PASSWORD:",
            "SENTINEL_RELEASE_CERT_SHA256:",
        ).forEach { mapping ->
            assertFalse(text.contains(mapping), mapping)
        }
        assertFalse(text.contains("upload-artifact"))
        assertFalse(text.contains("actions/upload"))
        assertFalse(text.contains("softprops/"))
        assertFalse(text.contains("keytool"))
        assertFalse(text.contains("genkeypair"))
        assertFalse(text.contains("debug.keystore"))
        assertFalse(text.contains("connectedAndroidTest"))
        assertFalse(text.contains("android-emulator-runner"))
        assertFalse(Regex("""\bemulator\b""").containsMatchIn(text))
        assertFalse(Regex("""\badb\b""").containsMatchIn(text))
        assertFalse(text.contains("set-device-owner"))
        assertFalse(text.contains("printenv"))
        assertFalse(Regex("""^\s+env$""", RegexOption.MULTILINE).containsMatchIn(text))
        assertFalse(text.contains("assembleProductionRelease"))
        assertFalse(text.contains("checkProductionDistributionSigning"))
    }

    @Test
    fun `bytecode policy still forbids recovery from referencing Checkpoint 19E`() {
        val source = File("src/main/kotlin/ProductionBytecodePolicyVerifier.kt").readText()
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
            source.contains("com/example/devicemanagement/destructive/Checkpoint19DDecision"),
        )
        val allowlistBlock = source
            .substringAfter("private val allowedDpmInvocations = mapOf(")
            .substringBefore("private val forbiddenLoaderOwners")
        assertTrue(allowlistBlock.contains("wipeDevice(I)V"))
        assertTrue(allowlistBlock.contains("AndroidDevicePolicyFactoryResetService"))
        assertTrue(!allowlistBlock.contains("wipeData"))
    }

    private fun yamlBlockAfter(text: String, header: String): String {
        val start = text.indexOf("\n$header")
        assertTrue(start >= 0, header)
        val remainder = text.substring(start + 1 + header.length)
        val next = Regex("""\n[a-zA-Z]""").find(remainder) ?: return remainder
        return remainder.substring(0, next.range.first)
    }
}

private object Checkpoint19EDecisionSource {
    val requiredGradleVerificationTasks = listOf(
        ":buildSrc:test",
        "test",
        "checkProductionBytecodePolicy",
        ":app:checkAppApiCompileNegative",
        ":app:checkAppDependencyIsolation",
        ":app:checkDebugEffectiveDeviceAdminMetadata",
        ":app:checkReleaseEffectiveDeviceAdminMetadata",
        ":app:checkDebugProductionBytecodePolicy",
        ":app:checkReleaseProductionBytecodePolicy",
        "assembleDebug",
        "assembleRelease",
        "bundleRelease",
        "checkReleaseProductionSecurity",
        "checkReleaseBundleProductionSecurity",
        ":sensitive-actions:test",
        ":sensitive-actions:checkMainProductionBytecodePolicy",
        ":app:checkDestructiveSigningCeremonyPreparation",
    )

    val forbiddenGradleTasks = listOf(
        "checkProductionDistributionSigning",
        "assembleProductionRelease",
        "bundleProductionRelease",
        "connectedAndroidTest",
        "connectedDebugAndroidTest",
        "connectedReleaseAndroidTest",
        "deviceCheck",
        "managedDevice",
    )

    val forbiddenWorkflowTriggers = listOf(
        "pull_request_target",
        "schedule",
        "workflow_run",
        "repository_dispatch",
        "deployment",
        "release",
        "create",
        "delete",
        "gollum",
        "page_build",
        "issue_comment",
        "issues",
    )
}
