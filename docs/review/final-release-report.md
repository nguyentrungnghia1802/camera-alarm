# Release hardening closure — 2026-09-22

**Verdict: NOT READY FOR RELEASE.** Code defects FV-01 through FV-04 are fixed; physical Samsung A50 and a real camera/cloud trigger cannot be certified without connected hardware. Emulator/synthetic-source evidence is not promoted to physical PASS.

Production milestone: `1bf4736`; final test-harness/build milestone: `21e7516` on `main`, version 1.1.0 (2). Existing documentation/evidence and `phone_now.png` were preserved. No push, reset, matcher ANY/ALL change or alarm architecture replacement.

## Fixed defects

| Finding | Root cause and fix | Regression |
|---|---|---|
| FV-01 privacy | Listener traced raw notification key before package filtering; key embeds arbitrary tag. Trace now emits package/id only, with independent UUID. Payload/key/tag never enter unrelated-notification trace. | NotificationTracePrivacyTest; existing source-filter persistence tests |
| FV-02 EN/VI | Main-only composition locale left Activity/dialog cached Resources and Service/application lookup on system language. Shared localized Activity resources, recreation on language change preserving saved state, selected-locale message/Service lookup, active notification action refresh. | SelectedLanguageInstrumentedTest: EN/VI dialog, bottom sheet, snackbar, Activity lookup and live notification actions |
| FV-03 failure history | RecordFailure lacked owner snapshot; factory used global source and null correlation. Required TriggerSnapshot flows from retirement/dispatch/reducer into factory, with no global fallback. | Factory lifecycle assertions + permission/schedule/watchdog/dispatch/retry-exhaustion owner tests |
| FV-04 API31 STOP | User STOP PendingIntent entered OS background broadcast queue behind cold-boot work. It was pending at queue #47, with no receiver dispatch. All user STOP broadcasts now request foreground receiver priority. | Cold before FAIL twice / warm before PASS; fixed cold PASS; unchanged 10-second STOP assertion, token ownership tests retained |

