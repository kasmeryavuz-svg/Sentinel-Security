/**
 * Parses official `apksigner verify --print-certs --verbose` output.
 *
 * Current signer identities come from `Signer #N certificate` lines.
 * Signing-certificate lineage (`Signer #N, cert #M` / "lineage") is not a
 * current signer. Distinct fingerprint count is never used as signer count.
 * If the current-signer count cannot be determined reliably, the result is
 * marked unreliable so the inspector can fail closed as UNVERIFIABLE.
 */
object DestructiveValidationApksignerSignerParser {
    private val numberOfSigners = Regex(
        """(?im)^\s*Number of signers:\s*(\d+)\s*$""",
    )
    private val malformedNumberOfSigners = Regex(
        """(?im)^\s*Number of signers:\s*(\S+)\s*$""",
    )
    private val currentSignerHeader = Regex(
        """(?im)^\s*Signer\s+#(\d+)\s+certificate\b""",
    )
    private val currentSignerSha256 = Regex(
        """(?im)^\s*Signer\s+#(\d+)\s+certificate\s+SHA-256\s+digest:\s*(.+?)\s*$""",
    )
    private val lineageSigner = Regex(
        """(?im)^\s*Signer\s+#(\d+)\s*,\s*cert(?:ificate)?\s+#""",
    )
    private val lineageWord = Regex("""(?i)lineage""")
    private val looseSha256Line = Regex(
        """(?im)^\s*(?:certificate\s+)?SHA-256\s+digest:\s*(.+?)\s*$""",
    )

    data class Parse(
        val numberOfSignersDeclared: Int?,
        val currentSignerIndexes: Set<Int>,
        val currentCertificateSha256BySigner: Map<Int, String>,
        val lineagePresent: Boolean,
        val currentSignerCount: Int?,
        val reliable: Boolean,
        val unreliabilityReason: String?,
    ) {
        val currentCertificateSha256: String?
            get() = currentCertificateSha256BySigner.values.distinct().singleOrNull()
                .takeIf { currentSignerCount == 1 }
    }

    fun parse(output: String): Parse {
        val declaredMatch = numberOfSigners.find(output)
        val declaredRaw = malformedNumberOfSigners.find(output)?.groupValues?.get(1)
        val declared = declaredMatch?.groupValues?.get(1)?.toInt()
        if (declaredRaw != null && declared == null) {
            return unreliable(
                numberOfSignersDeclared = null,
                currentSignerIndexes = emptySet(),
                currentCertificates = emptyMap(),
                lineagePresent = lineagePresent(output),
                reason = "malformed_number_of_signers",
            )
        }
        val currentIndexes = currentSignerHeader.findAll(output)
            .map { it.groupValues[1].toInt() }
            .toSet()
        val certificates = linkedMapOf<Int, String>()
        currentSignerSha256.findAll(output).forEach { match ->
            val index = match.groupValues[1].toInt()
            val digest = ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(
                match.groupValues[2],
            )
            if (digest == null) {
                return unreliable(
                    numberOfSignersDeclared = declared,
                    currentSignerIndexes = currentIndexes,
                    currentCertificates = certificates.toMap(),
                    lineagePresent = lineagePresent(output),
                    reason = "malformed_current_signer_certificate_digest",
                )
            }
            val previous = certificates[index]
            if (previous != null && previous != digest) {
                return unreliable(
                    numberOfSignersDeclared = declared,
                    currentSignerIndexes = currentIndexes,
                    currentCertificates = certificates.toMap(),
                    lineagePresent = lineagePresent(output),
                    reason = "contradictory_current_signer_certificate_digest",
                )
            }
            certificates[index] = digest
        }
        val lineage = lineagePresent(output)
        if (currentIndexes.isNotEmpty() && !contiguousFromOne(currentIndexes)) {
            return unreliable(
                numberOfSignersDeclared = declared,
                currentSignerIndexes = currentIndexes,
                currentCertificates = certificates,
                lineagePresent = lineage,
                reason = "non_contiguous_current_signer_indexes",
            )
        }
        if (declared != null && declared != currentIndexes.size) {
            return unreliable(
                numberOfSignersDeclared = declared,
                currentSignerIndexes = currentIndexes,
                currentCertificates = certificates,
                lineagePresent = lineage,
                reason = "contradictory_signer_count_and_indexes",
            )
        }
        if (declared != null &&
            declared > 0 &&
            certificates.size != declared &&
            certificates.keys != currentIndexes
        ) {
            return unreliable(
                numberOfSignersDeclared = declared,
                currentSignerIndexes = currentIndexes,
                currentCertificates = certificates,
                lineagePresent = lineage,
                reason = "contradictory_signer_count_and_fingerprint_output",
            )
        }
        if (declared != null && declared > 0 && certificates.size != declared) {
            return unreliable(
                numberOfSignersDeclared = declared,
                currentSignerIndexes = currentIndexes,
                currentCertificates = certificates,
                lineagePresent = lineage,
                reason = "contradictory_signer_count_and_fingerprint_output",
            )
        }
        if (declared == null && currentIndexes.isEmpty()) {
            val looseFingerprints = looseSha256Line.findAll(output)
                .mapNotNull {
                    ReleaseArtifactSecurityVerifier.normalizeSha256Fingerprint(it.groupValues[1])
                }
                .toList()
            if (looseFingerprints.isNotEmpty()) {
                return unreliable(
                    numberOfSignersDeclared = null,
                    currentSignerIndexes = emptySet(),
                    currentCertificates = emptyMap(),
                    lineagePresent = lineage,
                    reason = "missing_signer_indexes",
                )
            }
            return Parse(
                numberOfSignersDeclared = null,
                currentSignerIndexes = emptySet(),
                currentCertificateSha256BySigner = emptyMap(),
                lineagePresent = lineage,
                currentSignerCount = 0,
                reliable = true,
                unreliabilityReason = null,
            )
        }
        val count = declared ?: currentIndexes.size
        return Parse(
            numberOfSignersDeclared = declared,
            currentSignerIndexes = currentIndexes,
            currentCertificateSha256BySigner = certificates,
            lineagePresent = lineage,
            currentSignerCount = count,
            reliable = true,
            unreliabilityReason = null,
        )
    }

    private fun lineagePresent(output: String): Boolean {
        return lineageWord.containsMatchIn(output) || lineageSigner.containsMatchIn(output)
    }

    private fun contiguousFromOne(indexes: Set<Int>): Boolean {
        if (indexes.isEmpty()) {
            return true
        }
        val expected = (1..indexes.size).toSet()
        return indexes == expected
    }

    private fun unreliable(
        numberOfSignersDeclared: Int?,
        currentSignerIndexes: Set<Int>,
        currentCertificates: Map<Int, String>,
        lineagePresent: Boolean,
        reason: String,
    ): Parse {
        return Parse(
            numberOfSignersDeclared = numberOfSignersDeclared,
            currentSignerIndexes = currentSignerIndexes,
            currentCertificateSha256BySigner = currentCertificates,
            lineagePresent = lineagePresent,
            currentSignerCount = null,
            reliable = false,
            unreliabilityReason = reason,
        )
    }
}
