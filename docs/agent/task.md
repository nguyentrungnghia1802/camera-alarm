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

# Current release closure — 2026-09-22

**NOT READY FOR RELEASE.** Production milestone `1bf4736`, final harness/build `21e7516`, main, no push. This checklist supersedes historical completion/open states below; details in [final-release-report.md](../review/final-release-report.md).

- [x] FV-01 remove unrelated raw key/tag trace; privacy regression.
- [x] FV-02 shared EN/VI Activity/window, Service/action and message locale; dialog/bottom-sheet/snackbar/live-action regression.
- [x] FV-03 failure owner sourcePackage/ruleId/alarmToken retained across retirement/retry/dispatch; regression.
- [x] FV-04 cold/warm reproduced, OS background queue #47 measured; foreground-priority user STOP fixes dispatch; same timeout retained.
- [x] Full clean/test --rerun-tasks/lint/assembleDebug/:app:assembleRelease/:app:assembleAndroidTest PASS (284 unit executions).
- [x] Final API31/33/34/36 instrumentation: 27 PASS + 1 intentional fixture skip per API, zero failures/errors.
- [x] Actual local backup/clear/restore E2E; settings-only restore, no history/runtime/token/phantom alarm. Real cloud/OEM transport NOT VERIFIED.
- [x] Signed APK v2/v3 signature, SHA256, debuggable=false, API36 fresh install/3 smoke tests, versionCode 1→2 upgrade preserving English, synthetic listener trigger/STOP/Open Camera PASS. Real-camera signed trigger remains NOT VERIFIED.
- [ ] Samsung A50 physical certification and real camera trigger — brief late connection then offline/disconnected before certification; NOT VERIFIED.
- [ ] Physical EN/VI, font/layout, speaker/vibration and OEM matrix; NOT VERIFIED.

No matcher ANY/ALL or core alarm architecture replacement. Valid existing work/evidence and untracked phone_now.png retained. Historical records follow.

---

> **Final verification update — 2026-09-22, SHA 2bd97df87fc5e1275582fa0d962eb3309bb7eab0:** NOT VERIFIED / NOT READY FOR RELEASE. Verification-only; no implementation/test changes or push. Build/unit/lint/assembly PASS; instrumentation API33/34/36 PASS, API31 cold STOP timeout reproduced (warm control PASS does not close it). See [final release report](../review/final-release-report.md) and [verification evidence](../review/hardening-verification.md).
>
> Open checklist: [ ] FV-01 raw wrong-package notification key/tag in release logging; [ ] FV-02 runtime selected-language inconsistency; [ ] FV-03 failure history source/token correlation; [ ] FV-04 API31 cold STOP timeout disposition; [ ] Samsung A50 live/real-camera certification; [ ] physical UI matrix; [ ] actual backup/restore; [ ] current signed fresh/upgrade/trigger/STOP/Open Camera. Xiaomi physical validation: NOT VERIFIED. Historical completion records below do not close these current gates.
# Camera Alarm — Consolidated Task Plan

> **Source of truth mới:** file này.
>
> `docs/agent/task-01.md` và các task cũ chỉ còn giá trị lịch sử/tham khảo.
>
> Mục tiêu: hoàn tất release hardening đang dang dở, sau đó triển khai Rule V2 + Settings UX mới, rồi mới chạy device certification và release.

---

## 0. Nguyên tắc chung

- Làm trực tiếp trên `main` theo workflow hiện tại.
- Không reset/revert thay đổi đang dang dở của P1.6; trước tiên kiểm tra `git status` và hoàn tất đúng root cause.
- Không đổi core alarm architecture nếu không cần.
- Không thêm permanent foreground service.
- Không đổi matcher ANY/ALL hiện tại.
- Không đánh dấu PASS cho test chưa chạy thật.
- Emulator không thay thế bằng chứng OEM-specific.
- Mỗi thay đổi behavior/schema phải có regression test nếu khả thi.
- Không nâng dependency hàng loạt cùng feature work.
- Sau task quan trọng chạy tối thiểu:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

- Khi liên quan instrumentation:

