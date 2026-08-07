# DayStudy 学习打卡

> 一款轻量级番茄钟与学习打卡工具，支持 PWA 网页版和 Android APK 安装包，原生前台通知，学习统计一目了然。

---

## 功能亮点

### 番茄钟
- 专注 / 短休 / 长休 标准番茄循环
- 支持暂停、继续、停止，操作灵活
- 可自定义各阶段时长

### 计时器模式
- 倒计时 — 设定时长，倒计时归零提醒
- 正计时 — 从零开始累积，适合自由学习
- 原生前台服务：锁屏走秒、通知栏实时显示

### 待办清单
- 每日任务管理
- 每个任务可预估番茄数
- 完成自动勾选，进度一目了然

### 学习统计
- 日 / 周 / 月 / 总览 多维度图表
- 打卡日历 — 像 GitHub 绿格子一样直观
- 番茄完成数、总时长、趋势曲线

### 原生通知（Android）
- 前台服务 — 倒计时在通知栏实时走秒
- 精确闹钟 — 到点响铃，锁屏也响
- 通知栏按钮 — 暂停 / 继续 / 停止，无需打开 App
- 不同阶段（专注 / 休息）不同闹钟标识

### 跨平台
- PWA 网页版 — 浏览器打开即用，可添加到桌面
- Android APK — 原生壳打包，通知体验更好

---

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | 纯 HTML + CSS + JavaScript（零框架，PWA） |
| 图标 | SVG 矢量图标，自适应 |
| 原生壳 | Capacitor 6（Android） |
| 原生通知 | Java 前台服务（Foreground Service）+ AlarmManager 精确闹钟 |
| 构建 | GitHub Actions 自动构建 APK |
| 存储 | 浏览器 localStorage（离线可用） |

---

## 快速开始（本地构建）

### 前置条件

- Node.js 20+
- Java 21+
- Android SDK（Android Studio 推荐）

### 步骤

```bash
# 1. 克隆
git clone https://github.com/flyforfree8699/DayStudy.git
cd DayStudy

# 2. 安装 Capacitor
npm init -y
npm install @capacitor/core @capacitor/cli @capacitor/android

# 3. 初始化 Android 平台
npx cap init "DayStudy" "com.study.pomodoro" --web-dir=www
npx cap add android

# 4. 注入原生通知模块
cp native/TimerForegroundService.java native/TimerSoundReceiver.java native/TimerNotifierPlugin.java android/app/src/main/java/com/study/pomodoro/
cp native/ic_notification.xml android/app/src/main/res/drawable/
python3 native/patch_android.py android

# 5. 修复 Kotlin 依赖冲突（编辑 android/app/build.gradle 追加）
# 在文件末尾添加 configurations.all { resolutionStrategy { force 'org.jetbrains.kotlin:kotlin-stdlib:1.8.22' ... } }

# 6. 同步 + 构建
npx cap sync
cd android && ./gradlew assembleDebug

APK 路径：android/app/build/outputs/apk/debug/app-debug.apk

下载 APK
方式一：GitHub Releases
前往 Releases↗ 下载最新 DayStudy-v1.0.apk。

方式二：GitHub Actions
进入仓库 Actions↗ 页面
选择最新的 构建 Android APK 工作流
点击顶部 Artifacts 下载 DayStudy-v1.0
安装说明
Android 手机下载 APK 后直接安装
如提示「未知来源」，在设置中允许安装
通知权限首次打开时请允许
项目结构
DayStudy/
├── index.html                 # 主页面（PWA 入口）
├── manifest.json              # PWA 清单
├── sw.js                      # Service Worker（离线缓存）
├── icons/                     # SVG 图标
│   ├── icon-192.svg
│   └── icon-512.svg
├── native/                    # 原生通知模块
│   ├── TimerForegroundService.java  # 前台服务
│   ├── TimerSoundReceiver.java      # 闹钟接收器
│   ├── TimerNotifierPlugin.java     # Capacitor 插件桥
│   ├── ic_notification.xml          # 通知图标
│   └── patch_android.py             # 自动注入脚本
├── .github/workflows/
│   └── build-apk.yml          # GitHub Actions 构建流水线
└── deploy_gh.py               # 部署脚本

开发计划
暗色模式
多语言（英文界面）
数据导出 / 备份
iOS 版本（Capacitor iOS）
云同步
协议
MIT License (c) 2026 DayStudy
