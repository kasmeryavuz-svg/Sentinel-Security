package com.example.devicemanagement.destructive

import com.example.devicemanagement.action.ActionExecutor
import com.example.devicemanagement.action.Approval
import com.example.devicemanagement.action.ApprovalAuthority
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.Serializable

class Checkpoint18AuthorityGraphTest {
    @Test
    fun `no caller-created positive authority can satisfy the real-chain boundary`() {
        val handoff = FutureDestructiveRealChainBoundary::class.java.declaredMethods
            .single { it.name == "assembleAndHandoff" }
        assertFalse(handoff.parameterTypes.any { it == java.lang.Boolean.TYPE })
        assertFalse(
            FutureDestructiveExecutorContract::class.java.methods.any { method ->
                method.parameterTypes.any {
                    it == java.lang.Boolean.TYPE ||
                        it == String::class.java ||
                        it == Approval::class.java ||
                        it == PreExecutionEvidenceCommitProof::class.java ||
                        it == FinalExecutionPermit::class.java
                }
            },
        )
        assertFalse(
            ActionExecutor::class.java.methods.any { method ->
                method.parameterTypes.any {
                    it == FutureDestructiveExecutionBundle::class.java ||
                        it == RealChainFinalLiveValidationPermit::class.java
                }
            },
        )
        assertFalse(
            ApprovalAuthority::class.java.methods.any { method ->
                method.returnType == FutureDestructiveExecutionBundle::class.java
            },
        )
    }

    @Test
    fun `recovery UI and production controller cannot host the real-chain boundary`() {
        val recovery = File("src/main/kotlin/com/example/devicemanagement/recovery")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        listOf(
            "FutureDestructiveExecutorContract",
            "FutureDestructiveExecutionBundle",
            "FutureDestructiveRealChainBoundary",
            "assembleAndHandoff",
            "RuntimeDurablePreExecutionCommitProof",
            "RuntimeDurablePreExecutionCommitAuthority",
            "RealChainFinalLiveValidationPermit",
            "DestructiveWipeOptionPolicyProof",
            "DestructiveWipeOptionPolicyAuthority",
            "Checkpoint18Decision",
            "Checkpoint19ADecision",
            "Checkpoint19BDecision",
            "Checkpoint19CDecision",
            "UnwiredFutureDestructiveExecutor",
            "AndroidFutureDestructiveExecutor",
            "AndroidDevicePolicyFactoryResetService",
            "AuthorizedFactoryResetPort",
            "ProductionDestructiveRealChain",
            "RealChainHandoffRegistry",
            "HandoffRegistry",
            "IssuedRealChainFinalLiveValidationPermit",
            "IssuedFutureDestructiveExecutionBundle",
        ).forEach { token ->
            assertFalse(recovery.contains(token))
            assertFalse(controller.contains(token))
        }
    }

    @Test
    fun `real-chain types cannot be serialized restored or treated as simulation proofs`() {
        val types = listOf(
            FutureDestructiveExecutionBundle::class.java,
            RealChainFinalLiveValidationPermit::class.java,
            RuntimeDurablePreExecutionCommitProof::class.java,
            DestructiveWipeOptionPolicyProof::class.java,
        )
        types.forEach { type ->
            assertFalse(Serializable::class.java.isAssignableFrom(type))
            assertFalse(type.interfaces.any { it.name == "android.os.Parcelable" })
            assertFalse(type == PreExecutionEvidenceCommitProof::class.java)
            assertFalse(type == FinalExecutionPermit::class.java)
        }
        assertFalse(
            RuntimeDurablePreExecutionCommitProof::class.java.isAssignableFrom(
                PreExecutionEvidenceCommitProof::class.java,
            ),
        )
        assertFalse(
            PreExecutionEvidenceCommitProof::class.java.isAssignableFrom(
                RuntimeDurablePreExecutionCommitProof::class.java,
            ),
        )
    }

    @Test
    fun `in-memory persistence cannot satisfy the real-chain durability constructor`() {
        assertFalse(
            RuntimeDestructiveSafetyDurability::class.java.isAssignableFrom(
                InMemoryDenyOnlyCooldownMarkerStore::class.java,
            ),
        )
        assertFalse(
            RuntimeDestructiveSafetyDurability::class.java.isAssignableFrom(
                InMemoryDestructivePreExecutionDurableStore::class.java,
            ),
        )
        assertFalse(
            RuntimeDestructivePreExecutionStore::class.java.isAssignableFrom(
                InMemoryDestructivePreExecutionDurableStore::class.java,
            ),
        )
        assertTrue(
            FutureDestructiveRealChainBoundary::class.java.declaredConstructors.none { constructor ->
                constructor.parameterTypes.any {
                    it == InMemoryDenyOnlyCooldownMarkerStore::class.java ||
                        it == InMemoryDestructivePreExecutionDurableStore::class.java ||
                        it == DenyOnlyCooldownMarkerStore::class.java ||
                        it == DestructivePreExecutionDurableStore::class.java
                }
            },
        )
    }

    @Test
    fun `production composition still does not mint or hand off`() {
        val controller = File(
            "src/main/kotlin/com/example/devicemanagement/action/SensitiveActionController.kt",
        ).readText()
        assertFalse(controller.contains("issueRuntimeDurability"))
        assertFalse(controller.contains("RuntimeDestructiveSafetyDurabilityMint"))
        assertFalse(controller.contains("assembleAndHandoff"))
        assertFalse(controller.contains("FutureDestructiveRealChainBoundary"))
        val runtimeCommit = File(
            "src/main/kotlin/com/example/devicemanagement/destructive/" +
                "RuntimeDurablePreExecutionCommitProof.kt",
        ).readText()
        assertFalse(runtimeCommit.contains("UnwiredRuntimeDurablePreExecutionCommitSource"))
        assertTrue(runtimeCommit.contains("durableRepository.append(durableRecord)"))
    }
}
