package com.example.devicemanagement.persistence

import com.example.devicemanagement.integration.PolicyMutationResult
import com.example.devicemanagement.integration.SensitiveActionAuthorization
import com.example.devicemanagement.integration.SensitiveActionPolicyBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PolicyBackendStateRepositoryTest {
    @Test
    fun `authorization is re-read from the backend on every load`() {
        val backend = MutableAuthorizationBackend()
        val repository = PolicyBackendStateRepository(backend)

        val first = repository.load()
        backend.authorization = backend.authorization.copy(verifiedDeviceOwner = false)
        val second = repository.load()

        assertTrue(first.verifiedDeviceOwner)
        assertFalse(second.verifiedDeviceOwner)
        assertEquals(2, backend.reads)
    }

    @Test
    fun `repository source does not persist or cache authorization`() {
        val source = File(
            "src/main/kotlin/com/example/devicemanagement/persistence/PolicyBackendStateRepository.kt",
        ).readText()
        assertTrue(source.contains("backend.currentAuthorization()"))
        assertFalse(source.contains("SharedPreferences"))
        assertFalse(source.contains("SQLite"))
        assertFalse(source.contains("FileOutputStream"))
        assertFalse(source.contains("var cached"))
        assertFalse(source.contains("var authorization"))
    }

    private class MutableAuthorizationBackend : SensitiveActionPolicyBackend {
        var authorization = SensitiveActionAuthorization(
            policyServiceAvailable = true,
            sensitiveActionsEnabled = true,
            verifiedDeviceOwner = true,
            profileOwner = false,
            expectedAdminReceiverRegistered = true,
            expectedAdminActive = true,
            managementStateConsistent = true,
        )
        var reads = 0

        override fun currentAuthorization(): SensitiveActionAuthorization {
            reads += 1
            return authorization
        }

        override fun applyScreenCaptureDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult = PolicyMutationResult.Applied(disabled, disabled)

        override fun applyCameraDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult = PolicyMutationResult.Applied(disabled, disabled)

        override fun applyStatusBarDisabled(
            disabled: Boolean,
            correlationId: String,
        ): PolicyMutationResult = PolicyMutationResult.Applied(disabled, disabled)
    }
}
