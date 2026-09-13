# Camera Alarm — Implementation Tasks

## Cách dùng file này

- Thực hiện theo thứ tự từ trên xuống.
- Phase 1 dành cho model mạnh vì chứa logic/race/platform behavior khó và quyết định correctness của app.
- Phase 2 dành cho các task dễ -> trung bình/khá sau khi core đã ổn định.
- Không chuyển Phase 2 chỉ vì UI có thể chạy; Phase 1 phải đạt gate cuối phase.
- Tick checkbox ngay khi bước tương ứng thực sự hoàn tất và test pass.

---

# Phase 1 — Core correctness, concurrency và Android platform

> Chỉ gồm các phần thật sự cần reasoning mạnh. Mechanical setup được gộp tối thiểu vào task đầu tiên để không tiêu tốn riêng một phase.

## P1.1 — Core domain contracts, normalization, matcher, dedupe và alarm state machine

**Mục tiêu:** tạo phần logic thuần Kotlin quyết định notification nào được phép trigger và bảo đảm single-active-alarm invariant.

**Đọc trước:**

- `docs/project/01-product-requirements.md`
- `docs/project/02-architecture.md`
- `docs/project/03-domain-state-machine.md`
- `docs/project/04-notification-trigger-pipeline.md`
- `docs/project/08-testing-quality.md`

**Deliverables chính:**

```text
notification/NotificationExtractor domain DTO (Android adapter có thể để task sau)
trigger/NotificationNormalizer.kt
trigger/TriggerMatcher.kt
trigger/DuplicateGuard.kt
alarm/AlarmState.kt
alarm/AlarmReducer.kt
alarm/AlarmCoordinator.kt contracts/interfaces
util/Clock.kt
unit tests
```

### Checklist

- [x] Nếu project chưa có Android scaffold, tạo tối thiểu project Kotlin/Compose single-module `:app`, package `com.personal.cameraalarm`, minSdk 26, compile/target 36; không làm UI polish.
- [x] Tạo domain model `IncomingNotification`, `TriggerRule`, `MatchMode`, `AlarmToken`, `TriggerSnapshot`, `AlarmPolicy` theo docs.
- [x] Implement `NotificationNormalizer` null-safe, lowercase `Locale.ROOT`, collapse whitespace, giữ dấu tiếng Việt.
- [x] Viết `NotificationNormalizerTest` cho title/text/bigText/textLines/subText merge cases.
- [x] Implement `TriggerMatcher` với package check, `CONTAINS_ANY`, `CONTAINS_ALL`, rule priority deterministic.
- [x] Viết matcher tests cho wrong package, ANY, ALL, empty keyword, case-insensitive, duplicate normalized keyword.
- [x] Implement bounded TTL `DuplicateGuard` default 30s, max 200 entries.
- [x] Viết dedupe tests gồm exact TTL boundary và prune/bounded behavior.
- [x] Implement `AlarmState`, `AlarmEvent`, `AlarmEffect`, `AlarmTransition`.
- [x] Implement pure `AlarmReducer` đúng toàn bộ transition trong `03-domain-state-machine.md`.
- [x] Viết `AlarmReducerTest` bao phủ đủ INV-1 .. INV-10.
- [x] Tạo `Clock` abstraction và fake clock cho tests; không gọi `System.currentTimeMillis()` trực tiếp trong reducer.
- [x] Tạo coordinator contract + serialization bằng `Mutex`; effects platform vẫn có thể dùng fake ở task này.
- [x] Viết concurrency-focused test: hai `ValidTrigger` gần đồng thời chỉ tạo đúng một logical schedule effect.
- [x] Chạy `./gradlew test` và sửa toàn bộ failure.
- [x] Commit: `feat: add core trigger and alarm state machine`.

### Acceptance criteria

- Pure core tests không cần device/emulator.
- Không có Android API trong `TriggerMatcher`, `DuplicateGuard`, `AlarmReducer`.
- Hai event hợp lệ đồng thời không tạo hai Pending/Ringing.
- Schedule failure có transition về Idle, không mắc Pending giả.

