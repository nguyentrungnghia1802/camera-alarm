# Final system verification — 2026-09-22

Target: `3c0d440a261a58b365f1a173e28b8eec870c95ee`, main, Camera Alarm 1.1.0 (2).

The following paragraph records the initial baseline scope; the subsequent authorization and final closure below supersede it.

This is a new verification run. Earlier evidence is context only. Production, test and build configuration files are not modified. No commit or push is authorized in this round. The pre-existing untracked `phone_now.png` is preserved.

## Protocol and evidence

- `provenance.txt`, `initial-git-status.txt`, `initial-devices.txt`: starting state and time.
- `gates.ps1`, `gates.txt`, `gate-*.log`: exact six sequential Gradle commands and exit codes.
- `unit-xml/`, `unit-counts.json`, `unit-test-results.json`, `*-lint-*.xml`: retained automated results.
- `matrix.ps1`, `api*-protocol.txt`, `api*-result.txt`, `api*-TEST-*.xml`, `api*-runtime.log`: cold AVD matrix, OS fingerprint/boot count, command results and runtime trace. Normal suite intentionally skips the opt-in reboot setup fixture.
- `api31-cold-stop*`, `api31-warm-stop.txt`: unchanged STOP regression first after cold boot and repeated warm.
- `signed-signature.txt`, `signed-sha256.txt`, `signed-vcs-metadata.txt`: fresh build of exact target commit, signed using existing external release credentials.
- `probe.py`, `commands.jsonl`: external ADB/UI/DB evidence recorder. Not application code or a replacement test harness. All mutations target the disposable `emulator-5554`; there is no physical device data-clear command.

Headless/no-audio emulators verify Android MediaPlayer/vibrator state, not audible sound, physical vibration or OEM delivery. Shell/Fake Camera notifications are synthetic test sources, even when sent through the real Android notification listener. No synthetic source is reported as a vendor camera/cloud PASS.

See root `report-02.md` for final results, uncovered scenarios and bugs. Fixture setup, OS readiness and permissions are recorded explicitly; failures are retained rather than edited away.

## Subsequent API33 investigation authorization

The user subsequently prioritized FSV-01 and authorized evidence-backed minimal production/test fixes and milestone commits on main, without push. Original failure evidence above is immutable. `api33-before/` records three cold and three warm isolated probes before any implementation edit. Full release gates are deferred until the root cause and API33 stability are established.

## Completed API33 closure

FSV-01 is FIXED in `0f49a90`. See `api33-root-cause.md` for causal proof and limits, `findings.md` for the retained original failure and disposition, and `closure.md` for the final scoped result.

- Original failure: `api33-runtime.log`, `api33-TEST-*.xml`, `instrumentation-api33.log`.
- Controlled reproduction: `priority-before-1/` and `priority-before-2/` (FAIL); identical regression after fix in `priority-after-1/` and `priority-after-2/` (PASS).
- Stability: `api33-after/` (3 cold + 3 warm PASS), `api33-measured-timelines.json`, `api33-stability-assertions.txt`.
- Final focused set: `api33-focused-result.txt`, `api33-focused-assertions.txt` (3/3 PASS); affected unit XML and `affected-unit-results.json` (23 PASS).
- Provenance: `fix-commit.txt`, `api33-fixed-source-artifacts.json`.
- Numbered OS dumps are stored in lossless `os-dumps.zip` archives with SHA-256 manifests; redundant raw copies remain locally ignored by Git. Original failure evidence is preserved.

Full release verification: **NOT YET RERUN AFTER FIX**. Overall **NOT READY FOR RELEASE**. No further verification or push is part of this documentation closure.
