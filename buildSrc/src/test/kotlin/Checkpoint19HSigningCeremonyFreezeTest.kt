import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Checkpoint19HSigningCeremonyFreezeTest {
    @Test
    fun `ceremony contract stays build-only and repository default stays NOT_READY`() {
        val contract = File("src/main/kotlin/DestructiveSigningCeremonyPreparation.kt").readText()
        val task = File("src/main/kotlin/DestructiveSigningCeremonyPreparationTask.kt").readText()
        val expected = File("src/main/kotlin/DestructiveValidationExpectedIdentity.kt").readText()
        val appGradle = File("../app/build.gradle.kts").readText()

        assertTrue(expected.contains("expectedCertificateSha256 = null"))
        assertFalse(contract.contains("SENTINEL_RELEASE_"))
        assertFalse(task.contains("SENTINEL_RELEASE_"))
        assertFalse(contract.contains("sentinelSecret"))
        assertFalse(task.contains("sentinelSecret"))
        assertFalse(contract.contains("System.getenv"))
        assertFalse(task.contains("System.getenv"))
        assertFalse(contract.contains("local.properties"))
        assertFalse(task.contains("local.properties"))
        assertTrue(contract.contains("REPOSITORY_DEFAULT"))
        assertTrue(contract.contains("evaluateRepositoryDefault"))
        assertTrue(task.contains("evaluateRepositoryDefault"))
        assertTrue(task.contains("CheckDestructiveSigningCeremonyPreparationTask"))
        assertTrue(appGradle.contains("checkDestructiveSigningCeremonyPreparation"))
        assertTrue(appGradle.contains("signingConfig = null"))
        assertFalse(contract.contains(" var "))
        assertFalse(Regex("""\bfun set[A-Z]""").containsMatchIn(contract))
        assertFalse(HEX_SHA256.containsMatchIn(contract))
        assertFalse(HEX_SHA256.containsMatchIn(task))
        FORBIDDEN_COMMANDS.forEach { command ->
            assertFalse(contract.contains(command), command)
            assertFalse(task.contains(command), command)
        }
    }

    @Test
    fun `production runtime modules cannot access signing-ceremony preparation classes`() {
        val tokens = listOf(
            "DestructiveSigningCeremonyPreparation",
            "SigningCeremonyPreparationRecord",
            "RepositorySigningCeremonyPreparationSource",
            "CheckDestructiveSigningCeremonyPreparationTask",
            "checkDestructiveSigningCeremonyPreparation",
        )
        val runtimeRoots = listOf(
            File("../app/src/main"),
            File("../device-management/src/main"),
            File("../device-management-api/src/main"),
            File("../device-management-facade/src/main"),
            File("../sensitive-actions/src/main"),
        )
        runtimeRoots.filter { it.isDirectory }.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    val text = file.readText()
                    tokens.forEach { token ->
                        assertFalse(text.contains(token), "${file.path} $token")
                    }
                }
        }
    }

    @Test
    fun `workflow runs the 19H proof without uploads secrets signing or hardware`() {
        val workflowDir = File("../.github/workflows")
        val workflows = workflowDir.listFiles().orEmpty()
            .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        assertEquals(
            listOf("checkpoint-19e-independent-ci.yml"),
            workflows.map { it.name }.sorted(),
        )
        val text = workflows.single().readText()
        assertTrue(text.contains(":app:checkDestructiveSigningCeremonyPreparation"))
        assertTrue(text.contains(":app:checkUnsignedDisposableValidationBuildPurposeEvidence"))
        assertTrue(text.contains(":app:checkUnsignedDestructiveValidationCandidateEvidence"))
        assertTrue(text.contains(":app:checkDisposableValidationEffectiveDeviceAdminMetadata"))
        assertTrue(text.contains(":app:checkDisposableValidationProductionBytecodePolicy"))
        assertTrue(text.contains("ceremony_status=NOT_READY"))
        assertTrue(text.contains("contents: read"))
        assertFalse(text.contains("upload-artifact"))
        assertFalse(text.contains("\${{ secrets"))
        assertFalse(Regex(""":\s*write\b""").containsMatchIn(text))
        assertFalse(text.contains("id-token: write"))
        listOf(
            "SENTINEL_RELEASE_STORE_FILE:",
            "SENTINEL_RELEASE_STORE_PASSWORD:",
            "SENTINEL_RELEASE_KEY_ALIAS:",
            "SENTINEL_RELEASE_KEY_PASSWORD:",
            "SENTINEL_RELEASE_CERT_SHA256:",
            "SENTINEL_VALIDATION_STORE_FILE:",
            "SENTINEL_VALIDATION_CERT_SHA256:",
        ).forEach { mapping ->
            assertFalse(text.contains(mapping), mapping)
        }
        FORBIDDEN_COMMANDS.forEach { command ->
            assertFalse(text.contains(command), command)
        }
        assertFalse(Regex("""\bemulator\b""").containsMatchIn(text))
        assertFalse(Regex("""\badb\b""").containsMatchIn(text))
        assertFalse(text.contains("checkProductionDistributionSigning"))
        assertFalse(text.contains("assembleProductionRelease"))
        assertFalse(text.contains("assembleSignedDisposableValidation"))
        val uses = Regex("""^\s+uses:\s*(\S+)""", RegexOption.MULTILINE)
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(3, uses.size)
    }

    @Test
    fun `blank ceremony schema has placeholders only`() {
        val template = File("../docs/templates/DESTRUCTIVE_SIGNING_CEREMONY_RECORD.template.txt")
            .readText()
        assertTrue(template.contains("ceremony_id=<CEREMONY_ID>"))
        assertTrue(template.contains("utc_start=<UTC_START>"))
        assertTrue(template.contains("operator_approval_reference=<OPERATOR_APPROVAL_REFERENCE>"))
        assertTrue(template.contains("witness_approval_reference=<WITNESS_APPROVAL_REFERENCE>"))
        assertTrue(template.contains("source_checkout_revision=<SOURCE_CHECKOUT_REVISION>"))
        assertTrue(template.contains("unsigned_candidate_sha256=<UNSIGNED_CANDIDATE_SHA256>"))
        assertTrue(template.contains("immutable_snapshot_sha256=<IMMUTABLE_SNAPSHOT_SHA256>"))
        assertTrue(template.contains("signed_candidate_sha256=<SIGNED_CANDIDATE_SHA256>"))
        assertTrue(template.contains("public_signing_certificate_sha256=<PUBLIC_SIGNING_CERTIFICATE_SHA256>"))
        assertTrue(template.contains("current_signer_count=<CURRENT_SIGNER_COUNT>"))
        assertTrue(template.contains("verified_signature_schemes=<VERIFIED_SIGNATURE_SCHEMES>"))
        assertTrue(template.contains("package_name=<PACKAGE_NAME>"))
        assertTrue(template.contains("device_admin_component=<DEVICE_ADMIN_COMPONENT>"))
        assertTrue(template.contains("device_admin_policies=<DEVICE_ADMIN_POLICIES>"))
        assertTrue(template.contains("minimum_sdk=<MINIMUM_SDK>"))
        assertTrue(template.contains("target_sdk=<TARGET_SDK>"))
        assertTrue(template.contains("observed_build_purpose=<OBSERVED_BUILD_PURPOSE>"))
        assertTrue(template.contains("post_signing_inspection_result=<POST_SIGNING_INSPECTION_RESULT>"))
        assertTrue(template.contains("candidate_authority=<CANDIDATE_AUTHORITY>"))
        assertTrue(template.contains("runtime_authorization=<RUNTIME_AUTHORIZATION>"))
        assertTrue(template.contains("trusted_expectation_minted=<TRUSTED_EXPECTATION_MINTED>"))
        assertTrue(template.contains("hardware_validation_approval=<HARDWARE_VALIDATION_APPROVAL>"))
        assertTrue(template.contains("Private-key bytes"))
        assertTrue(template.contains("Keystore bytes"))
        assertTrue(template.contains("Store passwords"))
        assertFalse(HEX_SHA256.containsMatchIn(template))
        assertFalse(template.contains("TEST_ONLY_"))
    }

    @Test
    fun `19H document stays closed and contains no trusted digest`() {
        val docs = File("../docs/WIPE_19H_SIGNING_CEREMONY_PREPARATION.md").readText()
        assertTrue(docs.contains("CHECKPOINT_19H_SIGNING_CEREMONY_PREPARATION = YES"))
        assertTrue(docs.contains("19H_SIGNING_CEREMONY_CONTRACT_PRESENT = true"))
        assertTrue(docs.contains("19H_SIGNING_CEREMONY_READY = false"))
        assertTrue(docs.contains("19H_OFFLINE_KEY_GENERATED = false"))
        assertTrue(docs.contains("19H_PUBLIC_CERTIFICATE_SUPPLIED = false"))
        assertTrue(docs.contains("19H_EXPECTED_CERTIFICATE_RECORDED = false"))
        assertTrue(docs.contains("19H_SIGNED_VALIDATION_CANDIDATE_PRODUCED = false"))
        assertTrue(docs.contains("TRUSTED_DESTRUCTIVE_ARTIFACT_DIGEST_RECORDED = false"))
        assertTrue(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("PREPARATION IS NOT KEY GENERATION"))
        assertTrue(docs.contains("PREPARATION IS NOT PRODUCTION SIGNING"))
        assertTrue(docs.contains("PREPARATION IS NOT TRUSTED ARTIFACT ENROLLMENT"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertTrue(docs.contains("NO NEW WIPE SCOPE ADDED"))
        assertTrue(docs.contains("NO HARDWARE WIPE PERFORMED"))
        assertFalse(docs.contains("19H_SIGNING_CEREMONY_READY = true"))
        assertFalse(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        FORBIDDEN_COMMANDS.forEach { command ->
            assertFalse(docs.contains(command), command)
        }
    }

    @Test
    fun `no committed keystore or production fingerprint outside test fixtures`() {
        val root = File("..")
        val excluded = setOf(
            "buildSrc/src/test/kotlin/SigningCeremonyPreparationTestFixtures.kt",
        )
        root.walkTopDown()
            .onEnter { dir ->
                dir.name !in setOf(".git", "build", ".gradle", ".android-sdk")
            }
            .filter { it.isFile }
            .forEach { file ->
                val relative = file.relativeTo(root).path.replace('\\', '/')
                if (file.extension in setOf("jks", "keystore", "p12", "pfx", "pk8")) {
                    throw AssertionError("committed keystore or certificate: $relative")
                }
                if (relative in excluded || relative.startsWith("buildSrc/src/test/")) {
                    return@forEach
                }
                if (file.extension in setOf("kt", "kts", "md", "txt")) {
                    val text = file.readText()
                    if (relative.startsWith("docs/WIPE_19H") ||
                        relative.startsWith("docs/templates/DESTRUCTIVE_SIGNING_CEREMONY") ||
                        relative.startsWith("sensitive-actions/src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19H") ||
                        relative.startsWith("buildSrc/src/main/kotlin/DestructiveSigningCeremony")
                    ) {
                        assertFalse(HEX_SHA256.containsMatchIn(text), relative)
                    }
                }
            }
    }

    @Test
    fun `normal release and disposableValidation signing behavior remains unchanged`() {
        val appGradle = File("../app/build.gradle.kts").readText()
        val disposableBlock = appGradle
            .substringAfter("create(DestructiveValidationExpectedIdentity.DISPOSABLE_VALIDATION_BUILD_TYPE)")
            .substringBefore("val disposableValidationSigning")
        assertTrue(disposableBlock.contains("signingConfig = null"))
        assertTrue(appGradle.contains("disposableValidation must remain unsigned even if production-signing"))
        assertTrue(appGradle.contains("readProductionSigningSecrets"))
        assertTrue(appGradle.contains("checkProductionDistributionSigning"))
        val ceremonyBlock = appGradle
            .substringAfter("checkDestructiveSigningCeremonyPreparation")
            .substringBefore("checkBackupDataExtractionPolicy")
        assertFalse(ceremonyBlock.contains("dependsOn"))
        assertFalse(ceremonyBlock.contains("assembleProductionRelease"))
        assertFalse(ceremonyBlock.contains("checkProductionDistributionSigning"))
        FORBIDDEN_COMMANDS.forEach { command ->
            assertFalse(ceremonyBlock.contains(command), command)
        }
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
        val FORBIDDEN_COMMANDS = listOf(
            "apksigner sign",
            "jarsigner",
            "keytool -genkey",
            "keytool -genkeypair",
            "openssl genpkey",
            "openssl req",
            "openssl ecparam",
            "gpg --generate-key",
        )
    }
}
