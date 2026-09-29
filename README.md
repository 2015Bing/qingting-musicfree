<p align="center">
  <img src="assets/brand/qingting-logo.png" width="112" alt="轻听 Logo">
</p>

<h1 align="center">轻听 · QingTing</h1>

<p align="center">原生 Android 音乐播放器 · 兼容 MusicFree JS 插件 · 数据保存在本机</p>

<p align="center">
  <a href="https://github.com/2015Bing/qingting-musicfree/releases/latest"><img src="https://img.shields.io/github/v/release/2015Bing/qingting-musicfree" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0 及以上">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT License"></a>
</p>

<p align="center">
  <a href="https://github.com/2015Bing/qingting-musicfree/releases/latest/download/qingting-universal.apk"><strong>下载最新版安卓安装包（APK）</strong></a>
  · <a href="docs/user-guide.md">使用教程</a>
  · <a href="https://github.com/2015Bing/qingting-musicfree/releases">所有版本</a>
  · <a href="https://github.com/2015Bing/qingting-musicfree/issues">反馈问题</a>
</p>

## 项目简介

**轻听是一款以“找到歌、听得顺、收藏来源不乱”为目标的原生 Android 音乐播放器。**

它把多个 MusicFree 插件来源的搜索、排行榜和播放集中在一个应用里，同时提供收藏、自建歌单、当前播放队列和同步歌词。你可以从榜单发现歌曲，也可以搜索后查看可选来源；喜欢的版本连同播放来源一起保存，下次从收藏直接听。

项目使用 Java 与 Media3，参考 MusicFree 的 JS 插件协议独立实现，没有复制 MusicFree 应用代码，也不是 MusicFree 官方客户端。收藏、歌单和设置保存在本机，无需注册轻听账号。

### 适合谁

- 希望在一个播放器里搜索和使用多个 MusicFree 插件来源的人。
- 在意歌曲版本和播放来源，希望收藏后保持原来源的人。
- 喜欢简洁的图标导航、列表操作，并希望自己管理本地音乐库的人。

### 项目优势

| 优势 | 具体体验 |
| --- | --- |
| 多个来源，一个入口 | 搜索结果保留来源数量，可查看线路状态；普通歌曲失败后尝试已有线路并补搜匹配歌曲，减少反复手动搜索 |
| 收藏连同来源一起保存 | 收藏和歌单按固定来源播放，失效时提示用户手动处理，避免自动换成不想听的渠道或版本 |
| 本地管理，无需账号 | 收藏、歌单、历史和偏好留在设备内，无需依赖轻听账号服务；第三方插件仍按自身方式访问网络 |
| 歌单整理路径清楚 | 从收藏单首或批量加入歌单，自动跳过重复项，支持列表内搜索与排序 |
| 减少重复等待 | 榜单与线路验证状态缓存，重启优先展示有效榜单；封面请求合并并缓存，离屏取消无用加载 |
| 针对长列表优化 | 歌曲行按屏幕范围创建并复用，播放进度更新不扫描整页，音乐库修改在后台顺序保存 |
| 播放功能完整 | 队列、三种播放模式、同步歌词、时间校准、定时停止，以及通知栏与锁屏控制 |
| 源码可修改 | MIT 许可，提供 Android 源码、插件桥接、测试用例和本地合成音频服务，方便研究和二次开发 |

目前的流畅度优化有代码和回归测试支持，仍需要更多真机验证；不宣称在所有手机与网络条件下都不卡顿。

歌曲、歌词和排行榜来自用户启用的插件。项目不内置歌曲音频，不提供音乐会员权益，不保证所有插件或歌曲始终可用。

## 下载与安装

