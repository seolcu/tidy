package app.tidy.android.domain

import java.text.Collator
import java.util.Locale

data class Folder<T>(val category: Category, val apps: List<T>)

/**
 * Groups apps into non-empty folders: biggest first, ties in [Category]
 * order, SYSTEM and ETC always last. Apps inside a folder are sorted by
 * label in the user's locale (so Hangul sorts as Hangul).
 */
fun <T> buildFolders(
    apps: List<T>,
    categoryOf: (T) -> Category,
    labelOf: (T) -> String,
    locale: Locale = Locale.getDefault(),
): List<Folder<T>> {
    val collator = Collator.getInstance(locale)
    return apps.groupBy(categoryOf)
        .map { (category, list) -> Folder(category, list.sortedWith(compareBy(collator, labelOf))) }
        .sortedWith(
            compareBy<Folder<T>> { it.category.pinnedLast }
                .thenByDescending { it.apps.size }
                .thenBy { it.category.ordinal }
        )
}

/** Case-insensitive match on the label, or on the package name for people who know it. */
fun matchesQuery(query: String, label: String, packageName: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return label.contains(q, ignoreCase = true) || packageName.contains(q, ignoreCase = true)
}
