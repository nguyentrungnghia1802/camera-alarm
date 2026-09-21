# Camera Alarm Release Hardening Verification

## Phase 0 Baseline — 2026-09-21

### Repository

- Branch: `main`
- Baseline commit: `9fbd5fe4f30bf514a0c4e9c83f3dce6771476da3` (`fix(alarm): ensure full-screen alarm activity launches immediately when screen is unlocked`)
- Origin delta: `0 ahead / 0 behind`
- Initial working tree: documentation transition already present (`docs/agent/tasks/task.md` removed, `docs/agent/task.md`, `docs/agent/tasks/task-01.md`, and `docs/review/report.md` added). These inputs were preserved and included in the baseline milestone; no source change existed.
- Recent fixes reviewed with `git log --oneline -10`.

### Android configuration

- Package: `com.personal.cameraalarm`
- minSdk: 26
- compileSdk: 36
- targetSdk: 36
- versionCode: 1
- versionName: 1.0

### Test inventory

- App JVM tests: 18 source files.
- App instrumentation tests: `AlarmSchedulerInstrumentedTest`, `AlarmStopInstrumentedTest`, `DatabaseInstrumentedTest`, `V1IntegrationInstrumentedTest`.
- Fake Camera JVM tests: `FakeNotificationPayloadTest`.

### Runtime inventory

- Connected physical device: Samsung `SM-A505F`, serial `R58M5285MBV`.
- Available AVDs:
  - `CameraAlarm_API_31`
  - `CameraAlarm_API_33`
  - `CameraAlarm_API_34`
  - `Medium_Phone_API_36.1`

### Baseline gates

Command:

```text
.\gradlew.bat test lint assembleDebug :app:assembleRelease :app:assembleAndroidTest
```

Result:

| Gate | Result | Evidence |
|---|---|---|
| Unit tests | PASS (up-to-date baseline) | App and Fake Camera debug/release unit tasks completed |
| Lint | PASS | No lint error |
| Debug build | PASS | `assembleDebug` completed |
| Release build | PASS | `assembleRelease` completed; unsigned APK |
| AndroidTest build | FAIL — expected baseline reproduction | `DatabaseInstrumentedTest.kt:80` unresolved reference `pruneOverRetention` |

The AndroidTest failure is ISSUE-01 and is the first Phase 1 implementation task. No instrumentation/device result is promoted from this baseline because the test APK cannot compile.

## Verification Log

Further phase results are appended below only after the corresponding command actually runs.

## P1.1 — Instrumentation database gate

- Retention contract fixed at 100 newest events, maximum age 3 days, and at most 10 suppressed/ignored events.
- Removed the stale instrumentation dependency on `pruneOverRetention`; tests exercise the production `pruneRetention` transaction.
- Added on-device coverage for age pruning, maximum count, suppressed cap, and pagination after pruning.
- `\.\gradlew.bat :app:assembleAndroidTest test`: PASS.
- `\.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.personal.cameraalarm.DatabaseInstrumentedTest`: PASS, 4/4 tests on Samsung SM-A505F / Android 11.
- Direct AndroidJUnitRunner execution on `CameraAlarm_API_31` (`emulator-5554`): PASS, 4/4 tests.

## P1.2 — Wrong-package privacy

- `IGNORED_WRONG_PACKAGE` and `IGNORED_MONITORING_OFF` decisions no longer invoke persistent history.
- Existing rows with those legacy decisions are deleted once during application container initialization.
- Selected-source decisions still persist normally; pipeline scheduling behavior is unchanged.
- Release/debug logging on this path contains decision/package metadata only, not notification title or body.
- `\.\gradlew.bat test :app:assembleAndroidTest lint assembleDebug`: PASS.

## P1.3 — Non-blocking foreground startup

