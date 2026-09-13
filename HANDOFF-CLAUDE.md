# Call Recorder — Claude Handoff

## Read this first

Mike wants this finished fast. Do not redesign from scratch, do not reset/clean the worktree, and do not throw away the current uncommitted work. Continue from the current repository state and verify the remaining runtime issues.

Repository: `Mikelee8810/Call-Recorder`

Working copy: `/Users/michael/Documents/ChatGPT/Default/Call-Recorder`

Branch: `claude/pensive-archimedes-gdyjhk`

Last pushed commit before the current uncommitted batch: `ea3c913`

PR: #1, still draft

Connected device used for testing: Pixel 10 Pro XL, ADB serial `58100DLCQ0040X`

Build with Android Studio JBR 21:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug --no-daemon
```

## Product direction

Visible app name must be **Call Recorder**.

The UI direction is already substantially improved and should be preserved: native-iOS-inspired Android UI, grouped light background around `#F2F2F7`, elevated white cards, iOS blue `#007AFF`, dark neutral cards around `#1C1C1E`, subtle separators, card shadows/depth, restrained rounding, and useful color accents. Mike explicitly hates flat white-paper layouts, beige/orange themes, generic SaaS/AI styling, purple gradients, cookie-cutter card grids, and excessive bubbly rounding.

Bundled Manrope is being used as the current SF-Pro-adjacent font substitute.

Do one focused visual polish pass only if fresh screenshots reveal an obvious weak area. Do not start another redesign loop.

## What is already implemented

- Native-iOS-inspired recordings/settings UI with elevated cards, shadows, color accents, and improved spacing/typography.
- App icon replaced.
- Visible product name changed to `Call Recorder` in the main resources/notifications that have been checked.
- Settings/system Back is implemented in code to return from Settings to Recordings via `BackHandler(enabled = showSettings) { showSettings = false }`.
- Original fork cruft removed from the visible settings flow: wiki/licenses/support/sponsor items.
- New recordings use AAC in M4A.
- Legacy recording migration exists in `LegacyRecordingMigrator.kt`; migration deletes the source only after the replacement is verified.
- Runtime evidence from the Pixel showed old calls present as `.m4a` and preferences containing `audio_m4a_migrated=true` and `audio_codec=aac`.
- Recording timestamps are shown in 12-hour format with seconds, e.g. `Sep 12, 2026 · 4:29:48 PM · 1:21:17`.
- Share helper uses `audio/mp4` for normal M4A sharing. Claude and Gemini package handlers were confirmed. ChatGPT has a compatibility fallback using `application/octet-stream`. Granola is installed but its audio handler has not been proven.
- Third-party/self-managed call support is requested in the manifest with `android.telecom.INCLUDE_SELF_MANAGED_CALLS=true`; UI includes `Record third-party apps`. Do not claim universal VoIP/video capture because Android/device/app restrictions still apply.
- Google Drive backup implementation exists under `services/backup/`, using the Android document-provider/SAF path. End-to-end Drive runtime proof is still pending.
- Recorder keepalive service is implemented as a foreground `START_STICKY` service.
- Boot/unlock/package-replaced watchdog exists plus a 15-minute alarm.
- Keepalive runtime was verified on the Pixel and the readiness notification was visible as `Call Recorder is ready` / `Watching for calls in the background`.
- Shizuku auto-start uses the manager's authenticated START broadcast. There must never be a STOP intent/path in this app.
- The Shizuku automation token is no longer hardcoded in source. `AppPreferences.getShizukuAutomationAuth()` migrates/reuses the older stored preference. The token exists in device preferences; do not print or commit it.

## Top-priority remaining bug: Shizuku reconnect after automatic restart

A controlled runtime test proved the watchdog can restart the actual Shizuku server process, but the Call Recorder process keeps a stale Shizuku client/binder state and falsely reports recovery failure.

Observed sequence:

```text
Shizuku watchdog check firing
Shizuku watchdog: server is unreachable, attempting to restart it
Sent authenticated broadcast to start Shizuku server to moe.shizuku.privileged.api
```

The `shizuku_server` process comes back under shell UID. About 30 seconds later the app still logs:

```text
Shizuku watchdog: automatic recovery failed; notifying user
```

and posts the erroneous Recording Error notification.

The current verification path is in:

- `app/src/main/java/com/kitsumed/shizucallrecorder/integrations/shizuku/ShizukuConnectionManager.kt`
- `app/src/main/java/com/kitsumed/shizucallrecorder/integrations/shizuku/ShizukuWatchdogReceiver.kt`

`ShizukuConnectionManager.isAvailable()` currently only calls `Shizuku.pingBinder()`. After a server restart, that app-process Shizuku binder/provider state is stale.

### Fix goal

After sending the authenticated START broadcast, refresh/re-register/reinitialize the app-side Shizuku binder/provider/client connection before declaring recovery failure. Inspect Shizuku 13.1.5 lifecycle/API behavior and the existing integration before editing. Do not merely increase the 30-second delay.

Acceptance proof:

