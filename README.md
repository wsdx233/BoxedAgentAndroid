# BoxedAgent Android

一个使用 **Kotlin + Jetpack Compose + Material Design 3** 开发的 BoxedAgent 安卓竖屏客户端。项目复用 BoxedAgent 的 REST + WebSocket API，面向手机重新组织 WebUI 的三栏功能为底部导航：Boxes、Chat、Tools。

上游 WebUI/API：<https://github.com/wsdx233/BoxedAgent>

## 已实现功能

- API 连接与 Token 登录：支持 `BOXEDAGENT_TOKEN`，自动保存地址与 Token。
- 全局事件 WebSocket：同步 Box、Session、镜像构建/拉取进度。
- Box 管理：创建、启动、停止、重命名、克隆、删除。
- Session 管理：创建、启动、停止、重命名、复刻、Fork、删除。
- Chat：
  - 加载历史消息；
  - 实时 WebSocket 流式响应；
  - assistant 文本、thinking、tool 调用/结果卡片；
  - 图片输入、普通文件上传到 `/workspace/.upload` 并以 `@file` 引用；
  - abort、steer、follow-up；
  - 模型切换、thinking level、auto/manual compact、手动 compact；
  - token/cost/context stats 展示。
- Tools：
  - Shell：通过 `/ws/boxes/:boxId/terminal` 连接容器 shell；
  - Files：浏览、上传、下载、新建目录、删除；
  - Pi：编辑 provider/model/thinking、enabledModels、env JSON、models.json、settings.json、SYSTEM.md、APPEND_SYSTEM.md、AGENTS.md；
  - code-server：内嵌 WebView + 外部浏览器入口。

## 构建

```bash
./gradlew :app:assembleDebug
```

Debug APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 连接提示

- Android 模拟器访问宿主机 BoxedAgent 默认地址：`http://10.0.2.2:8080`
- 真机访问请使用局域网 IP 或公网 HTTPS 反代地址，例如：`http://192.168.1.10:8080`
- 如果服务端启用了认证，填写部署时的 `BOXEDAGENT_TOKEN`。

## 技术栈

- Kotlin 2.1
- Android Gradle Plugin 8.9
- Jetpack Compose + Material3
- OkHttp REST/WebSocket
- kotlinx.serialization JSON

## 说明

移动端 UI 不是简单 WebView 包壳，而是基于 WebUI 功能重新设计的原生竖屏体验。code-server 由于本身是 Web 应用，使用内嵌 WebView 展示，并保留外部浏览器打开能力。
