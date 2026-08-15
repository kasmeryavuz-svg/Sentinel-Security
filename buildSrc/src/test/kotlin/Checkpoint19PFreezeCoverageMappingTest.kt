import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Checkpoint19PFreezeCoverageMappingTest {
    @Test
    fun `every removed 19G 19H 19J clone is replaced by a shared invariant`() {
        val appShared = read(
            "../app/src/test/java/com/example/devicemanagement/security/" +
                "CurrentProductionWipeBoundaryInvariantTest.kt",
        )
        val deviceShared = read(
            "../device-management/src/test/java/com/example/devicemanagement/management/" +
                "CurrentProductionWipeBoundaryInvariantTest.kt",
        )
        val app19G = read(
            "../app/src/test/java/com/example/devicemanagement/security/" +
                "Checkpoint19GWipeBoundaryFreezeTest.kt",
        )
        val app19H = read(
            "../app/src/test/java/com/example/devicemanagement/security/" +
                "Checkpoint19HWipeBoundaryFreezeTest.kt",
        )
        val app19J = read(
            "../app/src/test/java/com/example/devicemanagement/security/" +
                "Checkpoint19JWipeBoundaryFreezeTest.kt",
        )

        mapping.forEach { replacement ->
            val shared = if (replacement.sharedModule == "app") appShared else deviceShared
            assertTrue(
                shared.contains(replacement.sharedTest),
                "missing shared invariant ${replacement.sharedTest}",
            )
            replacement.removedTokens.forEach { token ->
                assertTrue(
                    shared.contains(token),
                    "${replacement.sharedTest} must keep token $token",
                )
            }
        }

        listOf(app19G, app19H, app19J).forEach { source ->
            assertFalse(source.contains("wipeDevice"))
            assertFalse(source.contains("DevicePolicyManager"))
            assertFalse(source.contains("upload-artifact"))
            assertFalse(source.contains("checkProductionDistributionSigning"))
            assertTrue(source.contains("docs"))
        }

        listOf(
            "../device-management/src/test/java/com/example/devicemanagement/management/" +
                "Checkpoint19GWipeBoundaryFreezeTest.kt",
            "../device-management/src/test/java/com/example/devicemanagement/management/" +
                "Checkpoint19HWipeBoundaryFreezeTest.kt",
            "../device-management/src/test/java/com/example/devicemanagement/management/" +
                "Checkpoint19JWipeBoundaryFreezeTest.kt",
            "../app/src/test/java/com/example/devicemanagement/security/" +
                "Checkpoint19PWipeBoundaryFreezeTest.kt",
            "../device-management/src/test/java/com/example/devicemanagement/management/" +
                "Checkpoint19PWipeBoundaryFreezeTest.kt",
        ).forEach { path ->
            assertFalse(File(path).exists(), path)
        }
        assertFalse(appShared.contains("class Checkpoint19PWipeBoundaryFreezeTest"))
        assertFalse(deviceShared.contains("class Checkpoint19PWipeBoundaryFreezeTest"))
    }

    private fun read(path: String): String = File(path).readText()

    private data class Replacement(
        val removedClone: String,
        val sharedModule: String,
        val sharedTest: String,
        val removedTokens: List<String>,
    )

    private val mapping = listOf(
        Replacement(
            removedClone = "app/Checkpoint19GWipeBoundaryFreezeTest.app production sources",
            sharedModule = "app",
            sharedTest = "app production sources have no wipe invocation or destructive trigger",
            removedTokens = listOf("wipeData", "wipeDevice"),
        ),
        Replacement(
            removedClone = "app/Checkpoint19GWipeBoundaryFreezeTest.independent CI",
            sharedModule = "app",
            sharedTest = "independent CI refuses signing secrets uploads and hardware access",
            removedTokens = listOf(
                "checkUnsignedDisposableValidationBuildPurposeEvidence",
                "upload-artifact",
                "checkProductionDistributionSigning",
            ),
        ),
        Replacement(
            removedClone = "app/Checkpoint19HWipeBoundaryFreezeTest.app production sources",
            sharedModule = "app",
            sharedTest = "app production sources stay isolated from checkpoint and proof types",
            removedTokens = listOf(
                "Checkpoint19HDecision",
                "DestructiveSigningCeremonyPreparation",
                "checkDestructiveSigningCeremonyPreparation",
            ),
        ),
        Replacement(
            removedClone = "app/Checkpoint19HWipeBoundaryFreezeTest.independent CI",
            sharedModule = "app",
            sharedTest = "independent CI refuses signing secrets uploads and hardware access",
            removedTokens = listOf("checkDestructiveSigningCeremonyPreparation", "emulator"),
        ),
        Replacement(
            removedClone = "app/Checkpoint19JWipeBoundaryFreezeTest.app production sources",
            sharedModule = "app",
            sharedTest = "app production sources stay isolated from checkpoint and proof types",
            removedTokens = listOf(
                "Checkpoint19JDecision",
                "ProductionDistributionSigningGate",
                "inspectWriteAndAssertCleanup",
            ),
        ),
        Replacement(
            removedClone = "app/Checkpoint19JWipeBoundaryFreezeTest.independent CI",
            sharedModule = "app",
            sharedTest = "independent CI refuses signing secrets uploads and hardware access",
            removedTokens = listOf("destructive-validation-unsigned-release-snapshot", "adb"),
        ),
        Replacement(
            removedClone = "device-management/Checkpoint19GWipeBoundaryFreezeTest.DeviceAdmin metadata",
            sharedModule = "device-management",
            sharedTest = "DeviceAdmin metadata remains exactly disable-camera and wipe-data",
            removedTokens = listOf("disable-camera", "wipe-data"),
        ),
        Replacement(
            removedClone = "device-management/Checkpoint19GWipeBoundaryFreezeTest.implementation sources",
            sharedModule = "device-management",
            sharedTest = "exactly one production wipeDevice call uses literal zero flags",
            removedTokens = listOf("wipeDevice(0)", "AndroidDevicePolicyFactoryResetService.kt"),
        ),
        Replacement(
            removedClone = "device-management/Checkpoint19HWipeBoundaryFreezeTest.implementation sources",
            sharedModule = "device-management",
            sharedTest = "implementation sources retain the production chain without checkpoint or proof types",
            removedTokens = listOf(
                "Checkpoint19HDecision",
                "DestructiveSigningCeremonyPreparation",
                "retainForProduction",
            ),
        ),
        Replacement(
            removedClone = "device-management/Checkpoint19JWipeBoundaryFreezeTest.implementation sources",
            sharedModule = "device-management",
            sharedTest = "implementation sources retain the production chain without checkpoint or proof types",
            removedTokens = listOf(
                "Checkpoint19JDecision",
                "ProductionDistributionSigningGate",
                "inspectWriteAndAssertCleanup",
            ),
        ),
    )
}
