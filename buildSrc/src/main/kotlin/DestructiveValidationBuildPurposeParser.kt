/**
 * Parses official `aapt2 dump xmltree` AndroidManifest output for the
 * disposable-validation build-purpose metadata entry.
 *
 * Observed values come only from APK manifest bytes. The expected
 * contract is never consulted. Missing, duplicate, empty, or malformed
 * metadata fails closed.
 */
object DestructiveValidationBuildPurposeParser {
    const val STATUS_OBSERVED = "OBSERVED"
    const val STATUS_UNAVAILABLE = "UNAVAILABLE"
    const val STATUS_DUPLICATE = "DUPLICATE"
    const val STATUS_MALFORMED = "MALFORMED"
    const val STATUS_UNINSPECTABLE = "UNINSPECTABLE"

    private val xmlElement = Regex("""^(\s*)E:\s+([A-Za-z0-9_.-]+)\b""")
    private val xmlName = Regex("""android:name[^=]*="([^"]*)"""")
    private val xmlValue = Regex("""android:value[^=]*="([^"]*)"""")
    private val wellFormedPurpose = Regex("""^[A-Z][A-Z0-9_]{0,127}$""")

    data class Observation(
        val status: String,
        val observed: String?,
        val detail: String,
    )

    fun uninspectable(detail: String): Observation {
        return Observation(
            status = STATUS_UNINSPECTABLE,
            observed = null,
            detail = detail,
        )
    }

    fun parse(manifestXmltree: String): Observation {
        val values = mutableListOf<String?>()
        val lines = manifestXmltree.lineSequence().toList()
        var index = 0
        while (index < lines.size) {
            val element = xmlElement.find(lines[index])
            if (element == null || element.groupValues[2] != "meta-data") {
                index += 1
                continue
            }
            val indent = element.groupValues[1].length
            var name: String? = null
            var value: String? = null
            var sawValueAttribute = false
            var cursor = index + 1
            while (cursor < lines.size) {
                val next = xmlElement.find(lines[cursor])
                if (next != null && next.groupValues[1].length <= indent) {
                    break
                }
                xmlName.find(lines[cursor])?.groupValues?.get(1)?.let { name = it }
                val valueMatch = xmlValue.find(lines[cursor])
                if (valueMatch != null) {
                    sawValueAttribute = true
                    value = valueMatch.groupValues[1]
                }
                cursor += 1
            }
            if (name == DestructiveValidationExpectedIdentity.BUILD_PURPOSE_METADATA_NAME) {
                values += if (sawValueAttribute) value else null
            }
            index = cursor
        }
        if (values.isEmpty()) {
            return Observation(
                status = STATUS_UNAVAILABLE,
                observed = null,
                detail = "build_purpose_metadata_missing",
            )
        }
        if (values.size > 1) {
            return Observation(
                status = STATUS_DUPLICATE,
                observed = null,
                detail = "build_purpose_metadata_duplicate",
            )
        }
        val raw = values.single()
        if (raw == null || raw.isEmpty() || '\n' in raw || !wellFormedPurpose.matches(raw)) {
            return Observation(
                status = STATUS_MALFORMED,
                observed = null,
                detail = "build_purpose_metadata_malformed",
            )
        }
        return Observation(
            status = STATUS_OBSERVED,
            observed = raw,
            detail = "build_purpose_metadata_observed",
        )
    }
}
