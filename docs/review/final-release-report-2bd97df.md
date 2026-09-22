# Camera Alarm — FINAL VERIFICATION 2026-09-22

**Verdict: NOT VERIFIED / NOT READY FOR RELEASE**

Audited production/test commit: `2bd97df87fc5e1275582fa0d962eb3309bb7eab0`, branch `main`, versionCode `2`, versionName `1.1.0`, minSdk 26, target/compile SDK 36. This report supersedes the release-readiness assessment for `c165456`; old results remain historical in `hardening-verification.md` and do not certify newer code.

Verification-only scope: no production code, test, schema, resource, dependency or build configuration changed. No tests were weakened or fixed. Reproduced defects remain open. No push. Initial Git state: main ahead 1, only pre-existing untracked `phone_now.png`.

## 1. Build / automated gates

Run from the repository root in this exact sequence, using Windows wrapper:

| Command | Result |
|---|---|
| `.\gradlew.bat clean` | PASS, first attempt, 2 tasks executed |
| `.\gradlew.bat test --rerun-tasks` | PASS, 98 tasks executed; app 135 + fake-camera 5 tests per debug/release variant; 280 executions, 0 failure/error/skip |
| `.\gradlew.bat lint` | PASS; app 0 errors, 92 warnings |
| `.\gradlew.bat assembleDebug` | PASS |
| `.\gradlew.bat :app:assembleRelease` | PASS; unsigned artifact |
| `.\gradlew.bat :app:assembleAndroidTest` | PASS |
| `ANDROID_SERIAL=emulator-5554 .\gradlew.bat :app:connectedDebugAndroidTest` | Per-API outcomes below; not an unconditional PASS |

Warnings retained: 92 lint findings; existing Room MigrationTestHelper deprecation/nullability compilation warnings; native libraries packaged without stripping when unavailable. None was suppressed in this round.

Logs and full test artifacts: `C:/Windows/Temp/camera-alarm-final-2bd97df-20260922/` (`01-clean.log` through `07-instrumentation-api34.log`, `unit-counts.txt`, `instrumentation-api*/`, `matrix.ps1`). Temp files are not durable release attachments; essential failures, timestamps and evidence are recorded below and selected fixtures/screenshots are retained under [evidence/final-2bd97df](evidence/final-2bd97df/).

## 2. API / device results

ADB was checked at the start and repeatedly during verification. It returned only `emulator-5554`; **no Samsung A50 and no Xiaomi**. A connection request was sent to the user; physical results are not inferred from old database-only tests or emulators.

| Target | Model / Android / API | Current-round result |
|---|---|---|
| CameraAlarm_API_34 | sdk_gphone64_x86_64 / Android 14 / API 34 | PASS: 26 XML cases = 25 pass + 1 intentional preparation-fixture skip |
| CameraAlarm_API_31 | sdk_gphone64_x86_64 / Android 12 / API 31 | FAIL: 24 pass, 1 STOP timeout, 1 intentional skip; isolated cold test FAIL again; identical warm control PASS (2.331s) |
| CameraAlarm_API_33 | sdk_gphone64_x86_64 / Android 13 / API 33 | PASS: 25 pass + 1 intentional skip |
| Medium_Phone_API_36.1 | sdk_gphone64_x86_64 / Android 16 / API 36 | PASS: 25 pass + 1 intentional skip |
| Samsung A50 | Historical docs name SM-A505F; live Android/API/One UI unavailable | NOT VERIFIED — not connected/authorized in ADB |
| Xiaomi | No hardware | Xiaomi physical validation: NOT VERIFIED |

AVDs ran serially (only one emulator at a time). API31/33/36 launches used `-no-window -no-audio -no-snapshot -memory 1536 -cores 2`; resource/timing limits are part of the evidence boundary. Exact-alarm access was granted before instrumentation; POST_NOTIFICATIONS on API33+, FSI app-op on API34+. The preparation fixture intentionally skips in normal suites. It is not a missed production test.

## 3. Core alarm flow

