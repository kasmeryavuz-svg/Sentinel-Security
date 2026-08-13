package com.example.devicemanagement.logging

import android.util.Log
import com.example.devicemanagement.management.DeviceManagementLogger

class AndroidStructuredLogger(
    private val tag: String = "DeviceManagement",
) : StructuredLogger, DeviceManagementLogger {
    override fun info(event: String, fields: Map<String, Any?>) {
        Log.i(tag, format(event, fields))
    }

    override fun warn(event: String, fields: Map<String, Any?>) {
        Log.w(tag, format(event, fields))
    }

    override fun error(event: String, fields: Map<String, Any?>, throwable: Throwable?) {
        Log.e(tag, format(event, fields), throwable)
    }

    private fun format(event: String, fields: Map<String, Any?>): String {
        if (fields.isEmpty()) return event
        return buildString {
            append(event)
            fields.toSortedMap().forEach { (key, value) ->
                append(" ")
                append(key)
                append("=")
                append(value?.toString() ?: "null")
            }
        }
    }
}
