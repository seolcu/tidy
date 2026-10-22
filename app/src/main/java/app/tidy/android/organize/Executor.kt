package app.tidy.android.organize

/**
 * Does things to the apps screen. The real one ([OneUiDrawer][app.tidy.android.oneui.OneUiDrawer])
 * drives One UI's apps screen through accessibility; tests use a fake.
 * Each call returns whether the screen ended up as intended.
 */
interface DrawerDriver {
    suspend fun read(): DrawerLayout?
    suspend fun dissolve(folder: String): Boolean
    suspend fun rename(from: String, to: String): Boolean
    suspend fun addToFolder(apps: List<String>, folder: String): Boolean
    suspend fun createFolder(name: String, apps: List<String>): Boolean
}

sealed interface RunResult {
    data object Done : RunResult
    data class Stopped(val done: Int, val total: Int) : RunResult
    /** [step] didn't land; nothing after it was tried. */
    data class Failed(val step: Step, val done: Int, val total: Int) : RunResult
}

/**
 * Runs a plan one step at a time and stops at the first step that doesn't
 * land -- carrying on from an unexpected screen could scatter apps -- or
 * when [shouldStop] turns true between steps.
 */
class Executor(private val driver: DrawerDriver) {

    suspend fun run(
        steps: List<Step>,
        shouldStop: () -> Boolean = { false },
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): RunResult {
        onProgress(0, steps.size)
        steps.forEachIndexed { i, step ->
            if (shouldStop()) return RunResult.Stopped(i, steps.size)
            val ok = when (step) {
                is Step.Dissolve -> driver.dissolve(step.folder)
                is Step.RenameFolder -> driver.rename(step.from, step.to)
                is Step.AddToFolder -> driver.addToFolder(step.apps, step.folder)
                is Step.CreateFolder -> driver.createFolder(step.name, step.apps)
            }
            if (!ok) return RunResult.Failed(step, i, steps.size)
            onProgress(i + 1, steps.size)
        }
        return RunResult.Done
    }
}
