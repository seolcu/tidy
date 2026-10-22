package app.tidy.android.organize

import app.tidy.android.domain.Category
import app.tidy.android.domain.Category.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    // Shaped like a real phone's hand-made folders.
    private val categories = mapOf(
        "토스" to FINANCE, "카카오뱅크" to FINANCE, "카카오페이" to FINANCE, "KB Pay" to FINANCE,
        "브롤스타즈" to GAME, "Minecraft" to GAME,
        "Notion" to WORK, "Gmail" to WORK, "Claude" to WORK,
        "스타벅스" to SHOP, "CU" to SHOP,
        "해커스 토익" to EDU, "해커스 MP3" to EDU, "해커스ONE" to EDU,
        "WakeLink" to ETC, "Tidy" to ETC,
        "리마인더" to WORK, "캘린더" to WORK,
    )
    private val categoryOf: (String) -> Category? = { categories[it] }
    private val nameOf: (Category) -> String = { it.name.lowercase() }

    private val phone = DrawerLayout(
        folders = listOf(
            DrawerFolder("금융/결제", listOf("토스", "카카오뱅크", "카카오페이")),
            DrawerFolder("게임", listOf("브롤스타즈", "Minecraft")),
            DrawerFolder("생활/멤버십", listOf("스타벅스", "CU", "Notion")),
            DrawerFolder("할 일", listOf("리마인더", "캘린더", "Unknown")),
        ),
        loose = listOf("KB Pay", "해커스 토익", "해커스 MP3", "해커스ONE", "WakeLink", "Tidy", "Gmail", "Claude"),
    )

    @Test
    fun taxonomyFollowsTheMajority() {
        val t = Taxonomy.learn(phone, categoryOf)
        assertEquals("금융/결제", t[FINANCE])
        assertEquals("게임", t[GAME])
        assertEquals("생활/멤버십", t[SHOP]) // 2 of 3 is a majority
        // "할 일": 2 WORK of 2 known -> WORK; that's the user's call to make work apps go there.
        assertEquals("할 일", t[WORK])
    }

    @Test
    fun taxonomyIgnoresFoldersWithoutAMajority() {
        val mixed = DrawerLayout(listOf(DrawerFolder("잡동사니", listOf("토스", "Minecraft", "Notion"))), emptyList())
        assertTrue(Taxonomy.learn(mixed, categoryOf).isEmpty())
    }

    @Test
    fun keepFoldersFilesLooseAppsAndLeavesFoldersAlone() {
        val target = Planner.keepFolders(phone, categoryOf, nameOf)
        assertEquals(listOf("토스", "카카오뱅크", "카카오페이", "KB Pay"), target.folder("금융/결제")!!.apps)
        assertEquals(listOf("리마인더", "캘린더", "Unknown", "Gmail", "Claude"), target.folder("할 일")!!.apps)
        // Three EDU apps, no EDU folder: a new one.
        assertEquals(listOf("해커스 토익", "해커스 MP3", "해커스ONE"), target.folder("edu")!!.apps)
        // ETC never becomes a folder by itself in this mode.
        assertEquals(listOf("WakeLink", "Tidy"), target.loose)

        val steps = Planner.steps(phone, target)
        assertTrue(steps.none { it is Step.Dissolve })
        assertEquals(
            listOf(
                Step.AddToFolder(listOf("KB Pay"), "금융/결제"),
                Step.AddToFolder(listOf("Gmail", "Claude"), "할 일"),
                Step.CreateFolder("edu", listOf("해커스 토익", "해커스 MP3", "해커스ONE")),
            ),
            steps,
        )
    }

    @Test
    fun rebuildIgnoresTheUsersFolders() {
        val target = Planner.rebuild(phone, categoryOf, nameOf)
        assertEquals(
            listOf("work", "finance", "edu", "shop", "game", "etc"), // ties: Category order
            target.folders.map { it.name },
        )
        assertEquals(listOf("Unknown"), target.loose)
        val steps = Planner.steps(phone, target)
        // "게임" already holds exactly the game folder's apps: renamed, not rebuilt.
        assertEquals(listOf(Step.RenameFolder("게임", "game")), steps.filterIsInstance<Step.RenameFolder>())
        assertEquals(3, steps.count { it is Step.Dissolve })
        assertEquals(5, steps.count { it is Step.CreateFolder })
        assertTrue(steps.indexOfLast { it is Step.Dissolve } < steps.indexOfFirst { it is Step.CreateFolder })
    }

    @Test
    fun alreadySortedNeedsNoSteps() {
        val target = Planner.rebuild(phone, categoryOf, nameOf)
        val sorted = DrawerLayout(target.folders, target.loose)
        assertTrue(Planner.steps(sorted, Planner.rebuild(sorted, categoryOf, nameOf)).isEmpty())
    }

    @Test
    fun undoRestoresTheSnapshot() {
        val after = Planner.rebuild(phone, categoryOf, nameOf)
        val back = Planner.steps(after, phone)
        assertEquals(listOf(Step.RenameFolder("game", "게임")), back.filterIsInstance<Step.RenameFolder>())
        assertEquals(5, back.count { it is Step.Dissolve })
        assertEquals(
            phone.folders.filter { it.name != "게임" }.map { Step.CreateFolder(it.name, it.apps) },
            back.filterIsInstance<Step.CreateFolder>(),
        )
    }

    @Test
    fun aFolderWithTheRightAppsIsRenamedNotRebuilt() {
        // Seen on the phone: the folder got made but its name didn't stick.
        val current = DrawerLayout(listOf(DrawerFolder("", listOf("토스", "카카오뱅크"))), listOf("Minecraft"))
        val target = DrawerLayout(listOf(DrawerFolder("금융/결제", listOf("카카오뱅크", "토스"))), listOf("Minecraft"))
        assertEquals(listOf(Step.RenameFolder("", "금융/결제")), Planner.steps(current, target))
    }

    @Test
    fun appsGoneFromThePhoneAreSkipped() {
        val target = DrawerLayout(listOf(DrawerFolder("금융/결제", listOf("토스", "카카오뱅크", "카카오페이", "Deleted"))), emptyList())
        val current = DrawerLayout(emptyList(), listOf("토스", "카카오뱅크", "카카오페이"))
        assertEquals(listOf(Step.CreateFolder("금융/결제", listOf("토스", "카카오뱅크", "카카오페이"))), Planner.steps(current, target))
    }

    @Test
    fun previewEditsMoveAndRename() {
        val target = Planner.rebuild(phone, categoryOf, nameOf)
        val edited = Planner.edit(
            target,
            moves = mapOf("Notion" to "finance", "Minecraft" to null),
            renames = mapOf("finance" to "돈", "work" to "  "),
        )
        assertEquals(setOf("토스", "카카오뱅크", "카카오페이", "KB Pay", "Notion"), edited.folder("돈")!!.apps.toSet())
        assertTrue("Minecraft" in edited.loose)
        // A blank rename keeps the name; a folder left with one app falls apart.
        assertTrue(edited.folder("work") != null)
        assertEquals(null, edited.folder("game"))
        assertTrue("브롤스타즈" in edited.loose)
    }

    @Test
    fun aOneAppFolderIsNotMade() {
        val target = DrawerLayout(listOf(DrawerFolder("x", listOf("토스", "Deleted"))), emptyList())
        assertTrue(Planner.steps(DrawerLayout(emptyList(), listOf("토스")), target).isEmpty())
    }
}

class LayoutCodecTest {
    @Test
    fun roundTrips() {
        val layout = DrawerLayout(
            listOf(DrawerFolder("금융/결제", listOf("토스", "KB Pay")), DrawerFolder("빈 이름 ,", listOf("a, b", "c"))),
            listOf("Tidy"),
        )
        assertEquals(layout, LayoutCodec.decode(LayoutCodec.encode(layout)))
        assertEquals(DrawerLayout(emptyList(), emptyList()), LayoutCodec.decode(LayoutCodec.encode(DrawerLayout(emptyList(), emptyList()))))
        assertEquals(null, LayoutCodec.decode("garbage"))
    }
}
