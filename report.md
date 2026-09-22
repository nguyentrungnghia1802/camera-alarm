# Reboot + Pending alarm: điều tra, bản sửa và xác minh cuối

Ngày: 2026-09-22. Nhánh: `main`. Baseline điều tra: `6ce341ad03eb20e5ead4867605a8ad676501d20f`.

Báo cáo này thay thế bản điều tra chỉ đọc ban đầu: người dùng đã yêu cầu hoàn thiện implementation, tests và commit; không push. Các mô tả “trước fix” tham chiếu baseline trên; các mô tả “sau fix” tham chiếu implementation cùng commit với báo cáo. Đường dẫn Kotlin rút gọn tính từ `app/src/main/java/com/personal/cameraalarm/`; tên function/class là điểm đối chiếu chính.

## 1. Executive Summary

**Đã sửa lỗ hổng phục hồi Pending và race trong boot/receiver/service.** Trước fix, Pending không có hạn phục hồi: nếu OS callback mất hoặc chưa đăng ký được trước process death, mọi trigger mới tiếp tục bị `SUPPRESSED_PENDING`. Boot reconciler còn reset state trực tiếp ngoài owner của coordinator, có thể xóa alarm mới của chính boot hiện tại. Đây là lỗi độ tin cậy có bằng chứng code và regression tests, không phải thời gian “ổn định sau boot” có chủ đích.

**Pending ngắn trước khi alarm được giao là hành vi thiết kế chống spam.** Trên emulator API 34 đã đo được `AlarmManager min_futurity=+5s0ms`; request delay 3 giây được giao khoảng 5 giây sau đăng ký OS. Đây là giới hạn platform quan sát được, không phải app tự cộng thêm delay hoặc watchdog bị kẹt. Grace đã tăng từ 5 lên **10 giây sau deadline**, cho cả retry tức thời, để tránh thu hồi token hợp lệ sát sàn 5 giây này. Grace không nằm trong đường phát bình thường.

Kết quả final-code: `test`, `lint`, `assembleDebug`, `assembleRelease`, `assembleAndroidTest` PASS; 135 unit tests cho mỗi variant debug/release; instrumentation API 34: 25 PASS + 1 fixture cố ý skip. Reboot thật, nhập PIN, không mở Camera Alarm: `BOOT_RECONCILED → Pending → Ringing → AUDIO_STARTED`; Android xác nhận MediaPlayer `state:started / USAGE_ALARM`. Notification sau khi Ringing được phân loại `SUPPRESSED_RINGING`, không còn Pending sai. Kill process thật trong Pending cũng phát được token đó trong process mới.

Không có thiết bị/log gốc của người dùng, nên không khẳng định sàn 5 giây hoặc race cụ thể nào đã gây lần trễ ban đầu trên điện thoại đó. Bản sửa giải quyết các lỗ hổng xác định được; bằng chứng runtime cuối giới hạn ở emulator API 34.

## 2. Exact Reproduction Flow

Luồng người dùng báo: monitoring ON → tắt/bật máy → trước PIN chưa xử lý (được chấp nhận) → unlock → `BOOT_RECONCILED` → notification camera đầu tiên → các notification tiếp theo báo “chuông đang chờ phát” → sau một khoảng mới có âm thanh.

Luồng E2E đã chạy trên `emulator-5554 / CameraAlarm_API_34 / Android 14`:

