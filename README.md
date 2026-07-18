# 乐校通去广告(YXTAdFree)

一个 **LSPosed / Xposed 模块**,移除乐校通(`client.android.yixiaotong`)App 内的全部广告。

## 功能

- **去广告**:开屏 / 插屏 / 首页插屏 / 信息流 / banner 全部拦截,启动直接进主页,不卡启动页
- **拦截日志**:记录每条被拦截广告的 类型 / 广告位ID / 聚合平台 / 广告联盟 / 域名对照
- **日志管理**:自动清理(关闭 / 每天 / 每周)、手动清空、导出为 txt
- **界面**:莫奈(Material You)动态主题色、圆角卡片
- **隐藏桌面图标**:隐藏后仍可从 LSPosed 管理器打开设置界面
- 全程 **无需 root**(日志经 ContentProvider 跨进程收集)

## 安装

1. 需已安装 **LSPosed**(Zygisk 版,配 Magisk / KernelSU)
2. 从 [Releases](../../releases) 下载 `yxt-adfree.apk` 并安装
3. 在 LSPosed 管理器中**启用本模块**,作用域勾选**乐校通**(带 `xposedscope`,通常自动勾选)
4. 强制停止乐校通后重新打开即可生效

## 本地构建

```bash
./gradlew assembleRelease
# 产物:app/build/outputs/apk/release/app-release.apk
```

需要 JDK 17 + Android SDK(compileSdk 36 / build-tools 36.0.0)。国内网络已在 `settings.gradle.kts` 配置阿里云镜像。

## 原理

乐校通用 ijiami 加固,真实广告代码在 `client.android.yixiaotong.v3.ui.adv.*`(聚合 SDK 为云帆 YFanAds)。模块在运行时(真实类加载器就绪后)Hook 广告总控 `AdvControlUtil.isOpenAdv` 强制关闭各类广告开关,并对各广告入口方法 no-op,从源头阻止广告请求。

## 免责声明

仅供学习与个人使用。请勿用于商业或非法用途。

---

© 2026 Powered by zzdwymk
