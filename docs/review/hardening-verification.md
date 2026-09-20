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