---

## P1.2 — NotificationListenerService ingestion và trigger pipeline thực

**Mục tiêu:** nhận notification Android thật, extract an toàn và đưa đúng event vào core pipeline mà không chứa alarm logic trong listener.

**Đọc trước:**

- `docs/project/02-architecture.md`
- `docs/project/04-notification-trigger-pipeline.md`
- `docs/project/06-platform-permissions-compatibility.md`

### Checklist

- [x] Khai báo `CameraNotificationListener` đúng manifest với `BIND_NOTIFICATION_LISTENER_SERVICE`, exported false và service intent filter.
- [x] Implement `ListenerConnectionState` cập nhật `CONNECTED/DISCONNECTED` từ lifecycle callback.
- [x] Implement Android `NotificationExtractor` đọc TITLE, TEXT, BIG_TEXT, TEXT_LINES, SUB_TEXT mà không crash với null/sai type.
- [x] Viết adapter tests/Robolectric tests cho extractor nếu khả thi; tối thiểu phải unit-test helper convert Bundle -> domain data.
- [x] Implement `TriggerPipeline` theo đúng order: monitoring -> package -> dedupe -> rules -> coordinator.
- [x] Đảm bảo callback listener không làm Room/DataStore I/O blocking trên main thread.
- [x] Không gọi MediaPlayer, Vibrator hoặc `startForegroundService()` trong listener.
- [x] Ghi structured decision/history contract nhưng có thể dùng fake repository trước khi Room hoàn thiện Phase 2.
- [x] Thêm debug logging không chứa full notification text ở release path.
- [x] Tạo debug-only injection path để bơm `IncomingNotification` giả qua TriggerPipeline; không exported ở release.
- [x] Test: wrong package không tới coordinator.
- [x] Test: duplicate selected-package notification không tới coordinator lần hai.
- [x] Test: matching rule gửi đúng một `ValidTrigger`.
- [x] Chạy `./gradlew test lint`.
- [x] Commit: `feat: ingest camera notifications safely`.

### Acceptance criteria

- Notification listener chỉ là adapter/entry point.
- Missing extras không crash.
- Pipeline deterministic và không phụ thuộc UI.
- App có thể quan sát listener connection state.

---

## P1.3 — Exact-alarm scheduler, permission correctness và process-death-safe fire path

**Mục tiêu:** schedule alert time-sensitive từ background đúng Android rules và không phụ thuộc process còn sống.

**Đọc trước:**

- `docs/project/03-domain-state-machine.md`
- `docs/project/05-alarm-scheduling-runtime.md`
- `docs/project/06-platform-permissions-compatibility.md`
- `docs/project/08-testing-quality.md`

### Checklist

- [x] Khai báo `SCHEDULE_EXACT_ALARM`.
- [x] Implement `AlarmScheduler` interface và `AndroidAlarmScheduler`.
- [x] Luôn check `canScheduleExactAlarms()` trước schedule trên API yêu cầu.
- [x] Dùng `setExactAndAllowWhileIdle(RTC_WAKEUP, triggerAt, PendingIntent)` cho pending alert.
- [x] PendingIntent phải explicit + immutable, identity canonical, chứa alarm token/payload tối thiểu.
- [x] Implement cancel dùng đúng cùng PendingIntent identity.
- [x] Persist pending alarm metadata trước/đồng bộ với logical Pending đủ để receiver xử lý khi process bị recreate.
- [x] Nếu schedule fail, clear pending metadata và đưa coordinator về Idle qua failure event.
- [x] Implement `AlarmReceiver` validate action/token/persisted metadata, reject stale token.
- [x] Receiver không play audio; receiver chỉ start alarm runtime foreground service.
- [x] Không dùng inexact fallback khi exact alarm access thiếu.
- [x] Implement UI/platform helper mở `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`; UI polish để Phase 2 nhưng API phải sẵn.
- [x] Test fake scheduler: permission missing, success, failure, cancel, token propagation.
- [x] Test stale fired token không thay active token.
- [x] Test/process scenario: schedule 5 giây, process recreate trước fire vẫn có receiver path hợp lệ; không dùng force-stop để mô phỏng process reclaim.
- [x] Chạy `./gradlew test lint assembleDebug`.
- [x] Commit: `feat: schedule alerts with exact alarms`.

