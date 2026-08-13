package com.example.devicemanagement.trigger

data class Trigger(
    val command: String?,
    val requestId: String?,
    val expiresAtEpochMillis: Long?,
)