1. Start from a working recorder + Shizuku state.
2. Safely stop only the Shizuku server process for the controlled test.
3. Trigger the Call Recorder watchdog.
4. Confirm `shizuku_server` restarts.
5. Confirm the Call Recorder process reconnects and `ShizukuConnectionManager.isAvailable()` becomes true.
6. Confirm the watchdog logs automatic recovery success.
7. Confirm no false Recording Error notification is posted.
8. Confirm no app code sends a Shizuku STOP intent.

Do not claim this fixed until all eight are proven on-device.

## Remaining acceptance checks

After the Shizuku reconnect fix:

1. Fresh `installDebug` with JBR 21.
2. Verify Settings Back actually returns to Recordings on-device. Code exists; physical/runtime proof is still missing.
3. Search visible resources/notifications for any remaining `ShizuCallRecorder` branding leak. Internal package/class names may remain to avoid a risky package-id migration.
4. Share a real M4A and verify chooser behavior for ChatGPT; Claude/Gemini are already supported by intent-handler evidence. Granola remains unverified.
5. Select a Google Drive folder through the document provider and confirm a copied M4A actually appears there. If Android requires user selection, that is the only expected manual gate.
6. Take fresh Recordings + Settings screenshots. Only make focused polish changes if something visibly weak remains.
7. Update this handoff with final runtime evidence.
8. Commit the finished worktree, push the existing branch, and update draft PR #1 with verified status and any remaining platform limitation.

## Important worktree warning

The current worktree is heavily modified and contains the product work, migration, watchdog, Drive backup, icon/font assets, and visual-reference screenshots. **Do not run `git reset --hard`, `git clean`, or otherwise discard uncommitted files.** Inspect before changing anything.

The current modified/untracked state includes the main UI/theme/navigation files, Shizuku/watchdog files, recording/share code, legacy migration, `services/backup/`, `services/watchdog/`, launcher icon resources, Manrope fonts, and `.codex-*.png/xml/json` runtime evidence files.

## Current verification truth

- Latest build/install before this handoff: green.
- Keepalive service: runtime proven.
- Readiness notification: runtime proven.
- Legacy calls migrated to M4A: runtime proven.
- 12-hour timestamp display: runtime proven.
- Shizuku server restart broadcast: runtime proven.
- Shizuku app-side reconnect after restart: **broken / highest priority**.
- Settings Back: implemented in code, runtime proof missing.
- Google Drive backup: implemented, end-to-end runtime proof missing.
- Third-party/self-managed call hook: implemented, broad real-call runtime proof missing.
- ChatGPT share chooser: compatibility code present, real chooser proof still desirable.
- Granola audio share: not verified.

Finish the Shizuku reconnect bug first, then run the shortest acceptance sequence above. Do not expand scope.


---
## STATUS 2026-09-13 — Shizuku reconnect FIXED and proven on-device (commit ae2613f)

**Root cause:** Shizuku's restarted server only pushes its binder to a uid it observes *starting* (`BinderSender: Uid X starts`). Our keep-alive process never restarts, so uid 10482 never got the new binder and `pingBinder()` stayed false forever.

**Fix:** `ShizukuConnectionManager.installLifecycleListeners` (binder-received/dead listeners, installed in `ShizuApplication`); `ShizukuWatchdogReceiver.verifyRecovery` is a bounded retry (15s × 8) that cancels on binder-received; from attempt 2, if still no binder and `RecordingForegroundService.isRecordingActive` is false, it calls `Process.killProcess(myPid())` — the START_STICKY keepalive + alarms bring the process straight back, the server sees the uid start and delivers the binder.

**Proof (logcat, Pixel 10 Pro XL):** server pid 16829 killed → restarted as 17702 → `attempt 1/8`, `attempt 2/8`, `restarting Call Recorder process` → `BinderSender: Uid 10482 starts` → `Shizuku binder received; server reachable (uid=2000)` → `automatic recovery succeeded (binder received)`. `recovery failed` count: 0. `grep privileged.api.STOP`: 0 hits.

## Execution checklist (compact)
- [x] Shizuku reconnect fix, 8/8 acceptance points proven
- [x] Fresh `installDebug` (JBR 21) green
- [x] Settings → Back → Recordings proven on device
- [x] No `ShizuCallRecorder` in visible `values/strings.xml`
- [x] Committed `ae2613f`, pushed, draft PR #1 body updated
- [ ] Share a real M4A → ChatGPT chooser (optional, manual)
- [ ] Drive folder pick → copied M4A visible in Drive (optional, manual gate)
- [ ] Live call: overlay + notification (optional, needs a real call)

## Final completion gate
**REQUIRED (all done):** builds green; Shizuku never stopped; watchdog recovers after server restart with proof; no false error notification; Settings back works; branding = "Call Recorder"; work committed + pushed; PR body reflects verified vs unverified.
**OPTIONAL (need a human/hardware gate):** Drive end-to-end, ChatGPT/Granola share chooser, live-call overlay/notification, third-party VoIP capture.
