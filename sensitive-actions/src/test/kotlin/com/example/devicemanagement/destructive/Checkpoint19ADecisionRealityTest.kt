package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19ADecisionRealityTest {
    @Test
    fun `approval-request readiness remains YES after the human approval is recorded`() {
        assertEquals("YES", Checkpoint19ADecision.ARCHITECTURE_READY_RECONFIRMED)
        assertEquals("YES", Checkpoint19ADecision.DESTRUCTIVE_IMPLEMENTATION_APPROVAL_REQUEST_READY)
        assertEquals("YES", Checkpoint19ADecision.DESTRUCTIVE_IMPLEMENTATION_APPROVED)
        assertEquals("YES", Checkpoint18Decision.ARCHITECTURE_READY_FOR_SEPARATE_DESTRUCTIVE_APPROVAL)
        assertFalse(Checkpoint19ADecision.DESTRUCTIVE_IMPLEMENTATION_PRESENT)
        assertFalse(Checkpoint19ADecision.DESTRUCTIVE_HARDWARE_VALIDATION_PRESENT)
        assertTrue(Checkpoint19ADecision.REQUIRED_APPROVAL_SENTENCE.contains("factory-resetting"))
        assertTrue(
            Checkpoint19ADecision.REQUIRED_APPROVAL_SENTENCE.contains(
                "dedicated disposable Sentinel test device",
            ),
        )
        assertFalse(
            Checkpoint19ADecision.REQUIRED_APPROVAL_SENTENCE.contains("I have approved"),
        )
    }

    @Test
    fun `implementation presence is 19B while 19A still records the approval`() {
        assertFalse(Checkpoint19ADecision.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertFalse(Checkpoint19ADecision.DESTRUCTIVE_POLICY_WRAPPER_PRESENT)
        assertFalse(Checkpoint19ADecision.DESTRUCTIVE_METADATA_PRESENT)
        assertFalse(Checkpoint19ADecision.PRODUCTION_REACHABLE_SIMULATION)
        assertFalse(Checkpoint19ADecision.DESTRUCTIVE_PRODUCTION_SIGNING_ENABLED)
        assertFalse(Checkpoint19ADecision.DESTRUCTIVE_HARDWARE_VALIDATION_APPROVED)
        assertTrue(Checkpoint19ADecision.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertFalse(Checkpoint19ADecision.GRAPHENEOS_WIPE_BEHAVIOR_VERIFIED)
        assertFalse(Checkpoint19ADecision.WIPE_DATA_METADATA_REVIEW_APPROVED)
        assertFalse(Checkpoint19ADecision.DPM_DESTRUCTIVE_ALLOWLIST_REVIEW_APPROVED)
        assertFalse(Checkpoint19ADecision.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertTrue(Checkpoint18Decision.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertTrue(Checkpoint18Decision.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertFalse(Checkpoint18Decision.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_EXECUTOR_PRESENT)
        assertTrue(Checkpoint17BHardBlock.DESTRUCTIVE_HUMAN_APPROVAL_RECORDED)
        assertFalse(Checkpoint17BHardBlock.DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_RUNTIME_COOLDOWN_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_DURABLE_AUDIT_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_ARTIFACT_IDENTITY_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_HUMAN_APPROVAL_ENFORCED)
        assertTrue(Checkpoint17BHardBlock.REAL_DESTRUCTIVE_CHAIN_WIPE_OPTION_POLICY_ENFORCED)
    }

    @Test
    fun `no fake approval or artifact hash is recorded`() {
        assertNull(Checkpoint19ADecision.RECORDED_DISPOSABLE_DEVICE_CERTIFICATE_SHA256)
        assertNull(Checkpoint19ADecision.RECORDED_DISPOSABLE_DEVICE_ARTIFACT_SHA256)
        assertEquals(
            "Yavuz Kasmer <kasmeryavuz@gmail.com>",
            Checkpoint19ADecision.RECORDED_APPROVAL_OPERATOR,
        )
        assertEquals("2026-08-15T14:28:00Z", Checkpoint19ADecision.RECORDED_APPROVAL_TIMESTAMP)
        assertEquals(
            Checkpoint19ADecision.REQUIRED_APPROVAL_SENTENCE,
            Checkpoint19ADecision.RECORDED_APPROVAL_SENTENCE,
        )
        val source = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19ADecision.kt",
        ).readText()
        assertFalse(HEX_SHA256.containsMatchIn(source))
        assertTrue(source.contains("RECORDED_APPROVAL_SENTENCE"))
        assertEquals("com.example.devicemanagement", Checkpoint19ADecision.EXPECTED_PACKAGE_NAME)
        assertEquals(
            "com.example.devicemanagement/com.example.devicemanagement.management.SentinelDeviceAdminReceiver",
            Checkpoint19ADecision.EXPECTED_ADMIN_COMPONENT,
        )
        assertEquals("DISPOSABLE_DEVICE_VALIDATION", Checkpoint19ADecision.EXPECTED_BUILD_PURPOSE)
    }

    @Test
    fun `future scope remains factory-reset only with extra options denied`() {
        assertTrue(Checkpoint19ADecision.FUTURE_SCOPE_DEVICE_FACTORY_RESET_ONLY)
        assertTrue(Checkpoint19ADecision.FUTURE_SCOPE_USER_SCOPED_WIPE_DENIED)
        assertTrue(Checkpoint19ADecision.FUTURE_OPTION_SILENT_FORBIDDEN)
        assertTrue(Checkpoint19ADecision.FUTURE_OPTION_RESET_PROTECTION_DENIED)
        assertTrue(Checkpoint19ADecision.FUTURE_OPTION_EUICC_DENIED)
        assertTrue(Checkpoint19ADecision.FUTURE_OPTION_UNKNOWN_DENIED)
        assertTrue(Checkpoint19ADecision.FUTURE_EXTRA_FLAG_SET_MUST_BE_EMPTY)
        assertTrue(DestructiveWipeOptionPolicy.allowsScope(DestructiveScope.DEVICE_FACTORY_RESET))
        assertFalse(DestructiveWipeOptionPolicy.allowsScope(DestructiveScope.USER_SCOPED_WIPE))
        DestructiveWipeFlagOption.entries.forEach { option ->
            assertFalse(DestructiveWipeOptionPolicy.isPermitted(option))
        }
        assertFalse(DestructiveWipeOptionPolicy.isPermittedName("WIPE_EXTERNAL_STORAGE"))
        assertFalse(DestructiveWipeOptionPolicy.isPermittedName("UNKNOWN_FUTURE_FLAG"))
    }

    @Test
    fun `decision document separates the five states and does not record approval`() {
        val docs = File("../docs/WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md").readText()
        assertTrue(docs.contains("19_DESTRUCTIVE_IMPLEMENTATION_APPROVAL_REQUEST_READY = YES"))
        assertTrue(docs.contains("1. Architecture readiness"))
        assertTrue(docs.contains("2. Approval request readiness"))
        assertTrue(docs.contains("3. Actual destructive approval"))
        assertTrue(docs.contains("4. Destructive implementation"))
        assertTrue(docs.contains("5. Destructive hardware validation"))
        assertTrue(docs.contains("DESTRUCTIVE_IMPLEMENTATION_APPROVED = NO"))
        assertTrue(docs.contains("DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = false"))
        assertTrue(docs.contains("DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = false"))
        assertTrue(docs.contains(Checkpoint19ADecision.REQUIRED_APPROVAL_SENTENCE))
        assertTrue(docs.contains("capable of factory-resetting"))
        assertTrue(docs.contains("wipeDevice"))
        assertTrue(docs.contains("wipeData"))
        assertTrue(docs.contains("<wipe-data>"))
        assertTrue(docs.contains("A. Android destructive API implementation"))
        assertTrue(docs.contains("B. DeviceAdmin metadata"))
        assertTrue(docs.contains("C. DPM bytecode allowlist / wrapper review"))
        assertTrue(docs.contains("D. Trusted production composition"))
        assertTrue(docs.contains("E. Production signing / artifact identity"))
        assertTrue(docs.contains("F. Explicit human approval recording"))
        assertTrue(docs.contains("G. Disposable-device hardware validation"))
        assertTrue(docs.contains("H. GrapheneOS validation"))
        assertTrue(docs.contains("Do not implement any of A–H"))
        assertTrue(docs.contains("NO REAL WIPE IMPLEMENTED"))
        assertTrue(docs.contains("NO WIPE-DATA METADATA ADDED"))
        assertTrue(docs.contains("NO DESTRUCTIVE HARDWARE TEST PERFORMED"))
        assertTrue(docs.contains("NO DESTRUCTIVE APPROVAL RECORDED"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertFalse(docs.contains("DESTRUCTIVE_HUMAN_APPROVAL_RECORDED = true"))
        assertFalse(docs.contains("DISPOSABLE_DEVICE_ARTIFACT_HASH_RECORDED = true"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        Checkpoint19ADecision.remainingImplementationBlockers.forEach { name ->
            assertTrue(name, name in Checkpoint19ADecision.remainingImplementationBlockers)
        }
        assertTrue(
            Checkpoint19ADecision.gatesRequiringExplicitModification.any {
                it.contains("WIPE_19A_DESTRUCTIVE_IMPLEMENTATION_APPROVAL.md")
            },
        )
    }

    @Test
    fun `production sources still contain no destructive Android API tokens or executor wiring`() {
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("import android.app.admin.DevicePolicyManager"))
        assertFalse(sources.contains("<wipe-data>"))
        assertFalse(sources.contains("class DestructiveDevicePolicy"))
        assertFalse(sources.contains("fun wipe"))
        assertTrue(sources.contains("Checkpoint19ADecision"))
        assertTrue(sources.contains("REQUIRED_APPROVAL_SENTENCE"))
        assertTrue(sources.contains("Checkpoint19BDecision"))
        assertTrue(sources.contains("Checkpoint19CDecision"))
        assertTrue(sources.contains("Checkpoint19DDecision"))
        assertTrue(sources.contains("Checkpoint19EDecision"))
        assertFalse(sources.contains("wipeDevice"))
        assertFalse(sources.contains("wipeData"))
        listOf(
            File("../app/src/main"),
            File("../device-management-api/src/main"),
            File("../device-management-facade/src/main"),
        ).filter { it.isDirectory }.forEach { root ->
            val extra = root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .joinToString("\n") { it.readText() }
            assertFalse(root.path, extra.contains("wipeDevice"))
            assertFalse(root.path, extra.contains("wipeData"))
            assertFalse(root.path, extra.contains("<wipe-data>"))
            assertFalse(root.path, extra.contains("assembleAndHandoff"))
            assertFalse(root.path, extra.contains("Checkpoint19ADecision"))
            assertFalse(root.path, extra.contains("Checkpoint19CDecision"))
            assertFalse(root.path, extra.contains("Checkpoint19DDecision"))
            assertFalse(root.path, extra.contains("Checkpoint19EDecision"))
            assertFalse(root.path, extra.contains("assembleAlreadyBoundDeviceFactoryReset"))
            assertFalse(root.path, extra.contains("AndroidFutureDestructiveExecutor"))
            assertFalse(root.path, extra.contains("AndroidDevicePolicyFactoryResetService"))
        }
        val deviceManagement = File("../device-management/src/main")
        deviceManagement.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .forEach { file ->
                val extra = file.readText()
                if (file.name == "AndroidDevicePolicyFactoryResetService.kt") {
                    assertTrue(file.path, extra.contains("wipeDevice(0)"))
                    assertFalse(file.path, extra.contains("wipeData"))
                } else {
                    assertFalse(file.path, extra.contains("wipeDevice"))
                    assertFalse(file.path, extra.contains("wipeData"))
                    assertFalse(file.path, extra.contains("assembleAndHandoff"))
                    assertFalse(file.path, extra.contains("Checkpoint19ADecision"))
                    assertFalse(file.path, extra.contains("Checkpoint19CDecision"))
                    assertFalse(file.path, extra.contains("Checkpoint19DDecision"))
                    assertFalse(file.path, extra.contains("Checkpoint19EDecision"))
                    assertFalse(file.path, extra.contains("assembleAlreadyBoundDeviceFactoryReset"))
                }
            }
        val metadata = File("../device-management/src/main/res/xml/device_admin_receiver.xml").readText()
        assertTrue(metadata.contains("wipe-data"))
        assertTrue(metadata.contains("disable-camera"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
