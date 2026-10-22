package app.tidy.android.domain

import androidx.annotation.StringRes
import app.tidy.android.R

/**
 * The fixed set of folders Tidy sorts apps into. Stored by [name] (in
 * user overrides and renames), so never rename or remove an entry; adding
 * one anywhere is fine. Order = tie-break order and the move sheet's order.
 */
enum class Category(@StringRes val label: Int) {
    SOCIAL(R.string.category_social),
    FINANCE(R.string.category_finance),
    SHOP(R.string.category_shop),
    VIDEO(R.string.category_video),
    MUSIC(R.string.category_music),
    PHOTO(R.string.category_photo),
    WORK(R.string.category_work),
    MAP(R.string.category_map),
    NEWS(R.string.category_news),
    GAME(R.string.category_game),
    HEALTH(R.string.category_health),
    EDU(R.string.category_edu),
    TOOLS(R.string.category_tools),
    SYSTEM(R.string.category_system),
    ETC(R.string.category_etc);

    /** Kept at the end of the folder list regardless of size. */
    val pinnedLast: Boolean get() = this == SYSTEM || this == ETC

    companion object {
        fun fromName(name: String): Category? = entries.firstOrNull { it.name == name }
    }
}
