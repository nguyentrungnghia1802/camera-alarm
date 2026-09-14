# Fake Camera Test Tool (`:fake-camera`)

Ứng dụng Android độc lập mô phỏng camera IP an ninh ngoài đời thực (**Front Door Camera**), dùng để kiểm thử end-to-end (E2E) pipeline của **Camera Alarm** thông qua notification hệ thống thực tế.

---

## 1. Thông tin ứng dụng

* **Module:** `:fake-camera`
* **Application ID / Package:** `com.personal.fakecamera`
* **App Name:** `Front Door Camera`
* **Icon:** Camera Alert Vector Drawable
* **Quyền sử dụng:** Chỉ cần `android.permission.POST_NOTIFICATIONS` (Android 13+). Không cần camera, internet, hay storage permission.

---

## 2. Mục tiêu kiến trúc

```
Fake Camera (Front Door Camera)
      │
      │  (Gửi Android Notification thật qua NotificationManager)
      ▼
Android System Notification Center
      │
      │  (Bắt qua NotificationListenerService)
      ▼
Camera Alarm (:app)
      ├─ CameraNotificationListener
      ├─ NotificationExtractor & Normalizer
      ├─ TriggerMatcher (source package & keywords)
      ├─ ActiveScheduleGate (Phase 3 schedule)
      ├─ DuplicateGuard
      └─ AlarmCoordinator -> AlarmRuntime (Sound / Vibration / Full-screen STOP)
```

Không gọi trực tiếp AlarmService, không dùng broadcast nội bộ hay shared database để đảm bảo kiểm thử chính xác hành vi production.

---

## 3. Cách build và cài đặt

### Build APK:
```bash
./gradlew :fake-camera:assembleDebug
```
File APK đầu ra tại: `fake-camera/build/outputs/apk/debug/fake-camera-debug.apk`.

### Cài đặt lên thiết bị / máy ảo:
```bash
./gradlew :fake-camera:installDebug
```

---

## 4. Các nút thông báo giả lập

| Nút bấm | Title | Content Text | Mục đích kiểm thử |
| :--- | :--- | :--- | :--- |
| **Human Detected** | `Human detected` | `Front Door Camera detected a person` | Kiểm tra kích hoạt cảnh báo chính (Test 1, Test 4) |
| **Motion Detected** | `Motion detected` | `Front Door Camera detected movement` | Kiểm tra keyword trigger thay thế |
| **Camera Offline** | `Camera offline` | `Front Door Camera connection lost` | Kiểm tra keyword filtering (không khớp rule, Test 3) |
| **Custom Alert** | *(Tự nhập)* | *(Tự nhập)* | Kiểm tra các mẫu thông báo camera thực tế khác |

### Tùy chọn ID thông báo:
* **Unique Notification ID (mặc định BẬT):** Mỗi lần bấm tạo 1 notification với ID riêng biệt, giúp kiểm tra cơ chế chống spam của Alarm Engine (`1 SCHEDULED`, `4 SUPPRESSED_PENDING`).
* **Fixed Notification ID (TẮT):** Sử dụng ID cố định (1000) để kiểm tra cơ chế chặn trùng lặp của `DuplicateGuard` (`IGNORED_DUPLICATE`).

---

## 5. Tài liệu chi tiết
Xem hướng dẫn chi tiết các kịch bản kiểm thử tại: [`docs/tools/fake-camera.md`](../docs/tools/fake-camera.md).