- Added an application-level immutable `AlarmRuntimeConfig` cache with safe cold-start defaults.
- Settings flow updates sound, vibration, and full-screen values atomically; the service snapshots them once per start.
- Removed every `runBlocking` and direct DataStore read from `CameraAlarmService`; `startForeground()` now uses in-memory data only.
- Alarm receiver also uses the cached full-screen preference after starting the service.
- Added monotonic timing markers for receiver fire, service request, foreground completion, and runtime/audio start.
- Added a delayed-settings regression test proving cold reads return immediately and later reads use the loaded values.
- `\.\gradlew.bat test lint assembleDebug :app:assembleAndroidTest`: PASS.

## P1.5 — Test Alarm ownership

- Added a single runtime ownership policy: production Pending/Ringing blocks Test Alarm; production START replaces an active test runtime; Test Alarm never replaces production.
- The active test token is published by `CameraAlarmService` only after foreground promotion succeeds.
- ViewModels use one `TestAlarmController`; they no longer pre-publish tokens or launch an activity before service acceptance.
- Main/Diagnostics disable Test Alarm while production is Pending/Ringing. Settings exposes the same availability state.
- STOP token selection always prefers the active production Ringing token over a test token.
- Unit tests cover Pending/Ringing blocking, test-to-production replacement, rejection in the opposite direction, same-token idempotency, and STOP priority.
- `\.\gradlew.bat test lint assembleDebug :app:assembleAndroidTest`: PASS.

## P1.4 — Notification listener recovery after boot

- Removed the disable/enable component workaround entirely; boot recovery never mutates the listener component state.
- Recovery checks access, preserves an already connected listener, exposes `RECONNECTING`, and performs at most three `requestRebind` attempts with a 750 ms bounded interval.
- Failures and final disconnected state are recorded in runtime diagnostics; no polling loop or watchdog service is used.
- `DefaultBootReconciler` now has an injectable production dependency boundary and is tested directly for stale Ringing cleanup, test-token cleanup, recovery invocation, and history recording.
- Unit tests cover access denied, already connected, delayed connection, repeated request failure/no callback, and stale boot state.
- `\.\gradlew.bat test lint assembleDebug :app:assembleAndroidTest`: PASS.

## P1.6 — Full regression gate

Date: 2026-09-21. Commit candidate before the P1.6 milestone commit: `a512e07` plus the consolidated task plan and test-only STOP action selection fix.

- Root cause reproduced in `AlarmStopInstrumentedTest`: production exposes both `View Camera` and `Stop Alarm`, but the test assumed exactly one notification action via `actions.single()`.
- The test now selects STOP by the localized `btn_stop_alarm` label. Production notification/runtime code was not changed.
- `.\gradlew.bat test --rerun-tasks lint assembleDebug :app:assembleRelease :app:assembleAndroidTest`: PASS; 211 tasks executed, no unit-test failure, no lint error, debug/release APK and AndroidTest APK built.
- `.\gradlew.bat connectedDebugAndroidTest` with `ANDROID_SERIAL=emulator-5554`: PASS, 10/10 app instrumentation tests on `CameraAlarm_API_31` / Android 12 (API 31).
- Connected physical Samsung `SM-A505F` was detected but was not used for this P1.6 emulator gate; Samsung certification belongs to Phase 6 after Rule V2 and Settings V2.

## Phase 2 — Rule V2 data contract prerequisite

Date: 2026-09-21.

- Enabled Room schema export and checked in schema versions 1 and 2; application startup registers only the explicit `MIGRATION_1_2` (no destructive fallback).
- Schema 2 adds a unique priority index. Migration orders legacy rows by the previous deterministic evaluation order (`priority`, `createdAtEpochMs`, `id`) and assigns unique priorities `1..N` without deleting overflow rows.
- The existing version-1 schema already persisted each rule's `sourcePackage`, keywords, ANY/ALL mode, creation and update timestamps; migration preserves those fields verbatim.
- Repository writes enforce at most three rules for normal data, priorities 1–3, atomic occupied-priority swap, stable `createdAtEpochMs` on edit, and independent delete without renumbering.
- `.\gradlew.bat :app:assembleAndroidTest test --rerun-tasks`: PASS.
- Filtered API 31 instrumentation: `RuleMigrationInstrumentedTest` plus `RuleRepositoryInstrumentedTest`: PASS, 4/4.
- `.\gradlew.bat test lint assembleDebug`: PASS; no lint error.