1. Cài APK debug và test từ final implementation. Grant notification, exact alarm và listener. `cmd appops write-settings` lưu quyền trước reboot.
2. Chạy `RebootScenarioPreparation` với `prepareReboot=true, monitoring=true`: persist monitoring ON, delay 3000 ms, cooldown 0, always active, fullscreen OFF; rule `reboot-e2e`, package `com.android.shell`, keyword `reboot probe`. Đây là notification fixture qua OS listener thật; không giả hardware camera/cloud.
3. HOME, `adb reboot`; không gửi broadcast boot giả. Boot count lần xác minh cuối tăng từ 8 lên 9; lần 8 đang Ringing trước reboot.
4. Xác nhận `RUNNING_LOCKED`, không có PID Camera Alarm. Nhập PIN qua keyguard; xác nhận `RUNNING_UNLOCKED`, launcher là top resumed activity. Không mở Camera Alarm, không chạy instrumentation sau unlock trong đoạn E2E này.
5. Chờ log `BOOT_RECONCILED` thật với `action=android.intent.action.BOOT_COMPLETED`, `monitoring=true listener=CONNECTED exact=true config=true reconciled=Idle`.
6. Gửi hai notification có tag khác nhau bằng `cmd notification post -t Camera reboot9-1 "reboot probe first after recovery"` và `reboot9-2 "reboot probe burst"`.
7. Xác nhận một token được scheduled, burst trước delivery bị Pending hợp lệ, cùng token chuyển Ringing/audio, không có `PENDING_RECOVERY` nhầm.
8. Gửi `reboot9-3 "reboot probe after audio started"`; kết quả `SUPPRESSED_RINGING`. Thu `dumpsys audio`, alarm và activities.

Một lần thử sớm gửi notification chỉ 4 giây sau unlock trước khi listener kết nối không nhận được đầy đủ các notification đó. Không tính lần đó là PASS cho luồng sau `BOOT_RECONCILED`; lần boot 9 ở trên là evidence hoàn chỉnh. Hiện app không replay toàn bộ active notifications khi listener kết nối, nên notification xuất hiện trước callback connection vẫn là giới hạn riêng.

## 3. Relevant Components / Files

Các file implementation đã sửa/thêm:

| Nhóm | Files | Vai trò bản sửa |
|---|---|---|
| State/owner | `alarm/AlarmState.kt`, `AlarmCoordinator.kt`, `DataStoreAlarmStateStore.kt` | Persist deadline/attempt; một mutex; hydrate; invalidation atomic; claim/recover/STOP |
| Delivery/recovery | `alarm/AndroidAlarmScheduler.kt`, `AlarmReceiver.kt`, **`PendingRecovery.kt` mới** | PendingIntent theo token; receiver claim owner; grace/watchdog |
| Runtime | `alarm/CameraAlarmService.kt`, `AlarmRuntimeOwnership.kt`, `TestAlarmController.kt` | Revalidate trước audio, chống START/STOP stale, chờ hydration |
| Diagnostic | **`alarm/AlarmTrace.kt` mới** | Session/token, epoch/elapsed, deadline/delay/overdue cho từng stage |
| Boot | `boot/BootReceiver.kt`, `BootReconciler.kt`, `BootActionPolicy.kt`, `NotificationListenerRecovery.kt`, **`RecoveryJobService.kt` mới** | Reconcile idempotent, outcome thật, OS continuation/retry, exact permission change |
| Startup/config | `app/CameraAlarmApp.kt`, `AppContainer.kt`, `data/settings/SettingsRepository.kt` | Khởi động watchdog/hydration; đợi config thật; retry đọc config |
| Notification/pipeline | `notification/CameraNotificationListener.kt`, `IncomingNotification.kt`, `ListenerConnectionState.kt`, `trigger/TriggerPipeline.kt` | Correlation token; reconnect an toàn; config trước decision |
| UI | `ui/main/MainViewModel.kt` | Không coi state Idle mặc định là ready trước hydration |
| Android manifest | `app/src/main/AndroidManifest.xml` | JobService protected bằng BIND_JOB_SERVICE; exact permission change receiver |

Không đổi semantics của `AlarmReducer`, `DuplicateGuard`, rule matching, active hours, cooldown hay monitoring toggle. Không thêm Direct Boot hoặc phát audio trực tiếp từ boot/job.

Tests sửa/thêm nằm dưới `app/src/test/java/com/personal/cameraalarm/` và `app/src/androidTest/java/com/personal/cameraalarm/`, liệt kê ở mục 15. `phone_now.png` có sẵn từ trước, giữ nguyên ngoài commit.

## 4. Boot → Unlock → Notification → Alarm state flow

