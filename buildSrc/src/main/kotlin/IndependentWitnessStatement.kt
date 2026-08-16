import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Versioned independent-witness statement.
 *
 * Canonical signed bytes are the UTF-8 encoding of the signed fields in
 * [SIGNED_FIELD_ORDER], each rendered as `key=value` and terminated by a
 * single LF (`U+000A`). There is no BOM, no CR, and no signature field
 * inside those bytes. The JCA algorithm is [SIGNATURE_ALGORITHM]. The
 * `signature` field is unwrapped standard Base64 of the raw signature
 * bytes.
 *
 * A valid signature proves control of the corresponding public key. It
 * does not establish organizational independence or approval.
 */
object IndependentWitnessStatement {
    const val CHECKPOINT = "19T"
    const val STATEMENT_VERSION = "1"
    const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    const val STATEMENT_PROPERTY = "sentinel.independentWitnessStatement"
    const val VERIFICATION_KEY_PROPERTY = "sentinel.witnessVerificationCertificate"

    val SIGNED_FIELD_ORDER = listOf(
        "checkpoint",
        "statement_version",
        "candidate_apk_sha256",
        "validation_certificate_sha256",
        "source_head_claimed",
        "package_name",
        "admin_component",
        "policies",
        "min_sdk",
        "target_sdk",
        "build_purpose",
        "witness_identifier",
        "witness_timestamp_utc",
    )

    val ALL_FIELD_ORDER = SIGNED_FIELD_ORDER + listOf(
        "signature_algorithm",
        "signature",
    )

    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val GIT_REVISION = Regex("^[0-9a-f]{40}$")
    private val TIMESTAMP_UTC = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")
    private val WITNESS_IDENTIFIER = Regex("^[A-Za-z0-9._:@/-]{1,128}$")
    private val WHITESPACE = Regex("\\s")

    data class Statement(
        val candidateApkSha256: String,
        val validationCertificateSha256: String,
        val sourceHeadClaimed: String,
        val packageName: String,
        val adminComponent: String,
        val policies: List<String>,
        val minSdk: Int,
        val targetSdk: Int,
        val buildPurpose: String,
        val witnessIdentifier: String,
        val witnessTimestampUtc: String,
    )

    data class ParsedStatement(
        val statement: Statement,
        val signatureAlgorithm: String,
        val signatureBase64: String,
    )

    fun canonicalBytes(statement: Statement): ByteArray {
        val normalized = normalize(statement)
        return SIGNED_FIELD_ORDER.joinToString("") { key ->
            "$key=${signedValue(normalized, key)}\n"
        }.toByteArray(StandardCharsets.UTF_8)
    }

    fun render(statement: Statement, signatureBase64: String): String {
        val normalized = normalize(statement)
        check(signatureBase64.isNotEmpty() && !WHITESPACE.containsMatchIn(signatureBase64)) {
            "witness signature must be unwrapped Base64"
        }
        return buildString {
            SIGNED_FIELD_ORDER.forEach { key ->
                append(key)
                append('=')
                append(signedValue(normalized, key))
                append('\n')
            }
            append("signature_algorithm=")
            append(SIGNATURE_ALGORITHM)
            append('\n')
            append("signature=")
            append(signatureBase64)
            append('\n')
        }
    }

    fun parse(text: String): ParsedStatement {
        val values = FailClosedStatusLines.requireExactKeys(
            FailClosedStatusLines.parseUnique(text),
            ALL_FIELD_ORDER.toSet(),
        )
        check(values.getValue("checkpoint") == CHECKPOINT) {
            "witness statement checkpoint must be $CHECKPOINT"
        }
        check(values.getValue("statement_version") == STATEMENT_VERSION) {
            "unsupported witness statement version"
        }
        val apkSha256 = normalizeSha256(values.getValue("candidate_apk_sha256"))
        val certificateSha256 = normalizeSha256(
            values.getValue("validation_certificate_sha256"),
        )
        val sourceHead = values.getValue("source_head_claimed").trim().lowercase()
        check(GIT_REVISION.matches(sourceHead)) {
            "witness statement source_head_claimed must be an exact 40-hex commit"
        }
        val packageName = values.getValue("package_name")
        val adminComponent = values.getValue("admin_component")
        val policies = parsePolicies(values.getValue("policies"))
        val minSdk = parsePositiveInt(values.getValue("min_sdk"), "min_sdk")
        val targetSdk = parsePositiveInt(values.getValue("target_sdk"), "target_sdk")
        val buildPurpose = values.getValue("build_purpose")
        check(buildPurpose.isNotEmpty()) { "witness statement build_purpose is missing" }
        val witnessIdentifier = values.getValue("witness_identifier")
        check(WITNESS_IDENTIFIER.matches(witnessIdentifier)) {
            "witness_identifier is malformed"
        }
        val timestamp = values.getValue("witness_timestamp_utc")
        check(TIMESTAMP_UTC.matches(timestamp)) {
            "witness_timestamp_utc must be YYYY-MM-DDTHH:MM:SSZ"
        }
        val algorithm = values.getValue("signature_algorithm")
        check(algorithm == SIGNATURE_ALGORITHM) {
            "unsupported witness signature algorithm"
        }
        val signature = values.getValue("signature")
        check(signature.isNotEmpty() && !WHITESPACE.containsMatchIn(signature)) {
            "witness signature must be unwrapped Base64"
        }
        val statement = normalize(
            Statement(
                candidateApkSha256 = apkSha256,
                validationCertificateSha256 = certificateSha256,
                sourceHeadClaimed = sourceHead,
                packageName = packageName,
                adminComponent = adminComponent,
                policies = policies,
                minSdk = minSdk,
                targetSdk = targetSdk,
                buildPurpose = buildPurpose,
                witnessIdentifier = witnessIdentifier,
                witnessTimestampUtc = timestamp,
            ),
        )
        return ParsedStatement(
            statement = statement,
            signatureAlgorithm = algorithm,
            signatureBase64 = signature,
        )
    }

