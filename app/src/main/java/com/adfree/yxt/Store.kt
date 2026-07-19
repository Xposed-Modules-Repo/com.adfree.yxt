package com.adfree.yxt

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 日志存储:全部落在模块自己的私有目录,钩子经 ContentProvider 写入,UI 直接读。无需 root。 */
object Store {
    private const val LOG = "adblock_log.txt"
    private const val PREFS = "cfg"
    private const val MAX = 1024L * 1024L

    private fun logFile(c: Context) = File(c.filesDir, LOG)

    @Synchronized
    fun append(c: Context, type: String) {
        runCatching {
            autoClear(c)
            val f = logFile(c)
            if (f.length() > MAX) f.writeText("")
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText(
                "$time | ${type}广告 | 广告位 ${slots(type)} | 聚合:云帆YFanAds(风船2121) | " +
                    "联盟:穿山甲/优量汇/快手/百度/OPPO/小米/华为 | 已拦截\n"
            )
        }
    }

    /** 广告类型 -> 对应广告位 ID(取自乐校通 Common 类) */
    fun slots(type: String): String = when {
        type.contains("开屏") -> "2121004(冷启)/2121005(热启)"
        type.contains("首页插屏") -> "2121001"
        type.contains("插屏") -> "2121001/2121003/2121006/2121007/2121011"
        type.contains("首页信息流") -> "2121002"
        type.contains("信息流") -> "2121002/2121008/2121009/2121010"
        type.contains("banner") -> "8448916392638485"
        else -> "-"
    }

    /** 广告联盟与域名对照(展示/导出时置于日志顶部) */
    fun legend(): String = buildString {
        append("【广告联盟 · 域名对照】\n")
        append("穿山甲 CSJ   pangolin-sdk-toutiao.com / dig.bdurl.net\n")
        append("优量汇 GDT   gdt.qq.com / qzs.qq.com\n")
        append("快手 KS      e.kuaishou.com / ad.partner.gifshow.com\n")
        append("百度 Baidu   mobads.baidu.com / mobads-logs.baidu.com\n")
        append("OPPO        adx.ads.oppomobile.com / ck.opmobile.heytapmobi.com\n")
        append("小米 Mimo    api.ad.xiaomi.com\n")
        append("华为 HiAd    adxserver.ad.hicloud.com\n")
        append("聚合平台     云帆 YFanAds(风船 appKey=2121 / 创智 41719)\n")
        append("说明:拦截发生在请求发出之前,故不产生实际广告流量;上列为该广告位可能调用的联盟及域名。\n")
        append("────────────────────────────\n")
    }

    fun read(c: Context): String = runCatching {
        val f = logFile(c); if (!f.exists()) "" else f.readText()
    }.getOrDefault("")

    @Synchronized
    fun clear(c: Context) {
        runCatching { logFile(c).writeText("") }
    }

    fun count(c: Context): Int = read(c).count { it == '\n' }

    fun getPolicy(c: Context): String =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("policy", "off") ?: "off"

    fun setPolicy(c: Context, p: String) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("policy", p).apply()
    }

    @Synchronized
    fun autoClear(c: Context) {
        runCatching {
            val p = getPolicy(c)
            if (p != "daily" && p != "weekly") return
            val sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = sp.getLong("last_clear", 0)
            if (last == 0L) {
                sp.edit().putLong("last_clear", now).apply(); return
            }
            val interval = if (p == "daily") 86_400_000L else 604_800_000L
            if (now - last >= interval) {
                logFile(c).writeText("")
                sp.edit().putLong("last_clear", now).apply()
            }
        }
    }
}
