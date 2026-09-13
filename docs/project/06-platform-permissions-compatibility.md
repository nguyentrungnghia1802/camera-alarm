# Platform Permissions and Compatibility

## 1. Baseline SDK

```text
minSdk     26
compileSdk 36
targetSdk  36
```

Lý do min 26: giảm nhánh compatibility và có `VibrationEffect` waveform API baseline.

## 2. Manifest permissions

Baseline V1:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

Phase 2 nếu bật full-screen intent:

```xml
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

V1 không cần:

```text
INTERNET
CAMERA
RECORD_AUDIO
ACCESS_FINE_LOCATION
QUERY_ALL_PACKAGES
```

## 3. Notification listener permission model

`BIND_NOTIFICATION_LISTENER_SERVICE` không phải runtime permission request thông thường. Nó được khai báo trên service và người dùng cấp Notification Access từ Settings.

UI dùng Intent system settings để người dùng tự cấp.

Listener phải đợi `onListenerConnected()` trước các operation yêu cầu connection.

## 4. Exact alarm access

Android 12+ yêu cầu special app access khi dùng `SCHEDULE_EXACT_ALARM`.

V1 phải:

- check `canScheduleExactAlarms()`;
- mở `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` khi user bấm Grant;
- re-check trong `onResume` của Activity;
- lắng nghe permission-state-changed broadcast nếu implementation cần refresh nhanh;
- không assume quyền tồn tại vĩnh viễn.

## 5. POST_NOTIFICATIONS

Android 13+:

- request runtime permission sau khi giải thích lý do;
- nếu denied, foreground service vẫn có system-level behavior nhưng foreground notification có thể không xuất hiện trong notification drawer như bình thường;
- vì STOP notification là UX quan trọng, readiness phải cảnh báo khi permission denied.

Core auto-trigger có thể được đánh dấu `Degraded` thay vì `Ready` nếu app vẫn có đường STOP qua alarm screen/main screen. Tuy nhiên V1 recommended policy: yêu cầu notification permission để đạt `Ready`.

## 6. Foreground service background start

Apps target Android 12+ thường không được tự khởi động foreground service từ background ngoại trừ các trường hợp cho phép.

Architecture V1 không start alarm FGS trực tiếp từ NotificationListener callback. Nó schedule exact alarm trước; exact alarm phục vụ action user-facing là path được platform cho phép để hoàn thành time-sensitive action.

## 7. FGS type Android 14+

Target 34+ phải có foreground-service type phù hợp.

V1 dùng `systemExempted` vì Android mô tả apps giữ exact-alarm permission có thể dùng type này để tiếp tục alarm ở background, kể cả haptic-only alarm.

Phải declare:

```text
FOREGROUND_SERVICE_SYSTEM_EXEMPTED
```

Nếu platform từ chối type, không được fallback âm thầm sang type unrelated. Ghi lỗi rõ và giữ readiness/error diagnostics.

## 8. Full-screen intents Android 14+

Full-screen intent chỉ là Phase 2 enhancement.

Android 14+ kiểm soát quyền chặt hơn cho alarm/call-style experiences. App phải check `canUseFullScreenIntent()` và cho người dùng mở Settings nếu cần.

Không được coi full-screen permission là điều kiện để audio/vibration hoạt động.

## 9. Android 17 forward compatibility

Mặc dù target baseline là API 36, thiết kế phải tránh đường audio background mơ hồ:

- có exact-alarm access;
- audio dùng `USAGE_ALARM`;
- alarm runtime chạy foreground service;
- không phát background audio tùy tiện từ NotificationListenerService.

Khi project nâng target API 37, đọc lại behavior changes trước khi đổi target.

## 10. OEM battery management

Samsung/Xiaomi/Oppo/... có thể thêm battery management riêng ngoài AOSP.

V1 không tự yêu cầu user disable battery optimization ngay lập tức. Thay vào đó:

- test trên thiết bị mục tiêu;
- nếu OEM thực tế làm listener/alarm unreliable, thêm troubleshooting screen/documentation cụ thể;
- không spam user bằng nhiều special-access request không chứng minh là cần.

## 11. Readiness model

```kotlin
data class ReadinessState(
    val notificationAccessGranted: Boolean,
    val listenerConnected: Boolean,
    val exactAlarmGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val sourceConfigured: Boolean,
    val ruleConfigured: Boolean,
    val alarmVolumeNonZero: Boolean
)
```

Derived:

```text
BLOCKING:
- notification access false
- exact alarm false
- source false
- rule false

RECOMMENDED/DEGRADED:
- listener temporarily disconnected
- POST_NOTIFICATIONS false
- alarm volume = 0
```

V1 UI có thể yêu cầu tất cả green để người dùng bật Monitoring, nhưng domain phải phân biệt permission bị thiếu và transient disconnect.

## 12. Official Android references

- NotificationListenerService: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- Exact alarms: https://developer.android.com/develop/background-work/services/alarms
- Foreground-service background start restrictions: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Foreground-service types: https://developer.android.com/develop/background-work/services/fgs/service-types
- Notification runtime permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Android 14 full-screen intent changes: https://developer.android.com/about/versions/14/behavior-changes-14
- AudioAttributes: https://developer.android.com/reference/android/media/AudioAttributes
- MediaPlayer wake mode: https://developer.android.com/media/platform/mediaplayer/basics
- VibrationEffect: https://developer.android.com/reference/android/os/VibrationEffect