```text
Reboot, credential storage khóa
  → không chạy audio / không Direct Boot
Unlock + Android start/bind app / BOOT_COMPLETED
  → CameraAlarmApp.startRecovery: watchPending + coordinator.reconcile
  → DataStore transaction: boot khác? clear runtime → Idle
  → settings/rules thật được nạp; không dùng default OFF khi đọc lỗi
  → BootReconciler: hydrate qua coordinator + recover listener + outcome/history
Valid notification (config đã nạp)
  → rules / active hours / dedupe
  → coordinator owner: repair overdue trước suppression
  → arm OS recovery job → persist Pending → schedule exact token-specific
  → Pending chống burst, không kéo dài deadline theo notification tiếp theo
Exact callback
  → owner claim đúng token → persist Ringing → cancel alarm/job → request FGS
  → service kiểm tra owner lần nữa → promote → MediaPlayer.start
STOP đúng token → Cooldown; STOP cũ không được hủy runtime/service owner mới

Nếu không có callback:
  deadline + 10s → watchdog hoặc OS job hoặc hydrate/trigger repair
  → retire token cũ trước cancel → retry đúng 1 lần bằng token mới
  → retry cũng quá grace → Idle + diagnostic failure
```

State phục hồi: monitoring/rules/delay/cooldown/sound/lịch là cấu hình dài hạn, giữ qua reboot. Runtime Pending/Ringing/Cooldown của boot cũ bị invalidated theo `BOOT_COUNT`. Pending cùng boot giữ snapshot, monotonic deadline và retryAttempt qua process death, được đăng ký lại một lần mỗi hydration. Ringing thuộc process nonce cũ bị bỏ để không giả vờ audio vẫn đang chạy. Cooldown cùng boot giữ nguyên. Test token chỉ ở memory, không replay qua reboot.

## 5. Root Cause Analysis

### 5.1 Lỗi code đã xác nhận

- **Pending không có recovery:** baseline `AlarmCoordinator.onValidTrigger` đọc state, reducer gặp Pending luôn suppression; không xét overdue hoặc OS registration. `AlarmReducer.reduce` không phải queue delivery và không tự chạy theo thời gian. Nếu callback mất, không có timer nào tự giải phóng state.
- **Khoảng crash giữa persist và schedule:** baseline ghi Pending trước gọi `scheduleExact`. Process chết ở giữa để lại intent trong DataStore nhưng có thể chưa có alarm OS. Không có repair khi hydrate. Notification đầu tiên chỉ tạo Pending; các notification sau chỉ thấy Pending, không thể tự sửa registration.
- **Boot viết state ngoài owner:** baseline `DefaultBootReconciler` đọc/reset store trực tiếp; bất kỳ state non-Idle nào cũng bị reset, kể cả vừa được tạo trong boot hiện tại. Mutex coordinator không bảo vệ writer này. StateFlow không luôn đồng bộ với persisted state sau restore/reset.
- **Readiness/config bị báo sai:** baseline timeout nạp config bị bỏ qua; IOException ở SettingsRepository emit empty preferences có thể làm monitoring được hiểu là OFF. History `BOOT_RECONCILED` được ghi dù các bước chưa thực sự thành công.
- **Race runtime delivery:** receiver/service delivery bất đồng bộ cần claim token và revalidate trước audio; STOP cũ đến trước START mới hoàn tất không được destroy service của owner mới. Fix serializes production ownership và kiểm tra cả runtime token lẫn Ringing owner.

### 5.2 Điều đã loại trừ / chưa được chứng minh

Không có coroutine/job cũ “sống xuyên reboot”: process và heap cũ mất; chỉ persisted state/cấu hình tồn tại. Với `BOOT_COUNT` hợp lệ, runtime từ boot cũ không được restore thành Pending mới. Không có bằng chứng delay gốc do cooldown 10 phút, debounce của các notification mới kéo deadline, hoặc OEM battery policy. Không khẳng định đã tái hiện đúng mất callback của handset người dùng.

**Delay đã đo trên emulator có nguyên nhân platform:** `min_futurity=5s` áp dụng lúc registration. Nó giải thích phần chờ khoảng 5 giây từ OS registration đến receiver dù delay app=3s. Chênh thêm thời gian load/persist/dispatch/audio được thể hiện riêng ở timeline; không gán toàn bộ latency cho AlarmManager.

