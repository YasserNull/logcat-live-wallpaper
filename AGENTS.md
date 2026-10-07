# AGENTS.md

## Build & Run

```bash
./gradlew assembleDebug              # full debug build
./gradlew test                       # unit tests only
./gradlew connectedCheck             # instrumented tests (device/emulator required)
./gradlew :app:tasks --group=build   # list all build tasks
```

No codegen, lint, or typecheck steps are configured. The project has only placeholders for `test` and `androidTest`.

## Architecture

- **Single module**: `:app`
- **Entrypoints**: `MainActivity.kt` (launcher/settings) + `LogCatWallpaperService.kt` (wallpaper engine)
- **Theme**: forced dark — dynamic dark (Android 12+) or static purple/pink fallback (`app/src/main/java/com/yassernull/logcatlivewallpaper/ui/theme/`)
- **Namespace / applicationId**: `com.yassernull.logcatlivewallpaper` (namespace in `app/build.gradle.kts`, not in `AndroidManifest.xml`)
- **Wallpaper**: `LogCatWallpaperService` — reads `logcat -v brief *:*` on a background thread, renders scrolling green-on-black text via Canvas at ~20fps

## Key Config

| Setting | Value |
|---|---|
| Gradle | 9.4.1 |
| AGP | 9.2.1 |
| Kotlin | 2.2.10 |
| Compose BOM | 2026.02.01 |
| minSdk | 23 (Android 6.0) |
| targetSdk / compileSdk | 36 (Android 16) |
| Java | 17 |
| Kotlin code style | official |

## Dependencies

Version catalog at `gradle/libs.versions.toml` — always add new deps there.

## Tests

- `test/` — JUnit 4 (runs on host JVM)
- `androidTest/` — Espresso + Compose UI test (`androidx.test.ext.junit4` runner)
- Both are placeholder stubs.

## Implementation Notes

- `Engine.onCreate()` in API 36 takes a `SurfaceHolder` parameter — `override fun onCreate(holder: SurfaceHolder)`.
- `READ_LOGS` must be granted via ADB on non-rooted devices: `adb shell pm grant com.yassernull.logcatlivewallpaper android.permission.READ_LOGS`
- MainActivity is a stub — not needed for wallpaper functionality; the wallpaper is set via the system wallpaper picker.
