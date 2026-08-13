package com.example.devicemanagement.management

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidMonotonicCompositionGuardTest {
    @Test
    fun `production composition uses Android elapsed realtime monotonic clock`() {
        val sourceRoot = File(
            requireNotNull(System.getProperty("deviceManagementSourceDir")),
        )
        val compositionSource = File(
            sourceRoot,
            "java/com/example/devicemanagement/management/DeviceManagementSensitiveActions.kt",
        ).readText()

        assertTrue(compositionSource.contains("AndroidElapsedRealtimeMonotonicTimeSource"))
        assertTrue(compositionSource.contains("SystemClock.elapsedRealtime()"))
        assertTrue(
            compositionSource.contains(
                "monotonicTimeSource = AndroidElapsedRealtimeMonotonicTimeSource",
            ),
        )
        assertFalse(compositionSource.contains("System.nanoTime"))
        assertFalse(compositionSource.contains("currentTimeMillis"))
    }
}