**PASS for bounded API34 synthetic-source E2E, not physical camera certification.** Installed the unchanged `fake-camera` APK and changed the existing test rule through the actual Rule editor: source `com.personal.fakecamera` (Front Door Camera), ANY keyword `Human`, priority 1. Monitoring ON, delay 3s, always active, cooldown 0, vibration ON, fullscreen ON. Settings and rule source were changed via UI; no app/test source was changed.

- Fake Camera UI posted two real Android notifications, IDs 1001/1002. Production NotificationListener → rule → coordinator → AlarmManager → AlarmReceiver → FGS → MediaPlayer/vibrator executed.
- Token `5e48f4af-8ee7-4382-bbe6-6f9f91a26edf`: received `1790036839415`, matched `1790036839444`, Pending `1790036839511`, requested deadline `1790036842462`, OS registered `1790036839517`, receiver `1790036844523`, Ringing `1790036844575`, audio `1790036844673` (epoch ms).
- Second notification was suppressed while the first was legitimately Pending; no second alarm or deadline extension. AlarmManager registration → delivery ≈5s, consistent with previously measured emulator min_futurity. No false watchdog retry.
- Android audio service: MediaPlayer piid 183, UID/PID 10197/6192, `state:started`, `USAGE_ALARM`. Vibrator service: repeating 700ms/300ms effect, status running, caller Camera Alarm. These are OS-level observations, not human hearing/touch on hardware.
- Notification **View Camera** action: AlarmActivity created, STOP receiver at `1790036889528`, runtime stopped at `1790036889623`; top resumed app was `com.personal.fakecamera/.MainActivity`. Alarm service removed, no active MediaPlayer, current vibration null.
- Second alert, token `f4954d89-b355-4919-9130-968676ac8859`, was scheduled then screen put to sleep. Receiver fired, audio started at `1790036922289`; AlarmActivity CREATED at `1790036922486` and was top resumed. STOP from AlarmActivity removed audio/service/vibration. Repeated STOP safety also has unchanged unit/instrumentation coverage, with the API31 timing failure below retained.
- No permanent Pending/Ringing or duplicate audio was observed in these successful paths. This is not a proof over all timing/OEM cases.

