package app.tidy.android.organize

/**
 * What One UI's apps screen holds, as read from the screen: folders by name
 * and the apps outside any folder. Apps are identified by the label One UI
 * shows, since that's all the screen tells us. Order is the on-screen order.
 */
data class DrawerLayout(val folders: List<DrawerFolder>, val loose: List<String>) {
    val allApps: List<String> get() = folders.flatMap { it.apps } + loose

    fun folder(name: String): DrawerFolder? = folders.firstOrNull { it.name == name }
}

data class DrawerFolder(val name: String, val apps: List<String>)

/** One change Tidy makes on the apps screen. */
sealed interface Step {
    /** Breaks a folder up; its apps go back onto the apps screen. */
    data class Dissolve(val folder: String) : Step

    /** Renames a folder whose apps are already right. */
    data class RenameFolder(val from: String, val to: String) : Step

    /** Puts apps that are outside any folder into an existing folder. */
    data class AddToFolder(val apps: List<String>, val folder: String) : Step

    /** Makes a new folder from at least two apps that are outside any folder. */
    data class CreateFolder(val name: String, val apps: List<String>) : Step {
        init {
            require(apps.size >= 2) { "a folder needs at least two apps" }
        }
    }
}

/**
 * The apps screen after [steps] -- what a run leaves behind, so it needn't
 * be read again (every step is checked on screen as it's done).
 */
fun DrawerLayout.after(steps: List<Step>): DrawerLayout = steps.fold(this) { l, step ->
    when (step) {
        is Step.Dissolve -> l.folder(step.folder)?.let { f -> DrawerLayout(l.folders - f, l.loose + f.apps) } ?: l
        is Step.RenameFolder -> l.copy(folders = l.folders.map { if (it.name == step.from) it.copy(name = step.to) else it })
        is Step.AddToFolder -> DrawerLayout(
            l.folders.map { if (it.name == step.folder) it.copy(apps = it.apps + step.apps) else it },
            l.loose - step.apps.toSet(),
        )
        is Step.CreateFolder -> DrawerLayout(l.folders + DrawerFolder(step.name, step.apps), l.loose - step.apps.toSet())
    }
}

/**
 * Plain-text form of a [DrawerLayout] for the undo snapshot: one line per
 * folder ("F", name, apps) and one for the loose apps ("L", apps), fields
 * separated by unit/record separator characters that labels never contain.
 */
object LayoutCodec {
    private const val FIELD = '\u001F'
    private const val LINE = '\u001E'

    fun encode(layout: DrawerLayout): String =
        (layout.folders.map { (listOf("F", it.name) + it.apps).joinToString(FIELD.toString()) } +
            (listOf("L") + layout.loose).joinToString(FIELD.toString()))
            .joinToString(LINE.toString())

    fun decode(text: String): DrawerLayout? = runCatching {
        val folders = mutableListOf<DrawerFolder>()
        var loose = emptyList<String>()
        for (line in text.split(LINE)) {
            val fields = line.split(FIELD)
            when (fields.first()) {
                "F" -> folders += DrawerFolder(fields[1], fields.drop(2))
                "L" -> loose = fields.drop(1)
                else -> error("bad line")
            }
        }
        DrawerLayout(folders, loose)
    }.getOrNull()
}
