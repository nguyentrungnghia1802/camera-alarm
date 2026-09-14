# Fake Camera Test Tool & E2E Verification Guide

Tài liệu hướng dẫn sử dụng công cụ **Fake Camera** (`:fake-camera`) để kiểm thử toàn bộ pipeline cảnh báo của ứng dụng **Camera Alarm**.

---

## 1. Fake Camera dùng để test gì?

Fake Camera mô phỏng một ứng dụng camera IP (như Ezviz, Imou, Tuya, Xiaomi Camera, Tapo, v.v.) gửi thông báo cảnh báo về hệ thống Android.

Công cụ này giúp kiểm thử pipeline:
```
Fake Camera App (com.personal.fakecamera)
      │
      │  (Android Notification thật qua NotificationChannel IMPORTANCE_HIGH)
      ▼
Camera Alarm NotificationListenerService (CameraNotificationListener)
      │
      ▼
NotificationExtractor (trích xuất Title, Text, BigText, PostTime)
      │
      ▼
TriggerPipeline
      ├─ Package filter (com.personal.fakecamera)
      ├─ Keyword matcher (TriggerMatcher)
      ├─ Active Schedule gate (ScheduleConfiguration)
      └─ DuplicateGuard (TtlDuplicateGuard)
      │
      ▼
Alarm Engine (AlarmCoordinator -> AlarmReducer)
      │
      ▼
Alarm Runtime (CameraAlarmService, AlarmActivity full-screen, MP3 & Ringer, Vibration)
```

**Ưu điểm:**
* Không cần có camera vật lý thật.
* Không phụ thuộc internet, cloud camera, hay motion thực tế.
* Có thể tái hiện ngay lập tức các tình huống: spam thông báo dồn dập, thông báo ngoài giờ giám sát, thông báo offline không khớp từ khóa.

---

## 2. Cách cài đặt và cấu hình

### Bước 1: Cài đặt cả hai ứng dụng lên cùng một máy
```bash
# Cài Camera Alarm (production app)
./gradlew :app:installDebug

# Cài Fake Camera (test tool)
./gradlew :fake-camera:installDebug
```

### Bước 2: Cấp quyền cần thiết
1. Mở **Fake Camera** (`Front Door Camera`):
   * Cấp quyền **Notifications** (`POST_NOTIFICATIONS`) khi có thông báo yêu cầu (Android 13+).
2. Mở **Camera Alarm**:
   * Cấp quyền **Notification Access** (NotificationListenerService).
   * Cấp quyền **Exact Alarms** (`SCHEDULE_EXACT_ALARM`).
   * Cấp quyền **Full Screen Intent** và bỏ tối ưu hóa pin nếu trên Xiaomi/MIUI.

### Bước 3: Cấu hình Camera Alarm
1. Mở **Camera Alarm** -> Chọn **Source App**.
2. Chọn **Front Door Camera** (package: `com.personal.fakecamera`).
3. Cấu hình quy tắc cảnh báo (Trigger Rules):
   * Rule 1: Keywords `human detected`, Match Mode: `CONTAINS_ANY`, Priority: 1.
   * Rule 2: Keywords `motion detected`, Match Mode: `CONTAINS_ANY`, Priority: 2.
4. Bật công tắc **Enable Monitoring**.

---

## 3. Các kịch bản kiểm thử (Verification Scenarios)

---

### Test 1 — Basic trigger (Kích hoạt cảnh báo cơ bản)

* **Mục tiêu:** Xác nhận notification hợp lệ từ Fake Camera kích hoạt toàn bộ luồng báo động từ đầu đến cuối.
* **Các bước thực hiện:**
  1. Mở **Fake Camera**.
  2. Bấm nút **[ Human Detected ]**.
* **Kỳ vọng:**
  * Fake Camera hiển thị notification `Human detected: Front Door Camera detected a person`.
  * `CameraNotificationListener` nhận notification.
  * `TriggerMatcher` khớp với rule `human detected`.
  * Trạng thái Alarm chuyển sang `SCHEDULED` rồi `RINGING`.
  * Chuông báo động (MP3) và rung bắt đầu phát; màn hình `AlarmActivity` hiển thị toàn màn hình (kể cả khi màn hình đang khóa).
  * Bấm nút **STOP** trên màn hình cảnh báo -> Chuông tắt ngay lập tức, chuyển sang trạng thái `Cooldown`.

---

### Test 2 — Package filtering (Lọc theo Package)

* **Mục tiêu:** Xác nhận Camera Alarm chỉ xử lý notification từ package đã chọn (`com.personal.fakecamera`), các app khác không thể kích hoạt.
* **Các bước thực hiện:**
  1. Giữ nguyên cấu hình Source App là `com.personal.fakecamera`.
  2. Dùng một app khác (ví dụ: Gmail, Zalo, hoặc Telegram) gửi một thông báo có chứa từ khóa `human detected`.
* **Kỳ vọng:**
  * Notification của app khác hiển thị trên thanh thông báo.
  * Trong Camera Alarm History ghi nhận: `IGNORED_WRONG_PACKAGE`.
  * Không có báo động nào được kích hoạt.

---

### Test 3 — Keyword filtering (Lọc theo từ khóa)

