package com.example.devicemanagement.logging

/**
 * Production logcat sanitizer.
 *
 * Structured audit SQLite evidence is a separate subsystem and is not
 * written through this sanitizer. This object only prevents diagnostic
 * logcat from disclosing secrets, signing material, intent extras, or
 * database contents.
 */
object ProductionLogSanitizer {
    const val REDACTED = "<redacted>"
    private const val MAX_VALUE_LENGTH = 256

    private val sensitiveKeys = setOf(
        "approval",
        "approval_id",
        "approval_token",
        "approval_material",
        "token",
        "access_token",
        "auth_token",
        "authorization_token",
        "secret",
        "client_secret",
        "password",
        "passwd",
        "store_password",
        "key_password",
        "keystore_password",
        "keystore",
        "keystore_path",
        "private_key",
        "privatekey",
        "private-key",
        "signing",
        "signing_config",
        "signing_cert",
        "signing_password",
        "extras",
        "intent_extras",
        "raw_extras",
        "intent_extra",
        "intent",
        "sql",
        "sql_statement",
        "database_dump",
        "database_contents",
        "database",
        "stacktrace",
        "stack_trace",
    )

    private val sensitiveFragments = listOf(
        "password",
        "passwd",
        "secret",
        "keystore",
        "private_key",
        "privatekey",
        "auth_token",
        "access_token",
    )

    fun isSensitiveKey(key: String): Boolean {
        val normalized = key.trim().lowercase().replace('-', '_')
        if (normalized in sensitiveKeys) {
            return true
        }
        return sensitiveFragments.any { fragment -> fragment in normalized }
    }

    fun sanitize(fields: Map<String, Any?>): Map<String, String> {
        if (fields.isEmpty()) {
            return emptyMap()
        }
        return fields.entries.associate { (key, value) ->
            key to sanitizeValue(key, value)
        }
    }

    fun sanitizeValue(key: String, value: Any?): String {
        if (isSensitiveKey(key)) {
            return REDACTED
        }
        if (value == null) {
            return "null"
        }
        if (value is ByteArray) {
            return REDACTED
        }
        val text = value.toString()
        if (text.length > MAX_VALUE_LENGTH) {
            return text.take(64) + "<truncated>"
        }
        return text
    }
}
