# 飞书打卡助手 Android APP

一个用于自动打开飞书并完成打卡的 Android 应用。

## ✨ 功能特性

- 📱 一键打开飞书并自动完成打卡
- ⏰ 支持设置上班/下班定时打卡
- 🔄 开机自动恢复定时任务
- 🔔 打卡结果通知提醒

## 📋 系统要求

- Android 7.0 (API 24) 及以上
- 需要安装飞书 APP

## 🔧 使用方法

### 1. 编译安装

使用 Android Studio 打开项目，连接手机后点击运行即可安装。

或使用命令行编译：

```bash
cd FeishuPunch
./gradlew assembleDebug
```

编译完成后，APK 位于 `app/build/outputs/apk/debug/app-debug.apk`

### 2. 开启无障碍服务

这是最关键的一步！

1. 打开 APP，点击「开启无障碍服务」
2. 在系统设置中找到「飞书打卡助手」
3. 开启无障碍服务开关
4. 返回 APP

### 3. 测试打卡

点击「立即打卡」按钮测试打卡功能是否正常。

### 4. 设置定时打卡

1. 点击时间区域设置上班/下班打卡时间
2. 打开「定时打卡」开关
3. 保持 APP 后台运行

## ⚠️ 注意事项

### 权限说明

| 权限 | 用途 |
|------|------|
| 无障碍服务 | 用于模拟点击操作 |
| 前台服务 | 保持后台运行 |
| 精确闹钟 | 确保定时打卡准时触发 |
| 开机自启 | 开机恢复定时任务 |

### 后台保活

为确保定时打卡正常工作，请进行以下设置：

1. **电池优化**：将本 APP 设为不限制
2. **自启动**：允许本 APP 自启动
3. **后台运行**：允许本 APP 后台运行
4. **锁定任务**：在最近任务中锁定本 APP

不同手机品牌设置位置可能不同，请根据实际情况操作。

### 常见问题

**Q: 无障碍服务自动关闭？**
A: 部分手机系统会自动关闭无障碍服务，需要在设置中信任本 APP。

**Q: 定时打卡不触发？**
A: 检查电池优化和后台运行设置，确保 APP 没有被系统杀掉。

**Q: 打卡按钮找不到？**
A: 飞书更新后界面可能变化，需要修改代码中的按钮文字匹配规则。

## 📁 项目结构

```
FeishuPunch/
├── app/
│   ├── src/main/
│   │   ├── kotlin/com/example/feishupunch/
│   │   │   ├── MainActivity.kt          # 主界面
│   │   │   ├── service/
│   │   │   │   ├── PunchAccessibilityService.kt  # 无障碍服务(核心)
│   │   │   │   └── PunchForegroundService.kt     # 前台服务
│   │   │   ├── receiver/
│   │   │   │   ├── AlarmReceiver.kt      # 闹钟接收器
│   │   │   │   └── BootReceiver.kt       # 开机接收器
│   │   │   └── util/
│   │   │       ├── AlarmHelper.kt        # 闹钟工具
│   │   │       └── PreferenceHelper.kt   # 配置存储
│   │   ├── res/
│   │   │   ├── layout/                   # 布局文件
│   │   │   ├── drawable/                 # 图标资源
│   │   │   ├── values/                   # 字符串、主题等
│   │   │   └── xml/                      # 无障碍服务配置
│   │   └── AndroidManifest.xml           # 应用清单
│   └── build.gradle.kts                  # 模块构建配置
├── build.gradle.kts                      # 项目构建配置
├── settings.gradle.kts                   # 项目设置
└── README.md                             # 本文件
```

## 🔧 自定义修改

### 修改打卡按钮匹配规则

在 `PunchAccessibilityService.kt` 中修改：

```kotlin
// 打卡按钮文字列表
val punchTexts = listOf("上班打卡", "下班打卡", "打卡", "签到", "外出打卡")
```

### 适配其他 APP

修改以下配置：

```kotlin
// 在 PunchAccessibilityService.kt 中
const val FEISHU_PACKAGE = "com.ss.android.lark"  // 修改为目标APP包名

// 在 accessibility_service_config.xml 中
android:packageNames="com.ss.android.lark"  // 修改为目标APP包名
```

## ⚖️ 免责声明

本项目仅供学习研究使用，请遵守公司相关规定。使用本工具所产生的任何后果由使用者自行承担。

