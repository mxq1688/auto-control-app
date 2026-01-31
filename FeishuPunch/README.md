# 乐逍遥 - 通用自动化打卡助手

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher.xml" width="80" height="80" alt="Logo">
</p>

<p align="center">
  <a href="https://github.com/mxq1688/autojs/releases"><img src="https://img.shields.io/github/v/release/mxq1688/autojs?style=flat-square" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/mxq1688/autojs?style=flat-square" alt="License"></a>
  <img src="https://img.shields.io/badge/Android-7.0%2B-green?style=flat-square" alt="Android">
</p>

一个基于 Android 无障碍服务的**通用自动化打卡工具**，支持自定义操作流程，可适配任意打卡 APP（飞书、钉钉、企业微信等）。

## ✨ 功能特性

### 🎯 核心功能
- **自定义流程**：可视化编辑打卡操作步骤
- **多流程管理**：支持创建多个不同的自动化流程
- **定时执行**：支持上班/下班时间段随机触发
- **坐标采集**：悬浮窗实时采集屏幕坐标

### 📱 支持的操作类型
| 操作 | 说明 |
|------|------|
| 打开 APP | 启动指定应用 |
| 点击坐标 | 点击屏幕指定位置 |
| 点击文本 | 点击包含指定文字的元素 |
| 长按 | 长按指定坐标 |
| 双击 | 双击指定坐标 |
| 滑动 | 从起点滑动到终点 |
| 等待 | 等待指定时间 |
| 返回 | 模拟返回键 |
| 回到桌面 | 返回系统桌面 |

### ⏰ 定时功能
- 上班/下班时间段设置
- 时间段内随机触发（避免规律性）
- 按星期选择执行日
- 定时关闭目标 APP

## 📸 截图

<!-- 可以添加 APP 截图 -->

## 📋 系统要求

- Android 7.0 (API 24) 及以上
- 需要开启无障碍服务权限
- 需要悬浮窗权限（坐标采集功能）

## 🚀 快速开始

### 方式一：下载安装

前往 [Releases](https://github.com/mxq1688/autojs/releases) 下载最新 APK 安装。

### 方式二：编译安装

```bash
git clone https://github.com/mxq1688/autojs.git
cd autojs/FeishuPunch
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`

### 使用步骤

1. **开启无障碍服务**
   - 打开 APP → 点击「开启无障碍服务」
   - 在系统设置中找到「乐逍遥」并开启

2. **配置目标 APP**
   - 点击「选择目标 APP」
   - 选择你要自动化的打卡应用

3. **编辑操作流程**
   - 点击「添加步骤」
   - 按顺序添加打卡所需的操作
   - 可使用「坐标采集」功能获取精确坐标

4. **测试流程**
   - 点击「立即执行」测试流程是否正常

5. **设置定时**
   - 配置上班/下班时间段
   - 选择执行的星期
   - 开启「定时打卡」开关

## 🔧 配置示例

### 飞书打卡流程示例

```
1. 打开 APP: 飞书
2. 等待: 3000ms
3. 点击文本: "工作台"
4. 等待: 2000ms
5. 点击文本: "打卡"
6. 等待: 3000ms
7. 点击文本: "上班打卡" / "下班打卡"
8. 等待: 2000ms
9. 回到桌面
```

### 钉钉打卡流程示例

```
1. 打开 APP: 钉钉
2. 等待: 3000ms
3. 点击文本: "工作台"
4. 等待: 2000ms
5. 点击文本: "考勤打卡"
6. 等待: 3000ms
7. 点击坐标: (x, y)  # 打卡按钮位置
8. 等待: 2000ms
9. 回到桌面
```

## ⚠️ 权限说明

| 权限 | 用途 |
|------|------|
| 无障碍服务 | 模拟点击、滑动等操作 |
| 悬浮窗 | 坐标采集悬浮窗 |
| 前台服务 | 保持后台运行 |
| 精确闹钟 | 定时任务准时触发 |
| 开机自启 | 开机恢复定时任务 |

## 📁 项目结构

```
FeishuPunch/
├── app/src/main/
│   ├── kotlin/com/example/feishupunch/
│   │   ├── MainActivity.kt                    # 主界面
│   │   ├── WakeUpActivity.kt                  # 唤醒界面
│   │   ├── model/
│   │   │   └── FlowStep.kt                    # 步骤数据模型
│   │   ├── service/
│   │   │   ├── PunchAccessibilityService.kt   # 无障碍服务(核心)
│   │   │   ├── PunchForegroundService.kt      # 前台服务
│   │   │   └── FloatingWindowService.kt       # 坐标采集悬浮窗
│   │   ├── receiver/
│   │   │   ├── AlarmReceiver.kt               # 闹钟接收器
│   │   │   └── BootReceiver.kt                # 开机接收器
│   │   └── util/
│   │       ├── AlarmHelper.kt                 # 闹钟工具
│   │       └── PreferenceHelper.kt            # 配置存储
│   ├── res/
│   │   ├── layout/                            # 布局文件
│   │   ├── drawable/                          # 图标资源
│   │   └── xml/                               # 无障碍服务配置
│   └── AndroidManifest.xml
└── build.gradle.kts
```

## 🔨 技术栈

- **语言**: Kotlin
- **最低 SDK**: Android 7.0 (API 24)
- **目标 SDK**: Android 14 (API 34)
- **核心技术**: Android AccessibilityService

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 开源协议。

## ⚖️ 免责声明

- 本项目仅供学习研究使用
- 请遵守公司相关考勤规定
- 使用本工具所产生的任何后果由使用者自行承担
- 作者不对任何滥用行为负责

## 🙏 致谢

感谢所有贡献者和使用者的支持！

---

<p align="center">
  如果这个项目对你有帮助，请给个 ⭐ Star 支持一下！
</p>