## 6. Evidence từ code và runtime

| Kết luận | Điểm code / test kiểm chứng |
|---|---|
| Owner duy nhất serialize state | `AlarmCoordinator.owned/publish/reconcile/onValidTrigger/claimAlarm/withRingingOwnership/onStopRequested`; test `bootAndReceiverWaitForTriggerTransactionAndDoNotEraseNewState` dùng barrier |
| State cũ không xóa state mới | `DataStoreAlarmStateStore.read`: kiểm tra/clear CURRENT prefs trong `dataStore.edit`; `BootStateStoreInstrumentedTest.currentSnapshotTransactionCannotEraseNewBootWrite` 30 interleavings |
| Pending tồn tại không chứng minh OS alarm đã tồn tại | `AlarmCoordinator.load/register`; test `crashAfterPersistBeforeScheduleRearmsOnHydrateAndRepeatedBootDoesNotResetIt` |
| Recovery bounded, không cần notification mới | `PendingRecoveryPolicy`, `watchPending`, `recoverLocked`; test `autonomousWatchdogRetriesOnceThenReleasesWithoutAnotherNotification` |
| Không cancel nhầm alarm khác | `AndroidAlarmScheduler.tokenIntent/cancel`: data URI `cameraalarm://fire/<token>`; `AlarmSchedulerInstrumentedTest` |
| Retry attempt không reset khi restart | Persist `ATTEMPT`, `ELAPSED_DEADLINE`; test `recoveryBudgetSurvivesProcessRestart` |
| Settings read error không thành OFF | `SettingsRepository.settings.retryWhen`; `SettingsReadRecoveryInstrumentedTest` |
| Stale service/STOP | `withRingingOwnership`, `CameraAlarmService.onStartCommand`, `AlarmRuntimeOwnership.canStopService`; unit ownership tests và `AlarmStopInstrumentedTest` |

Evidence boot 9, session `b060753d-276b-45a0-a66f-9ccdd6bf494d`:

```text
BOOT_RECONCILED epoch_ms=1790035536657
 action=android.intent.action.BOOT_COMPLETED
 previous=ringing previous_boot=8 boot=9 invalidated=true reconciled=Idle
 monitoring=true listener=CONNECTED exact=true config=true result=ALREADY_CONNECTED error=null
AudioPlaybackConfiguration piid:103 type:android.media.MediaPlayer
 u/pid:10194/2049 state:started usage=USAGE_ALARM mutedState:none
AlarmManager: min_futurity=+5s0ms
```

Boot receiver và continuation job đều có thể ghi outcome thành công; hai log success không phải hai lần phát. Trong E2E chỉ một token được claim/audio. `dumpsys activity broadcasts` xác nhận BootReceiver DELIVERED cho BOOT_COMPLETED thật, không phải log từ broadcast giả.

Raw evidence tại `app/build/reboot-evidence/boot9.log`, `boot9-audio.txt`, `boot9-alarm.txt`, `boot9-activities.txt`, `boot9-broadcasts.txt`, `process-death.log`, `process-death-audio.txt`. Đây là build artifacts ignored, không nằm trong commit; các token/timestamps/kết quả quan trọng được giữ ngay trong báo cáo. Instrumentation có XML và per-test logcat ở `app/build/outputs/androidTest-results/connected/debug/`.

