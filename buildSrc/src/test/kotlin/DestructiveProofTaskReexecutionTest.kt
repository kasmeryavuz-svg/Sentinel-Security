import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DestructiveProofTaskReexecutionTest {
    @Test
    fun `all four proof task types refuse UP-TO-DATE and cache reuse`() {
        val project = ProjectBuilder.builder().build()
        val tasks = listOf<Task>(
            project.tasks.create("generate", GenerateDestructiveValidationCandidateEvidenceTask::class.java),
            project.tasks.create(
                "unsigned",
                CheckUnsignedDestructiveValidationCandidateEvidenceTask::class.java,
            ),
            project.tasks.create(
                "purpose",
                CheckUnsignedDisposableValidationBuildPurposeEvidenceTask::class.java,
            ),
            project.tasks.create(
                "ceremony",
                CheckDestructiveSigningCeremonyPreparationTask::class.java,
            ),
        )
        assertEquals(4, tasks.size)
        val sources = listOf(
            File("src/main/kotlin/DestructiveValidationCandidateEvidenceTask.kt").readText(),
            File("src/main/kotlin/DestructiveSigningCeremonyPreparationTask.kt").readText(),
        ).joinToString("\n")
        assertEquals(
            4,
            Regex("DestructiveProofTaskSemantics.neverReuseOutputs").findAll(sources).count(),
        )
        assertTrue(sources.contains("outputs.upToDateWhen { false }").not())
        val semantics = File("src/main/kotlin/DestructiveProofTaskSemantics.kt").readText()
        assertTrue(semantics.contains("outputs.upToDateWhen { false }"))
        assertTrue(semantics.contains("outputs.cacheIf { false }"))
        assertTrue(semantics.contains("doNotTrackState"))
        assertTrue(sources.contains("@DisableCachingByDefault"))
        assertTrue(
            File("../app/build.gradle.kts").readText().contains("filledCeremonyRecord.from"),
        )
    }

    @Test
    fun `second ceremony proof execution rewrites the report and leftover temp is removed`() {
        val workspace = File("build/tmp/19p-proof-reexecution").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val run = executeCeremonyProof(workspace, createFilledRecord = false)
            val firstModified = run.report.lastModified()
            assertTrue(run.report.isFile)
            assertTrue(run.report.readText().contains("ceremony_status=NOT_READY"))
            assertFalse(run.temp.exists())
            run.report.writeText("stale-report-must-not-skip-proof\n")
            File(run.temp, "leftover").apply {
                mkdirs()
                File(this, "stale.txt").writeText("hidden-by-up-to-date")
            }
            assertTrue(run.temp.exists())
            Thread.sleep(20)
            run.task.actions.forEach { action -> action.execute(run.task) }
            assertTrue(run.report.readText().contains("ceremony_status=NOT_READY"))
            assertFalse(run.report.readText().contains("stale-report-must-not-skip-proof"))
            assertTrue(run.report.lastModified() >= firstModified)
            assertFalse(run.temp.exists())
            assertFalse(run.task.state.upToDate)
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun `creating a filled ceremony record refuses the proof and is removed afterward`() {
        val workspace = File("build/tmp/19p-proof-filled-record").apply {
            deleteRecursively()
            mkdirs()
        }
        val filled = File(workspace, "local/destructive-signing-ceremony-record.txt")
        try {
            executeCeremonyProof(workspace, createFilledRecord = false)
            filled.parentFile.mkdirs()
            filled.writeText("ceremony_id=MUST_NOT_EXIST\n")
            val failure = assertFails {
                executeCeremonyProof(workspace, createFilledRecord = true)
            }
            assertTrue(
                failure.message?.contains("Filled signing-ceremony record") == true,
                failure.message,
            )
        } finally {
            filled.delete()
            filled.parentFile.delete()
            workspace.deleteRecursively()
        }
        assertFalse(filled.exists())
        assertFalse(File("local/destructive-signing-ceremony-record.txt").exists())
    }

    @Test
    fun `generate proof leftover snapshot cannot succeed as UP-TO-DATE`() {
        val workspace = File("build/tmp/19p-proof-generate-snapshot").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val project = ProjectBuilder.builder().withProjectDir(workspace).build()
            val task = project.tasks.create(
                "generate",
                GenerateDestructiveValidationCandidateEvidenceTask::class.java,
            )
            val snapshot = File(workspace, "snapshot")
            val leftover = File(snapshot, "immutable-candidate-snapshot.apk")
            leftover.parentFile.mkdirs()
            leftover.writeText("leftover-snapshot")
            task.candidateApkPath.set("")
            task.reportFile.set(File(workspace, "report.txt"))
            task.snapshotDirectory.set(snapshot)
            val failure = assertFails {
                task.actions.forEach { action -> action.execute(task) }
            }
            assertTrue(
                failure.message?.contains("must be supplied explicitly") == true ||
                    failure.suppressed.any { it.message?.contains("snapshot") == true },
                failure.message,
            )
            assertFalse(task.state.upToDate)
            assertTrue(task.didWork || failure.message != null)
        } finally {
            workspace.deleteRecursively()
        }
        assertFalse(File("local/destructive-signing-ceremony-record.txt").exists())
    }

    private data class CeremonyRun(
        val task: CheckDestructiveSigningCeremonyPreparationTask,
        val report: File,
        val temp: File,
    )

    private fun executeCeremonyProof(
        workspace: File,
        createFilledRecord: Boolean,
    ): CeremonyRun {
        val project = ProjectBuilder.builder().withProjectDir(workspace).build()
        val task = project.tasks.create(
            "ceremony-${System.nanoTime()}",
            CheckDestructiveSigningCeremonyPreparationTask::class.java,
        )
        val report = File(workspace, "ceremony-report.txt")
        val temp = File(workspace, "ceremony-temp")
        val filled = File(workspace, "local/destructive-signing-ceremony-record.txt")
        task.disposableValidationRemainsUnsigned.set(true)
        task.productionSigningConfigurationActive.set(false)
        task.productionDistributionRequested.set(false)
        task.filledCeremonyRecordPath.set(filled.absolutePath)
        if (createFilledRecord && filled.exists()) {
            task.filledCeremonyRecord.from(filled)
        }
        task.reportFile.set(report)
        task.temporaryDirectory.set(temp)
        task.actions.forEach { action -> action.execute(task) }
        assertFalse(task.state.upToDate)
        return CeremonyRun(task, report, temp)
    }
}