### Acceptance criteria

- Delay không phụ thuộc coroutine/Handler sống trong process.
- Không schedule khi exact permission thiếu.
- Alarm fired từ PendingIntent có thể khởi động app process lại.
- Stale alarm token không tạo alarm chồng.

---

## P1.4 — Foreground alarm runtime, continuous audio/vibration và idempotent STOP

**Mục tiêu:** khi exact alarm fire, tạo một alarm liên tục thực sự và cleanup đúng trong mọi race phổ biến.

**Đọc trước:**

- `docs/project/05-alarm-scheduling-runtime.md`
- `docs/project/06-platform-permissions-compatibility.md`
- `docs/project/08-testing-quality.md`

### Checklist

- [x] Khai báo `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SYSTEM_EXEMPTED`, `VIBRATE`, `WAKE_LOCK`.
- [x] Khai báo `CameraAlarmService` exported false, FGS type `systemExempted` cho target/API phù hợp.
- [x] Service promote foreground ngay khi start path yêu cầu.
- [x] Tạo channel `alarm_runtime`; không dùng channel sound làm nguồn alarm chính.
- [x] Foreground notification có `CATEGORY_ALARM`, ongoing và STOP action.
- [x] Implement `AlarmPlayer` dùng system default alarm URI, `USAGE_ALARM`, `CONTENT_TYPE_SONIFICATION`, `isLooping=true`, wake mode, player volume 1.0.
- [x] Implement failure-safe MediaPlayer lifecycle; mọi path STOP/onDestroy release resource.
- [x] Implement `VibrationController` repeating waveform; STOP luôn cancel.
- [x] Implement service START idempotency: same token không double-player/double-vibration.
- [x] Defensive behavior: token khác khi đang ringing không tạo runtime thứ hai.
- [x] Implement STOP từ notification action; repeated STOP safe.
- [x] Khi audio fail, vibration + STOP notification vẫn hoạt động và error được record.
- [x] Khi vibration unavailable/fail, audio vẫn hoạt động.
- [x] Integrate coordinator `ExactAlarmFired -> Ringing` và `StopRequested -> Cooldown`.
- [x] Viết service/controller tests bằng fake AlarmPlayer/VibrationController.
- [x] Manual test background + screen locked trên ít nhất một device/emulator API 34+.
- [x] Chạy `./gradlew test lint assembleDebug`.
- [x] Commit: `feat: add continuous foreground camera alarm`.

### Acceptance criteria

- Alarm kêu/rung liên tục tới STOP.
- Không có hai MediaPlayer active do repeated START.
- STOP dừng audio + vibration + foreground service.
- Service không crash khi audio URI/player lỗi.

---

## P1.5 — End-to-end reliability gate

**Mục tiêu:** chỉ đóng Phase 1 sau khi core thật sự chịu được background, lock screen, spam và permission failure.

**Đọc trước:** `docs/project/08-testing-quality.md`.

### Checklist

- [x] Chạy M1 Foreground app.
- [x] Chạy M2 Background.
- [x] Chạy M3 Screen locked.
- [x] Chạy M4 Doze/idle nếu môi trường hỗ trợ.
- [x] Chạy M5 Spam notification: 5 event / 2 giây, chỉ một alarm.
- [x] Chạy M6 Cooldown boundary.
- [x] Chạy M7 Exact alarm permission revoke.
- [x] Chạy M8 Notification listener access off/on.
- [x] Chạy M9 Process recreation before fire.
- [x] Chạy M10 Alarm volume zero diagnostics tối thiểu bằng temporary debug output nếu UI chưa có.
- [x] Fix mọi crash, duplicate alarm, stale token hoặc STOP race phát hiện được.
- [x] `./gradlew test lint assembleDebug` pass sạch.
- [x] Cập nhật docs nếu platform behavior thực tế khác assumption.
- [x] Commit: `test: harden core alarm flow end to end`.

