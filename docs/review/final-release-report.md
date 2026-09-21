# Camera Alarm 1.1.0 — Final Release Audit

Date: 2026-09-21

Branch: `main`

Release code commit: `c16545666ebc713bb8f5f2416f531af807da423b`

Release identity: versionCode `2`, versionName `1.1.0`

## Overall status

| Phase | Status | Evidence boundary |
|---|---|---|
| Phase 1 | PASS | All original High implementation gates closed; clean build/test/lint gates pass |
| Phase 2 | PASS | Room schema 2, explicit 1-to-2 migration, unique priority contract, on-device migration tests |
| Phase 3 | PASS | Rule V2 complete and covered by unit/instrumentation tests |
| Phase 4 | PASS | Settings V2 defaults, legacy preservation, reset, and persistence verified |
| Phase 5 | PASS | Product-integration gate and layout exercise passed |
| Phase 6 | NOT VERIFIED overall | Final API 31/33/34/36 matrix PASS; Samsung A50 and Xiaomi physical certification not run |
| Phase 7 | PARTIAL | Implementation and affected final matrix PASS; end-to-end Backup Manager restore NOT VERIFIED because the local transport rejected the release package |
| Phase 8 | PARTIAL | Clean gates, signing, fresh install, upgrade, and signed Test Alarm/STOP PASS; a real camera notification on the signed build is NOT VERIFIED |

## Original review issues

| Issue | Final status | Resolution |
|---|---|---|
| ISSUE-01 Instrumentation gate broken | CLOSED | Production retention API is tested; AndroidTest builds and runs |
| ISSUE-02 Wrong-package content persisted | CLOSED | Wrong-package/monitoring-off events are excluded and legacy rows removed |
| ISSUE-03 Blocking DataStore on FGS start | CLOSED | Alarm runtime uses an immutable in-memory snapshot on the critical path |
| ISSUE-04 Listener recovery component toggle | CLOSED | Bounded `requestRebind` recovery with no component mutation |
| ISSUE-05 Current release device matrix | PARTIAL / RELEASE GATE OPEN | API 31/33/34/36 PASS on final code; Samsung/Xiaomi physical validation missing |
| ISSUE-06 History contract mismatch | CLOSED | Rule/token/source lifecycle correlation is carried by `TriggerSnapshot` |
| ISSUE-07 Test Alarm conflicts with production | CLOSED | Single ownership policy and STOP priority are regression-tested |
| ISSUE-08 Backup may export notification data | CLOSED for policy | Settings-only allowlist; Room/history/runtime excluded. Actual restore remains NOT VERIFIED |
| ISSUE-09 Battery optimization flow incomplete | CLOSED | Policy-safe settings route, fallback, and visible failure messaging |
| ISSUE-10 Localization incomplete | CLOSED | EN/VI resources and locale instrumentation; signed UI language persistence verified |
| ISSUE-11 Rule edit changes tie-break | CLOSED | Stable creation time plus explicit unique priority/swap contract |
| ISSUE-12 Exported boot receiver | CLOSED | Non-exported receiver plus fixed action allowlist |
| ISSUE-13 Duplicate/deprecated full-screen paths | CLOSED | Service is sole launch owner; deprecated wake/priority calls removed |
| ISSUE-14 Logic-replica tests | CLOSED for scoped debt | Production mappers/policies/controllers are exercised directly |
| ISSUE-15 Lint/dependency/release packaging | CLOSED with accepted warnings | Lint PASS; warnings triaged; release identity, signing, hash, and install gates complete |

No unresolved Critical or High implementation defect was found on the final candidate. The open release gates are missing physical/environmental evidence, not silently promoted PASS results.

## Rule V2

- Maximum normal rules: 3.
- Priority: unique 1–3; occupied priority swaps atomically; delete gaps are preserved.
- ANY/ALL matching: PASS.
- Keyword editor: normalized add/edit/delete, blank and duplicate rejection: PASS.
- Per-rule source: PASS, including multiple packages and same-package deterministic priority.
- Vietnamese suggested template: PASS.
- Migration: explicit Room 1-to-2 migration and on-device migration tests PASS.

## Settings V2

- Default sound: Loud Warning 1.
- Default cooldown: 600 seconds.
- Default schedule: daily 22:30–06:00, start-inclusive/end-exclusive.
- Reset defaults: atomic and does not delete rules/history or mutate permissions.
- Basic/Advanced UI: PASS in EN/VI.
- Existing-user migration: missing V1 fields retain their prior effective values.
- Signed upgrade 1.0 to 1.1.0 preserved the saved English selection and `firstInstallTime`.

## Verification

Final commands were run in the required order on the release code commit:

| Gate | Result |
|---|---|
| `.\gradlew.bat clean` | PASS after stopping a Gradle daemon that held a `fake-camera` lint-cache JAR |
| `.\gradlew.bat test --rerun-tasks` | PASS; 123 tests per debug/release variant, 0 failure/error/skip |
| `.\gradlew.bat lint` | PASS; 0 errors, 92 triaged warnings |
| `.\gradlew.bat assembleDebug` | PASS |
| `.\gradlew.bat :app:assembleRelease` | PASS |
| `.\gradlew.bat :app:assembleAndroidTest` | PASS |

