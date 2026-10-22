package app.tidy.android.domain

import app.tidy.android.domain.Category.*

/**
 * Package names Tidy can place without guessing: popular Korean apps (which
 * rarely declare `android:appCategory`) and Samsung's own apps. An exact
 * entry beats a prefix; among prefixes the longest wins.
 */
object KnownApps {

    val exact: Map<String, Category> = mapOf(
        // Social / messaging
        "com.kakao.talk" to SOCIAL,
        "com.instagram.android" to SOCIAL,
        "com.instagram.barcelona" to SOCIAL, // Threads
        "com.facebook.katana" to SOCIAL,
        "com.facebook.orca" to SOCIAL,
        "com.discord" to SOCIAL,
        "org.telegram.messenger" to SOCIAL,
        "com.nhn.android.band" to SOCIAL,
        "com.twitter.android" to SOCIAL,
        "jp.naver.line.android" to SOCIAL,
        "com.whatsapp" to SOCIAL,
        "com.reddit.frontpage" to SOCIAL,

        // Finance
        "viva.republica.toss" to FINANCE,
        "com.kakaopay.app" to FINANCE,
        "com.nhnent.payapp" to FINANCE, // PAYCO
        "com.samsung.android.spay" to FINANCE, // Samsung Wallet
        "nh.smart.banking" to FINANCE,

        // Shopping / delivery
        "com.coupang.mobile" to SHOP,
        "com.coupang.mobile.eats" to SHOP,
        "com.sampleapp" to SHOP, // Baemin
        "com.fineapp.yogiyo" to SHOP,
        "com.towneers.www" to SHOP, // Karrot
        "com.musinsa.store" to SHOP,
        "com.elevenst" to SHOP,
        "com.ebay.kr.gmarket" to SHOP,
        "com.ebay.kr.auction" to SHOP,
        "kr.co.quicket" to SHOP, // Bunjang
        "com.dbs.kurly.m2" to SHOP,
        "com.samsung.sec.android.prd" to SHOP, // Samsung.com
        "com.bgfcu.membership" to SHOP, // CU
        "com.cj.twosome" to SHOP,
        "com.sulbing.mobile.app" to SHOP,
        "kr.co.angelinus.gift.m" to SHOP, // Lotte Eatz
        "kfc_ko.kore.kg.kfc_korea" to SHOP,
        "com.mcdonalds.mobileapp" to SHOP,
        "com.alibaba.aliexpresshd" to SHOP,
        "com.einnovation.temu" to SHOP,
        "com.zzkko" to SHOP, // SHEIN

        // Video / webtoons
        "com.google.android.youtube" to VIDEO,
        "com.netflix.mediaclient" to VIDEO,
        "net.cj.cjhv.gs.tving" to VIDEO,
        "kr.co.captv.pooqV2" to VIDEO, // Wavve
        "com.coupang.mobile.play" to VIDEO,
        "com.disney.disneyplus" to VIDEO,
        "com.nhn.android.webtoon" to VIDEO,
        "com.kakao.page" to VIDEO,
        "tv.twitch.android.app" to VIDEO,
        "com.zhiliaoapp.musically" to VIDEO, // TikTok
        "com.ss.android.ugc.trill" to VIDEO,
        "com.samsung.android.tvplus" to VIDEO,
        "co.kr.cgv.cjcgv" to VIDEO,

        // Music
        "com.iloen.melon" to MUSIC,
        "com.spotify.music" to MUSIC,
        "com.google.android.apps.youtube.music" to MUSIC,
        "com.ktmusic.geniemusic" to MUSIC,
        "com.sktelecom.flomusic" to MUSIC,
        "com.sec.android.app.fm" to MUSIC, // Samsung radio

        // Photo / camera
        "com.sec.android.app.camera" to PHOTO,
        "com.sec.android.gallery3d" to PHOTO,
        "com.google.android.apps.photos" to PHOTO,
        "com.lemon.lvoverseas" to PHOTO, // CapCut
        "com.campmobile.snow" to PHOTO,
        "com.linecorp.b612.android" to PHOTO,
        "com.adobe.lrmobile" to PHOTO,
        "com.vsco.cam" to PHOTO,

        // Work / productivity
        "notion.id" to WORK,
        "com.google.android.gm" to WORK,
        "com.google.android.calendar" to WORK,
        "com.google.android.keep" to WORK,
        "com.samsung.android.app.notes" to WORK,
        "com.samsung.android.calendar" to WORK,
        "com.samsung.android.email.provider" to WORK,
        "com.samsung.android.app.reminder" to WORK,
        "ch.protonmail.android" to WORK,
        "com.Slack" to WORK,
        "us.zoom.videomeetings" to WORK,
        "com.microsoft.teams" to WORK,

        // Maps / transport
        "com.nhn.android.nmap" to MAP,
        "net.daum.android.map" to MAP,
        "com.kakao.taxi" to MAP, // Kakao T
        "com.skt.tmap.ku" to MAP,
        "com.korail.talk" to MAP,
        "com.google.android.apps.maps" to MAP,
        "kr.co.tmoney.tia" to MAP, // T-money GO
        "com.cultsotry.yanolja.nativeapp" to MAP, // Yanolja
        "kr.goodchoice.abouthere" to MAP, // Yeogi Eottae

        // News / portals
        "com.nhn.android.search" to NEWS, // NAVER
        "net.daum.android.daum" to NEWS,
        "flipboard.app" to NEWS,

        // Health
        "com.sec.android.app.shealth" to HEALTH,
        "com.cashwalk.cashwalk" to HEALTH,
        "com.google.android.apps.fitness" to HEALTH,
        "com.nike.plusgps" to HEALTH,
        "com.samsung.android.shealthmonitor" to HEALTH,

        // Games
        "com.samsung.android.game.gamehome" to GAME, // Gaming Hub

        // Tools
        "com.android.chrome" to TOOLS,
        "com.sec.android.app.sbrowser" to TOOLS, // Samsung Internet
        "com.google.android.googlequicksearchbox" to TOOLS,
        "com.google.android.apps.translate" to TOOLS,
        "com.naver.labs.translator" to TOOLS, // Papago
        "org.mozilla.firefox" to TOOLS,
        "com.sec.android.app.voicenote" to TOOLS,
        "org.zwanoo.android.speedtest" to TOOLS,

        // Built-in
        "com.android.vending" to SYSTEM,
    )