### Phase 1 gate

Chỉ được sang Phase 2 khi tất cả đều đúng:

- [x] Matcher/dedupe/reducer tests pass.
- [x] Notification thật hoặc representative injected event đi xuyên pipeline đúng.
- [x] Exact alarm fire được từ background.
- [x] Alarm continuous đến STOP.
- [x] Spam không double alarm.
- [x] Permission missing không crash/không fake success.
- [x] Screen-lock test pass trên ít nhất một API 34+ target.

---

# Phase 2 — Product completion, UI, persistence và polish

> Làm từ dễ -> trung bình -> khá. Core architecture Phase 1 không được viết lại chỉ để thuận tiện UI.

## P2.1 — DataStore settings và Room persistence

**Đọc:** `docs/project/07-data-ui-observability.md`.

- [x] Implement `AppSettings` + Preferences DataStore defaults.
- [x] Implement Room `trigger_rules` entity/DAO.
- [x] Implement Room `alert_events` entity/DAO.
- [x] Implement history retention tối đa 500 rows.
- [x] Implement repositories trả Flow/suspend API rõ ràng.
- [x] Kết nối runtime metadata persistence từ Phase 1 vào repository thật.
- [x] Unit/database tests cho defaults, CRUD, retention.
- [x] Chạy tests.
- [x] Commit: `feat: persist settings rules and alert history`.

## P2.2 — Readiness/onboarding screen

**Đọc:** `docs/project/06-platform-permissions-compatibility.md`, `07-data-ui-observability.md`.

- [ ] Main screen hiển thị Notification Access status.
- [ ] Nút mở Notification Access Settings.
- [ ] Hiển thị Exact Alarm status + nút grant.
- [ ] Request/check POST_NOTIFICATIONS trên API 33+.
- [ ] Hiển thị listener Connected/Disconnected.
- [ ] Hiển thị source/rule readiness.
- [ ] Hiển thị alarm stream current/max volume và warning nếu zero.
- [ ] Monitoring toggle chỉ ON khi blocking setup đạt.
- [ ] UI state lấy từ `ReadinessRepository`, không duplicate permission logic ở Composable.
- [ ] Compose/UI tests cơ bản nếu project setup thuận lợi.
- [ ] Commit: `ui: add monitoring readiness setup`.

## P2.3 — Source app picker

- [ ] Thêm manifest `<queries>` cho launcher intent; không thêm `QUERY_ALL_PACKAGES`.
- [ ] Query launchable apps, show icon/label/package.
- [ ] Cho chọn một source app active.
- [ ] Advanced manual package-name input.
- [ ] Validate package string non-blank.
- [ ] Sau đổi source, readiness yêu cầu enabled rule cho source mới.
- [ ] Test repository/UI logic.
- [ ] Commit: `ui: add camera source app picker`.

## P2.4 — Trigger rule management

- [ ] Rule list screen.
- [ ] Create/edit/delete rule.
- [ ] `CONTAINS_ANY` / `CONTAINS_ALL` selector.
- [ ] Keywords one-per-line editor.
- [ ] Validate max 30 keywords, mỗi keyword max 100 chars.
- [ ] Enabled toggle + priority.
- [ ] Add editable template cho person/motion keywords; không auto-enable trước xác nhận.
- [ ] Hiển thị preview normalized keywords.
- [ ] Tests cho validation/ViewModel.
- [ ] Commit: `ui: add camera notification trigger rules`.

## P2.5 — Alarm settings và Test Alarm