```bash
./gradlew :app:assembleAndroidTest
```

---

# 1. Trạng thái kế thừa

Các milestone đã hoàn thành từ plan trước:

- Phase 0 baseline: `a227080`
- P1.1 instrumentation release gate: `d7e9e27`
- P1.2 privacy wrong-package: `495c7ee`
- P1.3 non-blocking FGS startup: `903170b`
- P1.4 boot listener recovery: `571c4b0`
- P1.5 Test Alarm isolation: `a512e07`

Hiện tại đang ở **P1.6 full regression gate**.

Trong API 31 instrumentation đã phát hiện test cũ giả định notification chỉ có một action bằng `actions.single()`, trong khi production hiện có `Open Camera` + `STOP`.

Yêu cầu:

- Hoàn tất fix test theo đúng production behavior.
- Chọn STOP action theo semantic/label/action id, không theo số lượng action.
- Chạy lại toàn bộ instrumentation API 31.
- Không coi lỗi test assumption là lỗi production nếu runtime đã đúng.
- Commit milestone P1.6 chỉ sau khi gate pass.

---

# Phase 1 — Đóng Release Gate hiện tại

## P1.6 — Full regression gate

Status: **COMPLETE** on 2026-09-21 (`connectedDebugAndroidTest` 10/10 on `CameraAlarm_API_31`).

Chạy:

```bash
./gradlew test --rerun-tasks
./gradlew lint
./gradlew assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:assembleAndroidTest
```

Nếu API 31 emulator đang có:

```bash
./gradlew connectedDebugAndroidTest
```

Acceptance:

- [x] Unit PASS.
- [x] Lint không có error.
- [x] Debug/Release build PASS.
- [x] AndroidTest compile PASS.
- [x] API 31 instrumentation PASS.
- [x] Cập nhật `docs/review/hardening-verification.md`.
- [x] Commit sạch trước khi sang Phase 2.

---

# Phase 2 — Data Contract Prerequisite cho Rule V2

Rule V2 sẽ thêm source app theo từng rule và thay invariant priority, vì vậy phải chuẩn bị migration trước khi sửa UI.

## P2.1 — Room schema + migration safety

Status: **COMPLETE** on 2026-09-21 (Room schema 2, exported schemas, explicit `MIGRATION_1_2`, on-device upgrade test PASS).

- Bật `exportSchema = true` nếu chưa có.
- Thiết lập migration test infrastructure.
- Không dùng destructive migration.
- Review schema Rule hiện tại trước khi thêm field.
- Giữ nguyên dữ liệu rule cũ.
- Nếu cần schema version mới, migration phải có test upgrade từ database hiện tại.

## P2.2 — Rule data contract mới

Status: **COMPLETE** on 2026-09-21 (unique priority transaction, max-three enforcement for normal data, legacy overflow retained).

Mỗi Rule có tối thiểu:

```text
id
name
enabled
priority        // 1, 2, 3
sourcePackage
keywords
matchMode       // ANY hoặc ALL, giữ nguyên logic hiện tại
createdAt
updatedAt       // nếu cần
```

Invariant:

- Tối đa 3 rule ở trạng thái bình thường.
- Priority chỉ thuộc `1..3`.
- Không có hai rule cùng priority.
- Priority `1` cao nhất.
- Edit rule không đổi `createdAt`.
- Không dùng `createdAt` để xử lý duplicate priority nữa.
- `matchMode` vẫn áp dụng cho toàn bộ keywords của rule.

### Migration rule cũ

- Giữ nguyên ANY/ALL và toàn bộ keyword.
- Mỗi rule cũ nhận `sourcePackage` từ camera source global hiện tại.
- Chuẩn hóa priority theo thứ tự evaluation cũ để không làm đổi behavior ngoài ý muốn.
- Nếu dữ liệu cũ có duplicate priority, normalize thành priority duy nhất theo thứ tự cũ.
- Không silently delete rule.

Nếu thực tế có hơn 3 rule đã lưu:

