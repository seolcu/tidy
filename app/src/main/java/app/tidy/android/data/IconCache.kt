package app.tidy.android.data

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * App icons as bitmaps, decoded once at full tile size (also used, scaled
 * down, for the small previews on folder tiles). Badged, so work-profile
 * apps show the briefcase.
 */
object IconCache {
    private const val ICON_DP = 56
    private const val MAX_BYTES = 32 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().byteCount
    }

    fun peek(entry: AppEntry): ImageBitmap? = cache.get(entry.key)

    /** Blocking; call off the main thread. */
    fun load(context: Context, entry: AppEntry): ImageBitmap {
        cache.get(entry.key)?.let { return it }
        val px = (ICON_DP * context.resources.displayMetrics.density).toInt()
        val bitmap = entry.info.getBadgedIcon(0).toBitmap(px, px).asImageBitmap()
        cache.put(entry.key, bitmap)
        return bitmap
    }

    /** Drops a package's icons after it was updated or removed (keys start with "pkg/"). */
    fun evictPackage(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { cache.remove(it) }
    }
}
