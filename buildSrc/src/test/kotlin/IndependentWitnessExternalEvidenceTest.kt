import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndependentWitnessExternalEvidenceTest {
    @Test
    fun `valid evidence schema round-trips without becoming repository state`() {
        val record = validEvidence()
        val parsed = IndependentWitnessExternalEvidence.parse(
            IndependentWitnessExternalEvidence.render(record),
        )
        assertEquals(record, parsed)
        assertEquals(IndependentWitnessExternalEvidence.FIELD_ORDER, parsedFieldOrder(record))
        assertTrue(
            IndependentWitnessExternalEvidenceSource.repositoryExternalWitnessEvidence()
                .isEmpty(),
        )
        assertTrue(IndependentWitnessAuthorityEnrollmentSource.repositoryEnrollments().isEmpty())
        assertTrue(
            IndependentWitnessAuthorityContract.establishedWitnessIdentifiers().isEmpty(),
        )
        IndependentWitnessExternalEvidence.EvidenceType.values().forEach { type ->
            val typed = IndependentWitnessExternalEvidence.parse(
                IndependentWitnessExternalEvidence.render(record.copy(evidenceType = type)),
            )
            assertEquals(type, typed.evidenceType)
            assertFalse(
                IndependentWitnessExternalEvidence.evidenceTypeImpliesIndependence(type),
            )
        }
    }

    @Test
    fun `parser rejects duplicate missing unknown and malformed evidence fields`() {
        val valid = IndependentWitnessExternalEvidence.render(validEvidence())
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace("evidence_version=1", "evidence_version=2"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(
                    "evidence_type=ORGANIZATIONAL_SEPARATION_ATTESTATION",
                    "evidence_type=SELF_ATTESTATION",
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(KEY_FINGERPRINT, "not-a-digest"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(EVIDENCE_DIGEST, "gg".repeat(32)),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(REVISION, "not-a-commit"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(REVISION, REVISION.dropLast(1)),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(ISSUED, "2026-08-16 18:00:00Z"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(ISSUED, "2026-13-01T18:00:00Z"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(EXPIRY, ISSUED),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(ISSUED, "2026-08-16T20:00:00Z")
                    .replace(EXPIRY, "2026-08-16T19:00:00Z"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace("\nreview_required=true\n", "\n"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace("witness_identifier=external-witness", "witness_identifier="),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(
                    "witness_identifier=external-witness\n",
                    "witness_identifier=external-witness\nwitness_identifier=other\n",
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(valid + "extra_critical=1\n")
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace("review_required=true", "review_required=yes"),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(
                    "witness_identifier=external-witness",
                    "witness_identifier=not a witness",
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                valid.replace(EVIDENCE_DIGEST, KEY_FINGERPRINT),
            )
        }
    }

    @Test
    fun `reserved and colliding identities cannot issue evidence`() {
        val valid = IndependentWitnessExternalEvidence.render(validEvidence())
        listOf(
            "ci",
            "github-actions",
            "cursor",
            "operator",
            "local-operator",
            "repository-owner",
            "19s-receipt",
            "19t-report",
        ).forEach { reserved ->
            assertFailsWith<IllegalStateException>(reserved) {
                IndependentWitnessExternalEvidence.parse(
                    valid.replace("witness_identifier=external-witness", "witness_identifier=$reserved"),
                )
            }
            assertFailsWith<IllegalStateException>(reserved) {
                IndependentWitnessExternalEvidence.parse(
                    valid.replace(
                        "evidence_issuer_identifier=external-attestor",
                        "evidence_issuer_identifier=$reserved",
                    ),
                )
            }
        }
        assertFailsWith<IllegalStateException> {
            IndependentWitnessExternalEvidence.parse(
                IndependentWitnessExternalEvidence.render(
                    validEvidence().copy(evidenceIssuerIdentifier = "external-witness"),
                ),
            )
        }
    }

    @Test
    fun `freshness is evaluated against an injected instant not the wall clock`() {
        val issued = IndependentWitnessExternalEvidence.parseInstantUtc(ISSUED)
        val expiry = IndependentWitnessExternalEvidence.parseInstantUtc(EXPIRY)
        assertTrue(issued.isBefore(expiry))
        assertTrue(
            IndependentWitnessExternalEvidence.isFreshAt(ISSUED, EXPIRY, Instant.parse(ISSUED)),
        )
        assertTrue(
            IndependentWitnessExternalEvidence.isFreshAt(ISSUED, EXPIRY, CLOCK),
        )
        assertFalse(
            IndependentWitnessExternalEvidence.isFreshAt(
                ISSUED,
                EXPIRY,
                Instant.parse("2026-08-16T17:59:59Z"),
            ),
        )
        assertFalse(
            IndependentWitnessExternalEvidence.isFreshAt(ISSUED, EXPIRY, Instant.parse(EXPIRY)),
        )
        val source = java.io.File("src/main/kotlin/IndependentWitnessExternalEvidence.kt").readText()
        val preparation = java.io.File(
            "src/main/kotlin/IndependentWitnessExternalEvidencePreparation.kt",
        ).readText()
        assertFalse(source.contains("Instant.now()"))
        assertFalse(preparation.contains("Instant.now()"))
        assertFalse(
            IndependentWitnessExternalEvidence.digestMatchImpliesIndependence(true),
        )
        assertFalse(
            IndependentWitnessExternalEvidence.wellFormedEvidenceImpliesIndependence(true),
        )
    }

    private fun parsedFieldOrder(record: IndependentWitnessExternalEvidence.Record): List<String> {
        return IndependentWitnessExternalEvidence.render(record)
            .trim()
            .lines()
            .map { it.substringBefore('=') }
    }

    companion object {
        const val KEY_FINGERPRINT =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val EVIDENCE_DIGEST =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val REVISION = "12".repeat(20)
        const val ISSUED = "2026-08-16T18:00:00Z"
        const val EXPIRY = "2026-08-23T18:00:00Z"
        val CLOCK: Instant = Instant.parse("2026-08-16T20:00:00Z")

        fun validEvidence(): IndependentWitnessExternalEvidence.Record {
            return IndependentWitnessExternalEvidence.Record(
                evidenceIdentifier = "org-separation-evidence",
                witnessIdentifier = "external-witness",
                evidenceType =
                    IndependentWitnessExternalEvidence.EvidenceType
                        .ORGANIZATIONAL_SEPARATION_ATTESTATION,
                evidenceIssuerIdentifier = "external-attestor",
                evidenceTimestampUtc = ISSUED,
                evidenceExpiryUtc = EXPIRY,
                repositoryRevision = REVISION,
                witnessVerificationKeySha256 = KEY_FINGERPRINT,
                evidenceDigestSha256 = EVIDENCE_DIGEST,
                reviewRequired = true,
            )
        }
    }
}