- Không xóa dữ liệu.
- Giữ behavior cũ tạm thời cho các legacy rule.
- Chặn tạo rule mới.
- Hiển thị trạng thái yêu cầu người dùng giảm còn tối đa 3.
- Khi số rule <= 3, invariant mới được áp dụng hoàn toàn.

Acceptance:

- Migration không mất rule/keyword.
- Existing matcher behavior giữ nguyên.
- Upgrade test PASS.

---

# Phase 3 — Rule System V2

## P3.1 — Tối đa 3 rule + priority duy nhất

Status: **COMPLETE** on 2026-09-21.

Behavior:

- Khi chưa đủ 3 rule, cho phép thêm.
- Đủ 3 rule thì disable/hide khả năng tạo thêm và giải thích ngắn gọn.
- Không cho hai rule cùng priority.
- Khi user đổi Rule B sang priority đang thuộc Rule A, **tự swap priority** trong một transaction.
- Delete rule không được làm hỏng các priority còn lại.
- Có thể giữ khoảng priority trống nếu chỉ còn 1–2 rule; không cần tự renumber nếu không cần.

Test:

- max 3;
- create/delete;
- swap 1↔2, 1↔3, 2↔3;
- edit giữ `createdAt`;
- process restart giữ priority.

## P3.2 — Keyword editor dạng chip/card

Status: **COMPLETE** on 2026-09-21; matcher ANY/ALL unchanged.

**Không thay đổi matcher.**

Giữ:

```text
ANY = chỉ cần một keyword match
ALL = tất cả keyword phải match
```

Chỉ thay UI nhập keyword.

Thay textbox CSV bằng:

```text
Từ khóa

┌──────────────────────────────┐
│ [Phát hiện người]            │
│ [Phát hiện chuyển động]      │
│ [Phát hiện phương tiện]      │
│ ...                          │
└──────────────────────────────┘

[ + Thêm từ khóa ]
```

Yêu cầu:

- Mỗi keyword là một chip/card riêng.
- Danh sách dài nằm trong vùng scroll có chiều cao giới hạn.
- Bấm keyword -> dialog/bottom sheet sửa.
- Có `Lưu` và `Xóa`.
- `+ Thêm từ khóa` -> nhập một keyword mới.
- Không dùng dấu phẩy làm UI nhập chính nữa.
- Trim whitespace.
- Không cho keyword rỗng.
- Không duplicate keyword sau normalize case/space.
- Match mode ANY/ALL vẫn là lựa chọn cấp Rule, không phải cấp keyword.
- Migration/UI hiển thị keyword cũ thành từng chip/card mà không thay nội dung.

## P3.3 — Mỗi Rule chọn app camera riêng

Status: **COMPLETE** on 2026-09-21; global source retained only as the default for a new rule.

Trong editor Rule:

```text
Ứng dụng áp dụng
[ Icon ] Imou Life        >
```

Bấm vào -> app picker:

```text
Tìm kiếm ứng dụng...
[ danh sách app ]
```

Yêu cầu:

- Reuse source/app picker hiện tại nếu có thể.
- Search theo app label; có thể fallback package name.
- Hiển thị icon + app name; package name nhỏ nếu cần phân biệt.
- Rule không được enable/save hợp lệ nếu chưa chọn source app.
- Nhiều rule được phép dùng cùng một app.

### Trigger pipeline mới

Global camera source không còn là hard gate duy nhất.

Nguồn hợp lệ được suy ra từ các Rule đang bật:

```text
allowedPackages =
enabledRules.map(sourcePackage).toSet()
```

Notification đến:

```text
package
  -> tìm enabled rule có cùng sourcePackage
  -> sort priority 1 -> 3
  -> evaluate matcher ANY/ALL hiện tại
  -> rule đầu tiên match -> trigger
```

Privacy invariant bắt buộc:

- Package không thuộc bất kỳ enabled Rule nào:
  - không persist title/text/body;
  - không tạo history chứa notification content;
  - không log raw content ở release.
- Không regression privacy fix của P1.2.

Global source setting cũ:

