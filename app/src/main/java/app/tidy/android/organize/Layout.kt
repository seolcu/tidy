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
