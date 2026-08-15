import org.gradle.api.Task

/**
 * Shared fail-closed incrementality policy for destructive proof tasks.
 *
 * These tasks exist to re-prove cleanup and NOT_READY / INELIGIBLE
 * conditions. An UP-TO-DATE or cached result can hide a leftover
 * snapshot or a newly created filled ceremony record.
 */
object DestructiveProofTaskSemantics {
    const val REASON =
        "destructive proof tasks must re-execute cleanup and fail-closed checks"

    fun neverReuseOutputs(task: Task) {
        task.outputs.upToDateWhen { false }
        task.outputs.cacheIf { false }
        task.doNotTrackState(REASON)
    }
}
