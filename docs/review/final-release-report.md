# Final release verification — 2026-09-22

**READY FOR RELEASE — software release readiness.** Physical-device certification and external backup verification remain separate, incomplete scopes. This section supersedes historical verdicts below under the user's revised acceptance criteria.

Tested and signed source HEAD: `fd06c2c855d3f3b1c02b2b84c2850fc4878aebde`; API33 production fix: `0f49a90803741d13b57725881fc21819907892c8`. No production/test changes in this round. The subsequent documentation-only commit does not change the tested source; the APK embeds fd06c2c, not that later documentation commit.

## One final verification round

| Gate | Result |
|---|---|
| clean; test --rerun-tasks; lint; assembleDebug; :app:assembleRelease; :app:assembleAndroidTest | All six PASS, each executed once |
| Unit | 294 executions (app 142 + fake-camera 5, debug and release); 0 failures/errors/skips |
| Lint | 0 errors/fatal; app 93 warnings, fake-camera 14 warnings |
| API31 | 27 PASS, 1 intentional reboot fixture skip; separate first cold STOP probe PASS |
| API33 | 28 PASS, 1 intentional reboot fixture skip; Pending recovery and controlled queue regression PASS |
| API34 | 27 PASS, 1 intentional reboot fixture skip |
| API36 | 27 PASS, 1 intentional reboot fixture skip |

Each full instrumentation suite ran once; no failure reruns or second matrix. The Android-13-only queue regression is SDK-filtered out on other APIs (not an omitted failing test). XML counts are authoritative; Gradle progress prints a different aggregate count. API33 retry trace assertions found two valid retry owners, each Ringing/audio exactly once, neither stale nor retired incorrectly. Existing privacy, EN/VI and sourcePackage/ruleId/alarmToken correlation regression coverage remains and passed.

## API33 disposition

**FSV-01 FIXED.** FIRE_ALARM previously waited in the background broadcast queue while the independent watchdog could exhaust grace and retire its token. A late callback was correctly rejected as STALE_ALARM. `FLAG_RECEIVER_FOREGROUND` routes time-critical delivery through the foreground queue; controlled before/after evidence establishes this mechanism. Grace stays 10 seconds, test timeout 25 seconds, retry at most once, stale-token protection and core architecture unchanged. The old log cannot identify its preceding system broadcast; no universal OS delivery-time guarantee is claimed. Original failure and 3 cold + 3 warm evidence remain in `final-system-3c0d440`; no reinvestigation or repeat of those six probes here.

## Signed release

- APK: `app/build/outputs/apk/release/camera-alarm-fd06c2c-signed.apk` (version 1.1.0, code 2).
- SHA-256: `a578eb00bfbfef3bbd172c870d31886f0c24b3e76e260eb3700443703e07f1f7`.
- apksigner: v2 and v3 verified, one signer. Certificate SHA-256: `e6f06f695b13d98150dba8a718b923f54d93ddd47901f47c64f27b9586f93900`.
- Embedded VCS revision equals tested HEAD fd06c2c. `artifacts.json` records hashes and paths. APKs remain build artifacts, not Git evidence blobs.
- Fresh install PASS on API36; signed STOP/live EN-VI smoke 3/3 PASS using the same-key signed instrumentation APK.
- UI Test Alarm PASS; Vietnamese AlarmActivity and STOP PASS.
- Synthetic trigger PASS: existing opt-in fixture initialized disposable emulator settings; UI selected Fake Camera as source and rule package; real Android notification listener delivered its custom notification to one Ringing/audio owner.
- Notification action Open Camera PASS: launched `com.personal.fakecamera/.MainActivity` and stopped alarm. Settled snapshots show no alarm service or active alarm MediaPlayer. Immediate snapshots preserve the in-flight activity/STOP transition, not a failed cleanup.
- Upgrade install PASS: existing same-key release 1.0/code 1 → new signed 1.1.0/code 2 via `adb install -r`; selected Front Door Camera setting retained in before/after UI. Baseline APK hash/signature retained; baseline has no embedded VCS metadata, so its directory name alone is not treated as commit attestation.
- REAL CAMERA TRIGGER: **NOT VERIFIED**. Fake Camera is synthetic.

