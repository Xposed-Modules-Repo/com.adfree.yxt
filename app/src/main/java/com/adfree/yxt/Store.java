package com.adfree.yxt;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 日志存储:全部落在模块自己的私有目录,钩子经 ContentProvider 写入,UI 直接读。无需 root。 */
public class Store {
    static final String LOG = "adblock_log.txt";
    static final String PREFS = "cfg";
    static final long MAX = 1024 * 1024; // 1MB 上限

    static File logFile(Context c) { return new File(c.getFilesDir(), LOG); }

    static synchronized void append(Context c, String type) {
        try {
            autoClear(c);
            File f = logFile(c);
            if (f.length() > MAX) { new FileWriter(f, false).close(); }
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String line = time + " | " + type + "广告 | 广告位 " + slots(type)
                    + " | 聚合:云帆YFanAds(风船2121) | 联盟:穿山甲/优量汇/快手/百度/OPPO/小米/华为 | 已拦截\n";
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.close();
        } catch (Throwable ignore) {}
    }

    // 广告类型 -> 对应广告位 ID(取自易校通 Common 类)
    static String slots(String type) {
        if (type.contains("开屏")) return "2121004(冷启)/2121005(热启)";
        if (type.contains("首页插屏")) return "2121001";
        if (type.contains("插屏")) return "2121001/2121003/2121006/2121007/2121011";
        if (type.contains("首页信息流")) return "2121002";
        if (type.contains("信息流")) return "2121002/2121008/2121009/2121010";
        if (type.contains("banner")) return "8448916392638485";
        return "-";
    }

    // 广告联盟与域名对照(展示/导出时置于日志顶部)
    static String legend() {
        return "【广告联盟 · 域名对照】\n"
                + "穿山甲 CSJ   pangolin-sdk-toutiao.com / dig.bdurl.net\n"
                + "优量汇 GDT   gdt.qq.com / qzs.qq.com\n"
                + "快手 KS      e.kuaishou.com / ad.partner.gifshow.com\n"
                + "百度 Baidu   mobads.baidu.com / mobads-logs.baidu.com\n"
                + "OPPO        adx.ads.oppomobile.com / ck.opmobile.heytapmobi.com\n"
                + "小米 Mimo    api.ad.xiaomi.com\n"
                + "华为 HiAd    adxserver.ad.hicloud.com\n"
                + "聚合平台     云帆 YFanAds(风船 appKey=2121 / 创智 41719)\n"
                + "说明:拦截发生在请求发出之前,故不产生实际广告流量;上列为该广告位可能调用的联盟及域名。\n"
                + "────────────────────────────\n";
    }

    static synchronized String read(Context c) {
        try {
            File f = logFile(c);
            if (!f.exists()) return "";
            RandomAccessFile r = new RandomAccessFile(f, "r");
            byte[] b = new byte[(int) r.length()];
            r.readFully(b);
            r.close();
            return new String(b);
        } catch (Throwable e) { return ""; }
    }

    static synchronized void clear(Context c) {
        try { new FileWriter(logFile(c), false).close(); } catch (Throwable ignore) {}
    }

    static int count(Context c) {
        String s = read(c);
        if (s.length() == 0) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    static String getPolicy(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("policy", "off");
    }

    static void setPolicy(Context c, String p) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("policy", p).apply();
    }

    static synchronized void autoClear(Context c) {
        try {
            String p = getPolicy(c);
            if (!p.equals("daily") && !p.equals("weekly")) return;
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            long last = sp.getLong("last_clear", 0);
            if (last == 0) { sp.edit().putLong("last_clear", now).apply(); return; }
            long interval = p.equals("daily") ? 86400000L : 604800000L;
            if (now - last >= interval) {
                new FileWriter(logFile(c), false).close();
                sp.edit().putLong("last_clear", now).apply();
            }
        } catch (Throwable ignore) {}
    }
}
