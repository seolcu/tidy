package app.tidy.android.domain

import app.tidy.android.domain.Category.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorizerTest {
    @Test
    fun knownPackageBeatsEverythingElse() {
        // Declares itself as a game, but the dictionary knows better.
        assertEquals(SOCIAL, Categorizer.classify("com.kakao.talk", "카카오톡", GAME, isSystem = false))
    }

    @Test
    fun exactEntryBeatsPrefix() {
        assertEquals(PHOTO, Categorizer.classify("com.sec.android.app.camera", "카메라", null, isSystem = true))
        assertEquals(SYSTEM, Categorizer.classify("com.sec.android.app.clockpackage", "시계", null, isSystem = true))
    }

    @Test
    fun longestPrefixWins() {
        assertEquals(WORK, Categorizer.classify("com.google.android.apps.docs.editors.sheets", "Sheets", null, isSystem = false))
        assertEquals(FINANCE, Categorizer.classify("com.kbstar.kbbank", "KB스타뱅킹", null, isSystem = false))
    }

    @Test
    fun declaredCategoryIsUsedForUnknownApps() {
        assertEquals(GAME, Categorizer.classify("com.example.dungeon", "Dungeon", GAME, isSystem = false))
    }

    @Test
    fun vagueDeclaredCategoryLosesToKeywords() {
        // Seen on a real phone: a card payment app and an e-book reader both
        // declare "productivity".
        assertEquals(FINANCE, Categorizer.classify("com.example.card", "My Pay", WORK, isSystem = false))
        assertEquals(NEWS, Categorizer.classify("com.example.ebook.reader", "Reader", WORK, isSystem = false))
        // ...but still counts when nothing else matches.
        assertEquals(WORK, Categorizer.classify("com.example.chatbot", "Assistant", WORK, isSystem = false))
        assertEquals(SOCIAL, Categorizer.classify("com.example.everytime", "Everytime", SOCIAL, isSystem = false))
    }

    @Test
    fun specificDeclaredCategoryBeatsKeywords() {
        assertEquals(GAME, Categorizer.classify("com.example.pizzagame", "Pizza Shop", GAME, isSystem = false))
    }

    @Test
    fun hospitalIsHealthEvenAtAUniversity() {
        assertEquals(HEALTH, Categorizer.classify("kr.or.examplemc", "한국대학교병원", null, isSystem = false))
        assertEquals(EDU, Categorizer.classify("com.example.app", "한국대학교", null, isSystem = false))
    }

    @Test
    fun keywordsInPackageOrLabel() {
        assertEquals(FINANCE, Categorizer.classify("com.example.mybanking", "My App", null, isSystem = false))
        assertEquals(MAP, Categorizer.classify("com.example.app", "서울 버스", null, isSystem = false))
        assertEquals(MUSIC, Categorizer.classify("com.example.player", "Music Player", null, isSystem = false))
    }

    @Test
    fun shortKeywordsNeedAWholeWord() {
        // "tv" must not match inside "activity".
        assertNull(Categorizer.byKeyword("com.example.activity", "Activity"))
        assertEquals(VIDEO, Categorizer.byKeyword("com.example.tv", "Live"))
    }

    @Test
    fun fallsBackToSystemOrEtc() {
        assertEquals(SYSTEM, Categorizer.classify("com.vendor.thing", "Thing", null, isSystem = true))
        assertEquals(ETC, Categorizer.classify("com.example.thing", "Thing", null, isSystem = false))
    }

    @Test
    fun userOverrideWins() {
        val overrides = mapOf("com.kakao.talk" to WORK)
        assertEquals(WORK, Categorizer.assign("com.kakao.talk", SOCIAL, overrides))
        assertEquals(MUSIC, Categorizer.assign("com.spotify.music", MUSIC, overrides))
    }
}
