/**
 * Fail-closed `key=value` status-line parser.
 *
 * Duplicate keys, blank lines, unknown separators, and NUL bytes are
 * rejected so later checkpoint parsers cannot silently pick a trailing
 * conflicting value.
 */
object FailClosedStatusLines {
    private val KEY = Regex("^[a-z][a-z0-9_]*$")

    fun parseUnique(text: String): Map<String, String> {
        check(!text.contains('\u0000')) {
            "status lines must not contain a NUL byte"
        }
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        check(normalized.isNotEmpty()) { "status lines are empty" }
        check(!normalized.startsWith("\n")) {
            "status lines must not start with a blank line"
        }
        val body = normalized.removeSuffix("\n")
        check(body.isNotEmpty()) { "status lines are empty" }
        val values = linkedMapOf<String, String>()
        for (line in body.split('\n')) {
            check(line.isNotEmpty()) {
                "status lines must not contain a blank line"
            }
            val separator = line.indexOf('=')
            check(separator > 0) { "status line is not key=value" }
            val key = line.substring(0, separator)
            val value = line.substring(separator + 1)
            check(KEY.matches(key)) { "status key is not allowed: $key" }
            check(!values.containsKey(key)) { "duplicate status key: $key" }
            values[key] = value
        }
        return values
    }

    fun requireExactKeys(
        values: Map<String, String>,
        expected: Set<String>,
    ): Map<String, String> {
        val extra = values.keys - expected
        val missing = expected - values.keys
        check(extra.isEmpty()) {
            "status lines contain unknown keys: ${extra.sorted().joinToString(",")}"
        }
        check(missing.isEmpty()) {
            "status lines are missing required keys: ${missing.sorted().joinToString(",")}"
        }
        return values
    }
}