## Phase 3 — Rule System V2

Date: 2026-09-21.

- Rule list enforces the normal maximum of three and explains both the limit and legacy overflow state; creation offers blank or the exact Vietnamese suggested template.
- Priority is limited to 1–3 and changing an edited rule to an occupied priority swaps both rows atomically. Delete leaves remaining gaps unchanged and edits preserve `createdAtEpochMs`.
- Keyword entry is now a bounded scrolling list of individual cards. Add/edit/delete uses a dialog; blank and normalized case/whitespace duplicates are rejected. ANY/ALL remains a rule-level matcher and its matching implementation was not changed.
- Rule editor reuses the installed-app picker, including label/package search and app icons. Each rule owns `sourcePackage`; multiple rules may share a package.
- Trigger privacy gate derives allowed packages only from enabled rules. Global source remains a new-rule default and no longer blocks another enabled rule's source.
- Regression coverage includes three different packages, two same-package rules with deterministic priority, disabled-only package privacy, template contract, keyword normalization, max-three enforcement, swaps, stable creation time, delete gaps, and Room migration.
- First full API 31 run exposed an obsolete instrumentation fixture using priority `0`; production now requires `1..3`. The fixture and debug injectors were corrected and the full suite then passed 14/14.
- `.\gradlew.bat test --rerun-tasks lint assembleDebug :app:assembleAndroidTest`: PASS; 184 tasks executed, no lint error.

## Phase 4 — Settings V2

Date: 2026-09-21.

- Official fresh/reset profile is Loud Warning 1 (`alarm_warning_aloud`), cooldown 600 seconds, and a daily custom overnight range 22:30–06:00 with existing start-inclusive/end-exclusive semantics.
- Settings profile version 2 distinguishes an empty fresh store from an existing unversioned store. Missing V1 fields are materialized with their old effective defaults before the new code defaults can change behavior.
- Reset writes the complete official profile in one DataStore edit, refreshes the draft immediately, and resets only coordinator cooldown. It does not access Room or Android permission APIs.
- Settings UI now has the same Basic/Advanced organization in EN and VI; full-screen/reliability sits under Advanced while sound, schedule, delay, cooldown, vibration, and language remain Basic.
- `SettingsDefaultsInstrumentedTest`: PASS 3/3 on API 31, covering fresh defaults, existing-install preservation, and reset persistence across repository recreation.
- The first full instrumentation run exposed two real test regressions: the integration fixture implicitly relied on the old always-active default, and test-alarm STOP dispatch could be delayed by application coroutine scheduling after cold start. The fixture now states its schedule precondition explicitly, while test-alarm STOP (which has no coordinator state to persist) dispatches synchronously from the receiver; the existing STOP instrumentation remains the regression test.
- `\.\gradlew.bat test --rerun-tasks lint assembleDebug :app:assembleAndroidTest`: PASS for all non-device gates. The combined command's first device attempt was interrupted by an ADB daemon restart and reported `No connected devices`; after relaunching the same API 31 AVD, this environmental failure was rerun rather than counted as PASS.
- `\.\gradlew.bat :app:connectedDebugAndroidTest` with `ANDROID_SERIAL=emulator-5554`: PASS, 17/17 on `CameraAlarm_API_31` / Android 12 after the regression fixes.

## Phase 5 — Product update integration gate

Date: 2026-09-21. Clean candidate: `71a9315`.