Nguồn platform: [Android Direct Boot](https://developer.android.com/privacy-and-security/direct-boot), [FGS background start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [JobService](https://developer.android.com/reference/android/app/job/JobService). [AOSP AlarmManagerService](https://android.googlesource.com/platform/frameworks/base/+/4896c9224b21cbf882474abcff6ad5c6981d2db0/apex/jobscheduler/service/java/com/android/server/alarm/AlarmManagerService.java) tính minTrigger từ nowElapsed + MIN_FUTURITY cho non-core UID; kết luận về emulator còn dựa trên dump thực tế, không giả định mọi ROM đều có cùng giá trị.

## 7. Giải thích chính xác BOOT_Reconciled

Tên decision thực tế là `BOOT_RECONCILED`. Trước fix nó chỉ chứng minh code đã đi đến bước ghi history sau thử reconciliation; không bảo đảm thành công. Sau fix `BootRecoveryOutcome.success` yêu cầu config đã nạp, reconcile trả state, không lỗi, listener CONNECTED/ALREADY_CONNECTED và exact permission nếu monitoring ON. Nếu thiếu điều kiện, ghi `BOOT_RECOVERY_INCOMPLETE` với chi tiết.

Đây là snapshot readiness/reconciliation, **không phải xác nhận audio đang phát**, cũng không bảo đảm callback tương lai đúng deadline. Runtime ở Idle sau boot là bình thường. Listener disconnected/config error có OS continuation, tối đa 5 boot attempts (initial và backoff 30/60/120/240 giây). Thiếu quyền không được tự cấp; exact-permission-granted broadcast cho phép reconcile lại. Boot retry exhausted được log rõ.

Manifest không đăng ký `USER_UNLOCKED` hay `LOCKED_BOOT_COMPLETED`, không `directBootAware`. BOOT_COMPLETED sau unlock và listener bind/process hydration là đường hiện có. Không cần thêm USER_UNLOCKED để phát trước lần unlock đầu; điều đó nằm ngoài scope yêu cầu.

## 8. Điều kiện tạo “Chuông đang chờ phát”

`TriggerPipeline.decide` chỉ đi tới coordinator sau monitoring ON, package/rule match, active hours và dedupe. Trong coordinator, sau hydration/overdue repair, nếu state vẫn Pending thì `AlarmReducer.reduce(ValidTrigger)` trả `RecordSuppression(PENDING)`, được map thành `SUPPRESSED_PENDING`. `ui/history/HistoryScreen.kt` map sang `R.string.decision_suppressed_pending`; `app/src/main/res/values-vi/strings.xml` chứa “Đã bỏ qua (chuông đang chờ phát)”.

Pending có nghĩa **đã nhận ý định phát, đang chờ delivery**, chưa có audio; vì vậy Pending nhưng chưa có chuông phát là bình thường trước callback. Sai là giữ Pending vô hạn khi delivery không còn khả năng tới. Bản fix không xóa suppression chống spam: notification đến trong delay/grace vẫn bị bỏ qua và không đổi deadline. Khi đã Ringing, quyết định phải là `SUPPRESSED_RINGING`. Khi retry exhausted, state Idle cho phép trigger mới.

## 9. Race conditions / stale state / pending state

- Boot, valid trigger, callback, watchdog, job, STOP, service claim dùng cùng coordinator mutex. Persist/state publication được giữ thành transaction không bị coroutine cancellation cắt giữa chừng; process death vẫn được xử lý qua hydration/jobs.
- Arm recovery job trước persist Pending, rồi đăng ký exact alarm. Crash trước persist có thể để job thừa; job token check làm nó vô hại. Crash sau persist được OS job/process hydrate sửa; test deterministic bao phủ crash window, kill thực tế bao phủ Pending đã đăng ký.
- Retire persist Idle trước cancel OS, sau đó mới tạo token retry. Callback cũ, duplicate recovery hoặc STOP cũ không có quyền với token mới. OS PendingIntent riêng theo token tránh cancel/update payload của token khác.
- Receiver chuyển Ringing và request FGS trong owner. Service queued sau STOP phải revalidate; old START không phát. Old STOP không `stopSelf` nếu Ringing owner mới đang chờ audio. Dispatch FGS exception trả Idle.
- Listener recovery dùng compareAndSet; lần recovery thất bại không ghi DISCONNECTED đè lên CONNECTED vừa nhận.
- In-process timer dùng elapsed deadline, tránh wall-clock rollback kéo dài Pending. Không dùng elapsed timestamp từ boot trước.

Cửa sổ còn lại khi process chết sau retire Idle nhưng trước persist retry có thể làm mất alert đó, nhưng không tạo Pending mồ côi. Không tuyên bố delivery exactly-once xuyên mọi điểm crash; token ownership bảo đảm callback stale không phát lại state đã retired.

## 10. Timeline của lỗi và bản fix

### Trước fix (interleaving được suy ra từ code, không phải log handset)

```text
T0 unlock → boot reconcile thử reset/history
T1 valid notification → persist Pending(token A, deadline D)
T2 process chết trước schedule hoặc delivery bị mất
T3..Tn các valid notifications → SUPPRESSED_PENDING, không xét D
Nếu A cuối cùng tới: Pending → Ringing → audio
Nếu A không tới: chỉ thời gian trôi qua không giải phóng state
```

### Final-code E2E boot 9 (epoch milliseconds)

Token `1be5434a-bd1d-4266-921a-fa3c4a8f5cdf`, delay cấu hình 3000 ms:

| Event | epoch_ms |
|---|---:|
| BOOT_RECONCILED | 1790035536657 |
| Notification received | 1790035553019 |
| Trigger matched | 1790035553159 |
| Pending persisted/published | 1790035553688 |
| Requested deadline | 1790035556187 |
| OS registration returned | 1790035553831 |
| Burst suppression Pending (hợp lệ) | 1790035553880 |
| Receiver entry | 1790035558871 |
| Ringing persisted | 1790035559037 |
| Service requested | 1790035559133 |
| AUDIO_STARTED | 1790035559452 |
| Notification sau audio: SUPPRESSED_RINGING | 1790035574283 |

Registration → receiver ≈ 5040 ms; notification → audio ≈ 6433 ms; requested deadline → audio ≈ 3265 ms. Phần trước registration ≈ 812 ms và receiver → audio ≈ 581 ms đo riêng, không phải grace. Token giữ attempt=0, không recovery nhầm.

### Mất delivery có chủ đích trong instrumentation

Primary alarm bị cancel sau schedule, giữ watchdog/job. `PendingDeliveryInstrumentedTest`: deadline `1790035275015`, retire `1790035285034` (overdue 10019 ms); retry token `9a235828-3e02-4ef6-83cb-b313236cc7c1` ở `1790035285036`; Ringing `1790035290115` (retry delivery ≈ 5.08s); callback token gốc được `STALE_ALARM`. Không cần notification thứ hai để tự recovery.

### Process death thật, không force-stop

Kill Ringing PID 2049 bằng `run-as ... kill -9`; listener tự bind process 4313, không replay audio cũ. Notification mới được SCHEDULED chứng minh không bị Ringing cũ chặn.

Sau đó tạo token `42b0710a-47ef-4b9c-b84b-d7f7a5eeaa65`, Pending `1790035597956`, registered `1790035597968`, kill PID 4313 trước receiver. PID mới 4414 nhận/hydrate cùng token, receiver entry `1790035602990`, Ringing `1790035603245`, AUDIO_STARTED `1790035603361`. `dumpsys audio`: piid 127, UID/PID 10194/4414, MediaPlayer started, USAGE_ALARM. Không có token retry vì delivery còn trong grace.

## 11. Expected Behavior vs Actual Behavior

| Tình huống | Trước fix | Final behavior / evidence |
|---|---|---|
| Trước PIN đầu tiên | Không hoạt động | Giữ nguyên, RUNNING_LOCKED không có app PID |
| Monitoring qua reboot | Persisted nhưng config timeout/read lỗi có thể hiểu sai | Đợi config thật; E2E monitoring=true |
| Boot cũ Pending/Ringing/Cooldown | Boot count invalidates; reset ngoài owner có race | Atomic invalidation; không reset alarm boot hiện tại |
| Burst trong Pending hợp lệ | Suppress | Giữ nguyên, deadline không bị extend |
| Callback mất | Có thể Pending vô hạn | Grace → retry 1 lần → delivery hoặc Idle/failure |
| Callback tới trong grace | Phát khi token hợp lệ | Claim một lần; không recovery sớm ở min_futurity |
| Notification sau audio | Có thể sai nếu state lệch | E2E SUPPRESSED_RINGING |
| STOP/old callback | Có interleaving ngoài owner | Token validation trước claim/audio/stop service |
| Reboot đang Ringing | Không được replay alert cũ | Boot9 previous=ringing boot8 → Idle; không audio trước notification mới |

## 12. Mức độ ảnh hưởng và remaining limitations

Lỗi Pending vô hạn là mức ảnh hưởng cao: có thể bỏ qua cảnh báo camera tiếp theo dù monitoring bật. Race boot có thể làm mất alert vừa tới. Bản sửa không đổi dữ liệu người dùng hay cấu hình giám sát theo lỗi đọc.

Giới hạn còn lại:

- Final runtime được kiểm chứng trên **API 34 emulator**, chưa chạy lại final-code API 31/33/36 hoặc ROM/thiết bị thật. Chưa chứng minh nguyên nhân khởi phát trên handset gốc.
- Emulator chạy headless/no-audio: xác nhận MediaPlayer started bằng Android audio service; không thay cho nghe âm thanh vật lý, kiểm tra loa/Bluetooth/DND/OEM.
- AlarmManager không bảo đảm delay 0/1/3s đúng tuyệt đối. Sàn 5s là số đo emulator này. Grace 10s là policy hữu hạn, không bảo đảm mọi OS/OEM giao trong khoảng đó.
- JobScheduler có thể bị defer do quota/Doze/system load. Có recovery tự động không đồng nghĩa hard realtime. Không đặt SLA wall-clock khi process bị suspend hoặc storage I/O treo. In-process coroutine cũng cần được CPU chạy.
- Không bypass force-stop, thiếu permission, hoặc OS/OEM restrictions. Khi hết retry, alert đang mất được ghi failure/Idle thay vì retry vô hạn; notification mới có thể tạo alert mới.
- Kill Pending và deterministic persist-before-register tests đã chạy; chưa kill thiết bị chính xác tại từng instruction của crash window, chưa kiểm chứng riêng OS job trong điều kiện listener bị OS giữ unbound lâu và app hoàn toàn không được CPU.
- State transaction dùng NonCancellable nên timeout receiver không phải hard deadline nếu storage I/O bị treo. Persistent I/O failure có diagnostics/retry, không có bảo đảm phục hồi dưới hỏng storage kéo dài.
- Notification có trước lúc listener kết nối không được chủ động replay. E2E yêu cầu sau BOOT_RECONCILED đã PASS; không mở rộng thành tính năng replay lịch sử.
- Lint PASS với **0 errors, 92 warnings**; không tuyên bố warning-free. Build có cảnh báo tool đọc SDK XML v3 trong khi SDK XML v4; không chặn các gate. Release APK assemble được, chưa là signed production release/device validation.

## 13. Đề xuất hướng sửa — đã thực hiện

1. Đưa boot reconciliation và mọi production state transition về coordinator owner; hydrate UI/runtime từ store thật.
2. Deadline epoch + elapsed, persist retry budget; Pending watchdog + OS job; token-specific recovery, tối đa 1 retry, retire rõ ràng.
3. Arm recovery trước persist để che registration crash window; restore same-boot Pending re-register idempotently.
4. Receiver claim và service revalidation; guard stale STOP kể cả khi runtime chưa bắt đầu; không phát trực tiếp từ boot/job.
5. Retry transient settings/config reads; chờ initial config; truthful BOOT_RECONCILED/incomplete với bounded continuation.
6. Trace liên tục từ notification tới audio và đưa platform floor vào policy/test.

Follow-up xác minh phù hợp là chạy cùng protocol trên handset gốc và API matrix còn lại. Không cần thêm feature, replay notification hoặc Direct Boot để đóng scope fix này.

## 14. Các file dự kiến cần sửa — đối chiếu bản đã sửa

Các file implementation trong mục 3 đều đã được cập nhật. Không còn patch implementation dự kiến trong phạm vi milestone này. `report.md` được đồng bộ thành báo cáo kết quả cuối; không thay đổi icon, âm thanh, UI feature, lịch/rules semantics hoặc module fake-camera.

Các file test mới: `AlarmRecoveryTest.kt`, `BootStateStoreInstrumentedTest.kt`, `PendingDeliveryInstrumentedTest.kt`, `SettingsReadRecoveryInstrumentedTest.kt`, `RebootScenarioPreparation.kt`.

Các file test cập nhật: `BootReconcilerTest.kt`, `AlarmRuntimeOwnershipTest.kt`, `AlarmSchedulerInstrumentedTest.kt`, `V1IntegrationInstrumentedTest.kt`. Test V1 chọn priority trống từ rule hiện có để chạy lặp sau fixture reboot; không bỏ assertion chức năng. Một lần chạy trước đó thất bại vì priority fixture trùng; sau sửa isolation, full instrumentation final PASS.

## 15. Regression tests và gates đã chạy

| Lệnh / test | Kết quả final-code |
|---|---|
| `.\gradlew.bat test lint assembleDebug assembleRelease assembleAndroidTest` | BUILD SUCCESSFUL (56s ở lần cuối) |
| `:app:testDebugUnitTest` | 135 tests, 0 failures/errors |
| `:app:testReleaseUnitTest` | 135 tests, 0 failures/errors |
| `lint` | PASS, 0 errors / 92 warnings cho app |
| debug / release / androidTest assembly | PASS |
| `.\gradlew.bat :app:connectedDebugAndroidTest` | BUILD SUCCESSFUL; XML 26 cases, 0 failures/errors, 1 skipped = 25 executed PASS |
| `RebootScenarioPreparation` explicit | 1 PASS; fixture skipped trong suite thường theo thiết kế |
| PIN reboot E2E boot8→9 | PASS như mục 2/10; không mở Camera Alarm sau unlock |
| Kill process Ringing rồi Pending | PASS, PID/session thay đổi, audio không replay cũ; Pending cùng token phát trong PID mới |
| `git diff --check` | PASS |

`AlarmRecoveryTest` có 13 tests: burst/single delivery, autonomous retry/exhaustion, zero-delay 5.1s không retry sớm, late delivery, duplicate recovery/old STOP, dispatch exception, hydrate crash window/repeated boot, persisted retry budget, revoke/regrant, failed job registration/schedule, controlled concurrent boot/receiver/trigger, STOP giữa receiver/service, monotonic deadline.

`AlarmRuntimeOwnershipTest` thêm race queued old STOP trước audio mới. `BootReconcilerTest` kiểm tra preservation cùng boot, config timeout và listener incomplete. Instrumentation mới dùng production DataStore cho invalidation/atomicity và settings IO retry; PendingDelivery deliberately cancel primary rồi kiểm chứng recovery tự động, stale token không claim replacement. Các tests hiện có (STOP thực, settings/rules/history/security/migration) đều nằm trong full API34 suite đã PASS.

## 16. Acceptance Criteria cho bản fix

- [x] Monitoring cấu hình không bị tắt do boot hoặc default khi đọc lỗi.
- [x] Runtime boot trước không replay; state mới trong cùng boot không bị reconcile xóa.
- [x] Persisted state và coordinator StateFlow đồng nhất sau restore/transition.
- [x] Pending có recovery khi không có notification mới; retry hữu hạn và failure không giữ Pending vô hạn khi các recovery worker được chạy.
- [x] Burst không tạo thêm alarm hoặc kéo deadline; grace không trì hoãn đường phát bình thường.
- [x] Stale callback/duplicate recovery/STOP cũ không lấy quyền token mới; FGS dispatch failure giải phóng owner.
- [x] Final-code test/lint/build và affected instrumentation API34 PASS sau thay đổi grace/STOP.
- [x] Reboot thật + PIN + không mở app + notification sau BOOT_RECONCILED → MediaPlayer started; notification kế tiếp không bị Pending sai.
- [x] Process death thực trong Pending được phục hồi; old Ringing process không replay.
- [x] Báo cáo tách lỗi app khỏi min_futurity emulator, có timestamps/token và giới hạn.
- [ ] Xác minh lại trên handset gốc và final-code API31/33/36: ngoài evidence của milestone này, không đánh dấu PASS thay.

Milestone được commit trên `main`; không push. Commit hash và working tree cuối được báo trong phản hồi hoàn tất. File untracked `phone_now.png` không thuộc milestone.