API31 measured fixed cold timeline: send 08:23:12.585 → receiver 08:23:12.849 → runtime stopped 08:23:13.047 (264ms to receiver, 462ms to stop). Before fix, send returned in 13–30ms but receiver was never entered within 10s. Main-thread idle and final direct-service cleanup distinguish this from test rendering/receiver/service delay. [Broadcast priority API](https://developer.android.com/reference/android/content/Intent#FLAG_RECEIVER_FOREGROUND).

## Full gates

Executed sequentially after source milestone:

| Gate | Result |
|---|---|
| `./gradlew clean` | PASS |
| `./gradlew test --rerun-tasks` | PASS: app 137 + fake-camera 5, both debug/release = 284 executions, no failure/error/skip |
| `./gradlew lint` | PASS: 0 errors, app 93 warnings and fake-camera 14 warnings; no suppression or warning-free claim |
| `./gradlew assembleDebug` | PASS |
| `./gradlew :app:assembleRelease` | PASS |
| `./gradlew :app:assembleAndroidTest` | PASS |

## API matrix

| API | Final XML result | Failures / errors |
|---|---|---|
| 31 | 27 PASS + 1 intentional fixture skip | 0 |
| 33 | 27 PASS + 1 intentional fixture skip | 0 |
| 34 | 27 PASS + 1 intentional fixture skip | 0 |
| 36 | 27 PASS + 1 intentional fixture skip | 0 |

AVDs are run serially, cold boot without snapshots, 1536MB/2 cores, headless/no-audio. Exact access and applicable notification/full-screen permissions are provisioned. Audio service observations do not certify physical speakers/vibration or OEM delivery guarantees. The explicit RebootScenarioPreparation fixture is intentionally skipped in normal suites.

## Samsung A50 / real camera

**NOT VERIFIED**: A50 briefly appeared during final checks (SM-A505F/API30, existing version1.1.0, listener enabled), then became offline/disconnected before APK/UI inspection. No install, data clear or reboot was performed on the handset. See [late connection evidence](evidence/release-fixes-20260922/a50-late-connection.txt). Background, screen locked/off, real camera notification, reboot + PIN/no Camera Alarm launch, Pending recovery, physical full-screen/audio/vibration, STOP/Open Camera, 600-second cooldown, listener reconnect and the original incorrect Pending symptom all remain unverified on A50. Historical handset results do not certify final code. Xiaomi physical validation: NOT VERIFIED.

## Backup/restore

**PASS for actual API31 local test transport E2E on final source.** Enabled Backup Manager and selected LocalTransport. The initial rejection came from the transport lacking the encryption capability required by production cloud policy. Configured Android's documented local-test `is_encrypted=true`; no application policy was relaxed. Ran backup while the synthetic fixture contained settings, rule/history and a Ringing runtime token; then force-stopped, cleared the disposable emulator's package data and restored dataset 1. Package backup Success; restoreFinished 0.

Immediately after restore and before opening the app, only the settings file existed: database/history/runtime excluded. After launch, settings were byte-identical (SHA256 `539962372cb365fbd390dad604c8678f4db16e937f1cb47ff282edc605d29d76`), runtime initialized to Idle, and old Ringing token/nonce/preview were absent. Service/audio evidence shows no restored alarm. Original transport/test parameter/disabled state restored afterward.

Actual Google cloud, device-to-device and OEM transports: **NOT VERIFIED**. Local simulated encryption capability is not proof of cloud encryption. [Official local backup test procedure](https://developer.android.com/identity/data/testingbackup).

## Signed release

Artifact: `app/build/outputs/apk/release/camera-alarm-1.1.0-signed.apk`, version 1.1.0 (2), `debuggable=false`.

- Existing release certificate SHA256: `E6F06F695B13D98150DBA8A718B923F54D93DDD47901F47C64F27B9586F93900`; APK v2/v3 signature verification PASS.
- Source-milestone tested APK SHA256: `A6FEB6CCBD0163A34ACA3086C6DBBA8A8F7B4410662F27D7A1879C9C8DC8DE2F`.
- Fresh API36 install PASS: target package absence recorded before installation. Release-signed instrumentation ran against the actual non-debuggable release target: Test Alarm/STOP plus EN/VI dialogs/sheet/snackbar/active actions, 3/3 PASS. The test APK is verification-only and is not the distributed artifact.
- Upgrade PASS: historical `0d76d41` versionCode 1 APK built in an isolated temporary archive and signed with the same key; saved English through Settings, then `adb install -r` to versionCode 2. Both dumps report firstInstallTime `2026-09-22 08:59:42`; English persisted in the new UI. Main repository was not checked out/reset.
- Signed listener E2E PASS for **synthetic Fake Camera source**: a real Android notification from `com.personal.fakecamera` matched rule `reboot-e2e`/ANY/Human and reached Pending → Ringing → FGS/audio. Example token `7735fee2-9b9b-4ea0-b77a-64f0088cdd40`: received 09:04:52.272, registered 09:04:52.606, receiver 09:04:57.607, audio 09:04:57.731. No recovery retry. Fixture used 3s delay, always-active and cooldown 0; this is not a physical 600s cooldown/overnight certification.
- Notification STOP PASS for a production token. Open Camera PASS: foreground activity settled on `com.personal.fakecamera/.MainActivity`, alarm service removed and no active alarm MediaPlayer afterward. Settled hierarchy and service/audio dumps support this result.
- Release privacy canaries with monitoring ON and OFF: shell callback confirmed; title/body/tag canaries absent from CameraAlarm logs. Signed DEX helper reads only package/id. Signed VI keyword dialog, notification actions and AlarmActivity have retained screenshots/XML; AlarmActivity shows Phát hiện lúc / Xem Camera / Tắt cảnh báo, and Activity STOP cleans up service/audio; EN/VI automated runtime coverage is above.
- Real camera/vendor/cloud trigger on signed APK: **NOT VERIFIED**. Synthetic listener E2E and Test Alarm do not replace it.


## Evidence and final boundary

Durable evidence: [release-fixes-20260922](evidence/release-fixes-20260922/README.md), including full gate logs, XML counts, cold/warm STOP probes and broadcast dump, backup/restore files and assertions, signature/hash and signed smoke evidence. Signing key and DPAPI secret stay outside the repository.

All code changes are committed on main. Final documentation commit and working-tree status are reported with the delivered artifact. `phone_now.png` is pre-existing and intentionally remains untracked. No push.

Release remains blocked by unavailable Samsung A50 physical certification and real-camera signed trigger verification. Cloud/OEM backup transport and full physical EN/VI/layout/audio matrix are explicit remaining limits. Historical records are linked separately and do not override this current assessment.

Historical audited snapshot and original reproductions are preserved in [the 2bd97df verification report](final-release-report-2bd97df.md).


Final-commit packaging: Android Gradle embeds the Git revision in APK version-control metadata, so a documentation-only commit changes the signed APK digest. The source-milestone digest above identifies the extensively tested artifact. The final HEAD build, signature/hash, ZIP-entry comparison against the installed tested artifact, and final release smoke results are delivered beside the APK in `release-manifest.json` and `final-*.txt`/`final-*.log`. These generated attestations stay outside Git to avoid a self-referential commit/hash cycle. Any changed executable/resource entry requires renewed affected verification.