- [ ] Delay presets 0/1/3/5 giây.
- [ ] Cooldown presets 0/10/30/60 giây.
- [ ] Vibration toggle.
- [ ] `TEST ALARM` gọi cùng AlarmPlayer/Vibration runtime production path nhưng không giả notification match.
- [ ] Test Alarm có STOP và không phá monitoring state.
- [ ] Nếu alarm volume zero, show explicit warning trước/đồng thời test.
- [ ] Commit: `ui: add alarm settings and test alarm`.

## P2.6 — History và diagnostics

- [ ] History newest-first.
- [ ] Filter All / Triggered / Suppressed / Errors.
- [ ] Show decision, time, source, preview, rule/alarm token diagnostics.
- [ ] Clear history confirmation.
- [ ] Diagnostics screen theo docs.
- [ ] Copy Diagnostics không chứa notification text mặc định.
- [ ] Hiển thị last scheduler/runtime error.
- [ ] Commit: `ui: add alert history and diagnostics`.

## P2.7 — Full-screen alarm enhancement

**Lưu ý:** audio/vibration core không được phụ thuộc task này.

- [ ] Khai báo `USE_FULL_SCREEN_INTENT` nếu feature được bật.
- [ ] API 34+ check `canUseFullScreenIntent()`.
- [ ] UI grant/open settings flow.
- [ ] Implement `AlarmActivity` với STOP button lớn.
- [ ] `setShowWhenLocked(true)` / `setTurnScreenOn(true)` ở API phù hợp.
- [ ] Foreground alarm notification dùng full-screen PendingIntent chỉ khi setting + permission hợp lệ.
- [ ] Permission denied => fallback notification bình thường, audio/vibration vẫn chạy.
- [ ] Manual test locked screen API 34+.
- [ ] Commit: `feat: add optional full screen alarm`.

## P2.8 — UI cleanup và accessibility

- [ ] App status dễ đọc: Ready / Needs setup / Alarming.
- [ ] STOP target lớn, accessible label rõ.
- [ ] Content descriptions cho icon quan trọng.
- [ ] Không dựa riêng vào màu để biểu diễn readiness/error.
- [ ] Strings đưa vào resources; tránh hardcode text rải rác.
- [ ] Dark/light mode không làm mất readability.
- [ ] Không thêm animation nặng ảnh hưởng alarm startup.
- [ ] Commit: `ui: polish camera alarm experience`.

## P2.9 — Final device validation và release APK

- [ ] Fresh install test từ đầu.
- [ ] Grant Notification Access.
- [ ] Grant Exact Alarm access.
- [ ] Grant Notifications.
- [ ] Chọn camera app thật.
- [ ] Tạo rule từ notification text thật.
- [ ] Test Alarm pass.
- [ ] Camera event thật khi app background pass.
- [ ] Camera event thật khi lock screen pass.
- [ ] Spam/cooldown pass.
- [ ] STOP từ notification pass.
- [ ] Full-screen fallback behavior pass.
- [ ] Review history không lưu dữ liệu quá mức cần thiết.
- [ ] `./gradlew test lint assembleDebug` pass.
- [ ] Nếu có device instrumentation: `./gradlew connectedDebugAndroidTest` pass hoặc ghi rõ test nào manual.
- [ ] Build APK release/debug dùng cho cá nhân theo nhu cầu.
- [ ] Cập nhật `docs/project/` nếu behavior cuối khác spec.
- [ ] Commit: `chore: prepare camera alarm v1 release`.
- [ ] Push `main`.

---

# Sau V1 — Không tự làm nếu chưa được yêu cầu

Các mục sau **không thuộc Phase 1/2 V1**:

- vendor camera API/webhook;
- backend;
- multi-device sync;
- nhiều source app đồng thời;
- custom remote push;
- AI/computer vision;
- account/payment;
- snooze phức tạp;
- tự động bypass DND;
- tự động ép global alarm volume lên max.
