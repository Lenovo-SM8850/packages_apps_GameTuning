// SPDX-License-Identifier: Apache-2.0
package io.github.miner7222.gametuning.data

import android.content.Context

/** Shared names for installed games and built-in profile sources. */
object GameNames {
    private enum class Region(val code: String) {
        CHINA("CN"),
        GLOBAL("Global"),
        KOREA("KR"),
        TAIWAN("TW"),
        VIETNAM("VN"),
        US("US"),
        SEA("SEA"),
        INDIA("IN"),
        JAPAN("JP"),
    }

    private data class Name(val english: String, val region: Region, val group: String = english)

    private val names = mapOf(
        "com.tencent.tmgp.pubgmhd" to Name("Game for Peace", Region.CHINA),
        "com.tencent.tmgp.sgame" to Name("Honor of Kings", Region.CHINA),
        "com.tencent.letsgo" to Name("Party Stars", Region.CHINA),
        "com.tencent.tmgp.cod" to Name("Call of Duty: Mobile", Region.CHINA),
        "com.miHoYo.Nap" to Name("Zenless Zone Zero", Region.CHINA),
        "com.tencent.ngr" to Name("Honor of Kings: World", Region.CHINA),
        "com.tencent.mf.uam" to Name("Arena Breakout", Region.CHINA),
        "com.tencent.tmgp.cf" to Name("CrossFire: Legends", Region.CHINA),
        "com.miHoYo.Yuanshen" to Name("Genshin Impact", Region.CHINA),
        "com.miHoYo.ys.bilibili" to Name("Genshin Impact", Region.CHINA),
        "com.tencent.jkchess" to Name("Battle of the Golden Spatula", Region.CHINA),
        "com.tencent.tmgp.dfm" to Name("Delta Force", Region.CHINA),
        "com.miHoYo.hkrpg" to Name("Honkai: Star Rail", Region.CHINA),
        "com.tencent.tmgp.speedmobile" to Name("QQ Speed", Region.CHINA),
        "com.netease.yhtj" to Name("Lost Light", Region.CHINA),
        "com.tencent.tmgp.gnyx" to Name("High Energy Heroes", Region.CHINA),
        "com.blizzard.wtcg.hearthstone" to Name("Hearthstone", Region.CHINA),
        "com.tencent.lolm" to Name("League of Legends: Wild Rift", Region.CHINA),
        "com.tencent.KiHan" to Name("Naruto Mobile", Region.CHINA),
        "com.Sunborn.SnqxExilium" to Name("GIRLS' FRONTLINE 2: EXILIUM", Region.CHINA),
        "com.netease.nshm" to Name("Sword of Justice", Region.CHINA),
        "com.kurogame.mingchao" to Name("Wuthering Waves", Region.CHINA),
        "com.tencent.tmgp.WePop" to Name("KartRider Rush+", Region.CHINA),
        "com.pwrd.hotta.laohu" to Name("Tower of Fantasy", Region.CHINA),
        "com.tencent.tmgp.supercell.brawlstars" to Name("Brawl Stars", Region.CHINA),
        "com.tencent.tmgp.codev" to Name("VALORANT Mobile", Region.CHINA),
        "com.tencent.projectc" to Name("VALORANT Mobile", Region.CHINA),
        "com.sofunny.Sausage" to Name("Sausage Man", Region.CHINA),
        "com.dragonli.projectsnow.lhm" to Name("Snowbreak: Containment Zone", Region.CHINA),
        "com.netease.pm02" to Name("Revelation", Region.CHINA),
        "com.tencent.tmgp.dnf" to Name("Dungeon & Fighter Mobile", Region.CHINA),
        "com.miHoYo.GenshinImpact" to Name("Genshin Impact", Region.GLOBAL),
        "com.HoYoverse.Nap" to Name("Zenless Zone Zero", Region.GLOBAL),
        "com.HoYoverse.hkrpgoversea" to Name("Honkai: Star Rail", Region.GLOBAL),
        "com.riotgames.league.wildrift" to Name("League of Legends: Wild Rift", Region.GLOBAL),
        "com.mobile.legends.usa" to Name("Mobile Legends: Bang Bang", Region.US, "Mobile Legends"),
        "com.proximabeta.mf.uamo" to Name("Arena Breakout", Region.GLOBAL),
        "com.kurogame.wutheringwaves.global" to Name("Wuthering Waves", Region.GLOBAL),
        "com.tencent.ig" to Name("PUBG MOBILE", Region.GLOBAL),
        "com.rekoo.pubgm" to Name("PUBG MOBILE", Region.TAIWAN),
        "com.vng.pubgmobile" to Name("PUBG MOBILE", Region.VIETNAM),
        "com.pubg.krmobile" to Name("PUBG MOBILE", Region.KOREA),
        "com.tencent.igce" to Name("PUBG MOBILE", Region.GLOBAL),
        "com.tencent.igfit" to Name("PUBG MOBILE FLASH", Region.GLOBAL, "PUBG MOBILE"),
        "com.proxima.dfm" to Name("Delta Force", Region.GLOBAL),
        "com.papegames.infinitynikki" to Name("Infinity Nikki", Region.CHINA),
        "com.hypergryph.endfield" to Name("Arknights: Endfield", Region.CHINA),
        "com.xiaoji.egggame.lenovo" to Name("GameHub", Region.CHINA),
        "com.xiaoji.egggame" to Name("GameHub", Region.GLOBAL),
        "com.hypergryph.arknights" to Name("Arknights", Region.CHINA),
        "com.netease.x19" to Name("Minecraft China Edition", Region.CHINA, "Minecraft"),
        "com.pi.czrxdfirst" to Name("Tomb Busters", Region.CHINA),
        "com.papegames.lysk.cn" to Name("Love and Deepspace", Region.CHINA),
        "com.tencent.nrc" to Name("Roco Kingdom Mobile", Region.CHINA),
        "com.hottagames.yh.laohu" to Name("NTE: Neverness to Everness", Region.CHINA),
        "com.tencent.rmcn" to Name("Rust Mobile", Region.CHINA),
        "com.hyhd.sgbhsd.g.baidu" to Name("Ice War: Three Kingdoms", Region.CHINA),
        "com.Sunborn.SnqxExilium.Glo" to Name("GIRLS' FRONTLINE 2: EXILIUM", Region.GLOBAL),
        "com.seasun.snowbreak.google" to Name("Snowbreak: Containment Zone", Region.GLOBAL),
        "com.gryphline.endfield.gp" to Name("Arknights: Endfield", Region.GLOBAL),
        "com.miHoYo.Nap.bilibili" to Name("Zenless Zone Zero", Region.CHINA),
        "com.miHoYo.hkrpg.bilibili" to Name("Honkai: Star Rail", Region.CHINA),
        "com.mobile.legends" to Name("Mobile Legends: Bang Bang", Region.GLOBAL, "Mobile Legends"),
        "com.netease.nshmhmt" to Name("Sword of Justice", Region.TAIWAN),
        "vng.games.revelation.mobile" to Name("Revelation", Region.SEA),
        "com.icantw.ty" to Name("Revelation", Region.TAIWAN),
        "com.netease.h75na" to Name("Lost Light", Region.GLOBAL),
        "com.netease.yysls" to Name("Where Winds Meet", Region.GLOBAL),
        "com.infoldgames.infinitynikkien" to Name("Infinity Nikki", Region.GLOBAL),
        "com.levelinfinite.hotta.gp" to Name("Tower of Fantasy", Region.GLOBAL),
        "tw.com.iwplay.ht" to Name("Tower of Fantasy", Region.TAIWAN),
        "com.GlobalSoFunny.Sausage" to Name("Sausage Man", Region.GLOBAL),
        "com.riotgames.league.wildrifttw" to Name("League of Legends: Wild Rift", Region.TAIWAN),
        "com.nexon.kart" to Name("KartRider Rush+", Region.GLOBAL),
        "com.tencent.stc.cfl" to Name("CrossFire: Legends", Region.GLOBAL),
        "com.activision.callofduty.shooter" to Name("Call of Duty: Mobile", Region.GLOBAL),
        "com.garena.game.codm" to Name("Call of Duty: Mobile", Region.SEA),
        "com.tencent.tmgp.kr.codm" to Name("Call of Duty: Mobile", Region.KOREA),
        "com.pubg.imobile" to Name("Battlegrounds Mobile India", Region.INDIA),
        "com.levelinfinite.sgameGlobal" to Name("Honor of Kings", Region.GLOBAL),
        "com.garena.game.fctw" to Name("Speed Drifters", Region.SEA, "QQ Speed"),
        "com.supercell.brawlstars" to Name("Brawl Stars", Region.GLOBAL),
        "com.hottagames.nte" to Name("NTE: Neverness to Everness", Region.GLOBAL),
        "com.YoStarEN.Arknights" to Name("Arknights", Region.GLOBAL),
        "com.YoStarJP.Arknights" to Name("Arknights", Region.JAPAN),
        "com.YoStarKR.Arknights" to Name("Arknights", Region.KOREA),
        "tw.txwy.and.arknights" to Name("Arknights", Region.TAIWAN),
        "com.mojang.minecraftpe" to Name("Minecraft", Region.GLOBAL),
        "com.papegames.lysk.en" to Name("Love and Deepspace", Region.GLOBAL),
        "com.papegames.lysk.tw" to Name("Love and Deepspace", Region.TAIWAN),
    )

    fun region(packageName: String): String? = names[packageName]?.region?.code

    fun resolve(context: Context, packageName: String, originalName: String, installed: Boolean): String {
        val name = names[packageName] ?: return originalName
        val chinese = context.resources.configuration.locales[0]?.language == "zh"
        return if (!chinese && (!installed || isChineseOnly(originalName))) name.english else originalName
    }

    private fun isChineseOnly(title: String): Boolean {
        var hasHan = false
        val allowed = title.codePoints().allMatch { point ->
            if (Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN) {
                hasHan = true
                true
            } else {
                Character.isDigit(point) || Character.isWhitespace(point) || Character.isSpaceChar(point) ||
                    Character.getType(point) in intArrayOf(
                        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
                        Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
                        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
                        Character.OTHER_PUNCTUATION.toInt(),
                    )
            }
        }
        return allowed && hasHan
    }
}
