package app.tidy.android.domain

import app.tidy.android.domain.Category.*

/**
 * Picks a folder for an app from what's on the phone alone -- nothing is
 * looked up online, so the app list never leaves the device. Pure Kotlin:
 * the caller maps `ApplicationInfo.category` to [osCategory] first.
 *
 * Order: known package -> the app's own declared category, if it's a
 * specific one -> keywords in the package name and label -> a vague
 * declared category -> built-in apps to SYSTEM -> ETC. A user's manual move
 * beats all of this; that's applied on top (see [assign]).
 */
object Categorizer {

    /**
     * Declared categories apps use loosely: on a real phone, payment apps,
     * e-book readers and VPNs all said "productivity", and Firefox and an
     * email app said "social". Keywords get a say before these.
     */
    private val VAGUE_DECLARED = setOf(SOCIAL, NEWS, WORK, TOOLS)

    fun classify(packageName: String, label: String, osCategory: Category?, isSystem: Boolean): Category =
        KnownApps.lookup(packageName)
            ?: osCategory?.takeIf { it !in VAGUE_DECLARED }
            ?: byKeyword(packageName, label)
            ?: osCategory
            ?: if (isSystem) SYSTEM else ETC

    /** The folder an app shows in: the user's override when there is one. */
    fun assign(packageName: String, auto: Category, overrides: Map<String, Category>): Category =
        overrides[packageName] ?: auto

    // Checked in order; the first category with a hit wins. ASCII keywords
    // match whole package-name segments or label words (so "tv" doesn't hit
    // "activity"), unless at least 5 letters long, which may match inside a
    // segment ("mybanking"). Hangul keywords match anywhere in the label.
    private val KEYWORDS: List<Pair<Category, List<String>>> = listOf(
        FINANCE to listOf("bank", "banking", "pay", "card", "stock", "invest", "securities", "wallet", "insurance",
            "은행", "뱅크", "뱅킹", "페이", "카드", "증권", "주식", "코인", "보험"),
        SHOP to listOf("shop", "shopping", "mall", "market", "delivery", "eats", "coupon",
            "chicken", "pizza", "burger", "coffee", "cafe", "mart",
            "쇼핑", "마켓", "배달", "쿠폰", "몰", "치킨", "피자", "버거", "커피", "마트", "편의점", "중고", "가격"),
        GAME to listOf("game", "games", "게임"),
        MUSIC to listOf("music", "radio", "podcast", "음악", "뮤직", "라디오", "팟캐스트"),
        VIDEO to listOf("video", "tv", "movie", "youtube", "cinema", "webtoon", "comic", "anime",
            "영화", "시네마", "웹툰", "드라마", "만화"),
        PHOTO to listOf("camera", "photo", "photos", "gallery", "카메라", "사진", "갤러리", "보정"),
        MAP to listOf("map", "maps", "navi", "navigation", "taxi", "bus", "subway", "transit", "train",
            "travel", "hotel", "trip", "flight",
            "지도", "내비", "택시", "버스", "지하철", "기차", "교통", "여행", "호텔", "숙소", "항공", "렌터카"),
        SOCIAL to listOf("chat", "messenger", "talk", "social", "메신저", "채팅", "톡"),
        // Before EDU, so a university hospital is a hospital.
        HEALTH to listOf("health", "fitness", "workout", "running", "hospital", "clinic",
            "건강", "운동", "헬스", "만보", "병원", "약국", "의원"),
        NEWS to listOf("news", "book", "ebook", "library", "novel", "뉴스", "신문", "도서", "문고", "웹소설"),
        EDU to listOf("edu", "school", "university", "univ", "campus", "toeic", "lecture", "class", "study", "academy",
            "대학교", "학교", "토익", "강의", "학습", "교육", "수업", "공부", "인강"),
        WORK to listOf("mail", "office", "docs", "note", "notes", "calendar", "메일", "캘린더", "메모", "노트", "문서"),
        TOOLS to listOf("browser", "vpn", "scanner", "translate", "translator", "flashlight", "calculator", "cleaner",
            "dictionary", "speedtest", "번역", "스캐너", "손전등", "계산기", "사전"),
    )

    private val SEPARATORS = Regex("[^a-z0-9]+")

    internal fun byKeyword(packageName: String, label: String): Category? {
        val lowerLabel = label.lowercase()
        val words = (packageName.lowercase().split(SEPARATORS) + lowerLabel.split(SEPARATORS))
            .filter { it.isNotEmpty() }
            .toSet()
        for ((category, keywords) in KEYWORDS) {
            for (k in keywords) {
                val hit = when {
                    !k.all { it in 'a'..'z' } -> k in lowerLabel
                    k.length >= 5 -> words.any { k in it }
                    else -> k in words
                }
                if (hit) return category
            }
        }
        return null
    }
}
