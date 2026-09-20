# Camera Alarm — Release Hardening Task Plan

> Source of truth: `docs/review/report.md`.
>
> Mục tiêu của file này là biến toàn bộ finding trong report thành kế hoạch triển khai A-Z có thứ tự, acceptance criteria, test gate và điều kiện chuyển phase rõ ràng.
>
> Không tự thêm feature ngoài scope report. Không refactor rộng nếu chưa cần cho issue cụ thể.

---

# 0. Nguyên tắc làm việc

## 0.1 Đọc trước khi code

Agent phải đọc:

- `docs/agent/agent.md`
- `docs/agent/task.md` hiện tại nếu còn dùng cho lịch sử dự án
- `docs/review/report.md`
- các file verification hiện có
- tài liệu liên quan trong `docs/project/`
- source/test trực tiếp liên quan task hiện tại

## 0.2 Thứ tự ưu tiên

Thực hiện theo thứ tự:

```text
Phase 0  Baseline + safety
   ↓
Phase 1  Release gate + High-risk fixes
   ↓
Phase 2  Runtime/device certification
   ↓
Phase 3  Data / Privacy / UX / Maintainability
   ↓
Phase 4  Release engineering + final review
```

Không chuyển phase nếu gate cuối phase chưa đạt, trừ trường hợp có giới hạn môi trường đã được ghi rõ.

## 0.3 Quy tắc sửa code

- Fix root cause, không che lỗi.
- Mỗi issue phải có regression test nếu khả thi.
- Không đổi core alarm architecture nếu không thật sự cần thiết.
- Không nâng dependency hàng loạt trong hotfix phase.
- Không thêm permanent foreground service.
- Không đánh dấu PASS cho test chưa chạy thật.
- Không coi emulator là bằng chứng cho OEM-specific behavior.
- Không commit screenshot/log/ADB dump tạm.

## 0.4 Build gate chung

Sau mỗi task quan trọng:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Khi task liên quan Android instrumentation:

```bash
./gradlew :app:assembleAndroidTest
```

Khi có emulator/device phù hợp:

```bash
./gradlew connectedDebugAndroidTest
```

Trước final release review:

```bash
./gradlew test --rerun-tasks
./gradlew lint
./gradlew assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:assembleAndroidTest
```

---

# Phase 0 — Baseline, Inventory và Safety Net

## P0.1 — Chụp baseline repository

### Mục tiêu

Có bằng chứng chính xác về trạng thái trước hardening để tránh sửa nhầm hoặc đánh mất behavior đã verified.

### Checklist

- [x] `git status` sạch hoặc ghi rõ thay đổi tồn tại.
- [x] Ghi commit SHA hiện tại.
- [x] `git log --oneline -10` để hiểu các fix gần nhất.
- [x] Xác nhận branch làm việc là `main`.
- [x] Xác định current target/compile/min SDK.
- [x] Liệt kê test source sets hiện có.
- [x] Liệt kê emulator/device hiện có bằng `adb devices -l`.
- [x] Liệt kê AVD/API có thể chạy.
- [x] Ghi baseline trong `docs/review/hardening-verification.md`.

### Verification

- [x] `./gradlew test lint assembleDebug` chạy và kết quả được ghi lại.
- [x] `./gradlew :app:assembleRelease` chạy và kết quả được ghi lại.
- [x] `./gradlew :app:assembleAndroidTest` chạy để xác nhận ISSUE-01 còn tồn tại hoặc đã được sửa bởi code mới hơn.

### Gate

Không sửa gì khác trước khi baseline được ghi lại.

---

# Phase 1 — Khôi phục Release Gate và đóng rủi ro High

> Phase này xử lý ISSUE-01 → ISSUE-05 và ISSUE-07 vì đây là nhóm ảnh hưởng trực tiếp release gate, privacy và reliability.

---

## P1.1 — ISSUE-01: Sửa instrumentation release gate

### Problem

`DatabaseInstrumentedTest` gọi DAO API cũ và retention expectation cũ, làm `assembleAndroidTest` fail compile.

### Mục tiêu

Khôi phục toàn bộ instrumentation pipeline trước khi làm device certification.

### Checklist

