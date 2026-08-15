import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DestructiveValidationBuildPurposeParserTest {
    @Test
    fun `exactly one well-formed metadata entry is observed`() {
        val parsed = DestructiveValidationBuildPurposeParser.parse(ONE_PURPOSE)
        assertEquals(DestructiveValidationBuildPurposeParser.STATUS_OBSERVED, parsed.status)
        assertEquals("DISPOSABLE_DEVICE_VALIDATION", parsed.observed)
    }

    @Test
    fun `missing metadata is unavailable and does not copy the expected contract`() {
        val parsed = DestructiveValidationBuildPurposeParser.parse(NO_PURPOSE)
        assertEquals(DestructiveValidationBuildPurposeParser.STATUS_UNAVAILABLE, parsed.status)
        assertNull(parsed.observed)
        assertEquals("build_purpose_metadata_missing", parsed.detail)
    }

    @Test
    fun `duplicate metadata fails closed`() {
        val parsed = DestructiveValidationBuildPurposeParser.parse(DUPLICATE_PURPOSE)
        assertEquals(DestructiveValidationBuildPurposeParser.STATUS_DUPLICATE, parsed.status)
        assertNull(parsed.observed)
    }

    @Test
    fun `empty or malformed values fail closed`() {
        val empty = DestructiveValidationBuildPurposeParser.parse(EMPTY_VALUE)
        assertEquals(DestructiveValidationBuildPurposeParser.STATUS_MALFORMED, empty.status)
        assertNull(empty.observed)

        val spaces = DestructiveValidationBuildPurposeParser.parse(SPACED_VALUE)
        assertEquals(DestructiveValidationBuildPurposeParser.STATUS_MALFORMED, spaces.status)
        assertNull(spaces.observed)
    }

    @Test
    fun `unexpected well-formed value is observed but not rewritten to the expected purpose`() {
        val parsed = DestructiveValidationBuildPurposeParser.parse(WRONG_VALUE)
        assertEquals(DestructiveValidationBuildPurposeParser.STATUS_OBSERVED, parsed.status)
        assertEquals("SOMETHING_ELSE", parsed.observed)
    }

    @Test
    fun `uninspectable helper never invents an observed purpose`() {
        val parsed = DestructiveValidationBuildPurposeParser.uninspectable("aapt2 missing")
        assertEquals(DestructiveValidationBuildPurposeParser.STATUS_UNINSPECTABLE, parsed.status)
        assertNull(parsed.observed)
    }

    private companion object {
        const val NAME =
            "com.example.devicemanagement.DESTRUCTIVE_VALIDATION_BUILD_PURPOSE"

        val ONE_PURPOSE = """
            N: android=http://schemas.android.com/apk/res/android
              E: manifest (line=2)
                E: application (line=4)
                  E: activity (line=8)
                    A: android:name(0x01010003)="com.example.devicemanagement.ui.MainActivity" (Raw: "com.example.devicemanagement.ui.MainActivity")
                  E: meta-data (line=20)
                    A: android:name(0x01010003)="$NAME" (Raw: "$NAME")
                    A: android:value(0x01010024)="DISPOSABLE_DEVICE_VALIDATION" (Raw: "DISPOSABLE_DEVICE_VALIDATION")
        """.trimIndent()

        val NO_PURPOSE = """
            E: manifest (line=2)
              E: application (line=4)
                E: meta-data (line=10)
                  A: android:name(0x01010003)="android.app.device_admin" (Raw: "android.app.device_admin")
                  A: android:resource(0x01010025)="@xml/device_admin_receiver"
        """.trimIndent()

        val DUPLICATE_PURPOSE = """
            E: application (line=4)
              E: meta-data (line=20)
                A: android:name(0x01010003)="$NAME" (Raw: "$NAME")
                A: android:value(0x01010024)="DISPOSABLE_DEVICE_VALIDATION" (Raw: "DISPOSABLE_DEVICE_VALIDATION")
              E: meta-data (line=24)
                A: android:name(0x01010003)="$NAME" (Raw: "$NAME")
                A: android:value(0x01010024)="DISPOSABLE_DEVICE_VALIDATION" (Raw: "DISPOSABLE_DEVICE_VALIDATION")
        """.trimIndent()

        val EMPTY_VALUE = """
            E: meta-data (line=20)
              A: android:name(0x01010003)="$NAME" (Raw: "$NAME")
              A: android:value(0x01010024)="" (Raw: "")
        """.trimIndent()

        val SPACED_VALUE = """
            E: meta-data (line=20)
              A: android:name(0x01010003)="$NAME" (Raw: "$NAME")
              A: android:value(0x01010024)="DISPOSABLE DEVICE VALIDATION" (Raw: "DISPOSABLE DEVICE VALIDATION")
        """.trimIndent()

        val WRONG_VALUE = """
            E: meta-data (line=20)
              A: android:name(0x01010003)="$NAME" (Raw: "$NAME")
              A: android:value(0x01010024)="SOMETHING_ELSE" (Raw: "SOMETHING_ELSE")
        """.trimIndent()
    }
}
