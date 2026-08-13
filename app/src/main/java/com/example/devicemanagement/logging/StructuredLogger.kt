package com.example.devicemanagement.logging

interface StructuredLogger {
    fun info(event: String, fields: Map<String, Any?> = emptyMap())

    fun warn(event: String, fields: Map<String, Any?> = emptyMap())

    fun error(
        event: String,
        fields: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    )
}
