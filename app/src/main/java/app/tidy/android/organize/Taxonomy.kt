package app.tidy.android.organize

import app.tidy.android.domain.Category

/**
 * Learns what the user's own folders mean, so "keep my folders" can file new
 * apps into them: each folder takes the category most of its apps have
 * ([Categorizer][app.tidy.android.domain.Categorizer]). A folder with no
 * clear majority ("To do", "Membership cards") is the user's own idea and
 * gets nothing automatically.
 */
object Taxonomy {

    /** Category -> the user's folder for it. When two folders share a category, the more decisive one wins. */
    fun learn(layout: DrawerLayout, categoryOf: (String) -> Category?): Map<Category, String> {
        data class Vote(val folder: String, val category: Category, val share: Double, val count: Int)

        val votes = layout.folders.mapNotNull { folder ->
            val known = folder.apps.mapNotNull(categoryOf)
            if (known.isEmpty()) return@mapNotNull null
            val (category, count) = known.groupingBy { it }.eachCount().maxBy { it.value }
            val share = count.toDouble() / known.size
            // More than half, and not a 1-of-1 guess in a bigger folder of unknowns.
            if (share > 0.5 && (count >= 2 || folder.apps.size == 1)) Vote(folder.name, category, share, count) else null
        }
        return votes
            .groupBy { it.category }
            .mapValues { (_, vs) -> vs.maxWith(compareBy<Vote> { it.share }.thenBy { it.count }).folder }
    }
}
