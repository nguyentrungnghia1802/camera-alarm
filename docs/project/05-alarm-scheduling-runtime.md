# Alarm Scheduling and Runtime

## 1. Tại sao dùng exact alarm

Core requirement là event user-facing, time-sensitive và cần khởi động alarm từ background sau delay ngắn. V1 dùng `AlarmManager` exact alarm với `PendingIntent` để:

- không phụ thuộc Activity;
- tồn tại khi process app bị reclaim trước thời điểm fire;
- đi theo platform path dành cho exact alarm;
- có cơ sở hợp lệ để bắt đầu foreground alarm service từ background.

Không dùng:

- WorkManager cho delay 1 giây;
- coroutine delay như cơ chế duy nhất;
- Handler delay như cơ chế duy nhất;
- background polling.

## 2. Exact alarm permission

V1 chọn:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

Không dùng `USE_EXACT_ALARM` trong V1 vì muốn quyền do người dùng chủ động cấp và tránh giả định về policy phân phối.

Trước mỗi schedule:

```kotlin
alarmManager.canScheduleExactAlarms()
```

Nếu false:

- không schedule inexact fallback rồi coi như thành công;
- return `ExactAlarmPermissionMissing`;
- readiness chuyển Required;
- history `SCHEDULE_FAILED`.

## 3. Scheduling API

Implementation baseline:

```kotlin
alarmManager.setExactAndAllowWhileIdle(
    AlarmManager.RTC_WAKEUP,
    triggerAtEpochMs,
    pendingIntent
)
```

`triggerAtEpochMs = now + delayMs`.

PendingIntent:

- explicit receiver component;
- `FLAG_IMMUTABLE`;
- `FLAG_UPDATE_CURRENT`;
- một canonical request code vì state machine chỉ cho một pending alert;
- extras chứa alarm token + trigger snapshot tối thiểu.

## 4. AlarmReceiver

Action constant ví dụ:

```text
com.personal.cameraalarm.action.FIRE_ALARM
```

Receiver validation:

1. action đúng?
2. alarm token non-blank?
3. source package/rule metadata hợp lệ?
4. nếu payload stale so với persisted pending token, không start duplicate runtime.

Sau validate:

```text
ContextCompat.startForegroundService(... CameraAlarmService ACTION_START ...)
```

Receiver không play audio trực tiếp.

## 5. Foreground service type

Trên Android 14+ V1 dùng foreground service type:

```text
systemExempted
```

vì app giữ exact-alarm access và service có mục đích tiếp tục một alarm ở background.

Manifest baseline:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />

<service
    android:name=".alarm.CameraAlarmService"
    android:exported="false"
    android:foregroundServiceType="systemExempted" />
```

Runtime API 34+ truyền `FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED` vào `ServiceCompat.startForeground()`.

API thấp hơn dùng compatible foreground start không truyền type mới.

Nếu exact-alarm access bị mất, app không được cố start `systemExempted` service như thể vẫn hợp lệ.

## 6. Service actions

```text
ACTION_START_ALARM
ACTION_STOP_ALARM
```

`ACTION_START_ALARM` idempotency:

- cùng token khi đang ringing: không tạo MediaPlayer thứ hai;
- token mới khi token khác đang ringing: state machine phải suppress trước khi tới service; service vẫn defensive và không chồng runtime.

`ACTION_STOP_ALARM`:

- safe khi player null;
- safe khi vibrator chưa chạy;
- safe khi gọi nhiều lần.

## 7. Foreground notification

Notification channel riêng:

```text
channelId: alarm_runtime
name: Camera alarms
importance: HIGH
```

Lưu ý: âm thanh chính không dựa vào notification-channel sound; alarm audio do `AlarmPlayer` phát. Channel sound có thể để null để tránh hai nguồn âm thanh chồng nhau.

Notification fields:

```text
Title: Camera Alert
Text: <title/text preview>
Category: CATEGORY_ALARM
Ongoing: true
AutoCancel: false
Action: STOP
```

STOP action dùng explicit broadcast/service PendingIntent immutable.

## 8. Audio runtime

### 8.1 Source

Primary source:

```kotlin
RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
```

Nếu null hoặc MediaPlayer prepare thất bại:

- ghi runtime error;
- vẫn giữ vibration nếu có;
- vẫn foreground notification + STOP;
- Test Alarm phải làm lỗi này lộ ra trước khi Monitoring Ready được khuyến nghị sử dụng.

### 8.2 AudioAttributes

```kotlin
AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()
```

### 8.3 MediaPlayer lifecycle

Sequence:

```text
new MediaPlayer
 -> setAudioAttributes
 -> setWakeMode(PARTIAL_WAKE_LOCK)
 -> setDataSource(context, alarmUri)
 -> isLooping = true
 -> prepare
 -> setVolume(1f, 1f)
 -> start
```

Stop sequence:

```text
if playing -> stop safely
reset/release
reference = null
```

Mọi IllegalStateException/IOException phải được catch tại adapter boundary và trả `Result`/log history; service không crash.

### 8.4 Volume semantics

`setVolume(1f, 1f)` chỉ đặt player scalar, không bảo đảm global alarm stream đang lớn.

V1:

- không tự thay global alarm volume;
- Main screen hiển thị warning nếu `STREAM_ALARM` volume = 0;
- Test Alarm là bước setup bắt buộc về mặt UX;
- người dùng tự chỉnh alarm volume của hệ thống.

## 9. Wake behavior

Manifest:

```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

MediaPlayer dùng `setWakeMode(..., PARTIAL_WAKE_LOCK)` để giúp playback tiếp tục khi screen off.

Không giữ custom wakelock vô hạn nếu MediaPlayer wake mode đã đủ.

## 10. Vibration

Manifest:

```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

Baseline API 26+:

```kotlin
VibrationEffect.createWaveform(
    longArrayOf(0, 700, 300),
    0
)
```

Pattern: rung 700 ms, nghỉ 300 ms, lặp.

Nếu device không có vibrator: no-op, không fail alarm.

STOP luôn cancel vibrator.

## 11. Full-screen alarm screen

Full-screen intent là enhancement Phase 2, không phải dependency của audio alarm.

Nếu triển khai:

- khai báo `USE_FULL_SCREEN_INTENT`;
- API 34+ kiểm tra `NotificationManager.canUseFullScreenIntent()`;
- nếu chưa có, UI mở settings tương ứng;
- AlarmActivity dùng `setShowWhenLocked(true)` và `setTurnScreenOn(true)` khi phù hợp;
- nếu full-screen permission không có, alarm audio/vibration vẫn phải hoạt động.

Không để core correctness phụ thuộc full-screen UI.

## 12. STOP guarantee

Khi STOP:

1. coordinator đánh dấu stop logic;
2. service cancel vibration;
3. release player;
4. remove foreground notification;
5. stop service;
6. state -> cooldown;
7. history `ALARM_STOPPED`.

Nếu step 2 hoặc 3 lỗi, vẫn tiếp tục các step sau theo best effort và ghi error.