- Không được tiếp tục block notification của rule khác.
- Nếu còn giữ trong UI/data, chỉ dùng làm default/preselect cho rule mới hoặc migrate legacy data.
- Xóa/deprecate hard dependency nếu không còn cần.

Test:

- 3 rule / 3 app khác nhau;
- 2 rule cùng app;
- wrong-package không persist;
- disabled rule source không trigger;
- priority evaluation deterministic.

## P3.4 — Nút `+`: Rule mới hoặc Template

Status: **COMPLETE** on 2026-09-21.

Khi bấm `+`:

```text
Tạo điều kiện cảnh báo
- Tạo mới
- Dùng mẫu đề xuất
```

### Tạo mới

Blank rule, user tự chọn app, priority, ANY/ALL và keywords.

### Dùng mẫu đề xuất

Template tiếng Việt:

```text
Tên: Camera an ninh phổ biến
Match mode: ANY

Keywords:
- phát hiện người
- đã phát hiện người
- phát hiện con người
- phát hiện chuyển động
- phát hiện chuyển động người
- phát hiện phương tiện
- phát hiện người/phương tiện
- phát hiện vượt ranh giới
- phát hiện xâm nhập
```

Flow:

1. Chọn template.
2. Chọn app áp dụng.
3. Chọn/nhận priority còn trống.
4. Cho user review/chỉnh sửa trước khi Save.

Template chỉ là dữ liệu khởi tạo, không có matcher riêng.

---

# Phase 4 — Settings UX + Default Profile

## P4.1 — Default profile mới

Status: **COMPLETE** on 2026-09-21 with versioned fresh/existing-install behavior.

Chỉ thay các default sau:

```text
Alarm sound = Âm cảnh báo lớn 1
Cooldown = 600 giây
Schedule = mỗi ngày 22:30 -> 06:00 hôm sau
```

Schedule giữ semantics hiện tại:

- start inclusive;
- end exclusive;
- overnight interval;
- device local time/timezone.

**Tất cả default/behavior khác giữ nguyên như production hiện tại.**

Không tự đổi cấu hình của user hiện tại sau app update.

Fresh install dùng default mới.

Nếu settings cũ có field chưa persist và đang phụ thuộc default code, phải có migration/versioning để **preserve effective value của user hiện tại**, không để update âm thầm thay behavior.

## P4.2 — Nút `Đặt lại mặc định`

Status: **COMPLETE** on 2026-09-21.

Settings có:

```text
[ Đặt lại mặc định ]
```

Bấm -> confirmation.

Sau xác nhận:

- Reset toàn bộ settings về default profile chính thức.
- Trong profile đó chỉ có 3 thay đổi mới ở trên; các setting khác dùng default cũ.
- Persist atomically.
- Refresh UI ngay.

Không được:

- xóa Rules;
- xóa History;
- revoke/reset system permissions;
- xóa sourcePackage trong Rule;
- tạo/xóa notification access;
- tạo phantom alarm.

Test reset sau process restart.

## P4.3 — Tổ chức Settings thành Cơ bản / Nâng cao

Status: **COMPLETE** on 2026-09-21; EN/VI share the same Compose structure.

Không đổi semantics của setting hiện có.

Gợi ý:

```text
Cơ bản
- Âm báo
- Rung
- Cooldown
- Lịch hoạt động
- các setting phổ thông hiện có

Nâng cao
- Full-screen / reliability
- quyền hệ thống
- battery/OEM guidance
- diagnostics
- các setting kỹ thuật hiện có
```

- Giữ language switch hiện tại.
- Không xóa setting cũ.
- Không tự đổi giá trị khi chỉ di chuyển UI.
- EN/VI phải cùng cấu trúc.
- Test small screen + font scale lớn.

---

# Phase 5 — Product Update Integration Gate

Trước device certification phải có một commit candidate sạch.

## P5.1 — Regression

Status: **COMPLETE** on 2026-09-21; automated contracts and API 31 integration suite PASS, with the large-font/small-screen Settings path manually exercised.

Bắt buộc test:

