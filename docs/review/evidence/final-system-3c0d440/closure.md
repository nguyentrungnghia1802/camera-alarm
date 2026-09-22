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
