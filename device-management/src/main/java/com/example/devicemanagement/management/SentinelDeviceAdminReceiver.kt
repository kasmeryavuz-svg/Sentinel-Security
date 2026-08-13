package com.example.devicemanagement.management

import android.app.admin.DeviceAdminReceiver

/**
 * Passive marker receiver used only for component and ownership diagnostics.
 *
 * It intentionally declares no callbacks or policy operations.
 */
class SentinelDeviceAdminReceiver : DeviceAdminReceiver()