- [x] Mở `DatabaseInstrumentedTest` và DAO production hiện tại.
- [x] Xác định contract retention đang được production sử dụng thật.
- [x] Chốt contract duy nhất cho release candidate.
- [x] Đồng bộ test với API DAO hiện tại, không thêm compatibility method giả chỉ để test pass.
- [x] Nếu retention contract là `100 event / 3 ngày / max suppressed policy`, cập nhật test đúng contract đó.
- [x] Nếu report/docs khác production, cập nhật docs sau khi contract được chốt.
- [x] Thêm test cho:
  - [x] prune theo thời gian;
  - [x] prune theo max count;
  - [x] suppressed cap nếu production vẫn có;
  - [x] pagination không vỡ sau prune.
- [x] Chạy `:app:assembleAndroidTest`.
- [x] Chạy instrumentation DB test thật trên emulator nếu có.

### Acceptance

- [x] `:app:assembleAndroidTest` PASS.
- [x] Không còn reference tới DAO API đã xoá.
- [x] Test phản ánh production contract thật, không phải contract lịch sử.

### Commit gợi ý

```text
test: restore database instrumentation release gate
```

---

## P1.2 — ISSUE-02: Không lưu nội dung notification ngoài source đã chọn

### Problem

Pipeline đang ghi title/text preview của wrong-package notification vào history.

### Mục tiêu

Đảm bảo app chỉ persist dữ liệu notification cần thiết cho chức năng camera alarm.

### Required behavior

```text
Notification từ selected camera package
    -> có thể lưu preview theo policy hiện tại

Notification từ package khác
    -> không lưu title/text/body
    -> không tạo history row chứa nội dung nhạy cảm
```

### Checklist

- [ ] Audit `TriggerPipeline.process()` và history callback trong `AppContainer`.
- [ ] Xác định chính xác nơi `IGNORED_WRONG_PACKAGE` được persist.
- [ ] Sửa để wrong-package không persist nội dung notification.
- [ ] Nếu vẫn cần diagnostic count:
  - [ ] chỉ dùng in-memory counter hoặc metadata tối thiểu;
  - [ ] không lưu title/text/subtext;
  - [ ] không log raw text ở release build.
- [ ] Review history schema để chắc không có hidden/raw payload field chứa text ngoài source.
- [ ] Thêm regression test:
  - [ ] wrong package không tạo DB row có content;
  - [ ] selected package vẫn ghi history đúng;
  - [ ] debug log không lộ full text trong release configuration nếu testable.
- [ ] Kiểm tra History UI không phụ thuộc vào wrong-package rows cũ.
- [ ] Nếu cần migration/cleanup existing wrong-package data, tạo cleanup an toàn.

### Acceptance

- [ ] Room không lưu preview từ package ngoài source đã chọn.
- [ ] Core trigger behavior không đổi.
- [ ] Privacy regression test PASS.

### Commit gợi ý

```text
fix: stop persisting unrelated notification content
```

---

## P1.3 — ISSUE-03: Loại bỏ blocking DataStore khỏi critical FGS startup

### Problem

`CameraAlarmService` dùng `runBlocking` trên main thread để đọc settings trước/giữa đường `startForeground()`.

### Mục tiêu

Foreground promotion và alarm startup không phụ thuộc I/O chậm.

### Target architecture

```text
AlarmReceiver / schedule-time snapshot
        ↓
immutable alarm runtime config
        ↓
CameraAlarmService.onStartCommand()
        ↓
startForeground() NGAY
        ↓
start audio/vibration
        ↓
async/non-blocking optional work
```

### Checklist

- [ ] Tìm tất cả `runBlocking` trong `CameraAlarmService` và critical alarm path.
- [ ] Phân loại setting nào bắt buộc ngay:
  - [ ] sound key;
  - [ ] vibration;
  - [ ] full-screen preference;
  - [ ] source/package metadata;
  - [ ] alarm token.
- [ ] Chọn một strategy rõ ràng:
  - [ ] cached settings snapshot; hoặc
  - [ ] immutable extras tại schedule/start time; hoặc
  - [ ] application-level StateFlow cache đã warm.
- [ ] Không đọc DataStore blocking trước `startForeground()`.
- [ ] `startForeground()` phải hoàn tất bằng data sẵn có.
- [ ] Audio fallback vẫn tồn tại nếu selected sound config unavailable.
- [ ] Full-screen config unavailable không được chặn audio alarm.
- [ ] Settings read chậm/fail không crash service.
- [ ] Thêm test settings source delay lớn.
- [ ] Thêm test cold process/service startup nếu có thể.
- [ ] Đo/ghi timestamp:
  - [ ] receiver fired;
  - [ ] service start requested;
  - [ ] startForeground completed;
  - [ ] audio started.

