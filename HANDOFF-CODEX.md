# Handoff — ShizuCallRecorder redesign branch

**Repo:** https://github.com/Mikelee8810/Call-Recorder
**Branch:** `claude/pensive-archimedes-gdyjhk` (draft PR #1, open)
**Local clone:** `~/Documents/Call-Recorder`
**Device:** Pixel 10 Pro XL, USB, `adb` works. Shizuku installed + granted.

## Build (works, green)
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug --no-daemon
```
- Wrapper is gitignored by design; regenerate with `gradle wrapper --gradle-version 9.4.1 --gradle-distribution-sha256-sum 2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb` if missing.
- `local.properties` has `sdk.dir=/Users/michael/Library/Android/sdk`.
- Debug build is installed on the phone, onboarding done, pointed at `/storage/emulated/0/Recordings` (38 real `.ogg` files).

## Done in commit bb7fef2
- Compile error fixed (missing `ArrowBack` import), lint error fixed.
- **Bug fixed:** `RecordingsRepository.extractPhoneNumber` matched the filename's timestamp instead of the number — contact names/search/sort were broken. Verified on device.
- **Shizuku never stopped:** removed `stopServer()` + the `onDestroy` stop call + the "Keep Shizuku alive" toggle/prefs/strings (all locales).
- **Watchdog:** `integrations/shizuku/ShizukuWatchdogReceiver.kt` — BOOT_COMPLETED + 15-min inexact alarm, restarts Shizuku if `isShizukuAutoManageEnabled()` and it's down. Armed in `ShizuApplication.onCreate`. Manifest updated. **Not yet tested on a real reboot.**
- `ui/common/DropdownComponents.kt` — settings pickers now a tappable row + bottom sheet (was stock outlined ExposedDropdownMenu).

## Verified working on device
Onboarding, permissions flow, recordings list as home, contact resolution, playback (Media3, .ogg), waveform seek, speed 0.5–2x, star + Starred filter, retention dropdown (Never / N days / storage cap), settings persist across reinstall.

## NOT done — the main job left
Owner wants it to look like a **native iOS app, not beige**. Current theme is warm-cream "Ember" — the owner explicitly rejected the beige. Do this:

1. **Palette → iOS system colors** in `ui/theme/Color.kt` + `Theme.kt`:
   light: background `#F2F2F7`, cells `#FFFFFF`, separator `#C6C6C8`, label `#000`, secondary `#3C3C43@60%`; dark: background `#000`, cells `#1C1C1E`, secondary `#2C2C2E`, separator `#38383A`. Keep ONE accent (iOS orange `#FF9500`, darker variant for text on white).
2. **`ui/common/AppBackground.kt`** — drop the glow/grain; plain grouped background.
3. **`AppShapes`** in `Theme.kt` → 10/12/16/20dp (current 14–32 is blobby).
4. **`SettingsSection`** (`SettingsScreen.kt` ~L1108) → iOS inset-grouped: white card, no elevation, hairline dividers inset 16dp, uppercase 13sp gray section headers.
5. **Recordings top bar** (`RecordingsScreen.kt`) — the dark charcoal bar is not Apple; use large-title on the grouped background.
6. **`Type.kt`** — drop Space Grotesk for display; Manrope heavy/tight-tracked for titles reads closest to SF Pro.
7. **`PermissionsScreen.kt`** — pink "Required" cards → neutral cell + small red status text.
8. Then: rebuild, screenshot every screen (`adb exec-out screencap -p > x.png`), light + dark + font scale up.

Also outstanding: test in-call overlay + recording notification with a real call; share sheet (`.ogg`/Opus may not appear as a target in ChatGPT/Claude/Gemini — may need AAC transcode or codec default change); update PR #1 "Known limitations" section.

## Gotchas
- `adb shell input tap` uses device pixels (1080×2404). Screenshots render at 899×2000 — multiply by 1.2. Use `uiautomator dump` bounds, don't eyeball.
- Not committed: `local.properties`, `gradlew*`, `gradle-wrapper.jar` (gitignored, correct).
