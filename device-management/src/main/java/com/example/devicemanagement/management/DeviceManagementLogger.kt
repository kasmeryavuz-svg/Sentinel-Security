package com.example.devicemanagement.management

import com.example.devicemanagement.logging.StructuredLogger

internal interface DeviceManagementLogger {
    fun info(event: String, fields: Map<String, Any?>)

    fun warn(event: String, fields: Map<String, Any?>)

    fun error(
        event: String,
        fields: Map<String, Any?>,
        throwable: Throwable?,
    )
}

internal class StructuredDeviceManagementLogger(
    private val delegate: StructuredLogger,
) : DeviceManagementLogger {
    override fun info(event: String, fields: Map<String, Any?>) {
        delegate.info(event, fields)
    }

    override fun warn(event: String, fields: Map<String, Any?>) {
        delegate.warn(event, fields)
    }

    override fun error(
        event: String,
        fields: Map<String, Any?>,
        throwable: Throwable?,
    ) {
        delegate.error(event, fields, throwable)
    }
}
