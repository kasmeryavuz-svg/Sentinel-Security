package com.example.devicemanagement.management

internal data class AdminComponentConfiguration(
    val packageName: String,
    val expectedComponentName: String,
    val registeredSentinelAdminComponents: List<String>,
    val isExpectedReceiverRegisteredCorrectly: Boolean,
)
