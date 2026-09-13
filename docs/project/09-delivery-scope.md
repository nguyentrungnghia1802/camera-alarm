# Delivery Scope and Implementation Boundaries

## 1. V1 deliverable

Một APK Android local-first có thể cài trên điện thoại cá nhân và cấu hình để lắng nghe notification của một app camera.

V1 hoàn thành khi toàn bộ Definition of Done trong `00-overview.md` và acceptance matrix trong `08-testing-quality.md` đạt.

## 2. Milestones

### Milestone A — Core reliable trigger

Bao gồm:

- notification listener;
- normalize/matcher;
- dedupe;
- state machine;
- exact alarm scheduler;
- alarm foreground runtime;
- STOP;
- core tests.

Đây là phần phải chính xác trước mọi polish UI.

### Milestone B — Usable personal app

Bao gồm:

- onboarding/readiness UI;
- source picker;
- rule editor;
- settings;
- history;
- diagnostics;
- test alarm;
- full-screen alarm enhancement nếu platform permission cho phép.

### Milestone C — Device hardening

Bao gồm:

- test trên device thật;
- OEM-specific troubleshooting nếu thực tế cần;
- fix race/permission/lifecycle bugs tìm được;
- release APK.

## 3. Future extensions không làm trước

Chỉ xem xét sau V1 ổn định:

### Direct camera integration

Tạo abstraction:

```kotlin
interface CameraEventSource {
    val events: Flow<CameraEvent>
}
```

Notification source có thể trở thành implementation đầu tiên. Sau này thêm vendor API/webhook nếu hãng camera hỗ trợ.

Không thiết kế backend ngay bây giờ chỉ để "phòng tương lai".

### Multiple cameras/apps

V2 có thể cho nhiều source packages và rule per source. V1 giữ một source app active để giảm ambiguity.

### Custom alarm audio

V2 có thể dùng ringtone picker và persist URI permission nếu cần. V1 dùng system default alarm URI.

### Snooze

Không có trong core V1 vì snooze tạo thêm state/timer và có thể làm người dùng bỏ lỡ security alert. Chỉ thêm nếu có use case rõ.

## 4. Explicit architecture decisions

| Decision | V1 choice | Lý do |
|---|---|---|
| Event source | NotificationListenerService | Không phụ thuộc vendor API |
| Delay | Exact Alarm | Time-sensitive background trigger |
| Alarm runtime | Foreground service | Continuous visible user-facing work |
| FGS type | systemExempted (API 34+) | Exact-alarm app continuing alarm |
| Audio usage | USAGE_ALARM | Đúng semantics alarm |
| Core state | Explicit state machine | Chống double trigger/race |
| Settings | DataStore | Scalar config, Flow-friendly |
| Rules/history | Room | Queryable structured local data |
| DI | Manual AppContainer | Dự án một người, giảm boilerplate |
| Backend | None | YAGNI |
| Regex rules | No | Tránh complexity/unbounded patterns |
| Force max system volume | No | Không thay global setting ngầm |
| DND bypass | No | Không cần cho V1, tránh permission/policy complexity |

## 5. Constraints cho AI/code agent

Khi code khác với docs:

- docs trong `docs/project/` là source of truth;
- nếu platform API thực tế bắt buộc khác do SDK hiện tại, agent phải cập nhật docs cùng code và giải thích trong commit;
- không tự thêm framework/dependency lớn để giải bài toán nhỏ;
- không tối ưu sớm;
- không triển khai feature Future Extensions trong V1.

## 6. Commit policy

Dự án cá nhân dùng duy nhất branch `main`.

Recommended commit style:

```text
feat: add notification trigger matcher
feat: schedule camera alerts with exact alarms
feat: add foreground alarm runtime
fix: prevent duplicate alarm start
ui: add monitoring readiness screen
test: cover cooldown boundary cases
docs: update alarm permission behavior
```

Mỗi commit phải build/test ở mức phù hợp với task. Không force-push nếu không thật sự cần.
