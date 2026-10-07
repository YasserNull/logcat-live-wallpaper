# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

LogCat Live Wallpaper is a single-module Android app (`:app`) that renders live `logcat` output as a scrolling, terminal-style live wallpaper. Kotlin + Jetpack Compose for the settings UI; the wallpaper itself is drawn with raw `Canvas` (no Compose). Package/appId `com.yassernull.logcatlivewallpaper`. minSdk 23 (Android 6.0), target/compileSdk 36 (Android 16), Java 17. Gradle 9.4.1 / AGP 9.2.1 / Kotlin 2.2.10 / Compose BOM 2026.02.01.

## Commands

```bash
./gradlew assembleDebug    # build debug APK (auto-formats sources via Spotless)
./gradlew installDebug     # build + install to connected device
./gradlew spotlessApply    # apply ktlint formatting
./gradlew spotlessCheck    # verify formatting
./gradlew test             # host JVM unit tests (none currently exist)
./gradlew connectedCheck   # instrumented tests (device/emulator required)
./gradlew build            # full build (what CI runs)
```

Notes:
- Spotless (ktlint) is wired to run automatically before `preBuild`, so every build reformats Kotlin sources. Enforced style: 4-space indent, max-line-length disabled, wildcard imports allowed (see root `build.gradle.kts`). `.editorconfig`'s 2-space setting is superseded by Spotless.
- No lint or typecheck steps are configured.
- There are currently **no test source files and no test dependencies** wired into `app/build.gradle.kts` — junit/espresso/compose-ui-test exist only in the version catalog. `./gradlew test` is effectively a no-op.

## Architecture

### Two entrypoints
- `service/LogCatWallpaperService.kt` — the live wallpaper engine (`WallpaperService`). Users select it via the system wallpaper picker; `MainActivity` is not involved in the wallpaper itself.
- `ui/activities/MainActivity.kt` — launcher that shows a live preview and a menu. Also auto-selects the best available permission method on launch.

### Rendering (core/LogCatRenderer.kt)
Shared by the wallpaper engine and `MainActivity`'s `LogCatPreviewView` (a plain `View` that calls `renderer.draw()`):
- Owns a bounded list of rendered lines (max 50) and a `ConcurrentLinkedQueue` of pending lines fed by the reader thread (bounded ~50).
- Per-line color is derived inside the renderer from logcat priority tags via regex `(?:^|\s)([VDIWEFS])(?:/|\s)` → per-level configurable colors.
- Two scroll modes: `smooth` (constant-speed scroll, speed factor ramps up under pending back-pressure) and `terminal` (line-by-line append). Line wrapping and word-wrap are configurable.
- Draws background color or a background image, then the log area (position/size/rotation configurable).

### Logcat access (core/PermissionManager.kt)
Three mutually exclusive methods chosen by the `permission` setting (`"shizuku"`, `"root"`, `"none"`):
- `shizuku` — spawns the configured command via `Shizuku.newProcess` (invoked reflectively, using `isAccessible`).
- `root` — `su -c <command>`.
- `none` — `sh -c <command>` (limited logs unless `READ_LOGS` is granted via ADB).

`startLogcat(context)` returns a `LogcatHandle` wrapping the spawned process; callers read lines on a background daemon thread and feed them to `renderer.enqueueLine()`. The command is fully user-configurable (default `logcat -c && logcat -v tag`).

### Settings (core/Preferences.kt)
Immutable `Settings` data class persisted as flat SharedPreferences keys under `logcat_wallpaper_prefs`. The wallpaper engine and preview both register an `OnSharedPreferenceChangeListener` and push live updates via `renderer.updateSettings(...)`; changing `permission` or `logcat_command` restarts the reader thread and clears the renderer.

### Wallpaper engine lifecycle (service/LogCatWallpaperService.kt)
- `LogCatEngine.onCreate(holder: SurfaceHolder)` — note the API 36 signature takes a `SurfaceHolder`.
- A `Choreographer.FrameCallback` drives redraws only while the engine is `visible`; rendering pauses when the screen is off or another app is in the foreground (for battery savings).
- Uses `lockHardwareCanvas()` on API 26+ and requests a 120fps surface frame rate on API 30+.
- The reader thread is a daemon; it is (re)started on visibility and preference changes.

## Key Configuration
- Version catalog `gradle/libs.versions.toml` — add all new dependencies there, never inline in `build.gradle.kts`.
- Deps: libsu (`core`/`service`/`nio`) and rikka Shizuku (`api`/`provider`). `ShizukuProvider` is declared in `AndroidManifest.xml` with authority `${applicationId}.shizuku`.
- On non-rooted devices, full logcat access requires an ADB grant:
  `adb shell pm grant com.yassernull.logcatlivewallpaper android.permission.READ_LOGS`
- CI: `.github/workflows/android.yml` (manual `workflow_dispatch`) runs `./gradlew clean build` on JDK 17.
- Localization: user-facing strings live in `res/values*` (ar, es, fr, hi, ja, zh). Language switching goes through `utils/LocaleHelper.kt` and activities must be recreated on change (`MainActivity.onResume` compares the saved language).
- `compileSdk` uses the AGP 9 `release(36) { minorApiLevel = 1 }` DSL; `multiDexEnabled = true`.

## Gotchas
- The Shizuku `newProcess` reflection (`isAccessible = true`) and `LogcatHandle.destroy()`'s reflective process teardown must keep working across Shizuku versions.
- In `none` permission mode there is no in-app permission dialog — logcat access relies on ADB grant or switching to Shizuku/root.
- Wallpaper preview in `MainActivity` is a separate render loop from the wallpaper service; both must be kept in sync when adding renderer features.
