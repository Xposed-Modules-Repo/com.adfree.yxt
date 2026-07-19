# 乐校通去广告 · 完整技术文档

> 本文档详尽记录本项目的全部技术细节:目标分析、逆向工程、加固对抗、广告系统剖析、Hook 原理、
> 两代 Xposed API、Kotlin + Compose 重构、跨进程日志架构、全部依赖、构建工具链演进、CI、版本历史与维护排错。
> 代码/包名/类名用英文,其余中文。

---

## 目录

1. [项目概述](#1-项目概述)
2. [目标 App 分析](#2-目标-app-分析)
3. [加固(壳)分析与对抗](#3-加固壳分析与对抗)
4. [逆向工程全过程](#4-逆向工程全过程)
5. [广告系统剖析](#5-广告系统剖析)
6. [为什么"改包"行不通](#6-为什么改包行不通)
7. [LSPosed 模块方案与 Hook 原理](#7-lsposed-模块方案与-hook-原理)
8. [加固 App 的延迟挂钩技术](#8-加固-app-的延迟挂钩技术)
9. [跨进程日志架构(无需 root)](#9-跨进程日志架构无需-root)
10. [界面(UI)功能与实现](#10-界面ui功能与实现)
11. [两代 Xposed API:经典 82 → LibXposed 102](#11-两代-xposed-api经典-82--libxposed-102)
12. [Java → Kotlin、Views → Compose 重构](#12-java--kotlinviews--compose-重构)
13. [构建工具链演进史](#13-构建工具链演进史)
14. [全部依赖与版本](#14-全部依赖与版本)
15. [GitHub Actions 云端构建](#15-github-actions-云端构建)
16. [工程结构](#16-工程结构)
17. [版本历史(开发迭代)](#17-版本历史开发迭代)
18. [构建流程](#18-构建流程)
19. [安装与使用](#19-安装与使用)
20. [维护与排错](#20-维护与排错)
21. [名词表](#21-名词表)

---

## 1. 项目概述

**乐校通去广告** 是一个 **LSPosed / Xposed 模块**,用于移除安卓校园 App **乐校通**(包名 `client.android.yixiaotong`)内的**全部广告**(开屏 / 插屏 / 首页插屏 / 信息流 / 首页信息流 / banner)。

- **不改包、不脱壳分发**:通过运行时 Hook(挂钩)在广告请求发出前拦截,不改动原 App。
- **无需 root**:拦截日志经 ContentProvider 跨进程收集。
- **现代技术栈**:LibXposed 现代 Xposed API 102、Kotlin、Jetpack Compose Material3、莫奈(Material You)动态主题。
- **自动化**:LSPosed 装机即自动识别 / 启用 / 作用域;GitHub Actions 打 tag 自动编译发布。

设计目标:在**加固(ijiami 爱加密)**且**代码运行时才解密**的前提下,精准、稳定地关闭广告,且长期可维护。

---

## 2. 目标 App 分析

| 项 | 值 |
|---|---|
| 名称 | 乐校通(进程名 GBK 误显示为"涔愭牎閫") |
| 包名 | `client.android.yixiaotong` |
| 版本 | v4.3.9 / versionCode 1439 |
| minSdk / targetSdk | 26 / 33 |
| 体积 | `base.apk` ≈ 77 MB |
| 业务 | 校园电费 / 洗衣 / 饮水 / 钱包 / 充值 / 退款 / 支付 |

**APK 结构要点**(用 zip 解析 + apktool 解码得到):

- `classes.dex`(20.4 MB):**只含加固壳 loader**,应用真实代码不在其中。
- `assets/ijiami_1011.VData` + `assets/libjiagu*.so`:加密的真实 dex 与加固运行时。
- `lib/arm64-v8a/` 等:大量广告与 SDK 原生库(见下)。
- `AndroidManifest.xml`:`application` 指向 `com.stub.StubApp`(ijiami 壳的代理 Application)。

**内置广告 SDK 矩阵**(据 `lib/`、`assets/`、清单 `<meta-data>`/组件识别):

| 联盟 | 包名 / 证据 |
|---|---|
| 聚合平台 云帆 YFanAds | `com.yfanads.android`、`libyf_ads.so`、`AdxRewardVideoActivity` |
| 穿山甲 CSJ / Pangle | `com.bytedance.sdk.openadsdk`(混淆壳 `com.byazt.*`)、`libpangleflipped.so`、`TTFileProvider` |
| 优量汇 GDT | `com.qq.e`、`assets/gdt_plugin/gdtadv2.jar`、`GDTFileProvider` |
| 快手 KS | `com.kwad.sdk`、`assets/ksad_*`、`KsRewardVideoActivity` |
| 百度 Baidu | `com.baidu.mobads`、`assets/bdxadsdk.jar`、`MobRewardVideoActivity` |
| OPPO | `com.opos.mobad`、`libods.so`、`opos_module_*` |
| 小米 mimo | `com.miui.zeus.mimo.sdk`、`mimo_xxx_id=1773802961261.539.29` |
| 华为 HiAd | `ads-lite:13.4.81.300`、`assets/dark/.../hiad_*` |
| 其它 | UBIX(`assets/ubix/*`)、阿拉丁/JAD 互动广告(shake/slide/twist json) |

设备(用于逆向 + 实测):OnePlus/OPPO **PJZ110**,arm64-v8a,**Android 16(API 36)**,root = **KernelSU**,已装 **LSPosed**(Zygisk)。

---

## 3. 加固(壳)分析与对抗

**加固厂商**:ijiami(爱加密)+ libjiagu。

**壳的工作方式**:
1. 清单里的 `Application` 被替换为 `com.stub.StubApp`(壳的代理)。
2. App 启动时,`StubApp.attachBaseContext` 中 libjiagu 解密 `assets/ijiami_1011.VData`,将真实 dex 载入内存(通常经 `InMemoryDexClassLoader` 或把 dex 追加进 `PathClassLoader`)。
3. `classes.dex` 本身只是解密引导 stub。

**验证**(决定性):对 `classes.dex` 做字符串统计——
`com/stub/StubApp` 出现 1 次,而 `client/android/yixiaotong`、`com/bytedance`、`com/qq/e` … **全部 0 次**。
→ 证明真实代码**完全不在** `classes.dex`,而在运行时从 `VData` 解密。

**对抗结论**:
- **静态反编译改 dex 无效**:dex 里没有广告代码可改。
- **重打包会破坏壳的完整性/签名校验**。
- 只能:① 运行时**脱壳**(内存 dump 真实 dex,用于分析)② 运行时 **Hook**(在壳解密后挂钩真实类)。

---

## 4. 逆向工程全过程

### 4.1 工具链(主机 Windows)

| 工具 | 版本 | 用途 |
|---|---|---|
| JDK | 17.0.8 | 运行各 jar |
| frida / frida-dexdump | 17.16.0 / 2.0.1(conda py3.11) | 尝试脱壳 |
| apktool | 2.11.1 | 解码资源/清单 |
| jadx | 1.5.1 | dex → Java 反编译 |
| uber-apk-signer | 1.3.0 | 经典期给 APK 签名 |
| adb | platform-tools | 设备交互 |

**网络**:`github.com` 直连超时,改用 **ghfast.top** 镜像;pypi 用清华源;maven 用阿里云。

### 4.2 脱壳:frida 失败 → /proc/mem 直读

1. 推送 `frida-server`(17.16.0 arm64)到 `/data/local/tmp`,以 root 启动。
2. `frida-dexdump` **attach 失败**:`unexpectedly timed out while waiting for stop`
   —— ijiami 反调试(自 ptrace / 检测 frida 线程)阻止 attach。
3. **改用 root 直读内存碎 dex**(不受反 frida 影响):
   - `cat /proc/<pid>/maps` 拿到可读内存段;
   - 用 `adb exec-out su -c "toybox dd if=/proc/<pid>/mem ..."` 逐段读原始字节(**注意:PowerShell 的 `>` 会给二进制加 BOM 损坏数据,必须用 Python `subprocess` 抓原始字节**);
   - 扫描 dex 魔数 `64 65 78 0a`(`dex\n`)+ 版本 `0XX\0`,按 dex 头 `file_size`(偏移 `0x20`)精确切出;
   - 校验 `header_size==0x70`、`endian==0x12345678` 去伪。
   - 脚本:`work/carve_dex.py`。**脱出 18 个真实 dex**。

### 4.3 分析:定位广告收口层

- 用脚本按包名标记 18 个 dex,确认 **app 主代码在 `dump_09`(`yixiaotong`×5137)+ `dump_06`(×1889)**,聚合 SDK 在 `dump_14`(`yfanads`×1691)。
- jadx 反编译 app dex,定位广告控制层(见第 5 节)。

---

## 5. 广告系统剖析

### 5.1 广告收口层类

`client.android.yixiaotong.v3.ui.adv.*`:

| 类 | 职责 | 关键入口 |
|---|---|---|
| `AdvControlUtil` | **总控**;决定各类广告是否开启 | `isOpenAdv()`(私有)、`initAdvInfo()` |
| `SplashUtil` | 开屏 | `onSplash(FrameLayout)` |
| `InsertUtil` | 插屏 | `onInsert()` |
| `ShouYeInsertUtil` | 首页插屏 | `onInsert()` |
| `NativeUtil` | 信息流 | `onNative(RelativeLayout)` |
| `ShouYeNativeUtil` | 首页信息流 | `onNative(RelativeLayout)` |
| `BannerUtil` | banner(本身基本为空) | `onBanner(FrameLayout)` |
| `Common` | 常量:AppKey、广告位 ID、`AdvType` 枚举 | — |

另有 `v4.util.homeinfo.V4HomeInfoUtil.isOpenAdv()`(v4 平台开屏门)、`v4.util.adv.V4AdvControlUtil`(空壳)。

### 5.2 广告位与 AppKey(取自 `Common`)

- AppKey:`AppKey_FengChuan = 2121`(风船,即 YFanAds 主 key)、`AppKey_ChuangZhi = 41719`(创智)。
- 广告位(风船 2121xxx):
  - 开屏:`2121004`(冷启)/`2121005`(热启)
  - 插屏:首页 `2121001` / 充值 `2121011` / 洗浴 `2121003` / 饮水 `2121006` / 吹风 `2121007`
  - 信息流:首页 `2121002` / 洗浴 `2121008` / 饮水 `2121009` / 吹风 `2121010`
  - banner(创智):`8448916392638485`

### 5.3 开屏驱动流程(关键)

`WelcomeActivity`(启动页)→ `getAdv()` → `AdvControlUtil.initAdvInfo()` →
`isOpenAdv()`(按服务器配置设 4 个开关)→ `initAdvInfoNew()` → 回调
`mAdvListener.isOpen(插屏, banner, 跳转, 开屏, 信息流)`。

`WelcomeActivity` 的 `isOpen` 实现:
- 第 4 参(开屏)`== 1` → 显示开屏;
- 第 4 参 `== -1` → **直接 `load()` 进主页**;
- 第 1 参 `== 1` → 加载插屏;第 5 参 `== 1` → 加载信息流。

→ **只要把开屏开关设为 false**,`initAdvInfoNew` 走 else 分支发 `isOpen(0,0,0,-1,0)`,启动页收到 `-1` 便**跳过开屏、直进主页,不卡启动页**。这是本模块开屏拦截的理论基础。

---

## 6. 为什么"改包"行不通

用户最初倾向"脱壳 → 改包"。经验证**此路不通**:

1. 真实广告代码(`AdvControlUtil` 等)是运行时从加密 `assets/ijiami_1011.VData` 解出的;`classes.dex` 只是壳 loader。
2. 把脱出的 dex 改完再塞回 `classes.dex`:App **根本不加载 `classes.dex`**,而是解密 VData,改动无效。
3. 重打包 + 重签名会破坏加固的完整性/签名校验,App 起不来。
4. 该 App 还是**支付类**(钱包/电费),篡改风险高。

→ 放弃改包,转 **LSPosed 运行时 Hook**(壳解密后挂钩真实类,不改 APK,无完整性问题)。

---

## 7. LSPosed 模块方案与 Hook 原理

在**壳解密后的运行时**,对广告收口层挂钩,从源头阻止广告请求:

| # | Hook 点 | 动作 | 效果 |
|---|---|---|---|
| 1 | `AdvControlUtil.isOpenAdv` | 反射把 `mIsOpenSplashAdv/Insert/Banner/Native` 全置 `false`,跳过原方法 | 开屏走 `-1` 跳过;插屏/信息流不被触发 |
| 2 | `V4HomeInfoUtil.isOpenAdv` | 返回 `false` | v4 平台开屏跳过 |
| 3 | `SplashUtil.onSplash` | 立即回调 `mAdvListener.onAdClosed(Common.AdvType.advsplash)` | 兜底:即便被调用也立刻进主页,不卡启动页 |
| 4 | `InsertUtil.onInsert` / `ShouYeInsertUtil.onInsert` | no-op(不 proceed) | 插屏 / 首页插屏不加载 |
| 5 | `NativeUtil.onNative` / `ShouYeNativeUtil.onNative` | no-op | 信息流 / 首页信息流不加载 |
| 6 | `BannerUtil.onBanner` | no-op | banner 不加载 |

**为何在 App 层收口而非 SDK 层**:App 自身的广告逻辑在 `client.android.yixiaotong.v3.ui.adv`,类名清晰、未混淆;一处收口即可覆盖底层全部 7 家联盟,且不产生任何广告网络流量。

---

## 8. 加固 App 的延迟挂钩技术

**难点**:模块加载回调(经典 `handleLoadPackage` / LibXposed `onPackageLoaded`)触发时,ijiami **尚未解密**真实 dex,此刻用类加载器 `loadClass("...AdvControlUtil")` 会 **ClassNotFound**。

**解法(本项目核心技巧)**——延迟安装:

1. 在 `onPackageLoaded` 里,先只挂钩**框架类方法**(必然可加载):
   - `android.app.Instrumentation.callApplicationOnCreate(Application)`
   - `android.app.Activity.onCreate(Bundle)`
2. 这两个方法在**壳解密之后**才被调用。在其拦截器里,从 `Application` / `Activity`(都是 `Context`)取 **运行时真实类加载器** `context.classLoader`。
3. 用该类加载器**探测** `AdvControlUtil` 是否已可加载:可加载 → 安装第 7 节的全部广告 Hook;不可加载 → 返回,等下一次回调再试。
4. `@Volatile installed` + `@Synchronized` 保证**只安装一次**、线程安全。

实测:钩子在 `real classloader ready -> installing hooks` 后全部 `OK`,App 直进 `V3MainActivity` 主页,广告曝光数 = 0。

---

## 9. 跨进程日志架构(无需 root)

**问题**:Hook 代码运行在**乐校通进程**,而设置界面运行在**模块自己的进程**(`com.adfree.yxt`),两个 App 沙箱隔离,互不可读私有目录。

**被否决的方案**:让模块 UI 用 root(`su`)读乐校通目录 —— 实测 KernelSU 对 `untrusted_app` 域的 su 请求返回退出码 1(拒绝),脆弱且依赖 root。

**采用方案**:**ContentProvider 跨进程 IPC**(无需 root):
1. 模块声明一个导出的 `LogProvider`(`content://com.adfree.yxt.logs`,`exported=true`)。
2. Hook 侧(乐校通进程,清单含 `QUERY_ALL_PACKAGES` 权限,可见模块)通过
   `context.contentResolver.insert(LOG_URI, {type})` 写入拦截事件。
3. `LogProvider.insert` 运行在**模块进程**,把日志写进**模块自己的私有目录**(`filesDir/adblock_log.txt`)。
4. UI 直接读模块自己的文件。**全程无 root、无沙箱越权**。

**日志格式**(每条):
```
2026-07-19 12:41:50 | 开屏广告 | 广告位 2121004(冷启)/2121005(热启) | 聚合:云帆YFanAds(风船2121) | 联盟:穿山甲/优量汇/快手/百度/OPPO/小米/华为 | 已拦截
```
展示/导出时在顶部附**联盟 ↔ 域名对照表**(穿山甲 `pangolin-sdk-toutiao.com`、优量汇 `gdt.qq.com`、快手 `e.kuaishou.com`、百度 `mobads.baidu.com`、OPPO `adx.ads.oppomobile.com`、小米 `api.ad.xiaomi.com`、华为 `adxserver.ad.hicloud.com`)。
说明:拦截发生在请求发出**之前**,不产生真实广告流量,域名为该广告位**可能**调用的联盟参考。

**自动清理**:策略存 `SharedPreferences`(`off`/`daily`/`weekly`);`Store.autoClear` 在每次写日志及打开界面时按时间间隔清空(daily=24h,weekly=7d,记录 `last_clear` 时间戳)。

**导出**:读日志 + 图例写入 `getExternalFilesDir(null)/yxt_adfree_log_<时间戳>.txt`(App 专属外部目录,无需存储权限)。

---

## 10. 界面(UI)功能与实现

当前实现:**Jetpack Compose + Material3**(见第 12 节演进)。

| 功能 | 实现 |
|---|---|
| 莫奈动态主题 | `dynamicLightColorScheme` / `dynamicDarkColorScheme`(API 31+)跟随系统壁纸取色;低版本回退 `lightColorScheme/darkColorScheme` |
| 顶栏 | Material3 `TopAppBar`,`primaryContainer` 着色,`enableEdgeToEdge()` 沉浸式,`Scaffold` 自动处理状态栏 inset |
| 状态显示 | "模块运行中 · 已记录 N 条拦截" |
| 隐藏桌面图标 | `Switch` + `setComponentEnabledSetting` 切换 `activity-alias`(`LauncherAlias`);隐藏后仍可从 LSPosed 打开 |
| 自动清理 | `SingleChoiceSegmentedButtonRow`:关闭 / 每天 / 每周 |
| 操作 | 清空 / 导出txt / 刷新 按钮 |
| 拦截记录 | `Card` 圆角卡片 + 等宽字体可滚动文本 |
| 署名 | `© 2026 github@zzdwymk · Powered by zzdwymk` |

**从 LSPosed 打开**:`SettingsActivity` 带 `de.robv.android.xposed.category.MODULE_SETTINGS` intent 分类;桌面图标由 `activity-alias LauncherAlias`(`LAUNCHER` 分类)提供,可隐藏而不影响从 LSPosed 打开。

---

## 11. 两代 Xposed API:经典 82 → LibXposed 102

| 维度 | 经典 Xposed API 82(`de.robv.android.xposed`) | LibXposed 现代 API 102(`io.github.libxposed`) |
|---|---|---|
| 入口 | `implements IXposedHookLoadPackage` + `handleLoadPackage` | `class Main : XposedModule()` + `onPackageLoaded` |
| 挂钩 | `XposedHelpers.findAndHookMethod(类名, cl, 方法, 参数..., XC_MethodReplacement)` | `hook(Method).intercept(Hooker { chain -> ... })` |
| 拦截 | `XC_MethodReplacement.replaceHookedMethod` 返回值替换 | `Hooker.intercept`:不调 `chain.proceed()` 即拦截,返回值即结果 |
| 取字段 | `XposedHelpers.getObjectField/setBooleanField` | 普通 Java 反射(102 起**禁用** `de.robv` 旧 API) |
| 日志 | `XposedBridge.log` | `log(priority, tag, msg)` |
| 注册 | `assets/xposed_init`(入口类名)+ 清单 `<meta-data>`(`xposedmodule`/`xposedminversion`/`xposedscope`) | `META-INF/xposed/`:`module.prop` + `java_init.list` + `scope.list`,清单**无** xposed 元信息 |
| 依赖 | 本地 `xposed-api-82.jar`(compileOnly) | Maven `io.github.libxposed:api:102.0.0`(compileOnly) |

**LibXposed `module.prop`**(本项目):
```
id=com.adfree.yxt
name=乐校通去广告
version=4.0 / versionCode=4 / author=zzdwymk
minApiVersion=101 / targetApiVersion=102 / staticScope=true
```
`java_init.list` = `com.adfree.yxt.Main`;`scope.list` = `client.android.yixiaotong`。
LSPosed 据此**自动识别、启用、作用域**(经典期靠清单 `xposedscope` 自动作用域,现由 `scope.list` + `staticScope`)。

**迁移收益**:类型安全的现代 API、无旧 API 弃用、装机自动启用免手动。

---

## 12. Java → Kotlin、Views → Compose 重构

**语言**:Main / Store / LogProvider / SettingsActivity 四个类**全部 Java → Kotlin**。
- Kotlin `Hooker { chain -> ... }` SAM 转换调用 LibXposed;`runCatching {}` 替代 try/catch;`object Store`、`companion object` 等惯用法。

**UI**:**程序化 View → Jetpack Compose**。
- 经典期:纯代码 `LinearLayout/TextView/Switch/RadioGroup/ScrollView`,状态栏用反射调 `setStatusBarColor`,`GradientDrawable` 圆角,`system_accent1_*` 取莫奈色(`getResources().getIdentifier`)。
- 现在:`ComponentActivity + setContent`,Material3 组件,`dynamicColorScheme` 原生莫奈,`enableEdgeToEdge` + `Scaffold` 处理沉浸式与 inset(彻底解决 targetSdk 35+ 强制 edge-to-edge 下状态栏重叠问题)。

**Hook 逻辑不变**:延迟安装、四类开关强制 false、各入口 no-op、开屏兜底回调 —— 逻辑与已验证的版本一致,仅语言 + 打包升级。

---

## 13. 构建工具链演进史

### 阶段一:手搓命令行(经典 82 期,无 Gradle)
- `javac -encoding UTF-8`(**必须指定编码**,否则 Windows 默认 GBK 读不了 UTF-8 中文);
- 编译期用 **API 16 的 `android.jar`**(缺新 API,用反射 / 字面量常量绕过);
- `r8` 的 **D8** 把 class → `classes.dex`;
- **apktool** 构建"壳 APK"(生成二进制清单 + 资源),再用 **.NET `ZipArchive`** 把真 `classes.dex` 换进去;
- **uber-apk-signer** 签名;
- 纯代码 UI,**无 res 资源**。

### 阶段二:64 位尝试(失败)
- 为让模块跑 64 位,往 APK 塞 `lib/arm64-v8a/libbitmaps.so` → 安装报
  `INSTALL_FAILED_CONTAINER_ERROR (res=-18)`:.NET 改 zip 破坏了原生库对齐,安装器解压失败 → **放弃 64 位**(模块跑 32/64 位对功能无影响)。

### 阶段三:标准 Gradle 工程
- 迁移为 Android Studio 可直接打开的 Gradle 工程;
- AGP `8.9.1 → 9.3.0`,Gradle `8.11.1 → 9.6.1`,compileSdk 36,build-tools 36.0.0,JDK 17。

### 阶段四:AGP 9 内置 Kotlin(关键坑)

- **AGP 9.0+ 内置 Kotlin 支持**(runtime 依赖 **KGP 2.2.10**),**不能**再加 `org.jetbrains.kotlin.android` 插件(会报"not compatible with the new DSL");
- 因此 **Compose 编译器插件版本必须 = AGP 内置的 KGP 版本 = 2.2.10**;
- 想用更新的 Kotlin 2.4.10 需关掉内置 Kotlin,但 AGP 9 新 DSL 下问题多,故采用官方推荐的**内置 Kotlin 2.2.10 + Compose 插件 2.2.10**;
- Compose BOM 用最新 `2026.06.01`,与 2.2.10 编译器兼容。

### 关于 APK 体积
- 经典期:≈ 13–17 KB(纯代码,无依赖)。
- LibXposed(Java)release:≈ 638 KB(AGP 9 默认带 `kotlin-stdlib`,无害;api 是 compileOnly **未打包**)。
- Kotlin + Compose:≈ 11 MB(Compose 运行时,正常;仅模块自己界面进程用到,不影响注入乐校通)。

---

## 14. 全部依赖与版本

### 构建工具
| 组件 | 版本 | 说明 |
|---|---|---|
| AGP(Android Gradle Plugin) | 9.3.0 | 最新稳定;内置 Kotlin |
| Gradle | 9.6.1 | 最新稳定 |
| Kotlin(KGP) | 2.2.10 | 由 AGP 9.3.0 内置 |
| Compose 编译器插件 `org.jetbrains.kotlin.plugin.compose` | 2.2.10 | 必须与 KGP 一致 |
| JDK | 17 | 运行 Gradle |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 | — |
| build-tools | 36.0.0 | — |

### 运行/编译依赖
| 依赖 | 版本 | scope | 说明 |
|---|---|---|---|
| `io.github.libxposed:api` | 102.0.0 | **compileOnly** | LibXposed 现代 API,运行时由 LSPosed 提供,不打包 |
| `androidx.compose:compose-bom` | 2026.06.01 | platform | 统一管理 Compose 各库版本 |
| `androidx.compose.material3:material3` | (随 BOM) | implementation | Material3 组件 + 动态取色 |
| `androidx.activity:activity-compose` | 1.13.0 | implementation | `ComponentActivity` + `setContent` + `enableEdgeToEdge` |

### 仓库镜像(`settings.gradle.kts`)
- 阿里云:`repository/google`、`repository/public`、`repository/gradle-plugin`;
- Gradle 发行版:腾讯镜像 `mirrors.cloud.tencent.com/gradle`;
- 官方 `google()` / `mavenCentral()` / `gradlePluginPortal()` 兜底。

---

## 15. GitHub Actions 云端构建

`.github/workflows/build.yml`:
- **触发**:推送 `v*` tag 自动编译并发 Release;也可 `workflow_dispatch` 手动触发。
- **步骤**:checkout → `setup-java` JDK 17 → `setup-android` → `sdkmanager "platforms;android-36" "build-tools;36.0.0"` → `./gradlew assembleRelease` → `find` 定位 APK → 上传 artifact →(打 tag 时)`softprops/action-gh-release` 发布。
- **权限**:`contents: write`(创建 Release)。
- **签名**:release 用自动生成的 debug 证书签名(`signingConfigs.getByName("debug")`),产出可直接安装的 APK,无需配置密钥 secret。

---

## 16. 工程结构

```
YXTAdFree/
├── build.gradle.kts                 # 根:声明 AGP 9.3.0 + Compose 插件 2.2.10
├── settings.gradle.kts              # 仓库(含阿里云镜像)
├── gradle.properties
├── gradle/wrapper/                  # gradlew 包装器(Gradle 9.6.1)
├── local.properties                 # SDK 路径(gitignore,不提交)
├── .github/workflows/build.yml      # CI
├── README.md / CHANGELOG.md / DOCS.md
└── app/
    ├── build.gradle.kts             # 模块:Compose/依赖/打包
    └── src/main/
        ├── AndroidManifest.xml      # provider + activity + activity-alias(无 xposed 元信息)
        ├── java/com/adfree/yxt/
        │   ├── Main.kt              # LibXposed 入口 + Hook(延迟安装)
        │   ├── Store.kt             # 日志存储 + 自动清理 + 图例
        │   ├── LogProvider.kt       # 跨进程日志 ContentProvider
        │   └── SettingsActivity.kt  # Compose 设置界面
        └── resources/META-INF/xposed/
            ├── module.prop          # 模块元信息(LibXposed)
            ├── java_init.list       # 入口类名
            └── scope.list           # 默认作用域
```

逆向产物(不在仓库,主机 `D:\study\tmp\work`):`carve_dex.py`、脱出的 18 个 dex、jadx 反编译源码、apktool 解码结果。

---

## 17. 版本历史(开发迭代)

> 本项目在开发中经历多轮迭代,以下为按功能推进的里程碑(最终对外为 v4.0)。

- **M1 去广告内核**:经典 API 82,延迟安装 + 四类广告拦截,实测直进主页、曝光 0。
- **M2 日志 + 界面**:程序化 View UI;拦截日志(初版"已拦截");自动清理(关/每天/每周);手动清空;导出 txt。
- **M3 交互增强**:隐藏桌面图标(activity-alias);从 LSPosed 打开设置(MODULE_SETTINGS)。
- **M4 视觉 + 日志详化**:莫奈动态色;状态栏与顶栏同色、图标明暗自适应;日志框圆角;日志详化(类型 / 广告位 ID / 聚合平台 / 联盟 / 域名对照);署名。
- **M5 无 root 日志**:放弃 root 读,改 **ContentProvider** 跨进程收集。
- **M6 工程化**:手搓命令行 → 标准 Gradle 工程;AGP 8.9.1 / Gradle 8.11.1;GitHub Actions CI。
- **M7 依赖升级**:AGP 9.3.0 / Gradle 9.6.1(本地实编验证)。
- **M8 API 迁移**:经典 82 → **LibXposed 现代 API 102**;打包改 `META-INF/xposed/`;设备实测通过。
- **M9 Kotlin + Compose 重构**:四类全 Kotlin;UI 全 Jetpack Compose Material3;依赖全最新;AGP 9 内置 Kotlin 2.2.10 + Compose 插件 2.2.10;编译通过。

对外版本:见 [CHANGELOG.md](CHANGELOG.md)。

---

## 18. 构建流程

```bash
# 本地(需 JDK 17 + Android SDK: compileSdk 36 / build-tools 36.0.0)
./gradlew assembleRelease
# 产物:app/build/outputs/apk/release/app-release.apk
```
- 首次构建会下载 AGP / Kotlin(KGP 2.2.10)/ Compose 全家桶(经阿里云镜像)。
- 云端:推送 `v4.0` 之类 tag → Actions 自动编译 + 发 Release。

---

## 19. 安装与使用

1. 设备需已装 **LSPosed**(Zygisk 版,配 Magisk / KernelSU)。
2. 安装 `yxt-adfree.apk`。LibXposed 模块由 LSPosed **自动识别、启用、作用域到乐校通**(`staticScope` + `scope.list`)。
3. **强制停止乐校通**后重新打开 → 广告消失,直进主页。
4. 打开模块界面(桌面图标或 LSPosed 管理器 → 本模块 → 打开)查看拦截日志、配置自动清理、导出、隐藏图标。

---

## 20. 维护与排错

| 现象 | 原因 / 处理 |
|---|---|
| 更新乐校通后广告复现 | 广告类被改名 / 混淆 / 换 SDK。需:重跑 `carve_dex.py` 脱壳 → jadx 看新类名 → 改 `Main.kt` 类名重编 |
| 钩子未生效 | 确认 LSPosed 已启用本模块且作用域含乐校通;改模块后需 **force-stop 乐校通**重载钩子 |
| 日志为 0 | 确认乐校通触发过广告场景(冷启开屏);ContentProvider 写入需乐校通有 `QUERY_ALL_PACKAGES`(已具备) |
| 想更耐操 | 追加 YFanAds SDK 边界 Hook(如 `YFAdSplashAds.loadOnly`),即使 App 端新增调用点也拦得住 |
| Compose 编译器版本报错 | 必须等于 AGP 内置 KGP 版本(当前 2.2.10);升级 AGP 后同步改 `plugin.compose` 版本 |
| 本地 `./gradlew` 下 Gradle 太慢 | 把 `gradle-wrapper.properties` 的 `distributionUrl` 换腾讯镜像 |

---

## 21. 名词表

- **加固 / 壳(ijiami)**:对 APK 加密保护,运行时才解密真实代码。
- **脱壳**:运行时从内存 dump 出解密后的真实 dex。
- **Hook / 挂钩**:运行时拦截并改写方法行为。
- **LSPosed**:基于 Zygisk 的 Xposed 框架实现,把模块注入目标 App 进程。
- **LibXposed**:现代 Xposed API(102),类型安全,替代经典 `de.robv` API。
- **莫奈 / Material You**:Android 12+ 依壁纸生成的动态配色。
- **ContentProvider**:Android 跨进程数据组件,本项目用于无 root 收集日志。
- **YFanAds(云帆/风船)**:本 App 使用的广告聚合 SDK,底层挂多家联盟。

---

© 2026 github@zzdwymk · Powered by zzdwymk