- Rule regression is covered by matcher/validation/pipeline unit tests plus Room migration/repository instrumentation: 0–3/max-three, unique priority and swap, stable creation time, delete gaps, ANY/ALL, normalized keyword add/edit/delete contract, per-rule/multiple app sources, disabled-source privacy, and the exact template seed.
- Settings regression covers the official fresh profile, legacy effective-value preservation, atomic reset/recreation, persisted settings, and schedule boundaries. Alarm regression covers scheduler identity, pipeline/coordinator cooldown and active hours, ownership/isolation, boot recovery, runtime audio/vibration controller, and notification STOP routing.
- Privacy regression confirms wrong-package and disabled-only package content never reach persistent history. Release-path logs on that gate contain only decision/package metadata, not unrelated notification title/body.
- Manual layout exercise on API 31 used a 360 dp-wide viewport (`720x1280 @ 320 dpi`) with font scale `1.5`. Main and Settings remained scrollable; Basic, Advanced controls, and Reset Defaults were reachable. Emulator size, density, and font scale were restored afterward.
- Required build gate: `test --rerun-tasks` PASS (98 tasks executed); `lint` PASS; `assembleDebug` PASS; `:app:assembleRelease` PASS; `:app:assembleAndroidTest` PASS.
- Available-device instrumentation on the candidate: PASS, 17/17 on API 31. Phase 6 owns cross-API and physical-device certification.

## Phase 6 — Runtime / device certification

Date: 2026-09-21. Candidate: `0d76d41`.

- API 31 / 33 / 34 / 36 instrumentation was run for the Phase 6 candidate and passed after the API 36 fix.
- API 36 initially exposed a real foreground-service/full-screen launch failure. Foreground promotion and STOP dispatch were hardened in `0d76d41`; the full suite then passed. This is retained as defect evidence, not hidden as an environmental retry.
- Samsung A50 physical certification: **NOT VERIFIED**. The device disconnected from ADB before the required background/locked/reboot/real-camera matrix could be run. Earlier database-only instrumentation on this device does not satisfy P6.2.
- Xiaomi implementation: **COMPLETE**. Xiaomi physical validation: **NOT VERIFIED** because no physical Xiaomi device is available.
- Phase 7 production changes must receive affected final-commit instrumentation when an emulator is available; the historical Phase 6 pass is not automatically promoted over later code.

## P7.1 — History contract

- Trigger history carries the Rule V2 match ID for scheduled and suppressed outcomes.
- Fired, stopped, and pending-cancelled lifecycle effects now carry the original `TriggerSnapshot`; `ruleId`, `alarmToken`, source, and notification key do not depend on asynchronous Room query ordering.
- `AlarmHistoryEventFactoryTest` covers the production lifecycle mapper. Trigger pipeline, history repository, reducer/coordinator tests, and AndroidTest assembly passed.
- Wrong-package and monitoring-off notifications remain excluded from persistent history. Retention remains 3 days, at most 100 total rows, and at most 10 suppressed/ignored rows.
- Affected gate: `:app:testDebugUnitTest` filtered to history/pipeline/reducer/coordinator plus `:app:assembleAndroidTest`: PASS.

## P7.2 — Backup / restore

- Android 11-and-earlier `fullBackupContent` and Android 12+ `dataExtractionRules` use a settings-only allowlist.
- `camera_alarm_database` is excluded, preventing notification previews/history from cloud backup or device transfer. Rule rows share that database and are intentionally treated as local-only rather than weakening the privacy boundary.
- `datastore/alarm_runtime_state.preferences_pb` is explicitly excluded. Pending/Ringing state, alarm token, boot/process nonce, notification key/title/preview, and cooldown runtime metadata cannot be restored into a phantom alarm.
- `camera_alarm_settings.preferences_pb` is the only included data. Cloud backup additionally requires client-side encryption capability.

## P7.3 — Battery and OEM guidance

- Removed the direct `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` flow, which had no matching manifest permission and carried store-policy risk. The app now opens the general battery-optimization list and falls back to app details.
- Diagnostics no longer catches settings-launch failures silently. Standard and OEM actions use bounded fallbacks and show a localized snackbar if no settings activity is available.
- Samsung explicit Device Care intent is used only when it resolves; otherwise app details is opened. Xiaomi, Oppo/Realme, Huawei/Honor, and Vivo/iQOO explicit intents retain package-manager checks and defensive fallbacks.
- Guidance never claims that an OEM setting was changed automatically. Samsung/Xiaomi physical behavior remains subject to Phase 6 device evidence.

