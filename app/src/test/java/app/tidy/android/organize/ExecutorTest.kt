package app.tidy.android.organize

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** An apps screen in memory, with the same rules One UI enforces. */
class FakeDrawer(var layout: DrawerLayout, private val failOn: Step? = null) : DrawerDriver {
    val done = mutableListOf<Step>()

    override suspend fun read() = layout

    override suspend fun dissolve(folder: String): Boolean = act(Step.Dissolve(folder)) {
        val f = layout.folder(folder) ?: return@act false
        layout = DrawerLayout(layout.folders - f, layout.loose + f.apps)
        true
    }

    override suspend fun addToFolder(apps: List<String>, folder: String): Boolean = act(Step.AddToFolder(apps, folder)) {
        if (!layout.loose.containsAll(apps) || layout.folder(folder) == null) return@act false
        layout = DrawerLayout(
            layout.folders.map { if (it.name == folder) it.copy(apps = it.apps + apps) else it },
            layout.loose - apps.toSet(),
        )
        true
    }

    override suspend fun createFolder(name: String, apps: List<String>): Boolean = act(Step.CreateFolder(name, apps)) {
        if (!layout.loose.containsAll(apps)) return@act false
        layout = DrawerLayout(layout.folders + DrawerFolder(name, apps), layout.loose - apps.toSet())
        true
    }

    private fun act(step: Step, block: () -> Boolean): Boolean {
        if (step == failOn) return false
        return block().also { if (it) done += step }
    }
}

class ExecutorTest {
    private val before = DrawerLayout(
        folders = listOf(DrawerFolder("금융", listOf("토스", "카카오뱅크")), DrawerFolder("잡동사니", listOf("Minecraft", "Notion"))),
        loose = listOf("KB Pay", "브롤스타즈", "Gmail"),
    )
    private val target = DrawerLayout(
        folders = listOf(
            DrawerFolder("금융", listOf("토스", "카카오뱅크", "KB Pay")),
            DrawerFolder("게임", listOf("Minecraft", "브롤스타즈")),
        ),
        loose = listOf("Notion", "Gmail"),
    )

    @Test
    fun runningThePlanReachesTheTarget() = runBlocking {
        val drawer = FakeDrawer(before)
        val result = Executor(drawer).run(Planner.steps(before, target))
        assertEquals(RunResult.Done, result)
        assertEquals(target.folders.map { it.name to it.apps.toSet() }, drawer.layout.folders.map { it.name to it.apps.toSet() })
        assertEquals(target.loose.toSet(), drawer.layout.loose.toSet())
    }

    @Test
    fun undoGetsBackToTheSnapshot() = runBlocking {
        val drawer = FakeDrawer(before)
        Executor(drawer).run(Planner.steps(before, target))
        Executor(drawer).run(Planner.steps(drawer.layout, before))
        assertEquals(before.folders.map { it.name to it.apps.toSet() }, drawer.layout.folders.map { it.name to it.apps.toSet() })
    }

    @Test
    fun stopsAtTheFirstFailedStep() = runBlocking {
        val steps = Planner.steps(before, target)
        val bad = steps[1]
        val drawer = FakeDrawer(before, failOn = bad)
        val result = Executor(drawer).run(steps)
        assertEquals(RunResult.Failed(bad, 1, steps.size), result)
        assertEquals(listOf(steps[0]), drawer.done)
    }

    @Test
    fun stopsWhenAsked() = runBlocking {
        val steps = Planner.steps(before, target)
        var calls = 0
        val result = Executor(FakeDrawer(before)).run(steps, shouldStop = { calls++ == 2 })
        assertEquals(RunResult.Stopped(2, steps.size), result)
    }
}
