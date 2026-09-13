# V1 final verification

Date: 2026-09-13. This note covers the final STOP/history hardening diff. Earlier Phase 1 runtime evidence is retained in [phase1-verification.md](phase1-verification.md); it is not claimed as a rerun of the final code.

## Final-code automated gates

| Gate | Result | Evidence |
| --- | --- | --- |
| Unit | Pass | Final `./gradlew test lint assembleDebug --rerun-tasks` completed successfully, with debug and release unit suites executed. |
| Instrumentation API 31 | Pass | `connectedDebugAndroidTest`: 8/8, `CameraAlarm_API_31`. |
| Instrumentation API 36 | Pass | `connectedDebugAndroidTest`: 8/8, `Medium_Phone_API_36.1`. |
| Instrumentation API 33/34 | Not rerun on final code | Both passed the earlier Phase 1 build (see linked evidence); API 33 AVD remained offline during the shortened final run. |
| Lint/build | Pass | Final `./gradlew test lint assembleDebug --rerun-tasks`: `BUILD SUCCESSFUL`, 82 tasks executed. |

The API 31/36 suites include canonical exact-alarm PendingIntent scheduling/cancellation, Room and DataStore persistence, persisted pending state and orphan Ringing cleanup, monitoring-disabled pipeline suppression, and the new service STOP regression. The new regression was observed failing before the service guard: a second START replaced the active notification's STOP PendingIntent token, so STOP left the first alarm running. With the guard, the same test passed on API 31/36. The test grants exact-alarm access and (API 33+) notification permission to exercise the real foreground notification action. On a freshly installed API 36 app, `connectedDebugAndroidTest` requires exact-alarm access to be granted before the scheduler test; the assertion remains strict.

The earlier API 36 UI smoke and runtime scenarios (source/rules/settings/history/diagnostics, process recreation, spam, permission denial, full-screen fallback, Test Alarm and STOP) are documented in the task request and Phase 1 note. They were not all rerun after this service-only guard. No physical device or OEM battery-management test was available. Alarm volume zero remains an emulator limitation: the available emulator reports minimum 1.

Final verdict is **V1 NOT FULLY VERIFIED** because API 33/34 instrumentation was not rerun on this exact code. The previous API 33/34 Phase 1 passes remain valid historical evidence, not final-code verification.