Final instrumentation after all production changes:

| Target | Result |
|---|---|
| API 31 / Android 12 | PASS, 19/19 |
| API 33 / Android 13 | PASS, 19/19 |
| API 34 / Android 14 | PASS, 19/19 |
| API 36 / Android 16 | PASS, 19/19 |
| Samsung A50 (`SM-A505F`) | NOT VERIFIED — disconnected from ADB before physical certification |
| Xiaomi | Implementation COMPLETE; physical validation NOT VERIFIED — no device available |

The matrix includes alarm scheduling, STOP routing, Room/migration, Rule V2, Settings V2, history mapping, manifest exposure, localization resources, and integration paths. It does not substitute for a true camera notification, OEM background policy, reboot, lock-screen, or Doze exercise on the missing physical devices.

## Reliability and defects fixed

- API 36 originally exposed a real foreground-service/full-screen failure. Commit `0d76d41` fixed foreground promotion and STOP dispatch; the full suite passed afterward and again after Phase 7 cleanup.
- Alarm lifecycle history could lose Rule V2 correlation through asynchronous lookup. Commit `31d3465` carries the original snapshot through fired/stopped/cancelled events.
- Backup previously lacked an explicit privacy boundary. Commit `91168b8` restricts both legacy and Android 12+ backup paths to durable settings.
- Battery guidance could request an exemption directly and settings failures could be silent. Commit `04e0b62` uses policy-safe routes, bounded fallbacks, and localized visible errors.
- Boot/full-screen cleanup removed third-party receiver exposure, duplicate activity launch ownership, deprecated wake-lock/notification-priority use, and hard-coded user text while retaining the API 36-proven service fallback.
- The first final `clean` attempt hit an open Gradle lint-cache file. Stopping the daemon fixed the environmental lock; the rerun passed. This is not recorded as a product defect.

STOP from the signed API 36 Test Alarm was checked at runtime: `AlarmActivity` opened, the foreground service and alarm notification existed, and STOP returned to `MainActivity` while removing both the service and notification. A real camera-source trigger on the signed APK remains NOT VERIFIED.

## Privacy and security

- Wrong-package and monitoring-off notification content is not persisted.
- History retention remains 3 days, at most 100 rows, and at most 10 suppressed/ignored rows.
- Backup exports only `camera_alarm_settings.preferences_pb`; Room/history, rules, notification preview, Pending/Ringing state, token, owner nonce, and runtime metadata are excluded.
- `BootReceiver` is non-exported and accepts only the fixed boot/package action set.
- Signing secret is outside Git and protected for the current Windows user.

The API 36 local Backup Manager transport returned `ERROR_PREFLIGHT` and no restorable app dataset, so an actual clear-and-restore cycle was not performed. Policy/build verification is PASS; restore behavior is NOT VERIFIED.

## Signed artifact

- Path: `app/build/outputs/apk/release/camera-alarm-1.1.0-signed.apk` (generated, not committed).
- Size: 13,655,679 bytes.
- Signature: APK Signature Scheme v2 and v3 PASS; one RSA-4096 signer.
- Certificate SHA-256: `E6F06F695B13D98150DBA8A718B923F54D93DDD47901F47C64F27B9586F93900`.
- APK SHA-256: `5487B9A063748B6BDE96858D7865DAAB69C4A0F0190A54FD2096C71B9D98E6C9`.
- Fresh install/cold launch on API 36: PASS.
- Same-key versionCode 1 to versionCode 2 upgrade: PASS; persisted language retained.
- Signed Test Alarm/full-screen/STOP: PASS.
- Signed real camera notification: NOT VERIFIED.

## Remaining limitations

- Samsung A50 physical background, locked/off-screen, Doze, reboot/listener recovery, real-camera, audio/vibration, Open Camera, cooldown, and STOP matrix is NOT VERIFIED.
- Xiaomi physical validation is NOT VERIFIED.
- End-to-end cloud/device-transfer restore is NOT VERIFIED; the local emulator transport rejected the release package during preflight.
- A real camera application notification on the signed release was not available.
- The headless API 36 AVD returned black screenshot frames. UI hierarchy interaction, EN/VI persistence, font scale 1.5, and dark/light switching were exercised; final pixel-level screenshot review was not possible. Phase 5 retains the earlier 360 dp/font-1.5 layout evidence.
- 92 non-blocking lint warnings remain by explicit triage; dependency upgrades are deferred to a separate compatibility batch.

## Git

- Branch: `main`.
- Release code commit: `c16545666ebc713bb8f5f2416f531af807da423b`.
- Final audit documentation: the commit containing this report.
- Expected post-audit working tree: clean.
- Expected origin delta after the audit commit: 17 commits ahead, 0 behind.
- Push: not performed.

## Final verdict

**NOT READY FOR RELEASE — RELEASE HOLD.**

The code, automated gates, four-API emulator matrix, signed artifact, fresh install, upgrade, and signed Test Alarm/STOP are complete. The Definition of Done explicitly requires Samsung A50 physical validation, and the signed real-camera path is also unverified. Those results cannot be inferred from emulator or Test Alarm evidence.