### Acceptance

- [ ] Không còn `runBlocking` trên service main-thread critical path.
- [ ] startForeground không chờ DataStore.
- [ ] Slow-settings regression test PASS.
- [ ] Alarm vẫn dùng đúng setting trong normal path.

### Commit gợi ý

```text
fix: remove blocking settings io from alarm startup
```

---

## P1.4 — ISSUE-04: Hardening boot listener recovery

### Problem

Boot reconciliation request rebind rồi toggle component gần như ngay lập tức, có khả năng race với NotificationManager/OEM.

### Mục tiêu

Listener recovery có state rõ ràng, retry có giới hạn và không thay đổi user-granted state ngoài ý muốn.

### Checklist

- [ ] Audit `DefaultBootReconciler` production path.
- [ ] Audit component toggle workaround hiện tại.
- [ ] Xác định behavior chuẩn:
  - [ ] requestRebind;
  - [ ] wait/retry bounded;
  - [ ] no infinite polling;
  - [ ] no permanent FGS watchdog.
- [ ] Không toggle component ngay chỉ vì listener chưa CONNECTED tức thời.
- [ ] Nếu vẫn giữ OEM workaround:
  - [ ] chỉ activate trên OEM/version đã chứng minh cần;
  - [ ] timeout rõ;
  - [ ] fallback an toàn;
  - [ ] diagnostics rõ.
- [ ] Không disable/enable listener theo cách làm mất user permission.
- [ ] Thêm production-path tests cho BootReconciler, không replica logic.
- [ ] Test:
  - [ ] access granted + delayed connect;
  - [ ] access denied;
  - [ ] listener already connected;
  - [ ] requestRebind failure/no callback;
  - [ ] reboot with stale runtime state.
- [ ] Cập nhật diagnostics để phân biệt:
  - [ ] access granted;
  - [ ] connected;
  - [ ] reconnecting;
  - [ ] disconnected.

### Acceptance

- [ ] Không có immediate component toggle race.
- [ ] Boot recovery deterministic và bounded.
- [ ] Production reconciler được test trực tiếp.

### Commit gợi ý

```text
fix: harden notification listener recovery after boot
```

---

## P1.5 — ISSUE-07: Chặn Test Alarm xung đột production alarm

### Problem

Test Alarm có thể tạo test token trong khi production Pending/Ringing; STOP có thể ưu tiên nhầm test token.

### Mục tiêu

Production alarm luôn có ưu tiên tuyệt đối.

### Required rules

```text
Production Pending/Ringing
    -> Test Alarm disabled

Production starts while Test Alarm preview/runtime exists
    -> Test Alarm stops
    -> Production owns runtime

STOP
    -> always stops active production token first
```

### Checklist

- [ ] Audit test alarm token lifecycle.
- [ ] Audit MainViewModel / Diagnostics / Settings STOP behavior.
- [ ] Không publish test token trước khi service chấp nhận test start.
- [ ] Disable Test Alarm UI khi production `Pending` hoặc `Ringing`.
- [ ] Nếu production trigger xuất hiện khi test runtime active:
  - [ ] stop test runtime;
  - [ ] clear test token;
  - [ ] start production path.
- [ ] STOP ưu tiên production active token.
- [ ] Repeated STOP idempotent.
- [ ] Thêm regression tests cho tất cả interleavings chính.

### Acceptance

- [ ] Test Alarm không thể chặn production alarm.
- [ ] STOP không gửi stale test token khi production đang active.
- [ ] Regression suite PASS.

### Commit gợi ý

```text
fix: isolate test alarm from production runtime
```

---

## P1.6 — Phase 1 full regression gate

### Checklist

