package com.example.devicemanagement.logging

import android.util.Log

class AndroidStructuredLogger(
    private val tag: String = "DeviceManagement",
) : StructuredLogger {
    override fun info(event: String, fields: Map<String, Any?>) {
        Log.i(tag, format(event, fields))
    }

    override fun warn(event: String, fields: Map<String, Any?>) {
        Log.w(tag, format(event, fields))
    }

    override fun error(event: String, fields: Map<String, Any?>, throwable: Throwable?) {
        val sanitized = ProductionLogSanitizer.sanitize(fields).toMutableMap()
        if (throwable != null) {
            sanitized["exception_class"] = throwable.javaClass.name
        }
        Log.e(tag, formatSanitized(event, sanitized))
    }

    private fun format(event: String, fields: Map<String, Any?>): String {
        return formatSanitized(event, ProductionLogSanitizer.sanitize(fields))
    }

    private fun formatSanitized(event: String, fields: Map<String, String>): String {
        if (fields.isEmpty()) return event
        return buildString {
            append(event)
            fields.toSortedMap().forEach { (key, value) ->
                append(" ")
                append(key)
                append("=")
                append(value)
            }
        }
    }
}
