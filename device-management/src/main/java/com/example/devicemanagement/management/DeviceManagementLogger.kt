package com.example.devicemanagement.management

interface DeviceManagementLogger {
    fun info(event: String, fields: Map<String, Any?>)

    fun warn(event: String, fields: Map<String, Any?>)

    fun error(
        event: String,
        fields: Map<String, Any?>,
        throwable: Throwable?,
    )
}
