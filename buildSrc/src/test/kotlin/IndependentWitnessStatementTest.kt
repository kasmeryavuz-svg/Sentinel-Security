import java.io.File
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndependentWitnessStatementTest {
    @Test
    fun `canonical bytes are the documented signed fields in order`() {
        val statement = validStatement()
        val canonical = IndependentWitnessStatement.canonicalBytes(statement)
            .toString(Charsets.UTF_8)
        val expected = IndependentWitnessStatement.SIGNED_FIELD_ORDER.joinToString("") { key ->
            val value = when (key) {
                "checkpoint" -> "19T"
                "statement_version" -> "1"
                "candidate_apk_sha256" -> APK_SHA256
                "validation_certificate_sha256" -> CERTIFICATE_SHA256
                "source_head_claimed" -> SOURCE_HEAD
                "package_name" -> identity.packageName
                "admin_component" -> identity.adminComponent
                "policies" -> identity.policies.joinToString(",")
                "min_sdk" -> identity.minSdk.toString()
                "target_sdk" -> identity.targetSdk.toString()
                "build_purpose" -> identity.buildPurpose
                "witness_identifier" -> "external-witness"
                "witness_timestamp_utc" -> TIMESTAMP
                else -> error(key)
            }
            "$key=$value\n"
        }
        assertEquals(expected, canonical)
        assertFalse(canonical.contains("signature="))
        assertFalse(canonical.contains("\r"))
    }

    @Test
    fun `valid standard signature verifies and a wrong key or mutation fails`() {
        val statement = validStatement()
        val signature = sign(statement, rsa.private)
        assertTrue(
            IndependentWitnessStatement.verifySignature(
                statement,
                signature,
                rsa.public,
            ),
        )
        assertFalse(
            IndependentWitnessStatement.verifySignature(
                statement,
                signature,
                otherRsa.public,
            ),
        )
        val mutated = statement.copy(packageName = "com.example.other")
        assertFalse(
            IndependentWitnessStatement.verifySignature(
                mutated,
                signature,
                rsa.public,
            ),
        )
        val rendered = IndependentWitnessStatement.render(statement, signature)
        val parsed = IndependentWitnessStatement.parse(rendered)
        assertEquals(statement, parsed.statement)
        assertEquals(IndependentWitnessStatement.SIGNATURE_ALGORITHM, parsed.signatureAlgorithm)
        val modified = rendered.replace(
            "package_name=${identity.packageName}",
            "package_name=com.example.other",
        )
        val modifiedParsed = IndependentWitnessStatement.parse(modified)
        assertFalse(
            IndependentWitnessStatement.verifySignature(
                modifiedParsed.statement,
                modifiedParsed.signatureBase64,
                rsa.public,
            ),
        )
    }

    @Test
    fun `parser rejects malformed unsupported duplicate and missing fields`() {
        val signature = sign(validStatement(), rsa.private)
        val valid = IndependentWitnessStatement.render(validStatement(), signature)
        assertFailsWith<IllegalStateException> {
            IndependentWitnessStatement.parse(
                valid.replace("statement_version=1", "statement_version=2"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessStatement.parse(
                valid.replace(APK_SHA256, "not-a-digest"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessStatement.parse(
                valid.replace(SOURCE_HEAD, "not-a-commit"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessStatement.parse(
                valid.replace(TIMESTAMP, "2026-08-16 03:04:05Z"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessStatement.parse(
                valid.replace("\nwitness_identifier=external-witness\n", "\n"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessStatement.parse(
                valid.replace(
                    "witness_identifier=external-witness\n",
                    "witness_identifier=external-witness\nwitness_identifier=other\n",
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessStatement.parse(valid + "extra_critical=1\n")
        }
        assertFailsWith<IllegalStateException> {
            FailClosedStatusLines.parseUnique("apk_sha256=a\napk_sha256=b\n")
        }
    }

    @Test
    fun `statement matches only the independently observed candidate evidence`() {
        val statement = validStatement()
        assertTrue(
            IndependentWitnessStatement.matchesCandidate(
                statement = statement,
                apkSha256 = APK_SHA256,
                validationCertificateSha256 = CERTIFICATE_SHA256,
                sourceHeadClaimed = SOURCE_HEAD,
                identity = identity,
            ),
        )
        assertFalse(
            IndependentWitnessStatement.matchesCandidate(
                statement = statement.copy(candidateApkSha256 = "cc".repeat(32)),
                apkSha256 = APK_SHA256,
                validationCertificateSha256 = CERTIFICATE_SHA256,
                sourceHeadClaimed = SOURCE_HEAD,
                identity = identity,
            ),
        )
        assertFalse(
            IndependentWitnessStatement.matchesCandidate(
                statement = statement.copy(packageName = "com.example.other"),
                apkSha256 = APK_SHA256,
                validationCertificateSha256 = CERTIFICATE_SHA256,
                sourceHeadClaimed = SOURCE_HEAD,
                identity = identity,
            ),
        )
    }

    @Test
    fun `symlink statement is rejected`() {
        val root = Files.createTempDirectory("19t-statement-link").toFile()
        try {
            val real = File(root, "statement.txt").apply {
                writeText(
                    IndependentWitnessStatement.render(
                        validStatement(),
                        sign(validStatement(), rsa.private),
                    ),
                )
            }
            val link = File(root, "linked-statement.txt")
            Files.createSymbolicLink(link.toPath(), real.toPath())
            assertFailsWith<IllegalStateException> {
                IndependentWitnessStatement.parseFile(link)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun validStatement(): IndependentWitnessStatement.Statement {
        return IndependentWitnessStatement.Statement(
            candidateApkSha256 = APK_SHA256,
            validationCertificateSha256 = CERTIFICATE_SHA256,
            sourceHeadClaimed = SOURCE_HEAD,
            packageName = identity.packageName,
            adminComponent = identity.adminComponent,
            policies = identity.policies,
            minSdk = identity.minSdk,
            targetSdk = identity.targetSdk,
            buildPurpose = identity.buildPurpose,
            witnessIdentifier = "external-witness",
            witnessTimestampUtc = TIMESTAMP,
        )
    }

    private fun sign(
        statement: IndependentWitnessStatement.Statement,
        privateKey: java.security.PrivateKey,
    ): String {
        val verifier = Signature.getInstance(IndependentWitnessStatement.SIGNATURE_ALGORITHM)
        verifier.initSign(privateKey)
        verifier.update(IndependentWitnessStatement.canonicalBytes(statement))
        return Base64.getEncoder().encodeToString(verifier.sign())
    }

    private companion object {
        val identity = DestructiveValidationExpectedIdentity.repositoryContract()
        const val APK_SHA256 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val CERTIFICATE_SHA256 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val SOURCE_HEAD = "12".repeat(20)
        const val TIMESTAMP = "2026-08-16T03:04:05Z"
        val rsa: KeyPair by lazy {
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        }
        val otherRsa: KeyPair by lazy {
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        }
    }
}
