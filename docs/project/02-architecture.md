# Architecture

## 1. Kiến trúc tổng thể

V1 dùng single-module Android app nhưng tách package theo responsibility.

```text
app/src/main/java/com/personal/cameraalarm/
|
|-- app/
|   |-- CameraAlarmApp.kt
|   `-- AppContainer.kt
|
|-- notification/
|   |-- CameraNotificationListener.kt
|   |-- NotificationExtractor.kt
|   `-- ListenerConnectionState.kt
|
|-- trigger/
|   |-- TriggerMatcher.kt
|   |-- NotificationNormalizer.kt
|   |-- DuplicateGuard.kt
|   `-- TriggerPipeline.kt
|
|-- alarm/
|   |-- AlarmCoordinator.kt
|   |-- AlarmState.kt
|   |-- AlarmReducer.kt
|   |-- AlarmScheduler.kt
|   |-- AndroidAlarmScheduler.kt
|   |-- AlarmReceiver.kt
|   |-- CameraAlarmService.kt
|   |-- AlarmPlayer.kt
|   |-- VibrationController.kt
|   `-- StopAlarmReceiver.kt
|
|-- permission/
|   |-- ReadinessRepository.kt
|   |-- ExactAlarmAccess.kt
|   `-- NotificationAccess.kt
|
|-- data/
|   |-- settings/
|   |   |-- AppSettings.kt
|   |   `-- SettingsRepository.kt
|   `-- history/
|       |-- AlertEventEntity.kt
|       |-- AlertEventDao.kt
|       |-- AppDatabase.kt
|       `-- HistoryRepository.kt
|
|-- ui/
|   |-- MainActivity.kt
|   |-- main/
|   |-- settings/
|   |-- history/
|   `-- alarm/
|
`-- util/
    |-- Clock.kt
    `-- AndroidClock.kt
```

## 2. Dependency direction

Core logic phải hướng vào domain, không hướng ngược về UI.

```text
UI
 |
 v
Repositories / Coordinators
 |
 +--> pure trigger logic
 |
 +--> alarm domain state machine
 |
 +--> Android adapters
        NotificationListenerService
        AlarmManager
        Foreground Service
        MediaPlayer
        Vibrator
        Room
        DataStore
```

`TriggerMatcher` và `AlarmReducer` không import `android.*`.

## 3. Core components

### 3.1 CameraNotificationListener

Responsibility:

- nhận `onNotificationPosted`;
- kiểm tra listener connected;
- convert `StatusBarNotification` -> `IncomingNotification`;
- hand off nhanh cho `TriggerPipeline`;
- không tự play audio;
- không tự quyết định state.

### 3.2 NotificationExtractor

Chỉ làm platform extraction.

Input:

```kotlin
StatusBarNotification
```

Output domain:

```kotlin
data class IncomingNotification(
    val key: String,
    val packageName: String,
    val notificationId: Int,
    val tag: String?,
    val postTimeEpochMs: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: List<String>,
    val subText: String?
)
```

### 3.3 TriggerPipeline

Pipeline duy nhất cho event camera thật:

```text
received
 -> monitoring enabled?
 -> source package?
 -> normalize text
 -> duplicate?
 -> rule match?
 -> AlarmCoordinator.onValidTrigger()
 -> history
