# Data, UI and Observability

## 1. Settings storage

Dùng Preferences DataStore cho cấu hình nhỏ.

`AppSettings`:

```kotlin
data class AppSettings(
    val monitoringEnabled: Boolean,
    val sourcePackage: String?,
    val sourceLabel: String?,
    val alarmDelayMs: Long,
    val cooldownMs: Long,
    val vibrationEnabled: Boolean
)
```

Rule list có thể lưu bằng Room cùng history hoặc một bảng riêng để tránh serialize collection phức tạp trong Preferences.

Recommended: Room có `trigger_rules` và `alert_events`; DataStore chỉ giữ scalar settings/runtime metadata.

## 2. Room schema

### trigger_rules

```text
id TEXT PRIMARY KEY
name TEXT NOT NULL
enabled INTEGER NOT NULL
sourcePackage TEXT NOT NULL
matchMode TEXT NOT NULL
keywordsJson TEXT NOT NULL
priority INTEGER NOT NULL
createdAtEpochMs INTEGER NOT NULL
updatedAtEpochMs INTEGER NOT NULL
```

`keywordsJson` dùng kotlinx.serialization JSON; không cần TypeConverter phức tạp ngoài String <-> List.

### alert_events

```text
id INTEGER PRIMARY KEY AUTOINCREMENT
createdAtEpochMs INTEGER NOT NULL
sourcePackage TEXT
notificationKey TEXT
title TEXT
textPreview TEXT
normalizedHash TEXT
decision TEXT NOT NULL
ruleId TEXT
alarmToken TEXT
details TEXT
```

Index:

- `createdAtEpochMs` descending use-case;
- `alarmToken` nếu query lifecycle một alarm.

Retention:

- sau insert, xoá event quá 3 ngày, giữ tối đa 100 rows và tối đa 10 rows suppressed/ignored gần nhất;
- cleanup có thể batch sau insert, không cần worker định kỳ.

## 3. Main screen

Main screen ưu tiên trạng thái, không ưu tiên decoration.

Wireframe logic:

```text
Camera Alarm

Monitoring                 [ON/OFF]
Status                     READY / NEEDS SETUP / ALARMING

Setup
[✓] Notification access
[✓] Exact alarm access
[✓] Notifications
[✓] Source app: <Camera app>
[✓] Trigger rule: 1 enabled
[!] Alarm volume: Low

Alarm
Delay                      1 second
Cooldown                   600 seconds
Vibration                  ON

[ TEST ALARM ]

Recent
Last trigger: ...
Last decision: ...

[ Rules ] [ History ] [ Settings ]
```

Monitoring toggle:

- OFF luôn cho phép.
- ON chỉ cho phép khi blocking readiness requirements đạt.
- nếu user mất blocking permission trong lúc đang ON, repository tự coi monitoring ineffective và UI chuyển `NEEDS SETUP`; không tiếp tục giả Ready.

## 4. Rule editor

Fields:

```text
Name
Source app (read-only from selected source or selectable)
Match mode: Any / All
Keywords (individual cards in a bounded scrolling region)
Enabled
Priority
```

Validation:

- name non-blank;
- sourcePackage non-blank;
- ít nhất một keyword sau normalize;
- max 30 keywords;
- mỗi keyword max 100 chars.

## 5. Settings screen

Settings are grouped consistently in both EN and VI:

```text
Basic
- sound, vibration, cooldown, active schedule, common behavior

Advanced
- full-screen/reliability, permissions/diagnostics, data actions
```

Settings behavior:

- Alarm delay: 0 / 1 / 3 / 5 seconds.
- Cooldown: numeric seconds; fresh/reset default 600 seconds.
- Fresh/reset sound: Loud Warning 1 (`alarm_warning_aloud`).
- Fresh/reset schedule: daily 22:30–06:00, start-inclusive/end-exclusive.
- Vibration: on/off.
- Clear history.
- Diagnostics.
- Reset defaults is atomic and never deletes rules/history or changes system permissions.
- Profile versioning preserves the old effective defaults on upgrade when V1 had not persisted a field.

Phase 2 enhancement:

- Full-screen alarm toggle.
- Alarm screen snooze nếu sau này cần; không có trong core V1.

## 6. AlarmActivity

Nếu full-screen được bật và permission hợp lệ:

```text
🚨 CAMERA ALERT

<rule/source summary>
<title>
<text preview>
<time>

[ STOP ALARM ]
```

Requirements:

- STOP button lớn, một thao tác.
- Không có gesture khó.
- Không tự đóng alarm trước khi STOP.
- Nếu Activity bị destroy, service vẫn kêu.
- Activity là view, không phải owner của audio.

## 7. History screen

List newest-first.

Mỗi row:

```text
time
decision badge
source label/package
short text preview
rule/alarm token expandable diagnostics
```

Filter đơn giản:

- All
- Triggered/Scheduled
- Suppressed
- Errors

Không cần search full text ở V1.

## 8. Diagnostics screen

Mục đích: debug trên chính điện thoại.

Hiển thị:

```text
App version
SDK version
Device manufacturer/model
Notification access
Listener status
Exact alarm access
POST_NOTIFICATIONS
Can full-screen intent (nếu feature bật)
Alarm stream current/max volume
Monitoring enabled
Source package
Enabled rule count
Current alarm state
Last scheduler error
Last runtime error
```

Có nút:

- Open Notification Access Settings
- Open Alarms & Reminders Settings
- Open App Notification Settings
- Test Alarm
- Copy Diagnostics (không copy notification text mặc định)

## 9. Logging

Debug build:

- structured Logcat tags: `NotifListener`, `TriggerPipeline`, `AlarmCoordinator`, `AlarmScheduler`, `AlarmService`.

Release build:

- không log full camera notification text;
- chỉ log event ID/token + decision + package khi cần.

History database là nguồn observability chính cho người dùng.

## 10. UI state management

Mỗi screen có ViewModel nhỏ.

Không tạo một God ViewModel cho toàn app.

Example:

```text
MainViewModel
RuleListViewModel
RuleEditorViewModel
HistoryViewModel
DiagnosticsViewModel
AlarmViewModel (nếu AlarmActivity cần)
```

Repositories expose Flow. Compose collect lifecycle-aware.