Unlocked-screen behavior: system logged BAL_BLOCK for direct background activity attempts and showed an expanded alarm notification; when screen was off the full-screen activity appeared. This is consistent with the [Android full-screen intent behavior matrix](https://source.android.com/docs/core/permissions/fsi-limits): unlocked heads-up versus locked/off-screen FSI. It is not classified as a product defect merely because `FULLSCREEN_INTENT_SENT` did not mean an unlocked activity was resumed.

Cooldown 600s and schedule boundaries are verified by unchanged tests/default contract. This runtime scenario used cooldown 0 and always-active deliberately; it does not certify a physical 600-second waiting cycle or real overnight operation.

## 4. Rules V2

| Contract | Result / evidence |
|---|---|
| Max 3, unique 1..3, occupied-priority swap, delete gaps, stable createdAt | PASS in RuleRepositoryInstrumentedTest / RuleMigrationInstrumentedTest |
| ANY / ALL; per-rule package; multiple sources; disabled-rule filtering | PASS in CoreTest, TriggerPipelineTest, Phase3IntegrationTest |
| Vietnamese template / normalized keywords / validation | PASS in RuleValidationTest |
| Picker/search | PASS API34 UI smoke: search `Fake` resolves Front Door Camera by package; selected source persisted to rule |
| Edit keyword / save / rule scroll | PASS API34 UI operation: `reboot probe` → `Human`; saved rule used by real listener; dialog language FAIL FV-02 |
| Add/delete keywords, long chip scrolling and templates across all UI sizes | Logic/resource tests PASS; full physical UI matrix NOT VERIFIED |
| Restart/persistence | Repository/DB reopen tests PASS; rule survived process restart caused by exact-permission revoke and still matched |

Stored fixture is preserved in [trigger_rules.json](evidence/final-2bd97df/trigger_rules.json). No physical A50 Rule editor certification is claimed.

## 5. Settings

**PASS for automated data contract**: fresh/reset sound `alarm_warning_aloud` (Âm cảnh báo lớn 1), cooldown 600000ms, daily 22:30 inclusive → 06:00 exclusive, full-screen ON, Vietnamese default. SettingsDefaultsInstrumentedTest verifies fresh state, unversioned legacy effective defaults, V2 false fullscreen preserved, stale draft not disabling monitoring, and reset across repository recreation.

`SettingsRepository.resetToDefaults/updateAll` writes only settings DataStore; `SettingsViewModel.resetDefaults` resets coordinator cooldown. It does not call Rule/History DAO or revoke permissions. This is code + settings-test evidence, not a complete A50 reset-with-live-data UI certification. Existing-user preservation is PASS under these tests; current signed APK upgrade remains NOT VERIFIED.

## 6. Samsung A50 physical certification

**NOT VERIFIED for every physical item requested**: foreground/background, screen locked/off, swipe-away, process kill, Doze, reboot/PIN/no-manual-app-open, real camera notification, speaker/audio, physical vibration, full-screen, STOP/Open Camera, repeated events/cooldown, listener reconnect and the old Pending symptom.

Reason: no Samsung transport in current `adb devices -l`. No device model/API/Android/One UI reading could be obtained live. Historical `SM-A505F / Android 11` is not presented as a current measurement. Synthetic notifications and headless emulator audio states do not certify a real camera/cloud/OEM path.

## 7. Permissions / degradation

API34 probes on unchanged debug build:

| Probe | Result |
|---|---|
| Exact alarm app-op deny, then matched Fake Camera notification | PASS bounded failure: `PENDING_RETIRED ... Exact alarm permission missing` and `SCHEDULE_FAILED`; no Pending orphan/audio/crash |
| Exact regrant | App receives permission-change reconciliation; monitoring/rule survive process restart |
| Notification Access disallow | No notification callback during five-second probe; truthful `BOOT_RECOVERY_INCOMPLETE listener=DISCONNECTED result=ACCESS_DENIED` |
| Notification Access allow again | Listener reconnects and next matching notification is processed |
| POST_NOTIFICATIONS revoked + USE_FULL_SCREEN_INTENT denied | Audio/FGS still start (token `5684d202-0258-44af-8ee7-e33f74875459`); no full-screen request logged; Main shows missing notification permission and exposes STOP; STOP used successfully |
| Restore permissions | Performed after probe |
| Battery optimization/OEM fallback | Readiness/advisor tests and defensive code review; physical intent flows and real battery policy NOT VERIFIED |

Commands: `cmd appops set ... SCHEDULE_EXACT_ALARM deny/allow`, `cmd notification disallow_listener/allow_listener <component>`, `pm revoke/grant ... android.permission.POST_NOTIFICATIONS`, `cmd appops set ... USE_FULL_SCREEN_INTENT deny/allow`. No app or test was edited for permission failures.

## 8. History / privacy

- PASS normal lifecycle correlation: copied debug fixture DB contains SCHEDULED, ALARM_FIRED and ALARM_STOPPED with the same matched `ruleId=reboot-e2e` and token. See [alert_events.json](evidence/final-2bd97df/alert_events.json).
- PASS wrong-package persistence for the probe: the only enabled rule source was `com.personal.fakecamera`; shell canary title/body/tag do not appear in copied history. Unchanged pipeline/privacy tests also pass.
- PASS pagination/retention automated gates: actual Room tests cover 3-day/100-row/10-suppressed retention and paging. API34 UI also navigated from page 1/2 (1–25 of 29) to page 2/2 (26–29 of 29), then confirmed Clear History on this synthetic-only emulator dataset and observed the empty state. Physical clear-history interaction remains uncertified.
- **FAIL logging privacy FV-01**: unrelated notification tag is emitted before filtering, including release bytecode.
- **FAIL failure-event attribution FV-03**: permission failure additionally emits an uncorrelated row under the legacy global source, different from the matched rule source.

## 9. Boot / recovery

Current-round automated coverage: PendingDeliveryInstrumentedTest deliberately removes primary OS delivery, observes autonomous recovery to a new token and rejects old callback; AlarmRecoveryTest covers bounded retry/exhaustion, monotonic deadline, process-hydration crash window, duplicate recovery, stale STOP, concurrent boot/notification/receiver, and FGS dispatch exception. BootStateStoreInstrumentedTest covers real DataStore boot invalidation and same-boot atomicity. All these affected tests pass in the completed suites unless explicitly listed as failed in the matrix.

**Current-round API34 reboot/PIN E2E PASS, bounded synthetic source:** boot13, before PIN `RUNNING_LOCKED` and no Camera Alarm PID; after PIN `RUNNING_UNLOCKED`, Nexus Launcher top resumed. No Camera Alarm activity or instrumentation was started before the trigger. BOOT_RECONCILED at `1790037984849`: monitoring=true, listener=CONNECTED, exact=true, config=true, reconciled=Idle. Existing rule/settings survived reboot. Fake Camera UI then posted Human notifications.

Token `62b25d3f-2a9f-42e2-882d-ad0a27546e2e`: received `1790038021468`, Pending `1790038021654`, OS registered `1790038021693`, deadline `1790038024594`, receiver `1790038026698`, Ringing `1790038026766`, audio `1790038026850`. Registration → receiver **5005ms**, no recovery/retry (attempt=0). Audio dump: MediaPlayer piid95, UID/PID10197/1891, state:started, USAGE_ALARM. Same-ID immediate updates were correctly IGNORED_DUPLICATE; a new ID1001 at `1790038041886` was SUPPRESSED_RINGING at `1790038041944`, not stale Pending. No second playback was observed. Main was opened only AFTER that E2E assertion to use STOP; runtime stopped at `1790038087194` and audio/vibration/service dumps confirm cleanup. POST_NOTIFICATIONS was false in this reboot probe, so it does not certify post-reboot notification actions/full-screen; those were exercised earlier with permission granted. Restored POST/FSI/exact access afterward and cleared the emulator-only PIN.

Setup boundary retained: boot11 truthfully reported BOOT_RECOVERY_INCOMPLETE exact=false. `cmd appops get` showed default after reboot despite earlier transient shell grants. Before boot13, `cmd appops set --uid ... SCHEDULE_EXACT_ALARM allow`, package app-op allow, and `cmd appops write-settings` persisted the grant; post-reboot app-op was allow. Boot11 is a permission/setup failure, not a successful alarm E2E. Two BOOT_RECONCILED diagnostics in boot13 correspond to reconciliation continuations; they did not produce two alarms. Evidence: retained `reboot11-*`, `reboot13-*`, and runtime command index.

Same audited commit also has the prior real API34 reboot/PIN/no-app-open and process-kill evidence in root [report.md](../../report.md), boot8→9, token `1be5434a-bd1d-4266-921a-fa3c4a8f5cdf`, and kill-Pending token `42b0710a-47ef-4b9c-b84b-d7f7a5eeaa65`. Those artifacts were copied outside build before `clean`. Process-kill evidence is same-commit prior-run evidence, not a new Samsung run.

Manifest uses BOOT_COMPLETED after unlock, package replacement and exact-permission change. USER_UNLOCKED and LOCKED_BOOT_COMPLETED are not registered; no Direct Boot. This is intentional scope, not an unexecuted pretend-PASS receiver. BOOT_RECONCILED is readiness/reconciliation state, not proof audio started. No phantom alarm was observed; universal crash/OS scheduling guarantees are not claimed.

## 10. UI / UX

- API34 normal-size/light screenshot review: Main, Rule editor, picker/search, Settings, notification actions, AlarmActivity reviewed/interacted with. Rule editor scroll and dialogs fit the observed 1080x2400 viewport. This does not prove all font/layout combinations.
- Additional API34 dark/large-font/small-screen smoke: `wm size 720x1280`, density320, font_scale1.5, night=yes. Main, Rules/editor with scrolling to Save, Settings, History pagination and clear-confirm dialog were inspected. No vertical-letter stacking or inaccessible Save was observed; Main uses ellipsis for long labels and History filters scroll horizontally. Retained `ui-small-*.png`/XML. Display/font/night settings were restored. This bounded emulator smoke is not the complete physical/English/AlarmActivity size matrix.
- **FAIL selected-language consistency**: Vietnamese Main/editor/Settings with English keyword dialog, notification actions and AlarmActivity (FV-02). Explicit EN/VI resource tests pass but do not cover this selected-language context propagation.
- A50 Vietnamese/English, light/dark, large font/small screen, overflow/vertical text, dialog/bottom-sheet, History/AlarmActivity matrix: **NOT VERIFIED**.
- Historical UI results from older commits are not promoted to final-code physical PASS.

## 11. Backup / restore and release artifact

Backup policy **PASS by code/artifact configuration review**: legacy fullBackupContent and Android12+ cloud/device-transfer rules include only `datastore/camera_alarm_settings.preferences_pb`. Room (rules/history) and `alarm_runtime_state` are not included. No restored Pending/Ringing/token/nonce should be accepted as backed-up runtime data under this allowlist.

Actual transport restore **NOT VERIFIED**: current API34 `bmgr enabled` returned disabled; `bmgr backupnow com.personal.cameraalarm` returned `Backup is not allowed`, no dataset/restore cycle. No user data was cleared to pretend a restore. A policy inspection is not a no-phantom restore E2E result.

Current artifact: `app/build/outputs/apk/release/app-release-unsigned.apk`.
SHA-256: `4826CB871AEE0D2C2F080D07284DD50D497AFB8F5021428640BF3575B08AAA8B`.
No signed production APK existed in the documented release output directory before clean; build has no release signing config. Historical signed APK/hash/install results belong to `c165456` and are not current certification. Signature, current signed fresh install/upgrade, real camera trigger and signed STOP/Open Camera: **NOT VERIFIED**. No signing secret was accessed and no new release artifact was signed.

`apksigner verify --verbose app/build/outputs/apk/release/app-release-unsigned.apk` returned exit 1, `DOES NOT VERIFY / Missing META-INF/MANIFEST.MF`, confirming that this assembled artifact is unsigned. This expected unsigned result is not reported as a damaged signed release. Output and SHA256 are retained with evidence.

## 12. Bugs discovered — retained, not fixed

### FV-01 — P2: unrelated notification tag is logged in release path

Reproduce: enabled rule source only `com.personal.fakecamera`, then run `adb shell 'cmd notification post -t UnrelatedTitleCanary unrelated-private-tag-996621 "UnrelatedBodyCanary"'`. Camera Alarm logs at epoch `1790036892034`:

```text
NOTIFICATION_RECEIVED ... package=com.android.shell
key=0|com.android.shell|2020|unrelated-private-tag-996621|2000
```

[Retained log](evidence/final-2bd97df/privacy-enabled-wrong-package.log). Also reproduced with monitoring OFF. No title/body leak or history persistence was observed for this wrong-package probe; the confirmed leak is the raw caller-controlled tag inside the notification key, which can carry private identifiers/content.

Evidence: `notification/CameraNotificationListener.kt:36-40` extracts/logs key before pipeline gate; `NotificationExtractor.from` takes `sbn.key`; `alarm/AlarmTrace.record` unconditionally uses Log.i. `apkanalyzer dex code --class com.personal.cameraalarm.notification.CameraNotificationListener app/build/outputs/apk/release/app-release-unsigned.apk` confirms `key=` and AlarmTrace invocation in release dex. Release execution was not faked: runtime reproduction used debug, release applicability is established by code/dex inspection. Proposed fix direction only: filter before logging and omit/hash raw key/tag; add release-path canary coverage.

### FV-02 — P2: app-selected Vietnamese not applied consistently

Reproduce on API34 with system language English and app language Vietnamese: edit a keyword. Parent editor is Vietnamese, dialog shows `Edit keyword / Keyword / Delete / Cancel / Save`. A triggered AlarmActivity and notification actions show `View Camera / Stop Alarm / Triggered at` despite app Vietnamese. History Clear confirmation also shows English over a Vietnamese page, including at large font/dark mode. Screenshots: [keyword dialog](evidence/final-2bd97df/vi-english-dialog.png), [AlarmActivity](evidence/final-2bd97df/vi-alarm-english.png), [history dialog](evidence/final-2bd97df/ui-small-clear-dialog.png).

Evidence: `ui/MainActivity.onCreate` supplies a localized Context/Configuration only to its Compose content; `AlarmActivity.onCreate` uses its normal activity resources and separate MaterialTheme; `CameraAlarmService.promote` uses service getString; `AppContainer.getString` uses application context. Vietnamese strings exist, and LocalizationInstrumentedTest only checks explicit resource configurations. Dialog behavior itself is reproduced; exact Compose dialog propagation internals have not been isolated. Proposed direction only: consistent selected-language handling across activity/service/dialog/message contexts and runtime UI regression coverage.

### FV-03 — P2: failure history loses rule/token and uses the wrong source

Reproduce: global source remains `com.android.shell` from fixture, enabled rule source changed to Fake Camera; deny exact alarm; send matching Fake Camera notification. DB row 9: SCHEDULE_FAILED, source Fake Camera, rule `reboot-e2e`, token `852461a5-eed9-4064-8fc5-07d17001ee09`. Row 10 at `1790036953448`: SCHEDULE_FAILED, source `com.android.shell`, null rule/token, details `Exact alarm permission missing`.

Evidence: `AlarmCoordinator.retire` emits `RecordFailure(reason)` without snapshot; `data/history/AlarmHistoryEventFactory.fromEffect` RecordFailure branch uses fallbackSourcePackage and null correlation fields; AppContainer supplies legacy global source. [Retained DB rows](evidence/final-2bd97df/alert_events.json). Normal fired/stopped mapping is correct. Proposed direction only: carry failure snapshot/recovery token or model a clearly source-neutral diagnostic instead of attributing it to an unrelated global source.

### FV-04 — open verification failure: API31 cold STOP timeout

Unchanged AlarmStopInstrumentedTest failed its 10-second timeout. Logs show test-first AUDIO_STARTED `07:32:18.638`, AlarmActivity RESUMED `07:32:19.324`; test timeout `07:32:30.786`; STOP_RECEIVER_FIRED only at `07:32:34.459`, runtime stopped `07:32:34.461` in the following test's log. Thus the observed delay precedes receiver handling; this is not evidence that STOP handler itself ran for 10 seconds.

Reproduction with the identical isolated test after another API31 cold boot failed again (13.358s total; AUDIO_STARTED `07:41:26.482`, timeout `07:41:38.392`, cleanup stopped runtime at `07:41:38.429`). A subsequent warm control with the same APK/test/permissions passed in 2.331s: STOP_RECEIVER_FIRED `07:41:53.468`, runtime stopped `07:41:53.551`. Command: `adb shell am instrument -w -e class com.personal.cameraalarm.AlarmStopInstrumentedTest com.personal.cameraalarm.test/androidx.test.runner.AndroidJUnitRunner`. Outputs and filtered logcat are retained in `evidence/final-2bd97df/api31-stop-*`; original suite XML is retained too.

Cold OS/test broadcast scheduling versus app causality remains unresolved. The warm PASS does not erase either cold failure or certify the full API31 gate. No timeout, test assertion or implementation was changed.

## 13. Remaining limitations / Git / final verdict

Release gates remain open: Samsung A50 physical/real-camera certification, selected-language/privacy/failure-history findings, API31 timing failure disposition, signed current release, restore transport and physical UI matrix. Xiaomi physical validation: NOT VERIFIED. Emulator audio/vibration states do not prove hardware output. OS grace/recovery is not a hard real-time guarantee under suspended process/storage/JobScheduler restrictions.

Audited final implementation commit remains `2bd97df87fc5e1275582fa0d962eb3309bb7eab0`. This verification changes documentation/evidence only; source and tests remain byte-for-byte at that commit. `phone_now.png` is preserved and excluded. Final Git status is recorded in the completion response; no push.

**NOT VERIFIED / NOT READY FOR RELEASE**
