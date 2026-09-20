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

## P1.4 — Notification listener recovery after boot

- Removed the disable/enable component workaround entirely; boot recovery never mutates the listener component state.
- Recovery checks access, preserves an already connected listener, exposes `RECONNECTING`, and performs at most three `requestRebind` attempts with a 750 ms bounded interval.
- Failures and final disconnected state are recorded in runtime diagnostics; no polling loop or watchdog service is used.
- `DefaultBootReconciler` now has an injectable production dependency boundary and is tested directly for stale Ringing cleanup, test-token cleanup, recovery invocation, and history recording.
- Unit tests cover access denied, already connected, delayed connection, repeated request failure/no callback, and stale boot state.
- `\.\gradlew.bat test lint assembleDebug :app:assembleAndroidTest`: PASS.
