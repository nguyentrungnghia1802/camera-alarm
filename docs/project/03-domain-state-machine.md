# Domain Model and Alarm State Machine

## 1. Mục tiêu

Alarm state machine là phần correctness quan trọng nhất. Mọi đường trigger thật phải đi qua state machine này để bảo đảm:

- không có hai alarm cùng lúc;
- không schedule lặp;
- STOP luôn dừng đúng;
- cooldown nhất quán;
- race condition được xử lý rõ.

## 2. Domain types

### 2.1 AlarmToken

```kotlin
@JvmInline
value class AlarmToken(val value: String)
```

Tạo bằng UUID khi một trigger được chấp nhận từ `Idle`.

### 2.2 TriggerSnapshot

```kotlin
data class TriggerSnapshot(
    val alarmToken: AlarmToken,
    val sourcePackage: String,
    val notificationKey: String,
    val ruleId: String,
    val title: String?,
    val textPreview: String?,
    val receivedAtEpochMs: Long
)
```

Snapshot phải đủ để service hiển thị alert mà không cần đọc lại notification gốc.

### 2.3 AlarmPolicy

```kotlin
data class AlarmPolicy(
    val delayMs: Long = 1_000,
    val cooldownMs: Long = 600_000,
    val vibrationEnabled: Boolean = true
)
```

Validation:

- `delayMs` thuộc `{0, 1000, 3000, 5000}` trong UI V1;
- `cooldownMs >= 0`;
- repository không trả số âm.

## 3. States

```kotlin
sealed interface AlarmState {
    data object Idle : AlarmState

    data class Pending(
        val trigger: TriggerSnapshot,
        val scheduledAtEpochMs: Long
    ) : AlarmState

    data class Ringing(
        val trigger: TriggerSnapshot,
        val startedAtEpochMs: Long
    ) : AlarmState

    data class Cooldown(
        val untilEpochMs: Long,
        val lastAlarmToken: AlarmToken
    ) : AlarmState
}
```

## 4. Events

```kotlin
sealed interface AlarmEvent {
    data class ValidTrigger(val trigger: TriggerSnapshot) : AlarmEvent
    data class ExactAlarmFired(val trigger: TriggerSnapshot) : AlarmEvent
    data class StopRequested(val alarmToken: AlarmToken?) : AlarmEvent
    data class ScheduleFailed(val alarmToken: AlarmToken) : AlarmEvent
}
```

## 5. Effects

Reducer không chạy Android API; nó phát effect để coordinator thực hiện.

```kotlin
sealed interface AlarmEffect {
    data class ScheduleExact(
        val trigger: TriggerSnapshot,
        val triggerAtEpochMs: Long
    ) : AlarmEffect

    data class StartRinging(val trigger: TriggerSnapshot) : AlarmEffect
    data class StopRuntime(val alarmToken: AlarmToken?) : AlarmEffect
    data class RecordSuppression(val reason: SuppressionReason) : AlarmEffect
    data class RecordFailure(val reason: String) : AlarmEffect
}
```

## 6. Transition rules

### 6.1 Idle + ValidTrigger

```text
Idle
 + ValidTrigger(T)
 -> Pending(T, now + delay)
 + ScheduleExact(T)
```

### 6.2 Pending + ValidTrigger

```text
Pending
 + ValidTrigger(any)
 -> Pending unchanged
 + suppress: PENDING
```

Không reschedule sang notification mới. Alert đầu tiên thắng cho đến khi alarm được xử lý hoặc fail.

### 6.3 Pending + ExactAlarmFired cùng token

```text
Pending(T)
 + ExactAlarmFired(T)
 -> Ringing(T, now)
 + StartRinging(T)
```

### 6.4 Pending + ExactAlarmFired khác token

Không được thay pending hiện tại. Ghi stale alarm event và ignore.

### 6.5 Idle + ExactAlarmFired

Đây là case process state bị mất/recreated nhưng exact PendingIntent vẫn hợp lệ.

Để không bỏ alarm thật, receiver/service path được phép start runtime khi token là một token do app tạo và payload hợp lệ. Coordinator sau khi hydrate từ persisted pending metadata phải cố gắng đưa state tương ứng sang Ringing.

