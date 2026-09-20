# Product Requirements

## 1. User story chính

> Khi camera gửi notification về điện thoại, nếu notification đúng loại cảnh báo tôi quan tâm, tôi muốn điện thoại bắt đầu kêu như chuông báo sau khoảng 1 giây và tiếp tục kêu/rung cho tới khi tôi chủ động tắt.

## 2. Đối tượng sử dụng

V1 chỉ tối ưu cho một người dùng trên một thiết bị Android cá nhân. Không cần multi-user, admin panel hoặc remote management.

## 3. Functional Requirements

### FR-001 — Notification access

Ứng dụng phải có màn hình onboarding/readiness cho phép người dùng mở Android Settings để cấp Notification Access cho `NotificationListenerService`.

Acceptance:

- UI phân biệt `Granted` và `Not granted`.
- Monitoring không được báo Ready khi chưa có quyền.
- Khi listener kết nối, trạng thái UI cập nhật.

### FR-002 — Source app selection

Người dùng phải chọn chính xác app camera cần theo dõi.

Acceptance:

- Lưu `packageName`, label và icon reference nếu có.
- Matcher luôn kiểm tra package trước keyword.
- Không có source app => Monitoring disabled.
- Có advanced manual package-name input để xử lý app không hiện trong picker.

### FR-003 — Trigger rule

Mỗi rule V1 gồm:

```text
id
name
enabled
sourcePackage
matchMode: CONTAINS_ANY | CONTAINS_ALL
keywords: List<String>
caseSensitive: false (V1 cố định)
```

Quy tắc chuẩn hóa keyword:

- trim đầu/cuối;
- bỏ keyword rỗng;
- loại duplicate không phân biệt hoa thường;
- normalize text bằng lowercase `Locale.ROOT` và collapse whitespace.

Không hỗ trợ regex trong V1 để tránh tăng độ phức tạp và các pattern khó kiểm soát.

### FR-004 — Notification text extraction

Hệ thống phải tạo một chuỗi normalized từ các field khả dụng:

- `Notification.EXTRA_TITLE`
- `Notification.EXTRA_TEXT`
- `Notification.EXTRA_BIG_TEXT`
- `Notification.EXTRA_TEXT_LINES`
- `Notification.EXTRA_SUB_TEXT`

Không giả định một hãng camera luôn dùng cùng một field.

### FR-005 — Delay

Alarm delay mặc định: `1000 ms`.

UI V1 cho các preset:

- 0 giây
- 1 giây
- 3 giây
- 5 giây

Delay được lưu dưới milliseconds. Delay <= 0 nghĩa là schedule tại thời điểm hiện tại bằng cùng exact-alarm pipeline, không bypass state machine.

### FR-006 — Single active alarm invariant

Tại mọi thời điểm chỉ được tồn tại tối đa một trong hai:

- pending alarm;
- ringing alarm.

Notification mới trong trạng thái `Pending` hoặc `Ringing` phải bị suppress và ghi history.

### FR-007 — Duplicate protection

Cùng một notification không được trigger hai lần do listener callback/repost.

Dedupe key ưu tiên:

```text
StatusBarNotification.key
```

Fallback nếu key rỗng:

```text
packageName + id + tag + postTime bucket + normalizedText hash
```

Giữ dedupe cache nhỏ theo TTL; default TTL 30 giây.

### FR-008 — Cooldown

Sau STOP, hệ thống chuyển sang cooldown.

Default cooldown: `10 giây`.

Preset UI:

- 0 giây
- 10 giây
- 30 giây
- 60 giây

Trong cooldown, trigger hợp lệ bị suppress và ghi history.

### FR-009 — Alarm audio

Alarm runtime phải:

- sử dụng `AudioAttributes.USAGE_ALARM`;
- content type `CONTENT_TYPE_SONIFICATION`;
- phát default alarm URI của hệ thống;
- `isLooping = true` khi MediaPlayer hỗ trợ source đó;
- player scalar volume = `1.0f` trái/phải;
- dùng wake mode để giảm nguy cơ CPU ngủ trong khi alarm đang phát;
- nếu audio source không mở được, vibration vẫn phải chạy và UI/history phải ghi lỗi.

Không tự bypass Do Not Disturb trong V1.

### FR-010 — Alarm vibration

Nếu setting vibration bật:

- rung bắt đầu cùng alarm;
- pattern lặp vô hạn đến STOP;
- STOP luôn gọi `cancel()`.

### FR-011 — Foreground alarm service

Khi exact alarm fire, app phải chạy alarm bằng foreground service có notification thường trực.

Notification phải có:

