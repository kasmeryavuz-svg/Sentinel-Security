package com.example.devicemanagement.action

import com.example.devicemanagement.trigger.Trigger

/**
 * The sole app-visible production mutation controller.
 *
 * Construction is owned by device-management. App and UI code can only submit
 * untrusted trigger input to an already-composed instance.
 */
fun interface SensitiveActionController {
    fun submit(trigger: Trigger?): ActionResult
}