## P7.4 — Localization and UI

- Replaced hard-coded Rule V2 validation, Source Picker, Main, Settings, Diagnostics, battery, and OEM guidance messages with EN/VI resources.
- Diagnostics clipboard labels now follow the active locale and omit notification text as before.
- `LocalizationInstrumentedTest` loads explicit English and Vietnamese configurations and covers core Rule/Settings strings plus Samsung/Xiaomi guidance.
- Filtered Rule validation/reliability unit tests, debug APK, and AndroidTest APK: PASS. Final locale/layout smoke remains tied to emulator availability.

## P7.5 — Boot receiver and full-screen cleanup

- `BootReceiver` is explicitly non-exported; Android system/privileged-system broadcasts remain eligible while third-party explicit broadcasts cannot invoke reconciliation. A fixed action policy rejects all unrecognized actions.
- Removed the duplicate direct Activity launch from `AlarmReceiver`. `CameraAlarmService` is now the sole owner of full-screen notification and measured fallback launch behavior.
- Preserved the service launch paths that fixed the real API 36 failure in `0d76d41`; cleanup does not revert that validated behavior.
- Removed deprecated screen-bright wake-lock and legacy notification priority. Lock-screen presentation relies on the high-importance alarm channel, full-screen `PendingIntent`, and `AlarmActivity.setShowWhenLocked` / `setTurnScreenOn`.
- API 36 uses `MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS` for the trusted, user-configured alarm `PendingIntent`; API 34/35 retain the compatible legacy mode.

## P7.6 — Test quality and scoped technical debt

- `AlarmHistoryEventFactoryTest` exercises the production lifecycle mapping instead of a logic replica.
- `BootActionPolicyTest` covers accepted/rejected receiver actions; `ManifestSecurityInstrumentedTest` checks the merged receiver exposure; `LocalizationInstrumentedTest` checks actual localized resources and OEM advisors.
- `TestAlarmController` remains the shared start/stop boundary used by Main, Settings, and Diagnostics. No duplicate controller was added.
- `AppContainer` remains the composition root. The policy/history mapping was extracted to a pure production mapper; a wider dependency-injection refactor was intentionally avoided.

## P8.1 — Lint and dependency triage

- `test --rerun-tasks lint assembleDebug :app:assembleAndroidTest`: PASS; 184 tasks executed.
- App lint reports 92 non-blocking warnings: 34 unused legacy resources, 26 plural suggestions, 15 dependency-update notices, 9 ellipsis typography suggestions, 6 dash typography suggestions, 1 densityless icon location, and 1 obsolete min-SDK resource qualifier.
- Behavior/policy/accessibility were prioritized: direct battery-exemption request, silent settings failures, deprecated alarm wake/full-screen APIs, dynamic-language split, and ViewModel context leak were resolved. No warning remains in those categories.
- AndroidX/Room/coroutines upgrades are intentionally deferred to a separate batch because they cross migration, Compose, and platform boundaries; no upgrade is required to close this release gate.
- Release identity advanced to versionCode `2`, versionName `1.1.0`.

## Final production-candidate device matrix

Date: 2026-09-21. Production candidate: `c16545666ebc713bb8f5f2416f531af807da423b`.

All four AVDs used the final debug APK produced from the candidate. Each fresh target was granted exact-alarm access before the run; no test was skipped.

| Target | Android | Instrumentation result |
|---|---:|---|
| `CameraAlarm_API_31` | 12 / API 31 | PASS, 19/19 |
| `CameraAlarm_API_33` | 13 / API 33 | PASS, 19/19 |
| `CameraAlarm_API_34` | 14 / API 34 | PASS, 19/19 |
| `Medium_Phone_API_36.1` | 16 / API 36 | PASS, 19/19 |

