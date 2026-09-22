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
