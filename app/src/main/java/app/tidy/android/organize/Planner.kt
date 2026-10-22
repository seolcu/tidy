package app.tidy.android.organize

import app.tidy.android.domain.Category
import app.tidy.android.domain.buildFolders

/**
 * Turns "what the apps screen should look like" into steps, and builds that
 * target for each way of organizing. Pure, so all of it is unit-tested; the
 * screen work is [Executor]'s.
 */
object Planner {

    /**
     * Keep the user's folders as they are; only file the apps that are outside
     * any folder: into the user's folder for their category ([Taxonomy]), or
     * into a new folder when at least [minNewFolder] of them share a category
     * nobody's folder covers. Anything else stays where it is.
     */
    fun keepFolders(
        current: DrawerLayout,
        categoryOf: (String) -> Category?,
        nameOf: (Category) -> String,
        minNewFolder: Int = 2,
    ): DrawerLayout {
        val taxonomy = Taxonomy.learn(current, categoryOf)
        val additions = mutableMapOf<String, MutableList<String>>()
        val unfiled = mutableListOf<String>()
        for (app in current.loose) {
            val folder = categoryOf(app)?.let(taxonomy::get)
            if (folder != null) additions.getOrPut(folder) { mutableListOf() } += app else unfiled += app
        }
        val newFolders = unfiled
            .groupBy(categoryOf)
            .filterKeys { it != null && it != Category.ETC }
            .filterValues { it.size >= minNewFolder }
            .map { (category, apps) -> DrawerFolder(nameOf(category!!), apps) }
            .filter { new -> current.folder(new.name) == null }
        val filed = newFolders.flatMap { it.apps }.toSet()
        return DrawerLayout(
            folders = current.folders.map { it.copy(apps = it.apps + additions[it.name].orEmpty()) } + newFolders,
            loose = unfiled.filter { it !in filed },
        )
    }

    /**
     * Ignore the user's folders and sort everything from scratch: one folder per
     * category with at least two apps (biggest first, System and Other last);
     * an app alone in its category stays outside.
     */
    fun rebuild(
        current: DrawerLayout,
        categoryOf: (String) -> Category?,
        nameOf: (Category) -> String,
    ): DrawerLayout {
        val apps = current.allApps
        val (categorized, unknown) = apps.partition { categoryOf(it) != null }
        val folders = buildFolders(categorized, { categoryOf(it)!! }, { it })
        val (real, single) = folders.partition { it.apps.size >= 2 }
        return DrawerLayout(
            folders = real.map { DrawerFolder(nameOf(it.category), it.apps) },
            loose = single.flatMap { it.apps } + unknown,
        )
    }

    /**
     * The preview's edits on a proposed [target]: [moves] puts apps in another
     * target folder (null = outside any folder), [renames] renames target
     * folders. A folder emptied to one app or fewer dissolves into loose apps.
     */
    fun edit(target: DrawerLayout, moves: Map<String, String?>, renames: Map<String, String>): DrawerLayout {
        val folders = target.folders.map { f -> f.copy(apps = f.apps.filter { it !in moves }) }.toMutableList()
        val loose = target.loose.filter { it !in moves }.toMutableList()
        for ((app, folder) in moves) {
            val i = folders.indexOfFirst { it.name == folder }
            if (i >= 0) folders[i] = folders[i].copy(apps = folders[i].apps + app) else loose += app
        }
        val (kept, tooSmall) = folders.partition { it.apps.size >= 2 }
        return DrawerLayout(
            folders = kept.map { it.copy(name = renames[it.name]?.trim()?.takeIf(String::isNotEmpty) ?: it.name) },
            loose = loose + tooSmall.flatMap { it.apps },
        )
    }

    /**
     * Steps from [current] to [target], touching as little as possible. A
     * current folder survives when the target has a folder of the same name
     * that still holds all its apps (missing ones are moved in); any other
     * folder is dissolved and, if the target wants it, made again. Target
     * apps not on the phone are skipped, and a target folder left with one
     * app isn't made (One UI folders need two). Order: dissolve, add, create.
     */
    fun steps(current: DrawerLayout, target: DrawerLayout): List<Step> {
        val onPhone = current.allApps.toSet()
        val wanted = target.folders.map { it.copy(apps = it.apps.filter(onPhone::contains)) }

        val kept = current.folders.filter { folder ->
            val goal = wanted.firstOrNull { it.name == folder.name }
            goal != null && goal.apps.containsAll(folder.apps)
        }.map { it.name }.toSet()

        val dissolve = current.folders.filter { it.name !in kept }.map { Step.Dissolve(it.name) }
        val moves = wanted.filter { it.name in kept }.mapNotNull { goal ->
            val already = current.folder(goal.name)!!.apps.toSet()
            goal.apps.filter { it !in already }.takeIf { it.isNotEmpty() }?.let { Step.AddToFolder(it, goal.name) }
        }
        val creates = wanted.filter { it.name !in kept && it.apps.size >= 2 }
            .map { Step.CreateFolder(it.name, it.apps) }
        return dissolve + moves + creates
    }
}