    val prefixes: List<Pair<String, Category>> = listOf(
        "com.kbstar." to FINANCE,
        "com.shinhan." to FINANCE,
        "com.wooribank." to FINANCE,
        "com.hanabank." to FINANCE,
        "com.kakaobank." to FINANCE,
        "kr.co.samsungcard" to FINANCE,
        "com.hyundaicard." to FINANCE,
        "com.miraeasset." to FINANCE,
        "com.kiwoom." to FINANCE,
        "com.dunamu." to FINANCE, // Upbit
        "com.kbcard." to FINANCE,
        "com.naverfin." to FINANCE, // Naver Pay

        "com.starbucks." to SHOP,

        "com.google.android.apps.docs" to WORK, // Drive, Docs, Sheets, Slides
        "com.microsoft.office." to WORK,

        "com.supercell." to GAME,
        "com.devsisters." to GAME,
        "com.miHoYo." to GAME,
        "com.HoYoverse." to GAME,
        "com.netmarble." to GAME,
        "com.nexon." to GAME,
        "com.ncsoft." to GAME,
        "com.kakaogames." to GAME,
        "com.valvesoftware." to GAME, // Steam
        "com.nintendo." to GAME,
        "jp.pokemon." to GAME,

        "kr.ac." to EDU, // Korean universities
        "com.hackers." to EDU,
        "com.blackboard." to EDU,

        "com.ahnlab." to TOOLS, // V3

        "com.samsung." to SYSTEM,
        "com.sec." to SYSTEM,
        "com.android." to SYSTEM,
    )

    fun lookup(packageName: String): Category? =
        exact[packageName]
            ?: prefixes
                .filter { (prefix, _) -> packageName.startsWith(prefix) }
                .maxByOrNull { (prefix, _) -> prefix.length }
                ?.second
}
