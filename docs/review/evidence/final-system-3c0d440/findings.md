# Findings from this verification round

Current disposition: **FSV-01 FIXED in `0f49a90`**; final evidence is recorded at the end. The original finding below is retained as a historical record.

## FSV-01 — API33 lost-delivery recovery gate failed (original finding)

- Severity: P1 release-gate failure; a deliberately lost primary delivery did not reach Ringing through its retry.
- Status: OPEN. No implementation/test edits made.
- Commit: `3c0d440a261a58b365f1a173e28b8eec870c95ee`.
- Device: CameraAlarm_API_33, Android 13 emulator, cold boot without snapshot, boot count 10, fingerprint in `api33-protocol.txt`.
- Reproduction: run `matrix.ps1`; API33 `:app:connectedDebugAndroidTest` failed `PendingDeliveryInstrumentedTest.lostOsDeliveryRetriesAutonomouslyAndLateCallbackCannotClaimReplacement` with the unchanged 25-second timeout. Suite: 26 PASS, 1 FAIL, 1 intentional fixture SKIP.
- Evidence: `api33-TEST-*.xml`, `api33-runtime.log`, `instrumentation-api33.log`.
- Measured sequence: original Pending registered 09:28:47.443; original retired 09:28:58.261; retry `4fb7259e-583a-4719-884b-d922f6c037c1` registered 09:28:58.278. Retry retired after grace at 09:29:08.371. Its receiver entered at 09:29:08.468 and was rejected as STALE_ALARM at 09:29:08.478. No AUDIO_STARTED for this owner.
- Proximate cause established: retry retirement won the race with receiver delivery; the callback arrived about 97ms after retirement and 10.19s after OS registration. The 25-second test timeout is the observed consequence, not proof that extending that timeout would restore delivery.
- Underlying cause of the platform/dispatch delay is not yet established by this log alone. A repeated isolated probe is required; no claim of a general Android scheduling guarantee or a completed fix.

Subsequent reproduction/control outcomes are retained separately and do not erase the original failure.

## Subsequent disposition — targeted API33 closure

FSV-01 confirmed background-dispatch defect FIXED in `0f49a90803741d13b57725881fc21819907892c8`. Original failure above is preserved. Controlled before 2 FAIL / after 2 PASS; unchanged original test after fix 3 cold + 3 warm consecutive PASS; affected JVM 23 PASS; final API33 focused suite 3 PASS. See `api33-root-cause.md` for causal evidence and historical-log limits. Full release verification is deferred by the latest user instruction.