This rerun covers the affected history, manifest/receiver, localization, alarm scheduling, full-screen/STOP, Room, Rule V2, Settings V2, and integration contracts after all Phase 7 production changes. It does not replace physical OEM evidence.

- Samsung A50 (`SM-A505F`): **NOT VERIFIED**; no longer connected to ADB.
- Xiaomi implementation: **COMPLETE**; Xiaomi physical validation: **NOT VERIFIED** because no device is available.

## Final clean verification

Date: 2026-09-21. Candidate: `c16545666ebc713bb8f5f2416f531af807da423b`.

| Command | Result |
|---|---|
| `.\gradlew.bat clean` | PASS after stopping the Gradle daemon; the first attempt was blocked by an open `fake-camera` lint-cache JAR and is retained as an environmental retry, not hidden |
| `.\gradlew.bat test --rerun-tasks` | PASS; 123 tests per debug/release variant, 0 failure/error/skip; 98 Gradle tasks executed |
| `.\gradlew.bat lint` | PASS; 0 errors, 92 triaged non-blocking warnings |
| `.\gradlew.bat assembleDebug` | PASS |
| `.\gradlew.bat :app:assembleRelease` | PASS |
| `.\gradlew.bat :app:assembleAndroidTest` | PASS |

Lint warning inventory remained: 34 `UnusedResources`, 26 `PluralsCandidate`, 15 `GradleDependency`, 9 `TypographyEllipsis`, 6 `TypographyDashes`, 1 `IconLocation`, and 1 `ObsoleteSdkInt`. AndroidTest compilation also reports the already-known Room `MigrationTestHelper` deprecation warning; no build or runtime gate failed.

## P8.2 — Signed release verification

- Artifact: `app/build/outputs/apk/release/camera-alarm-1.1.0-signed.apk` (generated build output, intentionally not committed).
- Size: 13,655,679 bytes.
- APK signatures: v2 PASS, v3 PASS; one RSA-4096 signer.
- Signer certificate SHA-256: `E6F06F695B13D98150DBA8A718B923F54D93DDD47901F47C64F27B9586F93900`.
- Artifact SHA-256: `5487B9A063748B6BDE96858D7865DAAB69C4A0F0190A54FD2096C71B9D98E6C9`.
- Signing material and DPAPI-protected secret are outside the repository in the current Windows user profile.
- API 36 fresh install, versionCode `2`, versionName `1.1.0`, cold launch, and process start: PASS.
- Signed runtime smoke: Test Alarm opened `AlarmActivity`; `CameraAlarmService` was foreground with an alarm-category notification and two actions. STOP returned to `MainActivity`, removed the service, and removed the alarm notification: PASS.
- Upgrade: an APK from `0d76d41` (versionCode `1`, versionName `1.0`) was signed with the same release key and installed first. English was selected and persisted. `adb install -r` to the final signed APK succeeded, retained `firstInstallTime`, reported versionCode `2` / versionName `1.1.0`, and retained English: PASS.
- Real camera-application notification against the signed build: **NOT VERIFIED**. Test Alarm is explicitly not treated as equivalent to a camera-source trigger.

## Final localization and backup probes

- Signed API 36 fresh state loaded Vietnamese. English selected through Settings persisted across process recreation and the signed upgrade path.
- Font scale `1.5` and dark/light-mode interaction remained reachable in the UI hierarchy. The headless AVD produced black `screencap` frames, so final pixel-level screenshot review is **NOT VERIFIED**; Phase 5 retains the earlier 360 dp/font-1.5 manual layout evidence.
- Backup rules remain a strict settings-only allowlist; Room/history and `alarm_runtime_state` are outside both cloud and device-transfer sets.
- Android Backup Manager was enabled temporarily with local transport. The transport rejected the release package during preflight (`ERROR_PREFLIGHT`) and created no restorable application dataset. No data was cleared and no restore was attempted. End-to-end restore is therefore **NOT VERIFIED**, while the manifest/rule/build policy gate remains PASS. The emulator backup transport and enabled state were restored to their originals afterward.
