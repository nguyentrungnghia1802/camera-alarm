# Notification and Trigger Pipeline

## 1. Notification listener contract

`CameraNotificationListener` extends `NotificationListenerService`.

Manifest service phải:

- `android:exported="false"`;
- dùng `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` ở service declaration;
- có intent action `android.service.notification.NotificationListenerService`.

Listener chỉ bắt đầu xử lý bình thường sau `onListenerConnected()`.

## 2. Listener connection state

Expose process-level state:

```kotlin
enum class ListenerStatus {
    DISCONNECTED,
    CONNECTED
}
```

UI dùng cùng với Notification Access setting. Hai khái niệm khác nhau:

- access có thể Granted;
- listener vẫn có thể tạm Disconnected.

Không coi `Disconnected` là permission denied.

## 3. Extraction

`NotificationExtractor.from(sbn)` phải null-safe.

### Title

Ưu tiên `EXTRA_TITLE` -> string conversion an toàn.

### Text

Lấy từng field riêng thay vì chỉ merge một field:

- EXTRA_TEXT
- EXTRA_BIG_TEXT
- EXTRA_TEXT_LINES
- EXTRA_SUB_TEXT

CharSequence phải convert bằng `toString()`.

Nếu bundle field sai type, extractor không crash; bỏ qua field đó.

## 4. Normalization

Tạo `searchableText`:

```text
[title, text, bigText, textLines..., subText]
 -> filterNotNull/notBlank
 -> join with newline
 -> Unicode-safe lowercase(Locale.ROOT)
 -> replace all whitespace runs with one space
 -> trim
```

Không strip dấu tiếng Việt và không transliterate trong V1.

Ví dụ:

```text
"  HUMAN\nDetected   at Door "
=> "human detected at door"
```

## 5. Trigger matching order

Thứ tự bắt buộc:

1. Monitoring enabled?
2. Package matches selected source?
3. Dedupe check.
4. Có rule enabled cho package?
5. Keyword match.
6. Submit `ValidTrigger` cho AlarmCoordinator.

Lý do package check trước normalization/matcher: giảm work và tránh app khác vô tình chứa cùng keyword.

## 6. Match modes

### CONTAINS_ANY

Rule match nếu ít nhất một keyword normalized là substring của `searchableText`.

### CONTAINS_ALL

Rule match nếu mọi keyword normalized đều là substring.

Rule có `keywords.isEmpty()` luôn invalid và không match.

Nếu nhiều rule match, V1 chọn rule enabled đầu tiên theo `priority` tăng dần rồi `createdAt`.

Rule model bổ sung:

```kotlin
data class TriggerRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val sourcePackage: String,
    val matchMode: MatchMode,
    val keywords: List<String>,
    val priority: Int,
    val createdAtEpochMs: Long
)
```

## 7. Dedupe

`DuplicateGuard` là component riêng và test được.

API đề xuất:

```kotlin
interface DuplicateGuard {
    fun isDuplicate(key: String, nowEpochMs: Long): Boolean
    fun markSeen(key: String, nowEpochMs: Long)
    fun prune(nowEpochMs: Long)
}
```

TTL default: 30,000 ms.

Cache bounded: tối đa 200 entries; prune expired trước, nếu vẫn quá giới hạn thì bỏ entry cũ nhất.

Mark seen xảy ra trước khi submit match success để repost cùng key không tạo trigger đôi. Tuy nhiên event wrong-package không cần lưu vào dedupe cache vì đã bị loại sớm.

## 8. History decisions

Mỗi notification source package đã chọn nên có record đủ để debug, nhưng tránh write database quá nhiều cho mọi notification hệ thống.

Policy:

- Wrong package: có thể không ghi DB mặc định để tránh noise; debug build có thể logcat.
- Selected package: ghi decision chính.

Decision order cho selected package:

```text
RECEIVED
 -> IGNORED_MONITORING_OFF
 -> IGNORED_DUPLICATE
 -> IGNORED_NO_RULE_MATCH
 -> SCHEDULED / suppression / SCHEDULE_FAILED
```

Không bắt buộc ghi cả RECEIVED và final decision thành hai rows; V1 có thể ghi một row với final decision. `RECEIVED` dùng cho debug nếu cần.

## 9. Source app picker

UI source picker query các launcher apps bằng `PackageManager` với MAIN/LAUNCHER intent và manifest `<queries>` tương ứng, tránh `QUERY_ALL_PACKAGES` trong V1.

Mỗi item:

```text
icon
app label
package name
```

Nếu app camera không hiện trong picker, Advanced section cho nhập package name thủ công.

Sau khi lưu source mới:

- rules cũ có source package khác không được silently chuyển;
- user có thể tạo rule mới;
- Monitoring Ready yêu cầu ít nhất một enabled rule khớp source đang chọn.

## 10. Default example rules

Không hardcode hãng cụ thể làm assumption hệ thống. Có thể cung cấp template người dùng sửa:

```text
Name: Person / motion alert
Mode: CONTAINS_ANY
Keywords:
- human detected
- person detected
- motion detected
- phát hiện người
- phát hiện chuyển động
```

Template không tự bật cho tới khi user xác nhận.

## 11. Security and robustness

- Không execute text trong notification.
- Không parse URI/HTML để mở tự động.
- Không click PendingIntent của app camera.
- Text chỉ dùng để match và hiển thị preview.
- Truncate text preview trước khi lưu DB.
- Không log full notification text ở release build.
