package com.example.devicemanagement.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionLogSanitizerTest {
    @Test
    fun `sensitive keys are redacted`() {
        val sanitized = ProductionLogSanitizer.sanitize(
            mapOf(
                "password" to "hunter2",
                "keystore_password" to "secret",
                "intent_extras" to "command=disable_camera",
                "approval" to "capability-bytes",
                "correlation_id" to "visible-correlation",
                "action" to "disable_camera",
            ),
        )

        assertEquals(ProductionLogSanitizer.REDACTED, sanitized["password"])
        assertEquals(ProductionLogSanitizer.REDACTED, sanitized["keystore_password"])
        assertEquals(ProductionLogSanitizer.REDACTED, sanitized["intent_extras"])
        assertEquals(ProductionLogSanitizer.REDACTED, sanitized["approval"])
        assertEquals("visible-correlation", sanitized["correlation_id"])
        assertEquals("disable_camera", sanitized["action"])
    }

    @Test
    fun `byte arrays and oversized values cannot dump database contents`() {
        assertEquals(
            ProductionLogSanitizer.REDACTED,
            ProductionLogSanitizer.sanitizeValue("blob", ByteArray(8)),
        )
        val truncated = ProductionLogSanitizer.sanitizeValue("note", "x".repeat(300))
        assertTrue(truncated.endsWith("<truncated>"))
        assertTrue(truncated.length < 300)
    }

    @Test
    fun `ordinary diagnostic keys stay visible`() {
        assertFalse(ProductionLogSanitizer.isSensitiveKey("correlation_id"))
        assertFalse(ProductionLogSanitizer.isSensitiveKey("action"))
        assertFalse(ProductionLogSanitizer.isSensitiveKey("reason"))
        assertTrue(ProductionLogSanitizer.isSensitiveKey("store_password"))
        assertTrue(ProductionLogSanitizer.isSensitiveKey("private-key"))
    }
}
