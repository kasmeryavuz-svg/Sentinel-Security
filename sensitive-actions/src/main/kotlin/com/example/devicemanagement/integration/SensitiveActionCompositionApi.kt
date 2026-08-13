package com.example.devicemanagement.integration

/**
 * Cross-module composition API reserved for the device-management facade.
 *
 * App and UI production sources are forbidden from opting in by build and test
 * guards. Runtime callers receive only the configured SensitiveActionController.
 */
@RequiresOptIn(
    message = "Controlled sensitive-action composition is device-management-only.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
annotation class SensitiveActionCompositionApi
