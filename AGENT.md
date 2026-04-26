# AGENT.md

This repository contains a native Android client for BoxedAgent. These notes are for coding agents working in this repo.

## Project Overview

- App type: native Android app, not a WebView wrapper.
- Language/UI: Kotlin, Jetpack Compose, Material Design 3.
- API: BoxedAgent REST and WebSocket APIs.
- Main module: `app/`.
- Embedded terminal modules:
  - `terminal-emulator/`
  - `terminal-view/`
- Current UX direction: mobile portrait, Chat-first, side panels for Boxes/Sessions and Tools.

## Build / Test

Use the Gradle wrapper from the repository root:

```bash
./gradlew :app:assembleDebug
```

For a clean verification build:

```bash
./gradlew clean :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Important Files

- `app/src/main/java/com/boxedagent/android/MainActivity.kt`
  - Main Compose host.
- `app/src/main/java/com/boxedagent/android/TerminalActivity.kt`
  - Dedicated native Activity for the remote terminal.
  - Keep the Termux-backed terminal isolated here; do not embed it back into Compose unless there is a strong reason.
- `app/src/main/java/com/boxedagent/android/data/BoxedAgentApi.kt`
  - REST/WebSocket API client.
- `app/src/main/java/com/boxedagent/android/data/Models.kt`
  - Shared DTOs and chat data models.
- `app/src/main/java/com/boxedagent/android/data/MessageNormalizer.kt`
  - Converts backend/session message shapes into Android chat messages.
- `app/src/main/java/com/boxedagent/android/ui/AppViewModel.kt`
  - Application state and BoxedAgent operations.
- `app/src/main/java/com/boxedagent/android/ui/BoxedAgentApp.kt`
  - Main Compose UI: chat, side panels, tools, files, markdown, tool cards.
- `app/src/main/java/com/boxedagent/android/ui/terminal/RemoteTerminalView.kt`
  - Termux emulator/renderer bridge for remote Docker shell WebSocket I/O.
- `app/src/main/java/com/boxedagent/android/ui/theme/Theme.kt`
  - Material theme and system bar handling.
- `app/src/main/java/com/boxedagent/android/syntax/PrismBundleConfig.java`
  - Prism4j syntax highlighter bundle config.

## UI / UX Guidelines

- Use Jetpack Compose + Material3 for the app UI.
- Do not use emoji as icons; use Compose `Icons.*` / Material icons.
- Keep the layout mobile portrait friendly.
- Chat is the primary screen.
- Boxes/Sessions and Tools should remain full-screen side overlays, not bottom navigation.
- Prefer mobile-friendly bottom sheets over small dropdown menus.
- Keep light mode visually consistent; avoid accidental half-dark/half-light surfaces.
- Top token/cost/context stats should stay in the chat top bar and remain horizontally scrollable.
- Attachment insertion should use only `@path ` references.
- Images should follow WebUI behavior: attach as `@path`, send referenced images as image payloads, and do not render raw image data inline in messages.
- Attachment chips should wrap left-to-right using flow layout.

## Terminal Guidelines

- The terminal is intentionally in `TerminalActivity` using native Android `View` layout.
- This avoids Compose/IME/system-insets conflicts with Termux terminal rendering.
- `TerminalActivity` manually handles status/navigation/IME insets.
- `RemoteTerminalView` feeds WebSocket bytes into Termux `TerminalEmulator` and sends user input back over WebSocket.
- Be careful with resize messages: avoid serializing `Map<String, Any>` with kotlinx serialization; send raw JSON or strongly typed DTOs.

## Markdown / Code / Tools

- Chat renders Markdown with custom lightweight parsing in `BoxedAgentApp.kt`.
- Code highlighting uses Prism4j first, with a fallback highlighter.
- Tool cards should stay close to WebUI behavior:
  - summary on the same line as the tool title,
  - one-line collapsed summary,
  - type-specific expanded rendering,
  - unified diff for write/edit where possible,
  - command/output preview for bash/shell.
- The latest tool call or thinking block should auto-expand so users can observe progress.

## Files / Attachments

- File browser should remain VSCode/WebUI-like and flat.
- Keep quick attach, copy path, download, delete, create file/dir, and upload flows mobile friendly.
- Do not insert Chinese prompt text such as `请读取附件：` when attaching files; insert only `@path `.

## Git / Generated Files

Do not commit local/generated directories or machine-specific files:

- `.gradle/`
- `.kotlin/`
- `.cache/`
- `build/`
- module `build/` directories
- `local.properties`
- `.idea/`

Before handing off, run at least:

```bash
./gradlew :app:assembleDebug
```

and report the APK path if the build succeeds.
