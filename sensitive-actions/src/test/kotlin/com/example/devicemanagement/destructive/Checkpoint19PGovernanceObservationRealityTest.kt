package com.example.devicemanagement.destructive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Checkpoint19PGovernanceObservationRealityTest {
    @Test
    fun `19P records cleanup and a drift-prone observation without later destructive states`() {
        assertEquals("YES", Checkpoint19PGovernanceObservation.MAINTAINABILITY_CLEANUP)
        assertTrue(Checkpoint19PGovernanceObservation.CLONED_FREEZE_TESTS_REPAIRED)
        assertTrue(Checkpoint19PGovernanceObservation.CURRENT_GOVERNANCE_RECORDED)
        assertTrue(Checkpoint19PGovernanceObservation.RELEASE_DOCUMENTATION_CORRECTED)
        assertTrue(Checkpoint19PGovernanceObservation.SYNTHETIC_READY_TEST_ONLY)
        assertTrue(Checkpoint19PGovernanceObservation.PROOF_TASKS_ALWAYS_REEXECUTE)
        assertTrue(Checkpoint19PGovernanceObservation.BYTECODE_VERIFIER_REFACTOR_DEFERRED)
        assertTrue(Checkpoint19PGovernanceObservation.HISTORICAL_19E_STATE_UNCHANGED)
        assertEquals("EXTERNAL_GITHUB_STATE", Checkpoint19PGovernanceObservation.OBSERVATION_KIND)
        assertTrue(Checkpoint19PGovernanceObservation.OBSERVATION_MAY_DRIFT)
        assertEquals("20897672", Checkpoint19PGovernanceObservation.RULESET_ID)
        assertEquals("Protect main - Sentinel CI", Checkpoint19PGovernanceObservation.RULESET_NAME)
        assertEquals("active", Checkpoint19PGovernanceObservation.RULESET_ENFORCEMENT)
        assertEquals("refs/heads/main", Checkpoint19PGovernanceObservation.RULESET_TARGET)
        assertTrue(Checkpoint19PGovernanceObservation.RULESET_TARGET_ONLY_MAIN)
        assertEquals(
            "Independent safety verification",
            Checkpoint19PGovernanceObservation.REQUIRED_CHECK_NAME,
        )
        assertEquals("15368", Checkpoint19PGovernanceObservation.REQUIRED_CHECK_INTEGRATION_ID)
        assertTrue(Checkpoint19PGovernanceObservation.STRICT_UP_TO_DATE_REQUIRED)
        assertTrue(Checkpoint19PGovernanceObservation.PULL_REQUEST_REQUIRED)
        assertEquals(0, Checkpoint19PGovernanceObservation.REQUIRED_APPROVING_REVIEW_COUNT)
        assertTrue(Checkpoint19PGovernanceObservation.CONVERSATION_RESOLUTION_REQUIRED)
        assertTrue(Checkpoint19PGovernanceObservation.FORCE_PUSH_BLOCKED)
        assertTrue(Checkpoint19PGovernanceObservation.DELETION_BLOCKED)
        assertTrue(Checkpoint19PGovernanceObservation.BYPASS_ACTORS_EMPTY)
        assertTrue(Checkpoint19PGovernanceObservation.PR_35_GOVERNING_CHECK_SUCCESS)
        assertFalse(Checkpoint19PGovernanceObservation.USED_AS_RUNTIME_AUTHORIZATION)
        assertFalse(Checkpoint19PGovernanceObservation.USED_AS_CEREMONY_APPROVAL)
        assertFalse(Checkpoint19PGovernanceObservation.USED_AS_ARTIFACT_TRUST)
        assertFalse(Checkpoint19PGovernanceObservation.USED_AS_SIGNING_AUTHORIZATION)
        assertFalse(Checkpoint19PGovernanceObservation.USED_AS_MERGE_AUTHORIZATION)
        assertFalse(Checkpoint19PGovernanceObservation.CEREMONY_READY)
        assertFalse(Checkpoint19PGovernanceObservation.NINETEEN_H_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED)
        assertEquals("NO", Checkpoint19PGovernanceObservation.CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET)
        assertFalse(Checkpoint19EDecision.BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED)
        assertFalse(Checkpoint19EDecision.GITHUB_CI_RUN_OBSERVED)
        assertFalse(Checkpoint19HDecision.BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED)
        assertFalse(Checkpoint19HDecision.SIGNING_CEREMONY_READY)
    }

    @Test
    fun `19P is not runtime authorization and cannot mint trust`() {
        val methods = Checkpoint19PGovernanceObservation::class.java.declaredMethods
            .filter { it.name !in setOf("equals", "hashCode", "toString") }
            .map { it.name }
        assertFalse(methods.contains("authorize"))
        assertFalse(methods.contains("approve"))
        assertFalse(methods.contains("wipe"))
        assertFalse(methods.contains("execute"))
        assertFalse(methods.contains("parse"))
        assertFalse(methods.contains("trust"))
        val composition = File(
            "../device-management/src/main/java/com/example/devicemanagement/management/" +
                "DeviceManagementSensitiveActions.kt",
        ).readText()
        val container = File(
            "../app/src/main/java/com/example/devicemanagement/app/AppContainer.kt",
        ).readText()
        val registry = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionRegistry.kt",
        ).readText()
        val artifactSource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/DestructiveArtifactIdentity.kt",
        ).readText()
        listOf(
            "Checkpoint19PGovernanceObservation",
            "19P_RULESET_ID",
        ).forEach { token ->
            assertFalse(token, composition.contains(token))
            assertFalse(token, container.contains(token))
            assertFalse(token, registry.contains(token))
            assertFalse(token, artifactSource.contains(token))
        }
        assertNull(TrustedDestructiveArtifactValidationSource.trustedExpectation())
        assertNull(ProductionDestructiveTrustedArtifactExpectationSource().trustedExpectation())
        assertTrue(TrustedPerAttemptDestructiveConfirmationRecord.current() == null)
        val sources = File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertTrue(sources.contains("Checkpoint19PGovernanceObservation"))
        assertFalse(sources.contains("wipeData"))
        assertFalse(sources.contains("wipeDevice"))
    }

    @Test
    fun `19P document keeps the observation closed and 19E history intact`() {
        val docs = File("../docs/WIPE_19P_MAINTAINABILITY_CLEANUP.md").readText()
        assertTrue(docs.contains("CHECKPOINT_19P_MAINTAINABILITY_CLEANUP = YES"))
        assertTrue(docs.contains("19O_6_BYTECODE_VERIFIER_REFACTOR_DEFERRED = true"))
        assertTrue(docs.contains("19P_OBSERVATION_MAY_DRIFT = true"))
        assertTrue(docs.contains("19P_RULESET_ID = 20897672"))
        assertTrue(docs.contains("19P_USED_AS_MERGE_AUTHORIZATION = false"))
        assertTrue(docs.contains("HISTORICAL_19E_STATE_UNCHANGED = true"))
        assertTrue(docs.contains("19H_BRANCH_PROTECTION_REQUIRED_CHECK_VERIFIED = false"))
        assertTrue(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = NO"))
        assertTrue(docs.contains("DO NOT MERGE"))
        assertFalse(docs.contains("CURRENT_REPOSITORY_CAN_COMPLETE_FACTORY_RESET = YES"))
        assertFalse(HEX_SHA256.containsMatchIn(docs))
        val nineteenE = File("../docs/WIPE_19E_INDEPENDENT_CI.md").readText()
        assertTrue(nineteenE.contains("19E_BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED = false"))
        assertTrue(nineteenE.contains("19E_GITHUB_CI_RUN_OBSERVED = false"))
        val nineteenESource = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/Checkpoint19EDecision.kt",
        ).readText()
        assertTrue(nineteenESource.contains("BRANCH_PROTECTION_REQUIRED_CHECK_CONFIGURED = false"))
        assertTrue(nineteenESource.contains("GITHUB_CI_RUN_OBSERVED = false"))
        assertFalse(
            HEX_SHA256.containsMatchIn(
                File(
                    "src/main/kotlin/com/example/devicemanagement/destructive/" +
                        "Checkpoint19PGovernanceObservation.kt",
                ).readText(),
            ),
        )
    }

    @Test
    fun `19P tests themselves do not invoke the platform whole-device call`() {
        val thisFile = File(
            "src/test/kotlin/com/example/devicemanagement/destructive/" +
                "Checkpoint19PGovernanceObservationRealityTest.kt",
        ).readText()
        assertFalse(thisFile.contains("manager." + "wipeDevice"))
        assertFalse(thisFile.contains("android.app." + "admin"))
        assertFalse(thisFile.contains("Checkpoint19P" + "WipeBoundaryFreezeTest"))
    }

    private companion object {
        val HEX_SHA256 = Regex("\\b[0-9a-fA-F]{64}\\b")
    }
}
