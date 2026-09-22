# API33 Pending delivery investigation

Original failing commit: `3c0d440a261a58b365f1a173e28b8eec870c95ee`.
The original full-suite XML/log and `findings.md` are retained. Subsequent fixes were explicitly authorized; the latest scope ends after targeted API33 stability, without full release recertification.

## What the evidence proves

1. The original retry was registered at 09:28:58.278. Recovery retired it at 09:29:08.371. Receiver entry was 09:29:08.468: 10,190ms after registration, 97ms after retirement. Rejecting this already-retired token was correct; allowing it to play would break exclusive ownership.
2. Three isolated cold and three warm executions of the unchanged original test all passed before the fix. The original failure was therefore intermittent, not deterministically reproduced by reboot alone. These PASS results do not erase it.
3. Queue dumps show the alarm PendingIntent had `flg=0x10` and entered the Android 13 **background ordered broadcast queue**. `setAlarmClock` wakes and dispatches the PendingIntent but does not automatically make its receiver foreground priority.
4. A new targeted instrumentation regression holds a background ordered broadcast using `goAsync()`, with no main-thread sleep/block. It then exercises the production coordinator, cancels only the primary exact alarm, and awaits its retry using the same 25-second bound as the original test. It failed twice before production changes. In `priority-before-1/8-activity-broadcasts.txt`, FIRE_ALARM is pending background #2; in `priority-before-2/8-activity-broadcasts.txt`, it is pending background #1. The independent watchdog still runs, retires the retry at its unchanged 10-second grace, and no alarm plays.
5. The only production change adds `Intent.FLAG_RECEIVER_FOREGROUND` to the scheduler's shared FIRE_ALARM intent builder. The exact same controlled regression then passed twice: the retry reached Ringing and AUDIO_STARTED **before** the held background broadcast was released. This is a causal before/after result, not a larger timeout.

The confirmed software defect is using a background dispatch queue for a time-critical alarm while its independent watchdog continues to enforce a bounded delivery grace. The proximate mechanism of the historical failure is callback delivery losing to expiry; its specific preceding system broadcast was not captured in that original filtered log. We do not claim to identify that missing historical queue occupant or prove all platform jitter has one cause. The controlled experiment proves the dispatch mismatch can independently cause the same lost-retry outcome and that the minimal change removes that mismatch.

## Disposition of alternative causes

| Candidate | Evidence-based disposition |
|---|---|
| Test timeout too short | Not the cause of the loss: state was already Idle/retired before the 25s assertion expired. Timeout unchanged. |
| Arbitrarily tight grace | No grace increase. The defect was confirmed while an unrelated OS queue blocked delivery. The grace remains a bounded recovery policy, not an OS delivery guarantee. |
| Watchdog retires before its configured deadline | Not observed. Both original and controlled traces retire after the configured deadline plus grace. |
| Old callback still owns retry | False. Retirement persists Idle before replacement registration; old token rejection remains mandatory. |
| Persisted/runtime split or two owners | Not observed; deterministic boundary and interleaving tests check stored/state equality and exactly one successful claim. |
| Instrumentation race only | Ruled out as sole cause by independent OS queue snapshots and the controlled production-path failure. The original test was not edited. |
| Emulator-only timing | Cold-load timing varies, but the background queue behavior is platform behavior. No physical/OEM timing guarantee is claimed. |

## Preserved invariants and regression coverage

- No changes to matcher ANY/ALL, coordinator/reducer state architecture, persistence schema, grace, retry budget, test timeout, or receiver token validation.
- Pending still suppresses bursts without extending its deadline; recovery remains at most one replacement, then Idle/failure.
- Callback at -1/0/+1ms relative to grace may claim only if it wins ownership before recovery. Recovery at 0/+1ms winning first invalidates the old callback; it cannot affect replacement Pending/Ringing.
- Explicit suspended-store interleavings test callback waiting behind retirement and recovery waiting behind a Ringing publication. Mutex order determines one owner, not a permissive stale callback.
- Existing recovery/runtime ownership tests plus new boundary tests: 23 affected JVM tests, 0 failures/errors/skips.
- API33-only controlled queue regression retains both pre-fix failures and post-fix passes. Its API filter documents Android 13's separate queue behavior; other API coverage is not claimed by this test.

## Platform references

- [Intent.FLAG_RECEIVER_FOREGROUND](https://developer.android.com/reference/android/content/Intent#FLAG_RECEIVER_FOREGROUND): foreground receiver priority with shorter timeout.
- [Android 13 AlarmManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-release/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java): `mBackgroundIntent` adds FLAG_FROM_BACKGROUND; `deliverLocked` sends the PendingIntent with that intent.
- [Android 13 BroadcastQueue](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android13-release/services/core/java/com/android/server/am/BroadcastQueue.java): ordered queue dispatch and deferral mechanics.

Final stability results and fix commit are recorded below and in `closure.md`. Full gates, other APIs, signing, backup and physical certification are deliberately deferred under the latest user scope.

## Watchdog timing visibility

The original production trace does not emit a separate arm timestamp. `RecoveryJobs.arm` runs before Pending publication, and sampled JobScheduler dumps preserve the armed job, minimum latency and earliest runtime. For example `priority-before-2/8-jobscheduler.txt` shows the repair job with minimum latency 9,999ms, enqueue age 6,365ms and earliest run in 3,634ms. Exact arm-call duration is not claimed; published deadline/grace and retirement/callback times are measured from epoch and monotonic trace fields. Raw numbered OS dumps are also retained in byte-verified `os-dumps.zip` archives; originals stay on disk.

## Final targeted result

Fix commit: `0f49a90803741d13b57725881fc21819907892c8`. Original unchanged test: 6 consecutive PASS, 3 cold + 3 warm. Post-fix controlled queue test: 2/2 PASS, plus a further PASS in the 3-case focused suite. All 23 affected unit tests PASS. `api33-stability-assertions.txt` and `api33-focused-assertions.txt` check no retired/stale retry, no duplicate structured audio-start trace, final Idle and service/audio cleanup. Final full release validation remains deferred, not PASS.