### Rule
- 0/1/2/3 rules.
- Không tạo rule thứ 4.
- Unique priority.
- Priority swap.
- ANY matcher.
- ALL matcher.
- Keyword add/edit/delete.
- Keyword scroll UI.
- Rule source picker/search.
- Multiple source apps.
- Template creation.
- Edit không đổi `createdAt`.
- Migration từ rule cũ.

### Privacy
- Wrong-package content không vào Room.
- Package thuộc enabled rule được xử lý.
- Disabled-rule package không trigger.
- Release log không chứa raw unrelated notification text.

### Settings
- Fresh install defaults:
  - sound = Âm cảnh báo lớn 1;
  - cooldown = 600s;
  - schedule = 22:30–06:00.
- Existing install không bị overwrite.
- Reset defaults đúng.
- Restart giữ settings.
- Schedule overnight boundary đúng.

### Alarm regression
- NotificationListener.
- Exact Alarm.
- FGS startup.
- Audio/vibration.
- Full-screen.
- STOP.
- Open Camera.
- Test Alarm isolation.
- Cooldown.
- Active hours.

## P5.2 — Build gate

Status: **COMPLETE** on 2026-09-21 at candidate `71a9315`.

```bash
./gradlew test --rerun-tasks
./gradlew lint
./gradlew assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:assembleAndroidTest
```

Nếu emulator/device có sẵn, chạy instrumentation phù hợp.

Chỉ sang Phase 6 khi candidate sạch và gate PASS.

---

# Phase 6 — Runtime / Device Certification

Thực hiện device certification **sau Rule V2 + Settings V2**, không dùng PASS của commit cũ làm bằng chứng release.

## P6.1 — API matrix

Status: **PASS on API 31 / 33 / 34 / 36** for both the Phase 6 candidate and the final production candidate `c165456`. API 36 exposed a real foreground-service/full-screen launch defect; commit `0d76d41` fixed foreground promotion and STOP dispatch. After the later Phase 7 production changes, the complete 19-test instrumentation suite passed again on every listed API with no failure/error/skip. Physical device certification remains separate.

Chạy API 31 / 33 / 34 / 36 theo khả năng môi trường.

Bao gồm:

- foreground/background;
- screen locked/off;
- Doze/idle;
- process recreation;
- reboot/listener reconnect;
- repeated notification;
- cooldown 600s;
- schedule 22:30–06:00;
- POST_NOTIFICATIONS behavior;
- exact alarm grant/deny/revoke;
- full-screen allow/deny;
- STOP từ Activity/notification/main;
- Open Camera action;
- history lifecycle.

## P6.2 — Samsung A50 physical validation

Status: **NOT VERIFIED**. The Samsung A50 disconnected from ADB before Phase 6 physical certification. Earlier Samsung database instrumentation is not promoted to this device-certification gate.

Bắt buộc vì đây là thiết bị mục tiêu hiện có.

Test:

- app background vài phút;
- screen locked;
- screen off;
- notification camera thật;
- Rule source package đúng;
- alarm audio/vibration;
- full-screen behavior;
- Open Camera;
- STOP;
- cooldown;
- reboot không mở app thủ công;
- listener recovery;
- alert thật sau reboot.

Ghi evidence và commit SHA.

## P6.3 — Xiaomi

Status:

```text
Xiaomi implementation: COMPLETE
Xiaomi physical validation: NOT VERIFIED
```

Chỉ ghi:

```text
Xiaomi implementation: COMPLETE
Xiaomi physical validation: NOT VERIFIED
```

nếu không có máy thật.

---

# Phase 7 — Remaining Hardening từ report cũ

Sau device certification mới xử lý các phần còn lại không bị feature mới thay thế.

## P7.1 History contract
Status: **COMPLETE**. Lifecycle mapping now preserves the matched Rule V2 `ruleId` and `alarmToken` directly from `TriggerSnapshot` for scheduled, fired, stopped, and pending-cancelled events; it does not depend on asynchronous Room lookup order. Wrong-package/monitoring-off content remains excluded and the existing 3-day / 100-row / 10-suppressed retention contract is unchanged.

