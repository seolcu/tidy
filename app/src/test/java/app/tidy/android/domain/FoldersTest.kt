package app.tidy.android.domain

import app.tidy.android.domain.Category.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FoldersTest {
    private data class App(val label: String, val category: Category)

    private fun folders(vararg apps: App) =
        buildFolders(apps.toList(), { it.category }, { it.label }, Locale.KOREAN)

    @Test
    fun biggestFirstWithSystemAndEtcLast() {
        val result = folders(
            App("설정", SYSTEM), App("전화", SYSTEM), App("시계", SYSTEM),
            App("손전등", ETC),
            App("멜론", MUSIC),
            App("토스", FINANCE), App("카카오뱅크", FINANCE),
        )
        assertEquals(listOf(FINANCE, MUSIC, SYSTEM, ETC), result.map { it.category })
    }

    @Test
    fun tiesKeepCategoryOrder() {
        val result = folders(App("멜론", MUSIC), App("카카오톡", SOCIAL))
        assertEquals(listOf(SOCIAL, MUSIC), result.map { it.category })
    }

    @Test
    fun appsSortedByLabel() {
        val result = folders(App("토스", FINANCE), App("업비트", FINANCE), App("카카오뱅크", FINANCE))
        assertEquals(listOf("업비트", "카카오뱅크", "토스"), result.single().apps.map { it.label })
    }

    @Test
    fun emptyCategoriesAreDropped() {
        assertTrue(folders().isEmpty())
    }

    @Test
    fun queryMatchesLabelOrPackage() {
        assertTrue(matchesQuery("  ", "아무거나", "x"))
        assertTrue(matchesQuery("kakao", "카카오톡", "com.kakao.talk"))
        assertTrue(matchesQuery("카카오", "카카오톡", "com.kakao.talk"))
        assertTrue(matchesQuery("TALK", "카카오톡", "com.kakao.talk"))
        assertFalse(matchesQuery("멜론", "카카오톡", "com.kakao.talk"))
    }
}
