# Phase 1 verification evidence

Date: 2026-09-13. Commands were run on Windows PowerShell against Android API 31, 33, 34 and 36 AVDs with targetSdk 36. Debug-only injections are protected by `android.permission.DUMP`, remain in the debug source set, and are absent from the merged release manifest.

| Scenario | Evidence | Result |
| --- | --- | --- |
| M1 Foreground | Real debug camera notification via `NotificationListenerService`: `SCHEDULED` 12:01:09, receiver/runtime 12:01:14; `MainActivity` was `topResumedActivity`. | Pass on API 36 |
| M2 Background | Real notification: `SCHEDULED` 12:02:24, receiver/runtime 12:02:29; launcher was `topResumedActivity`. | Pass on API 36 |
| M3 Locked screen | Real notification: `SCHEDULED` 12:02:45, runtime 12:02:51; `mWakefulness=Asleep`, service `isForeground=true`, vibrator caller UID was the app. | Pass on API 36 |
| M4 Doze | `dumpsys deviceidle force-idle` reported deep idle; injection scheduled 12:03:03 and runtime started 12:03:08 while `mState=IDLE`. Idle was unforced afterward. | Pass on API 36 |
| M5 Spam | Five distinct debug broadcasts in about 0.3 seconds: one `SCHEDULED`, four `SUPPRESSED_PENDING`; one receiver/runtime start. | Pass on API 36 |
| M6 Cooldown | Notification STOP, then `SUPPRESSED_COOLDOWN` at 11:56:55; a new event after 11 seconds was `SCHEDULED` at 11:57:06. | Pass on API 36 |
| M7 Exact access revoked | `appops ... SCHEDULE_EXACT_ALARM deny`; debug readiness reported `exact=false`, and new event returned `SCHEDULE_FAILED` at 11:57:45 without a new runtime. | Pass on API 36 |
| M8 Listener off/on | Debug log: connected 11:57:58, disconnected 11:58:00, connected 11:58:02; subsequent real notification was `SCHEDULED` 11:58:57. | Pass on API 36 |
| M9 Process recreation | Scheduled 5-second alarm in PID 9483; `run-as ... kill -9` (not force-stop); receiver/runtime started in new PID 9516 at 12:03:38. | Pass on API 36 |
| M10 Alarm volume zero | Emulator reported `STREAM_ALARM` range 1..7 and rejected `--set 0`; debug diagnostics read current/min/max. Unit test covers current 0 and warning. | Conditional fallback per `08-testing-quality.md`; actual zero unavailable on this emulator |

Additional observations: An alarm notification's STOP action stopped the foreground service and cancelled the repeating vibration. `dumpsys vibrator_manager` showed a 700 ms on / 300 ms off waveform with repeat 0 for 42 seconds until STOP; AudioFlinger showed an active track owned by the app. A configured 1-second alarm sometimes fired about 5 seconds after scheduling on this emulator; the reducer test accepts a late event only for the still-current token, and Android timing is not hard real-time.

## Compatibility matrix on the current build

| API | AVD and evidence | Result |
| --- | --- | --- |
| 31 | `CameraAlarm_API_31` (Android 12): fresh install/start; scheduler instrumentation pass; real notification received by the listener from launcher background; `SCHEDULED` 18:06:44, receiver/runtime 18:06:49; AlarmManager supplied the `ALARM_MANAGER_WHILE_IDLE` background-start allowlist; foreground notification exposed STOP; STOP at 18:07:15 removed the runtime; exact access denial produced `SCHEDULE_FAILED` with a live process. | Pass |
| 33 | `CameraAlarm_API_33` (Android 13): fresh install/start; scheduler instrumentation pass; real notification received from launcher background; `SCHEDULED` 18:03:10, receiver/runtime 18:03:16; STOP at 18:04:19 removed the runtime; exact access denial produced `SCHEDULE_FAILED`; denying `POST_NOTIFICATIONS` reported readiness false but still started the FGS without a crash, matching the platform Task Manager fallback. | Pass |
| 34 | `CameraAlarm_API_34` (Android 14): clean AVD boot, install/start and scheduler instrumentation pass; screen was `Asleep` before injection and remained `Asleep` after fire; `SCHEDULED` 18:12:44, receiver/runtime 18:12:49; running service reported foreground type `0x00000400` (`systemExempted`); notification exposed STOP; STOP at 18:14:07 removed the runtime; exact access denial produced `SCHEDULE_FAILED`. | Pass |
| 36 | `Medium_Phone_API_36.1` (Android 16): clean AVD boot, install/start and scheduler instrumentation pass; real notification received from launcher background; `SCHEDULED` 18:19:53, receiver 18:20:06, runtime 18:20:07; service reported foreground type `0x00000400`; notification exposed STOP; STOP at 18:21:19 removed the runtime; exact access denial produced `SCHEDULE_FAILED`. | Pass |

`connectedDebugAndroidTest` passed separately on every API above and verified canonical PendingIntent lookup/cancellation. The API 36 cold emulator delivered one 1-second exact alarm about 13 seconds late; this is recorded as timing variability, not a correctness failure, because it was not early, used the current persisted token, started one runtime, and remained stoppable.

## Platform compatibility audit

- API 31+: every exact schedule calls `canScheduleExactAlarms()` and permission denial never falls back to an inexact success. The exact-alarm receiver path is the documented exemption for starting the alarm FGS from background.
- API 33+: notification permission is modeled separately from blocking exact-alarm readiness. Denial was device-tested without a crash; the alarm FGS still starts, while the notification may be limited to the system Task Manager.
- API 34+: the manifest declares `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` and `systemExempted`; runtime promotion passes `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED`. API 34 and 36 both accepted type `0x00000400` while exact access was granted.
- All tested APIs: NotificationListenerService uses `BIND_NOTIFICATION_LISTENER_SERVICE`, the service action, and `exported=false`; PendingIntents are explicit and immutable, with a canonical request identity; alarm audio uses `USAGE_ALARM`; repeating vibration uses the API 26 waveform and is cancelled by STOP.

The debug APK does not include onboarding/settings UI; Phase 2 supplies those product flows. No physical Samsung device was available, so OEM-specific battery-management behavior remains unverified. M10 remains conditional: the API 36 emulator reported `STREAM_ALARM` range `[1..7]` and rejected index 0; automated tests cover the real `current == 0` diagnostic branch without claiming a device-zero pass.