Yêu cầu implementation: persist pending snapshot tối thiểu trước khi coi schedule thành công.

### 6.6 Ringing + ValidTrigger

```text
Ringing
 + ValidTrigger(any)
 -> Ringing unchanged
 + suppress: RINGING
```

### 6.7 Ringing + StopRequested

Nếu token null hoặc token khớp alarm hiện tại:

```text
Ringing(T)
 + StopRequested
 -> Cooldown(now + cooldown, T.token)
 + StopRuntime(T.token)
```

Nếu token không khớp: ignore stale STOP.

### 6.8 Pending + StopRequested

V1 không expose nút STOP pending trong UI chính, nhưng coordinator vẫn phải an toàn.

Nếu token khớp:

```text
Pending(T)
 + StopRequested
 -> Cooldown(now + cooldown, T.token)
 + cancel exact pending
```

### 6.9 Cooldown + ValidTrigger

Nếu `now < until`:

```text
Cooldown
 -> Cooldown unchanged
 + suppress: COOLDOWN
```

Nếu `now >= until`:

```text
Cooldown expired logically
 + ValidTrigger(T)
 -> Pending(T, now + delay)
 + ScheduleExact(T)
```

Không cần timer riêng để chuyển Cooldown -> Idle.

### 6.10 Schedule failure

Nếu Pending token khớp:

```text
Pending(T)
 + ScheduleFailed(T.token)
 -> Idle
 + record failure
```

Không để UI nghĩ đang Pending.

## 7. Persisted runtime metadata

Preferences DataStore lưu tối thiểu:

```text
pendingAlarmToken
pendingTriggerJson/fields
pendingScheduledAtEpochMs
ringingAlarmToken (nếu runtime đã bắt đầu)
cooldownUntilEpochMs
lastAlarmToken
```

Không dùng persisted state để tự khôi phục alarm sau reboot. Khi boot ID thay đổi hoặc app phát hiện reboot, stale pending/ringing metadata phải được clear.

Một cách đơn giản V1: lưu `bootCount` từ `Settings.Global.BOOT_COUNT` cùng runtime metadata và chỉ hydrate nếu boot count khớp.

Nếu không muốn đọc BOOT_COUNT vì testability, lưu `elapsedRealtime` marker và clear runtime metadata trong app startup khi thấy timestamp không hợp lý. Agent được chọn một cách, nhưng phải có test và không được tự ring lại sau reboot.

## 8. Coordinator serialization

Pseudo-flow:

```kotlin
suspend fun onValidTrigger(trigger: TriggerSnapshot) = mutex.withLock {
    val current = stateRepository.currentState(clock.now())
    val transition = reducer.reduce(current, ValidTrigger(trigger), clock.now(), policy)
    persistTransitionState(transition.nextState)
    executeEffects(transition.effects)
}
```

Quan trọng: với `ScheduleExact`, thứ tự phải tránh trạng thái giả.

Recommended transaction logic:

1. reducer tạo Pending candidate;
2. persist pending metadata;
3. gọi scheduler;
4. nếu scheduler success -> commit logical Pending;
5. nếu fail -> clear pending và transition Idle + history failure.

Do DataStore không phải multi-resource transaction với AlarmManager, implementation phải idempotent để retry/cleanup an toàn.

## 9. Invariants bắt buộc test

```text
INV-1: Không tồn tại Pending và Ringing đồng thời.
INV-2: ValidTrigger trong Pending không schedule thêm alarm.
INV-3: ValidTrigger trong Ringing không schedule thêm alarm.
INV-4: STOP từ Ringing luôn tạo StopRuntime tối đa một lần logic.
INV-5: Stale ExactAlarmFired không thay alarm token đang active.
INV-6: Cooldown trước deadline suppress trigger.
INV-7: Trigger đầu tiên sau deadline được phép schedule.
INV-8: Schedule failure không để state Pending.
INV-9: Duplicate notification không tới state machine lần hai.
INV-10: Repeated STOP không restart hoặc double-release runtime.
```
