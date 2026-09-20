# Testing and Quality Requirements

## 1. Test strategy

Chia test thành 3 lớp:

1. Pure unit tests — bắt buộc và chạy nhanh.
2. Android/JVM adapter tests — khi hợp lý dùng Robolectric/fakes.
3. Instrumented + manual device matrix — bắt buộc cho platform behavior khó mô phỏng.

Không cố unit-test framework Android implementation details vô ích. Test contract của adapter và end-to-end trên device.

## 2. Pure unit tests bắt buộc

### NotificationNormalizerTest

Cases:

- lowercase Locale.ROOT;
- trim;
- collapse whitespace;
- join multiple fields;
- preserve Vietnamese accents;
- empty fields.

### TriggerMatcherTest

Cases:

- wrong package không match;
- ANY: một keyword hit;
- ANY: none hit;
- ALL: tất cả hit;
- ALL: thiếu một keyword;
- empty keywords invalid;
- case-insensitive;
- duplicate keywords normalized.

### DuplicateGuardTest

Cases:

- first key false then mark;
- same key inside TTL true;
- same key after TTL false;
- prune expired;
- bounded size.

### AlarmReducerTest

Tối thiểu bao phủ tất cả transition trong `03-domain-state-machine.md` và 10 invariants.

Đặc biệt:

- simultaneous logical triggers => one schedule effect;
- stale alarm token ignored;
- schedule failure -> Idle;
- cooldown boundary exactly at `until` cho phép trigger mới;
- repeated STOP idempotent.

## 3. Repository tests

- Settings defaults đúng: delay 1000, cooldown 10000, vibration true, monitoring false.
- Rule CRUD + validation.
- History retention <= 100, không quá 3 ngày và tối đa 10 event suppressed/ignored.
- Runtime metadata persistence/hydration.

## 4. Scheduler adapter tests

Với fake/wrapper:

- permission missing trả đúng result;
- request uses expected trigger time;
- canonical PendingIntent identity ổn định;
- cancel dùng cùng identity;
- extras có alarm token.

Không cần verify nội bộ AlarmManager bằng mocking quá sâu nếu test brittle.

## 5. Alarm runtime tests

Fake `AlarmPlayer` + `VibrationController` để test service/controller logic:

- START gọi audio/vibration đúng một lần;
- repeated START same token không double-start;
- STOP gọi both stop;
- repeated STOP safe;
- audio error không chặn vibration/STOP path;
- vibration error không crash service.

## 6. Instrumented/device tests bắt buộc

### Device/API targets

Tối thiểu test:

- API 31 hoặc 32 emulator/device — background FGS restriction baseline.
- API 33 — notification permission.
- API 34 — FGS type + full-screen behavior baseline.
- API 36 — target baseline hiện tại.
- Ít nhất một thiết bị Samsung thật nếu thiết bị sử dụng chính là Samsung.

### Scenario matrix

#### M1 — Foreground app

- Camera Alarm đang mở.
- Inject/test notification path.
- Alarm fires và STOP works.

#### M2 — Background

- Home screen, Camera Alarm không foreground.
- Notification hợp lệ.
- Alarm fires.

#### M3 — Screen locked

- Khóa màn hình.
- Notification hợp lệ.
- Audio/vibration vẫn chạy.
- STOP qua notification hoặc full-screen UI tùy permission.

#### M4 — Doze/idle

- Dùng adb đưa device/emulator vào idle nếu có thể.
- exact alarm vẫn được deliver theo platform behavior.

#### M5 — Spam notification

Gửi 5 event hợp lệ trong 2 giây.

Expected:

- 1 SCHEDULED;
- các event sau suppressed Pending/Ringing;
- chỉ một audio runtime.

#### M6 — Cooldown

- STOP.
- trigger trong 5 giây với cooldown 10 => suppressed.
- trigger sau 10 giây => scheduled.

#### M7 — Permission revoke

- revoke exact alarm special access.
- trigger hợp lệ.

Expected:

- không crash;
- SCHEDULE_FAILED;
- UI readiness Required.

#### M8 — Listener disconnected/reconnected

- toggle Notification Access off/on.
- UI state cập nhật;
- sau reconnect nhận event mới.

#### M9 — Process death before fire

- schedule delay 5 sec.
- làm process bị reclaim/kill theo test method không tương đương force-stop nếu có thể.
- PendingIntent fire phải khởi tạo receiver/service lại.

Không dùng `force-stop` để chứng minh case này vì Android cố tình chặn app sau force-stop.

#### M10 — Audio volume zero

- set alarm stream zero.
- diagnostics cảnh báo.
- Test Alarm không được giả báo là loud.

Nếu thiết bị báo `STREAM_ALARM` có minimum > 0 và từ chối đặt về 0, ghi lại giới hạn platform. Khi đó test unit nhánh `current = 0` của volume diagnostics và xác nhận debug output đọc đúng current/min/max trên thiết bị; không ghi rằng đã tạo được volume 0 thật.

## 7. ADB/debug injection

Nên có debug-only `DebugTriggerActivity` hoặc developer action để tạo `IncomingNotification`/`ValidTrigger` giả, giúp test core mà không phụ thuộc hãng camera.

Quan trọng: debug injection phải đi qua AlarmCoordinator/state machine, không gọi service thẳng trừ Test Alarm riêng.

Release build không expose exported debug receiver/activity.

## 8. Build gates

Trước mỗi commit hoàn tất task:

```bash
./gradlew test
```

Trước khi push một milestone:

```bash
./gradlew test lint assembleDebug
```

Nếu có instrumentation environment:

```bash
./gradlew connectedDebugAndroidTest
```

Không đánh dấu task done khi test đỏ.

## 9. Quality rules

- Không warning quan trọng trong lint bị ignore bằng blanket suppression.
- Không catch `Exception` rồi bỏ trống.
- Không `!!` ở notification parsing path trừ invariant thật sự và có lý do.
- Không đọc DataStore blocking trên main thread.
- Không giữ Activity context trong singleton.
- Không leak MediaPlayer/Vibrator reference.
- Không duplicate logic permission ở nhiều ViewModel; tập trung vào readiness/platform helper.

## 10. Release acceptance checklist

- [ ] Fresh install flow hoàn tất được.
- [ ] Notification Access cấp được.
- [ ] Exact alarm access cấp được.
- [ ] Source app chọn được.
- [ ] Rule tạo và test được.
- [ ] Test Alarm kêu liên tục.
- [ ] STOP hoạt động từ notification.
- [ ] Camera notification thật trigger được khi screen lock.
- [ ] Spam không tạo double alarm.
- [ ] Cooldown đúng.
- [ ] History giải thích được event bị bỏ qua.
- [ ] `test`, `lint`, `assembleDebug` pass.