| 下载项 | 说明 |
| --- | --- |
| [最新版通用 APK](https://github.com/2015Bing/qingting-musicfree/releases/latest/download/qingting-universal.apk) | 推荐，点击直接下载安装包 |
| [v0.4.1 APK](https://github.com/2015Bing/qingting-musicfree/releases/download/v0.4.1/qingting-0.4.1-universal.apk) | 当前发布版本，固定下载地址 |
| [发布页及校验文件](https://github.com/2015Bing/qingting-musicfree/releases/latest) | 更新说明、SHA-256 校验值和其他产物 |

- 支持 **Android 8.0 及以上**。通用 APK 没有 ABI 限制，无需按手机芯片选择安装包。
- 首次安装按系统提示允许当前浏览器或文件管理器安装应用。
- 使用同签名正式版直接覆盖升级即可保留数据，**无需先卸载**；正式包与自行构建的 debug 包不能直接互相覆盖。
- 无需授权码。数据只保存在本机；卸载或清除应用数据会丢失收藏、歌单和订阅配置。

## 功能

- **多源搜索**：按歌曲展示来源数量，查看线路状态，手动选源，普通歌曲失败时自动尝试备用线路。
- **排行榜**：按来源浏览、分页、播放已加载歌曲；刷新检查空榜及失败榜，查看隐藏原因。
- **播放队列**：下一首播放、添加到队尾、左滑移出；顺序播放、列表循环和单曲循环。
- **固定来源收藏**：收藏记录选定的来源；失效时提示，由用户手动换源。
- **自建歌单**：创建、重命名、删除；仅从我的收藏提供单首或批量添加，重复歌曲自动跳过。
- **本地音乐库**：收藏、最近播放、歌单内搜索与排序；最近 30 条搜索历史。
- **播放详情**：唱片／歌词切换、歌词跟随和逐句跳转、时间校准、通知栏及锁屏控制。
- **定时停止**：15／30／60 分钟，或播完当前歌曲停止。
- **减少重复加载**：歌曲列表复用、封面缓存与取消、后台顺序保存、榜单及线路验证缓存。

没有账号、云同步、歌曲下载或数据导出功能。所有应用数据均本地保存；插件访问网络所需的账号或参数由用户自行配置。

## 快速开始

1. 安装 APK。首次启动会自动尝试补导四个默认订阅；点底部最右侧的订阅源图标查看进度，失败点「补导缺失插件 / 重试」。
2. 底部从左到右：**排行榜、搜索、我的音乐、订阅源**，均使用图标；长按可看名称。
3. 搜索歌名／歌手，或从榜单选歌；点击歌曲行的歌名区域播放，点「N 源 ›」查看线路。
4. 点底部小播放条的封面或标题打开详情；左下角图标切换播放模式，更多菜单设置定时停止。
5. 收藏歌曲后，在「我的音乐 → 我的收藏」单首或批量添加到自建歌单。

完整步骤见 **[使用教程](docs/user-guide.md)**。应用内也可从「订阅源 → 使用手册」离线阅读。

### 默认订阅

首次会自动尝试以下地址；重试补导不会覆盖已有插件配置与停用状态。它们由第三方维护，可用性以应用内实际导入结果为准。

- [MusicFreePluginsHub 合集](https://musicfreepluginshub.2020818.xyz/plugins.json)
- [nairocy 合集](https://music.nairocy.com/plugins.json)
- [qwerwhr 合集](https://qwerwhr.github.io/musicfree-plugins/plugins.json)
- [kevinr 合集](https://gitee.com/kevinr/tvbox/raw/master/musicfree/plugins.json)

也可通过「＋ 添加订阅」导入 MusicFree 插件 JSON 清单或单个 JS 地址。请只使用可信插件。**LX 自定义音源脚本与 MusicFree 插件格式不能直接互换。**

### 缓存说明

| 内容 | 当前策略 |
| --- | --- |
| 最近浏览榜单 | 本地最多 24 个、30 分钟，恢复已加载分页；有效时重启优先展示 |
| 线路验证状态 | 成功保留 5 分钟，失败保留 1 分钟；可手动重新检测 |
| 封面 | 内存与有容量上限的本地缓存，同地址合并请求，无用加载取消 |

缓存不等于歌曲下载；首次访问、过期或来源配置变化后仍可能需要联网。实际播放能力以来源当前状态为准。

## 从源码构建

### 环境

- JDK 21（Java 源码级别 17）
- Android SDK Platform 36，配置 `ANDROID_HOME` 或 `local.properties` 的 `sdk.dir`
- 仓库自带 Gradle Wrapper
- 修改 JS 运行时需要 Node.js 与 npm

### 构建调试版

```bash
# macOS / Linux
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

```powershell
# Windows
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。已打包的 JS 运行时随源码提供；不修改 JS 时不必运行 npm 构建。

修改 `runtime/entry.cjs` 后执行：

```bash
npm ci
npm run bundle
node tools/licenses.cjs
npm test
```

### 构建正式版

自行发布需要使用**自己的签名密钥**。本仓库不包含官方签名私钥，自己的签名无法覆盖这里发布的正式版。

在本地创建 `signing/release.properties`（该目录已忽略）：

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

```bash
./gradlew testReleaseUnitTest lintRelease assembleRelease bundleRelease
```

Windows 使用 `gradlew.bat`。输出位于 `app/build/outputs/apk/release/` 和 `app/build/outputs/bundle/release/`。不要提交签名文件或密码配置。

## 测试与验证

- Java：`./gradlew testDebugUnitTest`
- JS 插件运行时：`npm test`
- Android 静态检查：`./gradlew lintDebug`
- 完整设备测试使用项目自带的合成音频服务：

```bash
node tools/fixture-server.cjs
# 另开终端，连接专用测试设备或模拟器
adb reverse tcp:18765 tcp:18765
./gradlew connectedDebugAndroidTest
```

设备测试会添加测试来源与音乐库数据，请使用专用测试环境。合成音频服务不含商业音乐。

当前已有 109 项 Java 回归测试通过，正式版构建、Lint 与 APK 签名检查通过。最新列表复用、图片请求和导航设备用例已编译，尚未完成真机帧率及全流程验收；自动测试通过不代表所有设备、插件和网络环境都不卡顿。

## 插件兼容范围

支持 `module.exports`、异步 `search`、`getMediaSource`、`getLyric`、`getTopLists`、`getTopListDetail`、HTTP 请求头及 `env.getUserVariables()`，保留歌曲的原始插件字段；接受 `{plugins:[{name,url,version}]}` 订阅清单。

运行时内置 axios、crypto-js、dayjs、big-integer、qs、he、cheerio。当前不支持 Node 原生模块、`require('webdav')`、依赖浏览器 fetch/XHR 的插件或自动 Cookie 会话。不承诺兼容所有 MusicFree 插件。

## 项目结构

```text
app/src/main/       Android 界面、播放器、缓存与插件桥接
app/src/test/       Java 单元测试
app/src/androidTest/  设备端集成与布局测试
runtime/            MusicFree JS 插件运行时
tests/              JS 回归测试
tools/              打包、许可清单与合成音频测试工具
assets/brand/       项目标志及图标资源
docs/user-guide.md  最新使用教程
```

## 参与贡献与反馈

欢迎提交 [Issue](https://github.com/2015Bing/qingting-musicfree/issues) 和 Pull Request。请附应用版本、Android 版本、设备型号、复现步骤及脱敏日志；不要公开账号、Token 或带私密参数的地址。具体流程见 [贡献指南](CONTRIBUTING.md)。

版本变化见 [更新记录](CHANGELOG.md)，安装包见 [Releases](https://github.com/2015Bing/qingting-musicfree/releases)。

## 许可证与致谢

本项目原创代码采用 [MIT License](LICENSE)。第三方组件保留各自许可证，JS 依赖声明见 [THIRD_PARTY_NOTICES](app/src/main/assets/THIRD_PARTY_NOTICES.txt)；AndroidX、Media3、OkHttp 等遵循各自许可。MIT 许可不授予第三方音乐内容、商标或订阅服务的使用权。

- [MusicFree](https://github.com/maotoumao/MusicFree)：插件协议参考。
- [MusicFree 插件协议文档](https://musicfree.catcat.work/plugin/protocol.html)。
- [LX Music Mobile](https://github.com/lyswhut/lx-music-mobile)：部分播放与歌词交互参考。
- [AndroidX Media3](https://developer.android.com/media/media3)：音频播放与媒体会话。
