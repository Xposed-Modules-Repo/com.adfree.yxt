package com.adfree.yxt

import android.app.Application
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.RelativeLayout
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * 乐校通去广告 —— 基于 LibXposed(现代 Xposed API 102),Kotlin 实现。
 * 加固:onPackageLoaded 时真实类未解密,延迟到 Instrumentation.callApplicationOnCreate /
 * Activity.onCreate 拿到运行时真实类加载器后再挂钩。
 */
class Main : XposedModule() {

    @Volatile
    private var installed = false

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != PKG) return
        log(Log.INFO, TAG, "attached; deferring hooks until real dex decrypted")

        runCatching {
            val mApp = Instrumentation::class.java
                .getDeclaredMethod("callApplicationOnCreate", Application::class.java)
            hook(mApp).intercept(Hooker { chain ->
                val r = chain.proceed()
                tryInstallFrom(chain.getArg(0))
                r
            })
        }.onFailure { log(Log.ERROR, TAG, "instr hook fail", it) }

        runCatching {
            val mAct = android.app.Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java)
            hook(mAct).intercept(Hooker { chain ->
                tryInstallFrom(chain.thisObject)
                chain.proceed()
            })
        }.onFailure { log(Log.ERROR, TAG, "activity hook fail", it) }
    }

    private fun tryInstallFrom(ctx: Any?) {
        if (installed || ctx !is Context) return
        appCtx = ctx.applicationContext
        install(ctx.classLoader)
    }

    @Synchronized
    private fun install(cl: ClassLoader?) {
        if (installed || cl == null) return
        val advCtl = runCatching { cl.loadClass(ADV + "AdvControlUtil") }.getOrNull() ?: return
        installed = true
        log(Log.INFO, TAG, "real classloader ready -> installing hooks")

        // 1) 总控:强制四类广告开关 false(开屏走 -1 跳过主页)
        runCatching {
            hook(advCtl.getDeclaredMethod("isOpenAdv")).intercept(Hooker { chain ->
                val t = chain.thisObject
                setBool(t, "mIsOpenSplashAdv", false)
                setBool(t, "mIsOpenInsertAdv", false)
                setBool(t, "mIsOpenBannerAdv", false)
                setBool(t, "mIsOpenNativeAdv", false)
                logEvent("开屏")
                null
            })
            log(Log.INFO, TAG, "OK AdvControlUtil.isOpenAdv")
        }.onFailure { log(Log.ERROR, TAG, "FAIL isOpenAdv", it) }

        // 2) v4 开屏门 -> false
        runCatching {
            val m = cl.loadClass("client.android.yixiaotong.v4.util.homeinfo.V4HomeInfoUtil")
                .getDeclaredMethod("isOpenAdv")
            hook(m).intercept(Hooker { false })
        }.onFailure { log(Log.ERROR, TAG, "FAIL V4HomeInfoUtil.isOpenAdv", it) }

        // 3) 开屏兜底:onSplash 被调用则立即回调 onAdClosed 进主页
        runCatching {
            val m = cl.loadClass(ADV + "SplashUtil").getDeclaredMethod("onSplash", FrameLayout::class.java)
            hook(m).intercept(Hooker { chain ->
                runCatching {
                    val self = chain.thisObject
                    val listener = self.javaClass.getDeclaredField("mAdvListener")
                        .apply { isAccessible = true }.get(self)
                    if (listener != null) {
                        val at = cl.loadClass(ADV + "Common\$AdvType")
                        val advsplash = at.getField("advsplash").get(null)
                        listener.javaClass.getMethod("onAdClosed", at)
                            .apply { isAccessible = true }.invoke(listener, advsplash)
                    }
                }.onFailure { log(Log.ERROR, TAG, "splash skip fail", it) }
                null
            })
        }.onFailure { log(Log.ERROR, TAG, "FAIL SplashUtil.onSplash", it) }

        // 4) 插屏 / 信息流 / banner 入口 no-op + 记录
        nop(cl, ADV + "InsertUtil", "onInsert", "插屏")
        nop(cl, ADV + "ShouYeInsertUtil", "onInsert", "首页插屏")
        nop(cl, ADV + "NativeUtil", "onNative", "信息流", RelativeLayout::class.java)
        nop(cl, ADV + "ShouYeNativeUtil", "onNative", "首页信息流", RelativeLayout::class.java)
        nop(cl, ADV + "BannerUtil", "onBanner", "banner", FrameLayout::class.java)
    }

    private fun nop(cl: ClassLoader, cls: String, method: String, label: String, vararg params: Class<*>) {
        runCatching {
            val m = cl.loadClass(cls).getDeclaredMethod(method, *params)
            hook(m).intercept(Hooker { logEvent(label); null })
            log(Log.INFO, TAG, "OK nop $cls.$method")
        }.onFailure { log(Log.ERROR, TAG, "FAIL nop $cls.$method", it) }
    }

    companion object {
        private const val PKG = "client.android.yixiaotong"
        private const val ADV = "client.android.yixiaotong.v3.ui.adv."
        private const val TAG = "AdFree-YXT"
        private val LOG_URI: Uri = Uri.parse("content://com.adfree.yxt.logs")

        @Volatile
        private var appCtx: Context? = null

        private fun setBool(o: Any, field: String, v: Boolean) {
            runCatching {
                o.javaClass.getDeclaredField(field).apply { isAccessible = true }.setBoolean(o, v)
            }
        }

        /** 通过模块的 ContentProvider 写日志(无需 root) */
        fun logEvent(type: String) {
            runCatching {
                val c = appCtx ?: return
                c.contentResolver.insert(LOG_URI, ContentValues().apply { put("type", type) })
            }
        }
    }
}
