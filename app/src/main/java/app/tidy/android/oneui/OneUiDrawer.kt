package app.tidy.android.oneui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import app.tidy.android.organize.DrawerDriver
import app.tidy.android.organize.DrawerFolder
import app.tidy.android.organize.DrawerLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * One UI's apps screen, operated through its own controls -- no drag and
 * drop -- as found on a real S24 (One UI 8 / Android 17):
 *
 * - **Read**: icons are `icon_container` (app) and `folder_icon_container`
 *   (folder), each named by its inner `icon` / `folder_icon_view` text (the
 *   content description carries extras like ", 알림 1개"). An open folder is
 *   `folder_container`, titled by `folder_title`, 12 icons per page; other
 *   pages are reached by swiping, they aren't in the tree until shown.
 * - **New folder**: long-press an app → "선택" → tap the other apps →
 *   `multi_select_create_folder` → the new folder opens untitled → set
 *   `folder_title`.
 * - **Add to a folder**: open it → `add_apps` → the app picker: search,
 *   tap the row whose `title` is the app (its `left_frame` checkbox isn't
 *   clickable itself), `doneButton`.
 * - **Dissolve**: long-press the folder → "삭제" → the dialog ("폴더를
 *   삭제할까요? … 앱은 앱스 화면에서 계속 볼 수 있습니다") → "삭제". Apps stay.
 *
 * An app's long-press menu has "설치 삭제" (uninstall) and select mode has a
 * `multi_select_uninstall` button: menu items are only ever matched by
 * their exact text, and a menu is only acted on after checking it belongs to
 * the icon that was long-pressed.
 *
 * Every action checks the screen afterwards and reports failure instead of
 * guessing. All One UI specifics are in the constants at the bottom.
 */
class OneUiDrawer(private val service: AccessibilityService) : DrawerDriver {

    // --- finding things ----------------------------------------------------------

    /** The launcher's windows, including popups (menus, dialogs) that are windows of their own. */
    private fun roots(): List<AccessibilityNodeInfo> {
        val all = service.windows.mapNotNull { it.root } + listOfNotNull(service.rootInActiveWindow)
        return all.filter { it.packageName == LAUNCHER }.distinct()
    }

    private fun byId(id: String): List<AccessibilityNodeInfo> =
        roots().flatMap { it.findAccessibilityNodeInfosByViewId("$LAUNCHER:id/$id") }.filter { it.isVisibleToUser }

    /** Visible nodes whose text is exactly [text] (the platform search matches substrings). */
    private fun exactText(text: String): List<AccessibilityNodeInfo> =
        roots().flatMap { it.findAccessibilityNodeInfosByText(text) }
            .filter { it.isVisibleToUser && it.text?.toString()?.trim() == text }

    private fun appsScreenUp() = byId(APPS_SCREEN).isNotEmpty()
    private fun folderOpen() = byId(FOLDER_CONTAINER).isNotEmpty()
    private fun pickerUp() = byId(PICKER_DONE).isNotEmpty()
    private fun list() = byId(APP_LIST).firstOrNull()

    private data class Icon(val name: String, val isFolder: Boolean, val node: AccessibilityNodeInfo)

    /** App and folder icons under [container], in screen order. */
    private fun icons(container: AccessibilityNodeInfo?): List<Icon> {
        val out = mutableListOf<Icon>()
        fun label(node: AccessibilityNodeInfo, id: String): String =
            node.findAccessibilityNodeInfosByViewId("$LAUNCHER:id/$id").firstOrNull()?.text?.toString()?.trim().orEmpty()
        fun walk(node: AccessibilityNodeInfo?) {
            node ?: return
            val wrapsFolder = node.findAccessibilityNodeInfosByViewId("$LAUNCHER:id/$FOLDER_ICON").any { it != node }
            when (node.viewIdResourceName) {
                // Some folders sit inside an extra icon_container (seen on the first rows).
                "$LAUNCHER:id/$APP_ICON" if wrapsFolder -> for (i in 0 until node.childCount) walk(node.getChild(i))
                "$LAUNCHER:id/$APP_ICON" -> if (node.isVisibleToUser) out += Icon(label(node, APP_LABEL), false, node)
                "$LAUNCHER:id/$FOLDER_ICON" -> if (node.isVisibleToUser) out += Icon(label(node, FOLDER_LABEL), true, node)
                else -> for (i in 0 until node.childCount) walk(node.getChild(i))
            }
        }
        walk(container)
        return out
    }

    private fun bounds(node: AccessibilityNodeInfo) = Rect().also(node::getBoundsInScreen)

    /**
     * Icons once the screen has stopped moving: the same names and positions
     * twice in a row. Read mid-animation (the apps screen sliding up, a folder
     * zooming open), the top rows aren't "visible" yet and would be missed.
     */
    private suspend fun settledIcons(container: () -> AccessibilityNodeInfo?): List<Icon> {
        var last: List<Pair<String, Rect>>? = null
        repeat(SETTLE_POLLS) {
            val now = icons(container())
            val shape = now.map { it.name to bounds(it.node) }
            if (shape.isNotEmpty() && shape == last) return now
            last = shape
            delay(POLL_MS * 2)
        }
        return icons(container())
    }

    // --- open / leave ------------------------------------------------------------

    /** Brings up the apps screen with nothing open on it. */
    suspend fun open(): Boolean {
        repeat(3) {
            if (pickerUp() || folderOpen()) back()
        }
        if (appsScreenUp() && !folderOpen()) return true
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        if (!waitFor { roots().isNotEmpty() && !appsScreenUp() }) log("home didn't come up")
        delay(SETTLE_MS * 2)
        val m = service.resources.displayMetrics
        val x = m.widthPixels / 2f
        repeat(2) { attempt ->
            val sent = swipe(x, m.heightPixels * 0.8f, x, m.heightPixels * 0.3f)
            if (waitFor { appsScreenUp() && !folderOpen() }) return true
            log("swipe up #$attempt (sent=$sent) didn't open the apps screen; launcher windows=${roots().size}")
            delay(SETTLE_MS)
        }
        return false
    }

    /**
     * Closes the apps screen with BACK, leaving One UI on its home screen.
     * (Not HOME: right after a home press Android blocks other apps from
     * starting activities for a few seconds, so Tidy couldn't come back.)
     */
    suspend fun leave() {
        repeat(3) {
            if (pickerUp() || folderOpen() || appsScreenUp()) back()
        }
    }

    private suspend fun back() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        delay(SETTLE_MS)
    }

    private suspend fun closeFolder(): Boolean {
        if (!folderOpen()) return true
        back()
        return waitFor { !folderOpen() }
    }

    // --- reading -------------------------------------------------------------------

    override suspend fun read(): DrawerLayout? {
        if (!open()) return null
        val top = topLevel() ?: return null.also { log("apps list not found") }
        log("apps screen: ${top.count { it.isFolder }} folders, ${top.count { !it.isFolder }} apps")
        val folders = top.filter { it.isFolder }.map { folder ->
            DrawerFolder(folder.name, readFolder(folder.name) ?: return null.also { log("couldn't read a folder") })
        }
        return DrawerLayout(folders, top.filterNot { it.isFolder }.map { it.name })
    }

    /** Every icon on the apps screen (not inside folders), scrolling through the whole list. */
    private suspend fun topLevel(): List<Icon>? {
        list() ?: return null
        scrollToTop()
        val seen = LinkedHashMap<Pair<String, Boolean>, Icon>()
        while (true) {
            settledIcons { list() }.forEach { seen.putIfAbsent(it.name to it.isFolder, it) }
            if (list()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) break
            delay(SCROLL_MS)
        }
        return seen.values.toList()
    }

    private suspend fun scrollToTop() {
        var guard = 0
        while (list()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true && guard++ < 30) delay(SCROLL_MS)
    }

    /** Opens [name], reads every page of it, and closes it again. */
    private suspend fun readFolder(name: String): List<String>? {
        if (!openFolder(name)) return null
        val apps = LinkedHashSet<String>()
        var page = 0
        while (page++ < MAX_FOLDER_PAGES) {
            val onPage = settledIcons { byId(FOLDER_PAGE).firstOrNull() }.map { it.name }
            if (!apps.addAll(onPage)) break
            val pager = byId(FOLDER_PAGER).firstOrNull() ?: break
            val r = bounds(pager)
            swipe(r.right - r.width() * 0.15f, r.exactCenterY(), r.left + r.width() * 0.15f, r.exactCenterY())
            delay(SETTLE_MS)
        }
        closeFolder()
        return apps.toList()
    }

    private suspend fun openFolder(name: String): Boolean {
        val folder = find(name, isFolder = true) ?: return false
        if (!folder.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return false
        return waitFor { folderOpen() }
    }

    /** Scrolls the apps list until the icon [name] is on screen. */
    private suspend fun find(name: String, isFolder: Boolean): Icon? {
        fun look() = icons(list()).firstOrNull { it.name == name && it.isFolder == isFolder }
        look()?.let { return it }
        list() ?: return null
        scrollToTop()
        var guard = 0
        while (guard++ < 50) {
            look()?.let { return it }
            if (list()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != true) return null
            delay(SCROLL_MS)
        }
        return null
    }

    // --- changing ------------------------------------------------------------------

    override suspend fun addToFolder(apps: List<String>, folder: String): Boolean {
        if (!open() || !openFolder(folder)) return false
        val add = byId(ADD_APPS).firstOrNull() ?: return false
        if (!add.performAction(AccessibilityNodeInfo.ACTION_CLICK) || !waitFor { pickerUp() }) return false
        for (app in apps) {
            if (!pick(app)) {
                back() // leave the picker without saving
                return false
            }
        }
        byId(PICKER_DONE).firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: return false
        if (!waitFor { !pickerUp() }) return false
        closeFolder()
        if (!open()) return false
        return apps.all { find(it, isFolder = false) == null }
    }

    /** In the app picker: searches for [app] and ticks its row. */
    private suspend fun pick(app: String): Boolean {
        if (byId(PICKER_SEARCH_FIELD).isEmpty()) {
            byId(PICKER_SEARCH).firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (!waitFor { byId(PICKER_SEARCH_FIELD).isNotEmpty() }) return false
        }
        val field = byId(PICKER_SEARCH_FIELD).first()
        if (!setText(field, app)) return false
        delay(SETTLE_MS)
        val title = byId(PICKER_TITLE).firstOrNull { it.text?.toString()?.trim() == app } ?: return false
        val row = generateSequence(title) { it.parent }.take(4).firstOrNull { n ->
            n.findAccessibilityNodeInfosByViewId("$LAUNCHER:id/$PICKER_CHECKBOX").isNotEmpty()
        } ?: return false
        val box = row.findAccessibilityNodeInfosByViewId("$LAUNCHER:id/$PICKER_CHECKBOX").first()
        // The checkbox itself isn't clickable; the whole row is.
        if (!box.isChecked) click(row)
        delay(POLL_MS * 2)
        box.refresh()
        return box.isChecked
    }

    override suspend fun createFolder(name: String, apps: List<String>): Boolean {
        if (!open()) return false
        val first = find(apps[0], isFolder = false) ?: return false
        if (!longPressMenu(first, apps[0])) return false
        val select = exactText(MENU_SELECT).firstOrNull() ?: return dismissMenu()
        click(select)
        if (!waitFor { byId(CREATE_FOLDER).isNotEmpty() }) return false
        for (app in apps.drop(1)) {
            val icon = find(app, isFolder = false) ?: return cancelSelect()
            click(icon.node)
            delay(POLL_MS * 2)
        }
        val create = byId(CREATE_FOLDER).firstOrNull()?.takeIf { it.isEnabled } ?: return cancelSelect()
        click(create)
        if (!waitFor { folderOpen() }) return false
        val title = byId(FOLDER_TITLE).firstOrNull() ?: return false
        if (!setText(title, name)) return false
        delay(SETTLE_MS)
        // Keyboard, then the folder.
        repeat(2) { if (folderOpen()) back() }
        closeFolder()
        return open() && find(name, isFolder = true) != null
    }

    override suspend fun dissolve(folder: String): Boolean {
        if (!open()) return false
        val icon = find(folder, isFolder = true) ?: return false
        if (!longPressMenu(icon, folder.ifEmpty { UNNAMED_FOLDER })) return false
        // A folder's menu has "삭제"; an app's has "설치 삭제" -- exact match only.
        val delete = exactText(MENU_DELETE_FOLDER).firstOrNull() ?: return dismissMenu()
        click(delete)
        if (!waitFor { exactText(DIALOG_DELETE_FOLDER_TITLE).isNotEmpty() }) return false
        val confirm = byId(DIALOG_CONFIRM).firstOrNull { it.text?.toString()?.trim() == MENU_DELETE_FOLDER }
            ?: roots().flatMap { it.findAccessibilityNodeInfosByViewId(DIALOG_CONFIRM_ANDROID) }
                .firstOrNull { it.text?.toString()?.trim() == MENU_DELETE_FOLDER }
            ?: return dismissMenu()
        click(confirm)
        delay(SETTLE_MS)
        return open() && find(folder, isFolder = true) == null
    }

    /**
     * Long-presses [icon] and waits for its menu, whose header shows the
     * icon's name -- so the menu acted on is known to be this icon's.
     */
    private suspend fun longPressMenu(icon: Icon, header: String): Boolean {
        if (!icon.node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            val r = bounds(icon.node)
            press(r.exactCenterX(), r.exactCenterY())
        }
        return waitFor { exactText(header).any { !isIconLabel(it) } } || dismissMenu()
    }

    private fun isIconLabel(node: AccessibilityNodeInfo) =
        node.viewIdResourceName in setOf("$LAUNCHER:id/$APP_LABEL", "$LAUNCHER:id/$FOLDER_LABEL", "$LAUNCHER:id/$FOLDER_TITLE")

    private suspend fun dismissMenu(): Boolean {
        back()
        return false
    }

    private suspend fun cancelSelect(): Boolean {
        back()
        return false
    }

    private fun click(node: AccessibilityNodeInfo) {
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        (n ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        node.refresh()
        return node.text?.toString() == text
    }

    // --- gestures ------------------------------------------------------------------

    private suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        return gesture(GestureDescription.StrokeDescription(path, 0, SWIPE_MS))
    }

    private fun log(message: String) = Log.i(TAG, message)

    private suspend fun press(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y); lineTo(x + 1, y + 1) }
        gesture(GestureDescription.StrokeDescription(path, 0, LONG_PRESS_MS))
    }

    private suspend fun gesture(stroke: GestureDescription.StrokeDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val sent = service.dispatchGesture(
                GestureDescription.Builder().addStroke(stroke).build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(d: GestureDescription) { if (cont.isActive) cont.resume(true) }
                    override fun onCancelled(d: GestureDescription) { if (cont.isActive) cont.resume(false) }
                },
                null,
            )
            if (!sent && cont.isActive) cont.resume(false)
        }

    private suspend fun waitFor(timeoutMs: Long = WAIT_MS, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return true
            delay(POLL_MS)
        }
        return check()
    }

    private companion object {
        const val LAUNCHER = "com.sec.android.app.launcher"
        const val TAG = "TidyDrawer"

        // View ids, One UI 8 (Android 17) on an S24.
        const val APPS_SCREEN = "apps_screen"
        const val APP_LIST = "vertical_applist_recycler_view"
        const val APP_ICON = "icon_container"
        const val APP_LABEL = "icon"
        const val FOLDER_ICON = "folder_icon_container"
        const val FOLDER_LABEL = "folder_icon_view"
        const val FOLDER_CONTAINER = "folder_container"
        const val FOLDER_TITLE = "folder_title"
        const val FOLDER_PAGER = "folder_fr_view"
        const val FOLDER_PAGE = "open_folder_cell_layout"
        const val ADD_APPS = "add_apps"
        const val CREATE_FOLDER = "multi_select_create_folder"
        const val PICKER_DONE = "doneButton"
        const val PICKER_SEARCH = "searchIcon"
        const val PICKER_SEARCH_FIELD = "search_src_text"
        const val PICKER_TITLE = "title"
        const val PICKER_CHECKBOX = "left_frame"
        const val DIALOG_CONFIRM = "button1"
        const val DIALOG_CONFIRM_ANDROID = "android:id/button1"

        // Wording (Korean One UI; English untested).
        const val MENU_SELECT = "선택"
        const val MENU_DELETE_FOLDER = "삭제"
        const val DIALOG_DELETE_FOLDER_TITLE = "폴더를 삭제할까요?"
        /** What a folder without a name shows as its title. */
        const val UNNAMED_FOLDER = "폴더 이름"

        const val MAX_FOLDER_PAGES = 20
        const val SETTLE_POLLS = 15
        const val SETTLE_MS = 450L
        const val SCROLL_MS = 350L
        const val POLL_MS = 100L
        const val WAIT_MS = 3_000L
        const val SWIPE_MS = 250L
        const val LONG_PRESS_MS = 800L
    }
}