- title `Camera Alert`;
- mô tả trigger;
- timestamp;
- action `STOP`;
- ongoing trong lúc ringing.

### FR-012 — STOP

STOP có thể đến từ:

- action trong foreground notification;
- AlarmActivity;
- Main screen nếu alarm đang active.

STOP phải idempotent:

- release MediaPlayer an toàn;
- cancel vibration;
- stop foreground;
- stop service;
- update state sang cooldown;
- ghi history đúng một logical stop event.

### FR-013 — Test Alarm

Main screen phải có `TEST ALARM`.

Test Alarm:

- không cần notification từ camera;
- dùng cùng `CameraAlarmService` và alarm playback implementation;
- không đi qua trigger matcher;
- không được làm hỏng state của monitoring;
- phải có STOP.

Mục đích: kiểm tra âm lượng alarm, vibration và foreground behavior trước khi bật Monitoring.

### FR-014 — Readiness

Main screen hiển thị ít nhất:

```text
Notification access   Granted/Required
Exact alarm access    Granted/Required
App notifications     Granted/Denied (API 33+)
Source app            Selected/Required
Trigger rule          Valid/Required
Listener              Connected/Disconnected
Monitoring            Ready/Not ready
```

`Monitoring Ready` chỉ true khi toàn bộ dependency bắt buộc cho trigger tự động đã thỏa.

### FR-015 — History

Lưu tối đa 100 event gần nhất trong 3 ngày; trong đó chỉ giữ tối đa 10 event suppressed/ignored gần nhất.

Mỗi event chứa:

```text
id
createdAt
sourcePackage
notificationKey?
title?
textPreview?
normalizedHash?
decision
ruleId?
alarmToken?
details?
```

Decision enum tối thiểu:

```text
RECEIVED
IGNORED_MONITORING_OFF
IGNORED_WRONG_PACKAGE
IGNORED_NO_RULE_MATCH
IGNORED_DUPLICATE
SUPPRESSED_PENDING
SUPPRESSED_RINGING
SUPPRESSED_COOLDOWN
SCHEDULED
SCHEDULE_FAILED
ALARM_FIRED
ALARM_STOPPED
ALARM_RUNTIME_ERROR
```

Không lưu full notification payload không giới hạn. `textPreview` giới hạn 300 ký tự.

## 4. Non-functional Requirements

### NFR-001 — Latency

App phải tối thiểu hóa xử lý trước scheduler. Matcher không được thực hiện I/O nặng trên callback notification.

Mục tiêu quan sát trên thiết bị bình thường:

- parse + match: dưới 100 ms;
- schedule: ngay sau match;
- alarm fire theo hệ thống tại khoảng delay yêu cầu khi platform cho phép.

Không cam kết hard real-time vì Android scheduler và OEM power management không phải real-time OS.

### NFR-002 — Reliability

- Không phụ thuộc activity đang mở.
- Exact alarm dùng `PendingIntent` để tồn tại khi process không còn sống.
- Alarm service chịu được STOP lặp.
- Không crash nếu notification extras thiếu hoặc sai kiểu.

### NFR-003 — Privacy

- Không gửi notification content ra mạng.
- V1 không khai báo INTERNET permission.
- History chỉ ở local Room database.
- Có action xóa toàn bộ history.

### NFR-004 — Battery

- Không polling.
- Không background loop để dò notification.
- Chỉ hoạt động khi Android callback notification tới hoặc khi alarm đang kêu.

### NFR-005 — Maintainability

- Logic matcher/state machine không phụ thuộc Android framework nếu có thể.
- Android platform calls được bọc qua interface nhỏ để unit test.
- Mỗi file có một trách nhiệm rõ ràng.

## 5. Edge cases bắt buộc

1. Notification không có title nhưng có bigText.
2. Notification có `EXTRA_TEXT_LINES` nhiều dòng.
3. Camera repost cùng notification key.
4. Hai notification hợp lệ đến gần như cùng lúc.
5. Exact alarm permission bị thu hồi sau khi app từng Ready.
6. Notification permission bị từ chối trên API 33+.
7. Listener bị disconnected.
8. User STOP đúng lúc alarm receiver/service đang khởi động.
9. MediaPlayer prepare/start thất bại.
10. System alarm URI null hoặc không đọc được.
11. Vibration hardware không hỗ trợ amplitude control.
12. App process chết giữa lúc pending và exact alarm fire.
13. User force-stop app: app không được tuyên bố có thể tự phục hồi; cần người dùng mở lại app theo quy tắc Android.
14. Device reboot: pending one-shot alert cũ không được khôi phục và không tự kêu lại.