- [ ] `./gradlew test --rerun-tasks`
- [ ] `./gradlew lint`
- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew :app:assembleRelease`
- [ ] `./gradlew :app:assembleAndroidTest`
- [ ] instrumentation tests chạy được trên ít nhất một emulator.
- [ ] Không còn High issue nào của P1 chưa xử lý.
- [ ] Cập nhật `docs/review/hardening-verification.md`.

### Phase 1 Gate

Chỉ chuyển Phase 2 khi:

```text
Unit PASS
Lint PASS
Debug build PASS
Release build PASS
AndroidTest compile PASS
Privacy fix PASS
FGS startup fix PASS
Boot recovery tests PASS
Test Alarm conflict tests PASS
```

---

# Phase 2 — Runtime / Device Certification

> Mục tiêu: chứng minh đúng release candidate hoạt động trên runtime Android thật, không dựa vào PASS lịch sử của commit cũ.

---

## P2.1 — Chuẩn bị release candidate verification

### Checklist

- [ ] Chọn một commit SHA duy nhất làm candidate.
- [ ] Không sửa source trong khi đang chạy matrix trừ bug được phát hiện.
- [ ] Nếu có fix, tạo commit mới và chạy lại toàn bộ affected matrix.
- [ ] Ghi mỗi device/emulator:
  - [ ] manufacturer/model;
  - [ ] API;
  - [ ] Android version;
  - [ ] build fingerprint;
  - [ ] commit SHA;
  - [ ] permission state;
  - [ ] test result.

---

## P2.2 — API 31 matrix

### Test

- [ ] install/fresh start.
- [ ] Notification Access grant/deny.
- [ ] Exact alarm behavior phù hợp API.
- [ ] foreground trigger.
- [ ] background trigger.
- [ ] screen locked.
- [ ] screen off.
- [ ] repeated notifications.
- [ ] cooldown.
- [ ] STOP từ AlarmActivity.
- [ ] STOP từ notification.
- [ ] STOP từ main screen.
- [ ] process kill/recreation.
- [ ] Doze/idle nếu môi trường hỗ trợ.
- [ ] reboot/listener reconnect.

### Gate

- [ ] Không crash.
- [ ] Không duplicate runtime.
- [ ] Alarm/STOP semantics đúng.

---

## P2.3 — API 33 matrix

Ngoài toàn bộ P2.2, thêm:

- [ ] POST_NOTIFICATIONS granted.
- [ ] POST_NOTIFICATIONS denied.
- [ ] app vẫn không crash nếu denied.
- [ ] STOP path còn khả dụng qua UI phù hợp.

---

## P2.4 — API 34 matrix

Ngoài toàn bộ baseline:

- [ ] FGS type `systemExempted` đúng.
- [ ] full-screen capability allowed.
- [ ] full-screen capability denied.
- [ ] locked-screen UI.
- [ ] unlocked-screen UI.
- [ ] audio/vibration không phụ thuộc AlarmActivity.
- [ ] full-screen denied vẫn alarm được.

---

## P2.5 — API 36 matrix

### Test

- [ ] toàn bộ latest-target behavior.
- [ ] exact alarm capability grant/revoke.
- [ ] POST_NOTIFICATIONS deny.
- [ ] full-screen allow/deny.
- [ ] process recreation.
- [ ] screen lock.
- [ ] Doze.
- [ ] reboot.
- [ ] STOP idempotency.
- [ ] history lifecycle correctness.

---

## P2.6 — Samsung physical validation

> Bắt buộc vì thiết bị mục tiêu thực tế là Samsung.

### Checklist

- [ ] Ghi model và One UI/Android version.
- [ ] Camera Alarm nằm trong Never Sleeping/appropriate battery config.
- [ ] Notification Access enabled.
- [ ] App ở background ít nhất vài phút.
- [ ] Screen locked.
- [ ] Screen off.
- [ ] Real camera notification.
- [ ] Alarm audio starts.
- [ ] vibration starts.
- [ ] full-screen behavior được ghi nhận.
- [ ] STOP hoạt động.
- [ ] Open-camera action hoạt động.
- [ ] repeated alert/cooldown hoạt động.
- [ ] reboot rồi không mở app thủ công.
- [ ] listener phục hồi.
- [ ] real camera alert sau reboot hoạt động.

### Evidence

- [ ] log/checkpoint hoặc history timeline.
- [ ] không chỉ ghi “PASS” không có bằng chứng.

---

## P2.7 — Xiaomi / Redmi / POCO physical validation

Chỉ bắt buộc nếu app tuyên bố hỗ trợ Xiaomi/HyperOS.

### Checklist

- [ ] real Xiaomi-family device.
- [ ] HyperOS/MIUI version.
- [ ] Autostart configured.
- [ ] Battery No Restrictions.
- [ ] relevant popup/lock-screen permission.
- [ ] background lock nếu cần.
- [ ] screen-off real camera trigger.
- [ ] selected sound.
- [ ] STOP.
- [ ] reboot + post-reboot real camera trigger.

Nếu không có device:

```text
Xiaomi implementation: COMPLETE
Xiaomi physical validation: NOT VERIFIED
```

Không được ghi fully verified.

---

## P2.8 — Phase 2 certification document

Tạo/cập nhật:

```text
docs/review/runtime-certification.md
```

Phải có:

- [ ] commit SHA;
- [ ] API/device matrix;
- [ ] command đã chạy;
- [ ] permission state;
- [ ] scenario result;
- [ ] bug phát hiện;
- [ ] bug fix commit nếu có;
- [ ] remaining limitations.

### Phase 2 Gate

Không chuyển trạng thái release-ready nếu current release candidate chưa có matrix tương ứng.

---

# Phase 3 — Data, Privacy, UX và Maintainability Hardening

---

## P3.1 — ISSUE-06: Chuẩn hóa History contract

### Mục tiêu

Một contract duy nhất giữa docs, production, DB, UI và tests.

### Checklist

- [ ] Chốt retention policy chính thức.
- [ ] Đồng bộ docs với production.
- [ ] Chuẩn hóa decisions tối thiểu:
  - [ ] SCHEDULED;
  - [ ] ALARM_FIRED;
  - [ ] ALARM_STOPPED;
  - [ ] ALARM_RUNTIME_ERROR;
  - [ ] SUPPRESSED_COOLDOWN;
  - [ ] SUPPRESSED_OUTSIDE_ACTIVE_HOURS;
  - [ ] BOOT_RECONCILED;
  - [ ] SCHEDULE_FAILED.
- [ ] Receiver/service thật sự emit lifecycle events tương ứng.
- [ ] `ruleId` được ghi khi có rule match.
- [ ] `alarmToken` xuyên suốt lifecycle.
- [ ] `normalizedHash` chỉ giữ nếu có mục đích diagnostic rõ.
- [ ] Boot event có UI mapping riêng, không hiện như error mặc định.
- [ ] History filters đồng bộ với event enum.
- [ ] Không dùng raw unversioned string nếu có thể chuyển sang enum/version-safe adapter.
- [ ] Regression test cho history lifecycle một alarm hoàn chỉnh.

### Acceptance

- [ ] Có thể nhìn history và biết alarm đã schedule, fire, stop hay fail.
- [ ] Không có decision UI mà production không bao giờ emit.

---

## P3.2 — ISSUE-08: Định nghĩa backup / data extraction policy

### Mục tiêu

Không backup dữ liệu nhạy cảm hoặc runtime state không phù hợp.

### Checklist

- [ ] Xác định dữ liệu nào có thể backup:
  - [ ] user preferences an toàn;
  - [ ] rules nếu muốn;
  - [ ] schedules nếu muốn.
- [ ] Exclude:
  - [ ] history notification previews;
  - [ ] pending/ringing runtime state;
  - [ ] alarm token/runtime nonce;
  - [ ] dữ liệu diagnostic nhạy cảm.
- [ ] Định nghĩa `android:dataExtractionRules` cho Android 12+.
- [ ] Định nghĩa `android:fullBackupContent` cho Android <=11 nếu cần.
- [ ] Hoặc tắt backup hoàn toàn nếu phù hợp sản phẩm cá nhân.
- [ ] Test restore không tạo phantom alarm.
- [ ] Document backup policy.

### Acceptance

- [ ] Device transfer/restore không làm sống lại runtime alarm cũ.
- [ ] Sensitive history không bị backup ngoài kỳ vọng.

---

## P3.3 — ISSUE-09: Hoàn thiện battery optimization flow

### Checklist

- [ ] Audit direct `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` path.
- [ ] Quyết định distribution mode thực tế: personal sideload hay store.
- [ ] Nếu dùng direct exemption:
  - [ ] manifest/permission phù hợp;
  - [ ] behavior được test;
  - [ ] policy risk được document.
- [ ] Nếu không dùng direct exemption:
  - [ ] mở generic battery optimization settings;
  - [ ] hướng dẫn thủ công;
  - [ ] detect/fallback intent an toàn.
- [ ] Không catch lỗi im lặng; hiển thị feedback/diagnostics.
- [ ] Samsung/Xiaomi advisor dùng chung reliability state hợp lý.

### Acceptance

- [ ] User biết rõ còn thiếu battery configuration gì.
- [ ] Action không fail silent.

---

## P3.4 — ISSUE-10: Hoàn thiện localization EN/VI

### Checklist

- [ ] Quét toàn bộ user-facing hardcoded strings.
- [ ] Chuyển sang resources.
- [ ] Reliability advisor không hard-code tiếng Việt.
- [ ] Validation/snackbar không hard-code tiếng Anh.
- [ ] Diagnostics labels dùng resources nơi phù hợp.
- [ ] Kiểm tra English UI không lẫn Vietnamese.
- [ ] Kiểm tra Vietnamese UI không lẫn English không cần thiết.
- [ ] Review wording tiếng Việt cho người phổ thông.
- [ ] Test font scale lớn.
- [ ] Test Samsung A50/small screen layout.
- [ ] Test text overflow.
- [ ] Test dark mode.
- [ ] Thêm Compose smoke/UI test nếu framework hiện có cho phép.

### Acceptance

- [ ] Hai locale nhất quán.
- [ ] Không còn layout vỡ vì text tiếng Việt dài.

---

## P3.5 — ISSUE-11: Giữ deterministic rule order khi edit

### Checklist

- [ ] Khi edit rule, giữ nguyên `createdAtEpochMs`.
- [ ] Nếu cần, dùng `updatedAtEpochMs` riêng.
- [ ] Không đổi tie-break order chỉ vì sửa keyword/name.
- [ ] Test hai rule cùng priority trước/sau edit.
- [ ] Test matcher chọn rule ổn định.

### Acceptance

- [ ] Edit không âm thầm thay match order.

---

## P3.6 — ISSUE-12: Hardening BootReceiver exposure

### Checklist

- [ ] Liệt kê action chuẩn và vendor QUICKBOOT action.
- [ ] Xác định action nào bắt buộc exported.
- [ ] Tách system-protected path và vendor compatibility path nếu cần.
- [ ] Validate accepted action trong receiver.
- [ ] Không để arbitrary custom broadcast reset runtime state.
- [ ] Thêm sender/source policy nếu platform cho phép.
- [ ] Test unknown action bị ignore.
- [ ] Test spoof-like explicit vendor action theo khả năng môi trường.
- [ ] Physical OEM test nếu giữ vendor actions.

### Acceptance

- [ ] Receiver exposure nhỏ nhất có thể.
- [ ] Broadcast không hợp lệ không gây reconciliation side effect.

---

## P3.7 — ISSUE-13: Đơn giản hóa full-screen launch path sau khi có device evidence

### Mục tiêu

Chỉ làm sau Phase 2 matrix để tránh phá behavior screen lock đang hoạt động.

### Checklist

- [ ] Lập sơ đồ các launch path hiện tại:
  - [ ] notification full-screen PendingIntent;
  - [ ] direct startActivity;
  - [ ] fallback khác.
- [ ] Dựa trên device evidence chọn primary path.
- [ ] Giữ fallback có điều kiện và đo lường được.
- [ ] Loại duplicate launch attempt nếu không cần.
- [ ] Review deprecated wake/full-screen APIs.
- [ ] Thay API deprecated chỉ khi không làm giảm reliability.
- [ ] Re-run locked/unlocked API 34/36 + Samsung device.

### Acceptance

- [ ] Không double-launch.
- [ ] Screen-lock alarm không regression.

---

## P3.8 — ISSUE-14: Nâng chất lượng test từ logic-replica sang production-path

### Checklist

- [ ] Audit test nào copy logic thay vì gọi production class.
- [ ] Ưu tiên sửa test cho:
  - [ ] BootReconciler;
  - [ ] source selection;
  - [ ] ViewModel integration;
  - [ ] alarm start/stop controller;
  - [ ] history retention;
  - [ ] settings persistence.
- [ ] Thêm test backup/restore contract.
- [ ] Thêm migration test.
- [ ] Thêm slow-I/O FGS regression test.
- [ ] Thêm UI/font scale/accessibility smoke test nếu hợp lý.
- [ ] Thêm resource lifecycle/soak test nếu environment hỗ trợ.

### Acceptance

- [ ] Test count không phải mục tiêu; production boundary coverage mới là mục tiêu.

---

## P3.9 — Technical debt: AppContainer và Test Alarm controller

### Mục tiêu

Giảm wiring/policy side effects nhưng không refactor lớn trong một commit.

### Checklist

- [ ] Xác định logic history policy đang nằm trong `AppContainer`.
- [ ] Tách use-case/factory nhỏ nếu giúp test production path.
- [ ] Không tạo DI framework mới nếu không cần.
- [ ] Gom Start/Stop Test Alarm vào một controller/use case dùng chung.
- [ ] ViewModel chỉ gọi use case/controller.
- [ ] Giữ behavior y hệt sau refactor.
- [ ] Regression tests trước/sau.

### Acceptance

- [ ] Giảm duplicate Test Alarm logic.
- [ ] Composition root đơn giản hơn mà không tăng framework complexity.

---

## P3.10 — Room schema export và migration framework

### Checklist

- [ ] Bật `exportSchema = true`.
- [ ] Commit schema JSON đúng convention.
- [ ] Thiết lập migration test infrastructure.
- [ ] Nếu schema vẫn v1, chuẩn bị đường migration v1→v2 trước thay đổi schema tiếp theo.
- [ ] Không dùng destructive migration cho dữ liệu người dùng nếu chưa có quyết định rõ.
- [ ] Test upgrade DB release-like.

### Acceptance

- [ ] Có migration strategy trước schema version tiếp theo.

---

## P3.11 — Phase 3 gate

### Checklist

- [ ] privacy tests PASS.
- [ ] history lifecycle tests PASS.
- [ ] backup policy tests PASS.
- [ ] localization smoke PASS.
- [ ] rule ordering PASS.
- [ ] boot receiver security tests PASS.
- [ ] migration tests PASS.
- [ ] `test lint assembleDebug assembleRelease assembleAndroidTest` PASS.

---

# Phase 4 — Lint, Dependencies, Release Packaging và Final Release Review

---

## P4.1 — ISSUE-15: Triage lint warnings

### Mục tiêu

Không cần zero-warning tuyệt đối; phải xử lý warning liên quan behavior/policy/release trước.

### Checklist

Phân loại warnings:

- [ ] Behavior/API deprecated.
- [ ] Battery/policy.
- [ ] Locale/plural.
- [ ] Accessibility.
- [ ] Icon/density/resource.
- [ ] Dependency update.
- [ ] Cosmetic/low-risk.

Ưu tiên:

1. behavior/policy;
2. deprecated API ảnh hưởng target SDK;
3. accessibility/localization;
4. resource correctness;
5. dependency update.

- [ ] Không blanket suppress.
- [ ] Mỗi suppression phải có lý do.
- [ ] Ghi remaining warnings có chủ đích.

---

## P4.2 — Dependency upgrade theo batch

### Rule

Không nâng toàn bộ cùng lúc.

### Suggested batches

```text
Batch A: Kotlin / AGP / Compose toolchain
Batch B: AndroidX lifecycle/activity/navigation
Batch C: Room/DataStore
Batch D: test libraries
```

### Mỗi batch

- [ ] đọc release notes liên quan;
- [ ] upgrade nhỏ nhất hợp lý;
- [ ] compile;
- [ ] unit test;
- [ ] instrumentation;
- [ ] runtime smoke;
- [ ] commit riêng.

Nếu upgrade không cần cho release hiện tại, có thể defer và ghi rõ.

---

## P4.3 — Signed release pipeline

### Checklist

- [ ] Xác định versionCode/versionName.
- [ ] Signing config không commit secret vào repo.
- [ ] Build signed release artifact.
- [ ] Verify signature.
- [ ] Ghi SHA-256 artifact.
- [ ] Smoke-install signed APK trên device.
- [ ] Fresh install flow PASS.
- [ ] Upgrade từ previous internal build PASS nếu applicable.
- [ ] Alarm trigger/STOP trên signed release PASS.
- [ ] Không để debug-only exported component trong release.
- [ ] Debug logging nhạy cảm disabled.

---

## P4.4 — Release checklist hoàn chỉnh

### Checklist

- [ ] App name/icon/splash đúng.
- [ ] Version đúng.
- [ ] Privacy policy nội bộ/README nếu cần.
- [ ] Permissions được giải thích.
- [ ] Backup policy final.
- [ ] Samsung verification final.
- [ ] Xiaomi verification status trung thực.
- [ ] Full-screen behavior final.
- [ ] Reboot/listener recovery final.
- [ ] History privacy final.
- [ ] Signed artifact test final.

---

# Phase 5 — Final Audit Against Original Report

> Không phải feature phase. Đây là vòng đóng issue.

## P5.1 — Map ISSUE-01 → ISSUE-15

Tạo bảng trong:

```text
docs/review/final-release-report.md
```

Format:

| Issue | Status | Fix commit | Test evidence | Remaining limitation |
|---|---|---|---|---|
| ISSUE-01 | | | | |
| ... | | | | |
| ISSUE-15 | | | | |

Mỗi issue chỉ được đánh dấu `CLOSED` khi có fix + evidence phù hợp.

---

## P5.2 — Final commands

Chạy trên clean checkout/current main:

```bash
./gradlew clean
./gradlew test --rerun-tasks
./gradlew lint
./gradlew assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:assembleAndroidTest
```

Nếu có device/emulator matrix:

- [ ] connected instrumentation PASS.
- [ ] runtime certification doc khớp commit SHA final.

---

## P5.3 — Final decision

Chỉ được ghi:

```text
READY FOR RELEASE
```

khi:

- [ ] ISSUE-01 → ISSUE-05 CLOSED;
- [ ] không còn Critical/High unresolved;
- [ ] current release commit có device matrix;
- [ ] Samsung target validation PASS;
- [ ] privacy fix PASS;
- [ ] FGS startup no blocking I/O;
- [ ] boot listener recovery verified;
- [ ] instrumentation compile/run PASS;
- [ ] signed release artifact smoke-tested;
- [ ] repo sạch.

Nếu Xiaomi chưa test thật nhưng app vẫn giữ Xiaomi guidance:

```text
READY FOR RELEASE
Xiaomi physical validation: NOT VERIFIED
```

và không được tuyên bố Xiaomi fully supported/verified.

---

# Suggested Commit Sequence

Agent có thể dùng chuỗi commit tương tự:

```text
test: restore database instrumentation release gate
fix: stop persisting unrelated notification content
fix: remove blocking settings io from alarm startup
fix: harden notification listener recovery after boot
fix: isolate test alarm from production runtime
test: certify alarm runtime across android api levels
fix: normalize alarm history lifecycle
fix: define backup and restore policy
fix: harden battery optimization guidance
ui: complete english and vietnamese localization
fix: preserve deterministic rule ordering
fix: restrict boot receiver compatibility actions
refactor: simplify verified full screen launch path
test: increase production path coverage
build: add room schema and migration verification
chore: triage release lint warnings
build: prepare signed release pipeline
docs: publish final release verification
```

Không bắt buộc đúng message trên; mục tiêu là commit nhỏ, dễ rollback và trace được từng issue.

---

# Agent Final Report Format

Sau khi thực hiện xong toàn bộ plan, Agent báo cáo:

```text
## Overall Status