## Physical and backup scopes

- PHYSICAL CERTIFICATION: **NOT VERIFIED**. Initial ADB inventory empty; final inventory contains only emulator-5554, no A50. No physical reboot/PIN/locked-screen/audio/vibration/OEM certification claimed.
- Local backup/restore: retain actual API31 LocalTransport PASS in `release-fixes-20260922/`. Source diff from that tested milestone 21e7516 changes only alarm dispatch plus two regression tests; persistence/settings/backup unchanged. No unnecessary rerun.
- Google cloud/OEM/device transfer: **NOT VERIFIED**; unavailable external environment.
- Headless/no-audio emulator verifies software playback and UI state, not audible sound or physical vibration. Lint warnings remain; they are not suppressed or described as zero warnings.

## Evidence and execution notes

Evidence: `docs/review/evidence/final-release-fd06c2c/`. Gate logs, unit XML/counts, lint XML/counts, per-API protocol/XML/runtime, API33 retry assertions, signed signature/hash/VCS data, fresh/upgrade and UI/runtime cleanup snapshots retained. Existing evidence is untouched.

Setup-only issues: initial `emu kill` found no emulator; uninstall on fresh AVDs reported missing packages before successful installs. One recorder console needed Python UTF-8, and immediate UIAutomator snapshots needed the activity to settle; these were read-only capture retries, not test reruns. An initially mistyped listener component was revoked and the manifest component granted before synthetic delivery. None was classified as an application test PASS or hidden application failure.

No open software blocker found. No second verification round; no push. Pre-existing `phone_now.png` remains untracked. The final commit contains documentation/evidence only.

---

# API33 targeted closure — 2026-09-22

**API33 FSV-01 dispatch defect: FIXED; targeted stability PASS. Overall verdict: NOT READY FOR RELEASE.** The latest user instruction stops this round after API33. Full Gradle gates, API31/34/36, signed release, backup/restore and Samsung A50 were not rerun after this fix.

Original failing commit: `3c0d440a261a58b365f1a173e28b8eec870c95ee`. Fix commit: `0f49a90803741d13b57725881fc21819907892c8`. One production change: FIRE_ALARM uses `FLAG_RECEIVER_FOREGROUND`, routing this time-critical callback through the foreground broadcast queue instead of waiting behind a held background broadcast while the independent watchdog expires. Grace remains **10 seconds**, test timeout remains **25 seconds**, retry remains at most once, and stale callback protection is unchanged. No matcher or core architecture changes.

- Before: isolated original test 3 cold + 3 warm PASS; historical full-suite API33 failure retained. Controlled background-queue regression FAIL twice, with queued FIRE_ALARM and expired retry ownership.
- After: same controlled regression PASS twice; original unchanged test **6 consecutive PASS (3 cold + 3 warm)**. Retry registration→receiver: 5005–5708ms; every retry reached Ringing once, none was retired/staled incorrectly, at most one AUDIO_STARTED per token. Cold-1 cleanup preceded audio, so that run certifies ownership/Ringing only.
- Affected JVM tests: **23 PASS**, including 5 new boundary/interleaving tests, 14 recovery tests and 4 runtime ownership tests.
- Final API33 focused instrumentation: **3 PASS, 0 FAIL, 0 SKIP** (scheduler cancellation, STOP, queue regression). Cleanup: DataStore Idle, no alarm service or active alarm player.
- The original filtered failure log cannot identify the specific preceding system broadcast; controlled queue snapshots prove the dispatch mismatch and its correction. Foreground priority is not a hard real-time delivery guarantee under arbitrary OS/storage/CPU failure.

Evidence is retained under `docs/review/evidence/final-system-3c0d440/`, including `api33-root-cause.md`, before/after traces, unchanged historical failure, 6-run stability assertions and lossless OS dump archives. Raw numbered dumps remain on disk; Git stores byte-verified ZIPs to avoid redundant bulk. Tests ran on the exact source/APK hashes in `api33-fixed-source-artifacts.json` before the source milestone commit; no rebuild/signing claim is made for this new commit.

See root `report-02.md` for current scope and release limits. No push. `phone_now.png` remains untracked and untouched.

---

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
