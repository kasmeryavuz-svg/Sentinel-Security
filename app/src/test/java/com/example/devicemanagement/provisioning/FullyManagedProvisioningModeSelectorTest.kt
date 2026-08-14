package com.example.devicemanagement.provisioning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullyManagedProvisioningModeSelectorTest {
    @Test
    fun `admin component constant is the exact Sentinel receiver`() {
        assertEquals(
            "com.example.devicemanagement/.management.SentinelDeviceAdminReceiver",
            FullyManagedProvisioningContract.EXPECTED_ADMIN_COMPONENT,
        )
    }

    @Test
    fun `fully-managed mode is selected when offered`() {
        val selection = FullyManagedProvisioningModeSelector.select(
            listOf(
                FullyManagedProvisioningContract.MODE_FULLY_MANAGED_DEVICE,
                FullyManagedProvisioningContract.MODE_MANAGED_PROFILE,
            ),
        )

        assertEquals(
            ProvisioningModeSelection.Selected(
                FullyManagedProvisioningContract.MODE_FULLY_MANAGED_DEVICE,
            ),
            selection,
        )
    }

    @Test
    fun `fully-managed is selected even when offered among other modes`() {
        val selection = FullyManagedProvisioningModeSelector.select(
            listOf(FullyManagedProvisioningContract.MODE_FULLY_MANAGED_DEVICE),
        )

        assertEquals(
            FullyManagedProvisioningContract.MODE_FULLY_MANAGED_DEVICE,
            (selection as ProvisioningModeSelection.Selected).mode,
        )
    }

    @Test
    fun `managed-profile-only invocation fails closed`() {
        val selection = FullyManagedProvisioningModeSelector.select(
            listOf(FullyManagedProvisioningContract.MODE_MANAGED_PROFILE),
        )

        assertEquals(
            ProvisioningModeSelection.Rejected("fully_managed_mode_not_offered"),
            selection,
        )
    }

    @Test
    fun `empty allowed provisioning modes fail closed`() {
        val selection = FullyManagedProvisioningModeSelector.select(emptyList())

        assertEquals(
            ProvisioningModeSelection.Rejected("allowed_modes_empty"),
            selection,
        )
    }

    @Test
    fun `missing allowed provisioning modes fail closed`() {
        val selection = FullyManagedProvisioningModeSelector.select(null)

        assertEquals(
            ProvisioningModeSelection.Rejected("allowed_modes_missing"),
            selection,
        )
    }

    @Test
    fun `invalid allowed provisioning modes fail closed`() {
        val selection = FullyManagedProvisioningModeSelector.select(listOf(0, 1))

        assertEquals(
            ProvisioningModeSelection.Rejected("allowed_modes_invalid"),
            selection,
        )
    }
}

class ProvisioningAllowedModesParserTest {
    @Test
    fun `integer ArrayList is accepted`() {
        assertEquals(
            listOf(1, 2),
            ProvisioningAllowedModesParser.parse(arrayListOf(1, 2)),
        )
    }

    @Test
    fun `null extra is treated as missing`() {
        assertNull(ProvisioningAllowedModesParser.parse(null))
    }

    @Test
    fun `non-ArrayList extra fails closed`() {
        assertNull(ProvisioningAllowedModesParser.parse(listOf(1, 2)))
        assertNull(ProvisioningAllowedModesParser.parse(intArrayOf(1, 2)))
        assertNull(ProvisioningAllowedModesParser.parse("1"))
    }

    @Test
    fun `mixed-type ArrayList fails closed`() {
        assertNull(ProvisioningAllowedModesParser.parse(arrayListOf(1, "2")))
    }

    @Test
    fun `empty ArrayList is parsed so the selector can fail closed`() {
        val parsed = ProvisioningAllowedModesParser.parse(arrayListOf<Int>())
        assertTrue(parsed != null && parsed.isEmpty())
    }
}