Phase 0:
Phase 1:
Phase 2:
Phase 3:
Phase 4:
Phase 5:

## Issues

ISSUE-01:
...
ISSUE-15:

## Verification

Unit:
Lint:
Debug build:
Release build:
AndroidTest compile:
Instrumentation:
API 31:
API 33:
API 34:
API 36:
Samsung:
Xiaomi:

## Privacy / Security

Wrong-package persistence:
Backup policy:
Receiver exposure:

## Reliability

FGS cold start:
Screen lock:
Doze:
Process recreation:
Reboot:
STOP:

## Release Artifact

Version:
Signed artifact:
SHA-256:
Install smoke test:

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

---

# Definition of Done

Toàn bộ plan chỉ hoàn tất khi:

- [ ] report High issues được đóng;
- [ ] instrumentation gate được phục hồi;
- [ ] privacy leakage wrong-package được loại bỏ;
- [ ] FGS startup không blocking DataStore;
- [ ] boot listener recovery được harden/test;
- [ ] Test Alarm không xung đột production alarm;
- [ ] current commit có API/device matrix;
- [ ] Samsung target device được test thật;
- [ ] history contract thống nhất;
- [ ] backup policy rõ ràng;
- [ ] localization EN/VI hoàn chỉnh;
- [ ] rule ordering deterministic;
- [ ] BootReceiver exposure được kiểm soát;
- [ ] migration strategy tồn tại;
- [ ] lint warnings được triage;
- [ ] signed release artifact được smoke-test;
- [ ] final release report map đủ ISSUE-01 → ISSUE-15;
- [ ] repository sạch;
- [ ] không còn Critical/High unresolved.
