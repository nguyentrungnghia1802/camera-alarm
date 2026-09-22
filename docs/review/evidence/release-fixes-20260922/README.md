# Release fixes evidence — 2026-09-22

Production source: `1bf4736`; final test-harness/build milestone: `21e7516` on main. Later documentation-only commits do not change this source tree. No push.

## Root causes and bounded verification

- Privacy: listener emitted `incoming.key` before the source gate. Android notification keys contain application-controlled tags. Trace now accepts only package/id metadata; UUID trace tokens do not contain notification content. JVM canary verifies every payload/key/tag field is excluded.
- History: RecordFailure previously carried only reason; factory filled source from legacy global config and null rule/token. It now requires the owning TriggerSnapshot at schedule/permission/recovery/dispatch failures. Factory no longer has a fallback-source parameter. Regression covers permission, scheduler failure, watchdog-registration failure, dispatch exception and retry exhaustion, including replacement tokens.
- Locale: Main-only composition context did not cover Activity/window resources or Service/application strings. Shared localized Activity resources plus recreation on language change rebuild cached dialog resources while preserving saved state; Service and message lookup use selected locale. Active notification actions refresh on language change. Instrumentation covers EN/VI dialog, modal bottom sheet, snackbar and active notification actions.
- API31 STOP: before fix, cold test FAIL twice, warm control PASS. `api31-cold-broadcasts.txt` records STOP at Active Ordered Broadcast background #47, enqueue 08:20:00.393, dispatchTime unset. Test send returned at elapsed 61735, receiver never entered before original 10s deadline. Main idle and direct service cleanup succeeded; the delay was before receiver dispatch, not coordinator/service work. All user STOP broadcasts now use foreground receiver priority, retaining receiver/token validation and original timeout.
- Fixed cold STOP: SEND 08:23:12.585, receiver 08:23:12.849, runtime stopped 08:23:13.047 (264ms/462ms). Full cold-start API matrix is recorded separately.

Platform reference: https://developer.android.com/reference/android/content/Intent#FLAG_RECEIVER_FOREGROUND

## Backup/restore

Actual API31 LocalTransport round trip on the final source, with synthetic fixture only. Default disabled backup was enabled and LocalTransport selected. Initial package backup was rejected (no encryption capability). Used Android's documented local test setting `backup_local_transport_parameters=is_encrypted=true`; no production backup policy was changed.

Before backup: monitoring ON, shell fixture rule/history present, runtime Ringing with token `ce21e9ea-8782-47c2-a3e8-33ec95ee96fa`, owner nonce and preview. `backup-before-*.pb` contain only this synthetic fixture. `backup-run.txt` is package Success. Then force-stop, pm clear on disposable emulator and `bmgr restore 1 com.personal.cameraalarm`: restoreFinished 0. `restore-files.txt` taken before launching app contains settings only, no database or runtime file. Settings bytes and SHA256 match exactly after launch; newly initialized runtime is Idle, old token absent. Service/audio dumps retained. Transport, test parameter and enabled state restored afterward.

This is PASS for Android local transport backup/restore and exclusion/no-phantom behavior; real Google cloud, device transfer and OEM transport remain NOT VERIFIED. The test encryption capability is a local simulation, not proof of cryptographic protection by an actual cloud transport.

Platform test procedure: https://developer.android.com/identity/data/testingbackup

## Artifacts

- gate-1 through gate-6: requested clean/test/lint/debug/release/androidTest gates in order.
- gate-counts: full unit/lint counts.
- matrix.ps1: serial cold AVD matrix, no timeout relaxation.
- signature and signed-sha256: existing private release key used via external DPAPI signing helper; no secret copied into repository.
- Earlier `final-2bd97df` evidence remains historical and is not final-source certification.

Signed release DEX verification: listener invokes safeTraceDetails; the helper reads only getPackageName/getNotificationId and no key/tag/payload accessors (release-listener-bytecode.txt, release-trace-bytecode.txt).

## Test harness compatibility and gate retry

First API36 run failed only the two new UI tests: transitive Espresso 3.5.0 called removed InputManager.getInstance via reflection. DependencyInsight and failure log retained. Narrow androidTest-only pin to Espresso 3.7.0 fixes this upstream root cause (https://developer.android.com/jetpack/androidx/releases/test); assertions and production dependencies unchanged. Affected API36 tests PASS, then all six gates and four-API matrix repeated with final harness.

The repeated clean initially failed because the Gradle daemon held lint migrated JARs on Windows. Stopped the daemon with gradlew --stop, then repeated the exact gate sequence. No test bypass or arbitrary deletion; initial failure retained in clean-initial-file-lock.log.

## Signed release protocol and results

Artifact: `app/build/outputs/apk/release/camera-alarm-1.1.0-signed.apk`, version 1.1.0 (2), `debuggable=false`.

- Existing release certificate SHA256: `E6F06F695B13D98150DBA8A718B923F54D93DDD47901F47C64F27B9586F93900`; APK v2/v3 signature verification PASS.
- APK SHA256: `A6FEB6CCBD0163A34ACA3086C6DBBA8A8F7B4410662F27D7A1879C9C8DC8DE2F`.
- Fresh API36 install PASS: target package absence recorded before installation. Release-signed instrumentation ran against the actual non-debuggable release target: Test Alarm/STOP plus EN/VI dialogs/sheet/snackbar/active actions, 3/3 PASS. The test APK is verification-only and is not the distributed artifact.
- Upgrade PASS: historical `0d76d41` versionCode 1 APK built in an isolated temporary archive and signed with the same key; saved English through Settings, then `adb install -r` to versionCode 2. Both dumps report firstInstallTime `2026-09-22 08:59:42`; English persisted in the new UI. Main repository was not checked out/reset.
- Signed listener E2E PASS for **synthetic Fake Camera source**: a real Android notification from `com.personal.fakecamera` matched rule `reboot-e2e`/ANY/Human and reached Pending → Ringing → FGS/audio. Example token `7735fee2-9b9b-4ea0-b77a-64f0088cdd40`: received 09:04:52.272, registered 09:04:52.606, receiver 09:04:57.607, audio 09:04:57.731. No recovery retry. Fixture used 3s delay, always-active and cooldown 0; this is not a physical 600s cooldown/overnight certification.
- Notification STOP PASS for a production token. Open Camera PASS: foreground activity settled on `com.personal.fakecamera/.MainActivity`, alarm service removed and no active alarm MediaPlayer afterward. Initial immediate open-camera dumps were too early in the asynchronous Activity transition; final retained settled evidence follows UI-idle observation.
- Release privacy canaries with monitoring ON and OFF: shell callback confirmed; title/body/tag canaries absent from CameraAlarm logs. Signed DEX helper reads only package/id. Signed VI keyword dialog, notification actions and AlarmActivity have retained screenshots/XML; AlarmActivity shows Phát hiện lúc / Xem Camera / Tắt cảnh báo, and Activity STOP cleans up service/audio; EN/VI automated runtime coverage is above.
- Real camera/vendor/cloud trigger on signed APK: **NOT VERIFIED**. Synthetic listener E2E and Test Alarm do not replace it.
