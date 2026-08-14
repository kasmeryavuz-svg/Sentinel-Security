package com.example.devicemanagement.ui

import com.example.devicemanagement.trigger.SensitiveActionCommands
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Modifier

class DashboardCommandsTest {
    @Test
    fun `dashboard buttons map only to the six trusted commands`() {
        assertEquals(
            SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
            DashboardCommands.disableCommand(PolicyCapability.SCREEN_CAPTURE),
        )
        assertEquals(
            SensitiveActionCommands.ENABLE_SCREEN_CAPTURE,
            DashboardCommands.enableCommand(PolicyCapability.SCREEN_CAPTURE),
        )
        assertEquals(
            SensitiveActionCommands.DISABLE_CAMERA,
            DashboardCommands.disableCommand(PolicyCapability.CAMERA),
        )
        assertEquals(
            SensitiveActionCommands.ENABLE_CAMERA,
            DashboardCommands.enableCommand(PolicyCapability.CAMERA),
        )
        assertEquals(
            SensitiveActionCommands.DISABLE_STATUS_BAR,
            DashboardCommands.disableCommand(PolicyCapability.STATUS_BAR),
        )
        assertEquals(
            SensitiveActionCommands.ENABLE_STATUS_BAR,
            DashboardCommands.enableCommand(PolicyCapability.STATUS_BAR),
        )
        assertEquals(
            setOf(
                SensitiveActionCommands.DISABLE_SCREEN_CAPTURE,
                SensitiveActionCommands.ENABLE_SCREEN_CAPTURE,
                SensitiveActionCommands.DISABLE_CAMERA,
                SensitiveActionCommands.ENABLE_CAMERA,
                SensitiveActionCommands.DISABLE_STATUS_BAR,
                SensitiveActionCommands.ENABLE_STATUS_BAR,
            ),
            DashboardCommands.trustedCommands(),
        )
    }

    @Test
    fun `public command constants remain the six trusted operations`() {
        val constants = SensitiveActionCommands::class.java.declaredFields
            .filter { field ->
                Modifier.isStatic(field.modifiers) &&
                    Modifier.isPublic(field.modifiers) &&
                    field.type == String::class.java
            }
            .associate { field -> field.name to field.get(null) }

        assertEquals(
            mapOf(
                "DISABLE_SCREEN_CAPTURE" to "disable_screen_capture",
                "ENABLE_SCREEN_CAPTURE" to "enable_screen_capture",
                "DISABLE_CAMERA" to "disable_camera",
                "ENABLE_CAMERA" to "enable_camera",
                "DISABLE_STATUS_BAR" to "disable_status_bar",
                "ENABLE_STATUS_BAR" to "enable_status_bar",
            ),
            constants,
        )
    }
}