- Chuẩn hóa lifecycle events.
- `ruleId` phải phản ánh Rule V2.
- `alarmToken` xuyên lifecycle.
- Retention giữ contract hiện tại đã chốt.
- Không lưu nội dung unrelated app.

## P7.2 Backup / restore
Status: **COMPLETE (policy/build), restore transport NOT VERIFIED**. Backup is an explicit settings-only allowlist for both Android 11-and-earlier Auto Backup and Android 12+ cloud/device transfer. Room data and `alarm_runtime_state` are excluded, so notification previews, Pending/Ringing state, token, owner nonce, and runtime snapshot cannot be restored. API 36 Backup Manager was exercised, but its local transport rejected the release package during preflight and produced no restorable application dataset; no restore PASS is claimed.

- Exclude history preview nhạy cảm.
- Exclude runtime Pending/Ringing/token/nonce.
- Restore không tạo phantom alarm.

## P7.3 Battery/OEM guidance
Status: **COMPLETE (implementation)**. Battery guidance opens the policy-safe optimization list rather than directly requesting exemption, all intents have defensive app-settings fallback, and failures are surfaced to the user. Samsung/Xiaomi guidance is localized and remains manual/defensive; physical OEM behavior is still reported separately as NOT VERIFIED where hardware is unavailable.

- Hoàn thiện battery optimization flow.
- Không fail silent.
- Giữ Samsung/Xiaomi guidance defensive.

## P7.4 Localization/UI
Status: **PASS for code/resources and final emulator interaction smoke**. Rule V2, Settings V2, Main, Diagnostics, battery, and OEM user-facing messages use EN/VI resources. A locale instrumentation contract covers core Rule/Settings and Samsung/Xiaomi guidance. On the signed API 36 build, Vietnamese loaded by default, switching to English through Settings persisted across process restart and the signed 1.0-to-1.1 upgrade; font scale 1.5 and dark/light modes remained navigable through the UI hierarchy. The headless emulator returned black screencap frames, so no final pixel-level screenshot PASS is claimed; the earlier Phase 5 360 dp/font-1.5 layout exercise remains the visual layout evidence.

- Không hard-code user-facing EN/VI.
- Review Rule V2 + Settings V2 ở cả hai locale.
- Font scale lớn, small screen, dark mode.

## P7.5 BootReceiver / full-screen cleanup
Status: **PASS**. `BootReceiver` is non-exported and validates a fixed action allowlist. AlarmReceiver no longer launches UI directly; CameraAlarmService remains the single full-screen launch owner and keeps the API 36-proven notification/fallback behavior. Deprecated screen wake-lock and notification-priority calls were removed; API 36 uses the current BAL mode. The affected complete instrumentation suite passed 19/19 on API 31, 33, 34, and 36 on final production candidate `c165456`; signed API 36 Test Alarm also reached foreground/full-screen and STOP cleared the activity, service, and notification.

- Giới hạn receiver exposure.
- Chỉ đơn giản hóa full-screen launch path sau khi có device evidence.
- Không làm regression locked-screen alarm.

## P7.6 Test quality / technical debt
Status: **COMPLETE for scoped debt**. New tests call the production history mapper, boot action policy, manifest component metadata, localized resource contexts, and existing shared `TestAlarmController`; no broad composition-root refactor was introduced. Final device instrumentation remains an environment gate.

- Ưu tiên production-path tests.
- Triage AppContainer/Test Alarm controller nếu còn debt.
- Không refactor rộng chỉ để “đẹp code”.

---

# Phase 8 — Release Engineering + Final Audit

## P8.1 Lint/dependency
Status: **PASS with triaged non-blocking warnings**. Lint has no behavior, permission/policy, accessibility, deprecated-API, or context-leak warning. Remaining warnings are dependency-update candidates, unused legacy resources, plural/typography suggestions, and one obsolete resource qualifier; dependency upgrades are deferred to a separate compatibility batch.

- Triage behavior/policy/accessibility trước.
- Không cần zero-warning tuyệt đối.
- Dependency upgrade theo batch riêng nếu thực sự cần.