    fun parseFile(file: File): ParsedStatement {
        return parse(readStableText(file, "witness statement"))
    }

    fun loadVerificationKey(file: File): PublicKey {
        val bytes = readStableBytes(file, "witness verification key")
        val certificate = runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(bytes.inputStream()) as? X509Certificate
        }.getOrNull()
        if (certificate != null) {
            return certificate.publicKey
        }
        return try {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
        } catch (failed: GeneralSecurityException) {
            error(
                "witness verification key is not an X.509 certificate or RSA public key",
            )
        }
    }

    fun verifySignature(
        statement: Statement,
        signatureBase64: String,
        publicKey: PublicKey,
    ): Boolean {
        if (signatureBase64.isEmpty() || WHITESPACE.containsMatchIn(signatureBase64)) {
            return false
        }
        val decoded = try {
            Base64.getDecoder().decode(signatureBase64)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return try {
            val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(canonicalBytes(statement))
            verifier.verify(decoded)
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    fun matchesCandidate(
        statement: Statement,
        apkSha256: String,
        validationCertificateSha256: String,
        sourceHeadClaimed: String,
        identity: DestructiveValidationExpectedIdentity,
    ): Boolean {
        return statement.candidateApkSha256 == apkSha256 &&
            statement.validationCertificateSha256 == validationCertificateSha256 &&
            statement.sourceHeadClaimed == sourceHeadClaimed &&
            statement.packageName == identity.packageName &&
            statement.adminComponent == identity.adminComponent &&
            statement.policies == identity.policies &&
            statement.minSdk == identity.minSdk &&
            statement.targetSdk == identity.targetSdk &&
            statement.buildPurpose == identity.buildPurpose
    }

    private fun normalize(statement: Statement): Statement {
        return statement.copy(
            candidateApkSha256 = normalizeSha256(statement.candidateApkSha256),
            validationCertificateSha256 = normalizeSha256(
                statement.validationCertificateSha256,
            ),
            sourceHeadClaimed = statement.sourceHeadClaimed.trim().lowercase(),
            policies = statement.policies.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    private fun signedValue(statement: Statement, key: String): String {
        return when (key) {
            "checkpoint" -> CHECKPOINT
            "statement_version" -> STATEMENT_VERSION
            "candidate_apk_sha256" -> statement.candidateApkSha256
            "validation_certificate_sha256" -> statement.validationCertificateSha256
            "source_head_claimed" -> statement.sourceHeadClaimed
            "package_name" -> statement.packageName
            "admin_component" -> statement.adminComponent
            "policies" -> statement.policies.joinToString(",")
            "min_sdk" -> statement.minSdk.toString()
            "target_sdk" -> statement.targetSdk.toString()
            "build_purpose" -> statement.buildPurpose
            "witness_identifier" -> statement.witnessIdentifier
            "witness_timestamp_utc" -> statement.witnessTimestampUtc
            else -> error("unsigned field cannot be canonicalized: $key")
        }
    }

    private fun parsePolicies(raw: String): List<String> {
        check(raw.isNotEmpty()) { "witness statement policies are missing" }
        check(!raw.contains(' ')) { "witness statement policies must not contain spaces" }
        val policies = raw.split(',')
        check(policies.none { it.isEmpty() }) {
            "witness statement policies contain an empty value"
        }
        check(policies.size == policies.toSet().size) {
            "witness statement policies contain a duplicate value"
        }
        return policies
    }

    private fun parsePositiveInt(raw: String, field: String): Int {
        check(raw.matches(Regex("^[0-9]+$"))) { "$field is not a decimal integer" }
        return raw.toInt()
    }

    private fun normalizeSha256(value: String): String {
        val normalized = value.trim().lowercase()
        check(SHA256.matches(normalized)) {
            "witness statement digest is not a valid SHA-256 value"
        }
        return normalized
    }

    internal fun readStableText(file: File, label: String): String {
        return readStableBytes(file, label).toString(StandardCharsets.UTF_8)
    }

    internal fun readStableBytes(file: File, label: String): ByteArray {
        rejectSymlinkPath(file)
        val before = Files.readAttributes(
            file.toPath(),
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        check(before.isRegularFile) { "$label must be a regular file" }
        val first = Files.readAllBytes(file.toPath())
        val middle = Files.readAttributes(
            file.toPath(),
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        val second = Files.readAllBytes(file.toPath())
        val after = Files.readAttributes(
            file.toPath(),
            BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS,
        )
        check(
            stableIdentity(before, middle) &&
                stableIdentity(middle, after) &&
                first.contentEquals(second),
        ) {
            "$label changed during read"
        }
        return first
    }

    internal fun rejectSymlinkPath(file: File) {
        var cursor = file.toPath().toAbsolutePath().normalize()
        while (true) {
            check(!Files.isSymbolicLink(cursor)) {
                "$file must not resolve through a symbolic link"
            }
            cursor = cursor.parent ?: break
        }
    }

    private fun stableIdentity(
        first: BasicFileAttributes,
        second: BasicFileAttributes,
    ): Boolean {
        return first.isRegularFile == second.isRegularFile &&
            first.fileKey() == second.fileKey() &&
            first.size() == second.size() &&
            first.lastModifiedTime() == second.lastModifiedTime() &&
            first.creationTime() == second.creationTime()
    }
}