```

Không để UI gọi thẳng AlarmScheduler cho event thật.

### 3.4 AlarmCoordinator

Đây là boundary quan trọng nhất.

Responsibility:

- serialize các transition;
- giữ single-active-alarm invariant;
- gọi reducer;
- persist pending token cần thiết;
- gọi scheduler;
- nhận callback alarm fired/stop;
- ghi history.

Concurrent access phải được serialize bằng `Mutex` hoặc single-threaded dispatcher. Không dựa vào giả định callback luôn tuần tự.

### 3.5 AlarmReducer

Pure function:

```kotlin
fun reduce(
    state: AlarmState,
    event: AlarmEvent,
    nowEpochMs: Long,
    settings: AlarmPolicy
): AlarmTransition
```

Reducer quyết định state tiếp theo và effects logic, không gọi Android API.

### 3.6 AndroidAlarmScheduler

Wrapper quanh `AlarmManager`.

Interface:

```kotlin
interface AlarmScheduler {
    fun canScheduleExactAlarms(): Boolean
    fun scheduleExact(token: AlarmToken, triggerAtEpochMs: Long): ScheduleResult
    fun cancel(token: AlarmToken)
}
```

Implementation dùng `PendingIntent` + `setExactAndAllowWhileIdle`.

### 3.7 AlarmReceiver

Receiver chỉ là bridge từ `AlarmManager` vào alarm runtime.

- validate action;
- đọc alarm token/trigger info từ intent;
- không làm I/O kéo dài trên main thread;
- start `CameraAlarmService` trong exact-alarm exemption path;
- nếu cần async validation, dùng `goAsync()` và hoàn thành nhanh.

### 3.8 CameraAlarmService

Responsibility:

- lên foreground ngay;
- tạo ongoing notification;
- start audio/vibration;
- xử lý START/STOP idempotently;
- release resource ở `onDestroy()`;
- không chứa trigger matching.

### 3.9 AlarmPlayer

Interface:

```kotlin
interface AlarmPlayer {
    fun start(): Result<Unit>
    fun stop()
    val isPlaying: Boolean
}
```

Implementation dùng MediaPlayer với `USAGE_ALARM`.

### 3.10 VibrationController

Interface:

```kotlin
interface VibrationController {
    fun startRepeating()
    fun stop()
}
```

## 4. AppContainer

Dự án cá nhân không cần DI framework ở V1.

`AppContainer` tạo singleton application-scoped dependencies:

```text
SettingsRepository
HistoryRepository
Clock
AlarmScheduler
AlarmCoordinator
TriggerPipeline
ReadinessRepository
```

Service/receiver lấy dependency từ `application as CameraAlarmApp`.

Không dùng global mutable singleton ngoài container.

## 5. Data flow chi tiết

```text
[Camera app notification]
       |
       v
CameraNotificationListener
       |
       v
NotificationExtractor
       |
       v
IncomingNotification
       |
       v
TriggerPipeline
       |-- settings monitoring?
       |-- source package?
       |-- DuplicateGuard
       |-- TriggerMatcher
       |
       v
ValidTrigger
       |
       v
AlarmCoordinator
       |
       v
AlarmReducer: Idle -> Pending
       |
       v
AlarmScheduler.scheduleExact()
       |
       v
AlarmManager PendingIntent
       |
   process may die
       |
       v
AlarmReceiver
       |
       v
CameraAlarmService
       |-- foreground notification
       |-- AlarmPlayer.start()
       `-- VibrationController.startRepeating()
       |
       v
STOP
       |
       v
AlarmCoordinator / service cleanup
       |
       v
Cooldown
```

## 6. Process-death strategy

Không giả định process sống trong 1 giây delay.

Vì vậy:

- exact alarm luôn dùng `PendingIntent`, không dùng listener-only API;
- intent mang `alarmToken`, source summary và timestamp cần thiết;
- state machine correctness trong process được bảo vệ bởi coordinator;
- receiver/service vẫn có thể khởi động khi process mới được tạo;
- không phục hồi alarm cũ sau reboot;
- force-stop là ranh giới Android: app không bảo đảm tự chạy cho đến khi người dùng mở lại/OS cho phép.

## 7. Error strategy

Mọi platform adapter trả về kết quả explicit, không nuốt lỗi.

Ví dụ:

```kotlin
sealed interface ScheduleResult {
    data object Scheduled : ScheduleResult
    data object ExactAlarmPermissionMissing : ScheduleResult
    data class Failed(val reason: String) : ScheduleResult
}
```

Nếu schedule fail:

- state không được để ở `Pending` giả;
- history ghi `SCHEDULE_FAILED`;
- UI readiness được refresh nếu liên quan permission.

Nếu audio fail:

- foreground service không crash;
- vibration vẫn chạy nếu bật;
- notification vẫn có STOP;
- history ghi `ALARM_RUNTIME_ERROR`.

## 8. Threading

- Notification callback: extract nhanh, sau đó launch coroutine application scope.
- Room/DataStore: suspend/Flow.
- AlarmCoordinator: serialized qua `Mutex`.
- MediaPlayer lifecycle: điều khiển từ service main thread hoặc một serialized owner; không gọi start/stop cạnh tranh từ nhiều thread.
- UI: collect StateFlow lifecycle-aware.

## 9. Không được làm

- Không gọi `Thread.sleep(1000)` để tạo delay.
- Không giữ một background loop 24/7.
- Không dùng WorkManager cho delay 1 giây.
- Không phát alarm trực tiếp trong NotificationListenerService.
- Không tạo nhiều PendingIntent cho cùng một logical alert.
- Không để Activity là owner duy nhất của alarm state.