## P8.2 Signed release
Status: **PASS for signing/fresh-install/upgrade/Test Alarm; real camera trigger NOT VERIFIED**.

- Release identity: versionCode `2`, versionName `1.1.0`.
- Signing key and DPAPI-protected password are outside the repository under the current Windows user profile.
- `camera-alarm-1.1.0-signed.apk` verifies with APK Signature Scheme v2/v3, one RSA-4096 signer.
- SHA-256: `5487B9A063748B6BDE96858D7865DAAB69C4A0F0190A54FD2096C71B9D98E6C9`.
- API 36 fresh install and cold launch: PASS.
- Real upgrade path: versionCode 1 / versionName 1.0 APK built from `0d76d41`, signed with the same release key, installed, configured to English, and upgraded with `adb install -r` to versionCode 2 / versionName 1.1.0. `firstInstallTime` and the saved language were preserved: PASS.
- Signed Test Alarm + foreground service + full-screen AlarmActivity + STOP cleanup: PASS on API 36.
- A notification from a real camera application on the signed build was not available in this environment: **NOT VERIFIED**. Test Alarm is not promoted as equivalent evidence.

- versionCode/versionName;
- signing secret ngoài repo;
- signed APK;
- verify signature;
- SHA-256;
- install smoke;
- fresh install;
- upgrade install;
- real alarm + STOP trên signed build.

## P8.3 Final report

Status: **COMPLETE**. See `docs/review/final-release-report.md`. The report keeps Samsung A50, Xiaomi, Backup Manager restore, and signed real-camera notification as explicit `NOT VERIFIED` release limits.

Cập nhật/tạo:

```text
docs/review/final-release-report.md
```

Phải gồm:

- commit SHA release;
- status các issue report cũ;
- Rule V2 verification;
- Settings V2 verification;
- API/device matrix;
- Samsung result;
- Xiaomi status;
- privacy/security;
- remaining limitations;
- signed artifact;
- final verdict.

---

# Definition of Done

Chỉ ghi `READY FOR RELEASE` khi:

- Phase 1 release gate PASS.
- Rule V2 hoàn tất và migration PASS.
- Max 3 rule + unique priority hoạt động.
- ANY/ALL matcher không regression.
- Per-rule camera source hoạt động.
- Wrong-package privacy vẫn PASS.
- Template Việt Nam hoạt động.
- Default profile đúng:
  - Âm cảnh báo lớn 1;
  - cooldown 600s;
  - 22:30–06:00.
- Reset defaults không xóa rules/history/permissions.
- Existing install không bị overwrite settings ngoài ý muốn.
- AndroidTest compile/run PASS.
- API/device matrix tương ứng PASS.
- Samsung A50 physical validation PASS.
- Không còn Critical/High unresolved.
- Signed release artifact smoke-tested.
- Repo sạch.

Nếu Xiaomi chưa test thật:

```text
READY FOR RELEASE
Xiaomi physical validation: NOT VERIFIED
```

Không được tuyên bố Xiaomi fully verified.

---

# Agent Final Report Format

```text
## Overall Status

Phase 1:
Phase 2:
Phase 3:
Phase 4:
Phase 5:
Phase 6:
Phase 7:
Phase 8:

## Rule V2

Max rules:
Priority:
ANY/ALL:
Keyword editor:
Per-rule source:
Template:
Migration:

## Settings V2

Default sound:
Default cooldown:
Default schedule:
Reset defaults:
Basic/Advanced UI:
Existing-user migration:

## Verification

Unit:
Lint:
Debug:
Release:
AndroidTest:
Instrumentation:
API 31:
API 33:
API 34:
API 36:
Samsung A50:
Xiaomi:

## Reliability

Screen lock:
Doze:
Process recreation:
Reboot:
STOP:
Open Camera:

## Privacy

Wrong-package persistence:
Backup policy:

## Remaining Limitations

- ...

## Git

Branch:
Final commit:
Working tree:
Ahead/behind origin:

## Final Verdict

READY FOR RELEASE

or

NOT READY FOR RELEASE
```
