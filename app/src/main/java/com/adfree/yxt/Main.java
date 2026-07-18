package com.adfree.yxt;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage {
    private static final String PKG = "client.android.yixiaotong";
    private static final String ADV = "client.android.yixiaotong.v3.ui.adv.";
    private static final String TAG = "[AdFree-YXT] ";
    private static final Uri LOG_URI = Uri.parse("content://com.adfree.yxt.logs");

    private static Context appCtx;
    private boolean installed = false;

    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + "attached; deferring hooks until real dex decrypted");

        try {
            XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader,
                "callApplicationOnCreate", "android.app.Application",
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) { tryInstallFrom(param.args[0]); }
                });
        } catch (Throwable e) { XposedBridge.log(TAG + "instr hook fail " + e); }

        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader,
                "onCreate", "android.os.Bundle",
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) { tryInstallFrom(param.thisObject); }
                });
        } catch (Throwable e) { XposedBridge.log(TAG + "activity hook fail " + e); }
    }

    private void tryInstallFrom(Object ctx) {
        if (installed || ctx == null) return;
        try {
            if (ctx instanceof Context) appCtx = ((Context) ctx).getApplicationContext();
            ClassLoader cl = (ClassLoader) XposedHelpers.callMethod(ctx, "getClassLoader");
            install(cl);
        } catch (Throwable e) { XposedBridge.log(TAG + "getClassLoader fail " + e); }
    }

    private synchronized void install(ClassLoader cl) {
        if (installed || cl == null) return;
        try { cl.loadClass(ADV + "AdvControlUtil"); } catch (Throwable e) { return; }
        installed = true;
        XposedBridge.log(TAG + "real classloader ready -> installing hooks");

        // 1) 总控:强制四类广告开关 false(开屏走 -1 跳过主页)
        try {
            XposedHelpers.findAndHookMethod(ADV + "AdvControlUtil", cl, "isOpenAdv",
                new XC_MethodReplacement() {
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        Object t = param.thisObject;
                        XposedHelpers.setBooleanField(t, "mIsOpenSplashAdv", false);
                        XposedHelpers.setBooleanField(t, "mIsOpenInsertAdv", false);
                        XposedHelpers.setBooleanField(t, "mIsOpenBannerAdv", false);
                        XposedHelpers.setBooleanField(t, "mIsOpenNativeAdv", false);
                        logEvent("开屏");
                        return null;
                    }
                });
            XposedBridge.log(TAG + "OK AdvControlUtil.isOpenAdv");
        } catch (Throwable e) { XposedBridge.log(TAG + "FAIL isOpenAdv " + e); }

        // 2) v4 开屏门 -> false
        try {
            XposedHelpers.findAndHookMethod(
                "client.android.yixiaotong.v4.util.homeinfo.V4HomeInfoUtil", cl, "isOpenAdv",
                XC_MethodReplacement.returnConstant(Boolean.FALSE));
        } catch (Throwable e) { XposedBridge.log(TAG + "FAIL V4HomeInfoUtil.isOpenAdv " + e); }

        // 3) 开屏兜底:onSplash 若被调用,立即回调 onAdClosed 进主页
        try {
            XposedHelpers.findAndHookMethod(ADV + "SplashUtil", cl, "onSplash", "android.widget.FrameLayout",
                new XC_MethodReplacement() {
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        try {
                            Object self = param.thisObject;
                            Object listener = XposedHelpers.getObjectField(self, "mAdvListener");
                            if (listener != null) {
                                Class<?> t = XposedHelpers.findClass(ADV + "Common$AdvType",
                                        self.getClass().getClassLoader());
                                Object advsplash = XposedHelpers.getStaticObjectField(t, "advsplash");
                                XposedHelpers.callMethod(listener, "onAdClosed", advsplash);
                            }
                        } catch (Throwable e) { XposedBridge.log(TAG + "splash skip fail " + e); }
                        return null;
                    }
                });
        } catch (Throwable e) { XposedBridge.log(TAG + "FAIL SplashUtil.onSplash " + e); }

        // 4) 插屏 / 信息流 / banner 入口 no-op + 记录
        nop(cl, ADV + "InsertUtil", "onInsert", "插屏");
        nop(cl, ADV + "ShouYeInsertUtil", "onInsert", "首页插屏");
        nop(cl, ADV + "NativeUtil", "onNative", "信息流", "android.widget.RelativeLayout");
        nop(cl, ADV + "ShouYeNativeUtil", "onNative", "首页信息流", "android.widget.RelativeLayout");
        nop(cl, ADV + "BannerUtil", "onBanner", "banner", "android.widget.FrameLayout");
    }

    private static void nop(ClassLoader cl, String cls, String method, final String label, String... paramTypes) {
        try {
            Object[] a = new Object[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, a, 0, paramTypes.length);
            a[paramTypes.length] = new XC_MethodReplacement() {
                protected Object replaceHookedMethod(MethodHookParam param) { logEvent(label); return null; }
            };
            XposedHelpers.findAndHookMethod(cls, cl, method, a);
            XposedBridge.log(TAG + "OK nop " + cls + "." + method);
        } catch (Throwable e) { XposedBridge.log(TAG + "FAIL nop " + cls + "." + method + " " + e); }
    }

    // 通过模块的 ContentProvider 写日志(无需 root;易校通有 QUERY_ALL_PACKAGES 可见模块)
    static void logEvent(String type) {
        try {
            Context c = appCtx;
            if (c == null) return;
            ContentValues v = new ContentValues();
            v.put("type", type);
            c.getContentResolver().insert(LOG_URI, v);
        } catch (Throwable ignore) {}
    }
}