* **Mục tiêu:** Xác nhận notification không khớp từ khóa đã cấu hình sẽ bị bỏ qua.
* **Các bước thực hiện:**
  1. Cấu hình rule chỉ gồm các từ khóa: `human detected`, `motion detected`.
  2. Mở **Fake Camera**.
  3. Bấm nút **[ Camera Offline ]** (Title: `Camera offline`, Text: `Front Door Camera connection lost`).
* **Kỳ vọng:**
  * Fake Camera gửi notification `Camera offline`.
  * `TriggerMatcher` không tìm thấy từ khóa trùng khớp.
  * `TriggerPipeline` trả về quyết định: `IGNORED_NO_RULE_MATCH`.
  * Không kích hoạt báo động.

---

### Test 4 — Spam notification (Kiểm tra chống spam)

* **Mục tiêu:** Kiểm tra cơ chế chống spam của Alarm Engine khi camera gửi liên tiếp nhiều cảnh báo trong thời gian rất ngắn.
* **Các bước thực hiện:**
  1. Đảm bảo switch **Unique Notification ID** trong Fake Camera đang BẬT.
  2. Bấm nhanh nút **[ Human Detected ]** 5 lần liên tiếp (trong vòng 1 - 2 giây).
* **Kỳ vọng:**
  * 5 notification riêng biệt được gửi lên hệ thống Android.
  * `TriggerPipeline` xử lý cả 5 notification.
  * Quyết định tương ứng:
    * Lần 1: `SCHEDULED` (Alarm được lên lịch / chuyển trạng thái `Pending`).
    * Lần 2, 3, 4, 5: `SUPPRESSED_PENDING` (bị triệt tiêu vì đang có một alarm đang pending).
  * **Chỉ có 1 chuông báo duy nhất được kích hoạt**, không tạo 5 alarm chồng chéo.

---

### Test 5 — Active schedule Phase 3 (Kiểm tra khung giờ hoạt động)

* **Mục tiêu:** Xác nhận ngoài khung giờ giám sát (Active Hours), thông báo camera sẽ bị triệt tiêu không gây phiền.
* **Các bước thực hiện:**
  1. Trong Camera Alarm, bật cấu hình lịch hoạt động (Active Hours): ví dụ `23:00 - 07:00`.
  2. Thực hiện test tại thời điểm ban ngày (ví dụ 14:00, nằm ngoài khung giờ trên).
  3. Mở Fake Camera, bấm nút **[ Human Detected ]**.
* **Kỳ vọng:**
  * Fake Camera gửi notification bình thường.
  * `ActiveScheduleGate` đánh giá thời điểm gửi: `ScheduleDecision.OUTSIDE_ACTIVE_HOURS`.
  * `TriggerPipeline` ghi nhận quyết định: `SUPPRESSED_OUTSIDE_ACTIVE_HOURS`.
  * Chuông báo **KHÔNG kêu**.

---

### Test 6 — Xiaomi / Background execution (Kiểm tra chạy ngầm & màn hình khóa)

* **Mục tiêu:** Xác nhận trên thiết bị thật (đặc biệt dòng máy Xiaomi/HyperOS/MIUI có cơ chế tiết kiệm pin nghiêm ngặt), cảnh báo vẫn đánh thức màn hình và reo chuông khi app chạy ngầm.
* **Các bước thực hiện:**
  1. Trên điện thoại Xiaomi:
     * Cấp quyền "Autostart" (Tự khởi chạy) cho Camera Alarm.
     * Tắt "Battery Saver" (Chọn *No restrictions*).
     * Cấp quyền "Display pop-up windows while running in the background".
     * Cấp quyền "Show on Lock screen".
  2. Mở Camera Alarm bật Monitoring, sau đó thoát hoàn toàn giao diện về màn hình Home hoặc xóa khỏi Recents.
  3. Khóa màn hình điện thoại (Screen OFF).
  4. Mở Fake Camera (hoặc gửi qua adb / trigger hẹn giờ) gửi thông báo `Human Detected`.
* **Kỳ vọng:**
  * Màn hình điện thoại tự động bật sáng (`turnScreenOn = true`).
  * Giao diện `AlarmActivity` hiển thị đè lên màn hình khóa (`showWhenLocked = true`).
  * Chuông và rung kích hoạt to rõ ràng.
  * Nút STOP cho phép mở khóa và dừng chuông.

---

## 4. Troubleshooting

| Triệu chứng | Nguyên nhân | Cách khắc phục |
| :--- | :--- | :--- |
| Bấm nút nhưng không thấy notification xuất hiện | Chưa cấp quyền `POST_NOTIFICATIONS` trên Android 13+ | Bấm nút "Grant Permission" trong Fake Camera hoặc vào App Settings để bật Thông báo. |
| Notification xuất hiện nhưng Camera Alarm không phản hồi | `NotificationListenerService` chưa được cấp quyền truy cập | Mở Camera Alarm -> Cấp quyền "Notification Access" cho Camera Alarm. |
| Camera Alarm báo `IGNORED_WRONG_PACKAGE` | Source App chưa chọn đúng `com.personal.fakecamera` | Vào Source Picker trong Camera Alarm, tìm và chọn `Front Door Camera`. |
| Camera Alarm báo `IGNORED_NO_RULE_MATCH` | Keywords trong quy tắc chưa khớp với chữ trong notification | Kiểm tra chữ hoa/thường hoặc kiểm tra rule có chứa `human detected` hay không. |
