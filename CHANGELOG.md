# 更新日志

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/) 与 [Keep a Changelog](https://keepachangelog.com/zh-CN/) 规范。

## [v4.0] - 2026-07-19

首个公开版本 —— 乐校通去广告 LSPosed 模块,基于现代 **LibXposed API 102**。

### 去广告
- 拦截**开屏**广告,启动直接进主页,不卡启动页
- 拦截**插屏 / 首页插屏**广告
- 拦截**信息流 / 首页信息流**广告
- 拦截 **banner** 广告
- 在广告请求发出前拦截,不产生实际广告流量;覆盖 穿山甲 / 优量汇 / 快手 / 百度 / OPPO / 小米 / 华为(云帆 YFanAds 聚合)

### 拦截日志
- 记录每条被拦截广告:时间 / 类型 / 广告位 ID / 聚合平台 / 广告联盟 / 域名对照
- 自动清理:关闭 / 每天 / 每周
- 手动清空日志
- 导出日志为 txt
- 跨进程收集,**无需 root**(ContentProvider)

### 界面
- 莫奈(Material You)动态主题色,跟随系统壁纸取色
- 顶栏色覆盖状态栏,图标按明暗自动适配、保证可读
- 拦截记录圆角卡片
- 隐藏桌面图标(隐藏后仍可从 LSPosed 管理器打开设置)
- 全中文界面

### 技术
- 迁移至 LibXposed 现代 Xposed API **102**
- 针对 ijiami 加固:延迟到运行时真实类加载器就绪后再挂钩
- LSPosed 装机即**自动识别、启用、作用域**(`module.prop` + `scope.list`)
- 构建工具链:AGP **9.3.0** / Gradle **9.6.1** / compileSdk 36 / JDK 17
- GitHub Actions 云端自动编译 + 发布 Release

### 说明
- 需已安装 **LSPosed**(Zygisk 版,配 Magisk / KernelSU)
- 仅供学习与个人使用,请勿用于商业或非法用途

---

© 2026 github@zzdwymk · Powered by zzdwymk
