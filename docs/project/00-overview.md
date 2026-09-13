# Camera Alarm — Project Overview

## 1. Mục tiêu

Camera Alarm là ứng dụng Android cá nhân có nhiệm vụ biến **notification ngắn từ ứng dụng camera** thành một **chuông báo liên tục, rõ ràng và khó bỏ lỡ**.

Luồng cốt lõi:

```text
Camera app
  -> Android notification
  -> NotificationListenerService
  -> normalize + filter + deduplicate
  -> Alarm state machine
  -> Exact alarm (mặc định +1 giây)
  -> BroadcastReceiver
  -> Foreground alarm service
  -> USAGE_ALARM audio loop + vibration loop
  -> người dùng bấm STOP
  -> cooldown
```

## 2. Bài toán cần giải quyết

Nhiều app camera chỉ phát một notification ngắn. Nếu người dùng không nhìn điện thoại đúng lúc, cảnh báo có thể bị bỏ lỡ. Ứng dụng này không thay camera và không phân tích video. Nó chỉ dùng notification của app camera như một **event source** rồi nâng mức cảnh báo thành alarm.

## 3. Phạm vi V1

V1 phải làm tốt các việc sau:

- Nhận notification từ app camera đã được người dùng chọn.
- Đọc title/text/bigText/textLines/subText của notification.
- Chỉ trigger khi notification thỏa rule đã cấu hình.
- Không trigger từ app khác.
- Không tạo nhiều alarm chồng nhau khi camera spam notification.
- Mặc định lên lịch alarm sau `1000 ms`.
- Hoạt động khi app Camera Alarm không mở trên màn hình.
- Phát âm thanh với `AudioAttributes.USAGE_ALARM` và lặp đến khi người dùng STOP.
- Rung lặp đến khi STOP nếu bật vibration.
- Có foreground notification trong lúc alarm đang chạy, với action STOP.
- Có màn hình Test Alarm để người dùng kiểm tra trước khi bật Monitoring.
- Có trạng thái readiness rõ ràng: Notification access, Exact alarm access, notification permission, source app, rule.
- Có lịch sử sự kiện đủ để debug vì sao notification bị trigger hoặc bị bỏ qua.

## 4. Ngoài phạm vi V1

Không xây trong V1:

- Backend/server riêng.
- Tích hợp cloud API của từng hãng camera.
- Đọc RTSP/video stream.
- Computer vision hoặc AI nhận diện người.
- Firebase Cloud Messaging do server của riêng app gửi.
- Đồng bộ nhiều thiết bị.
- Tài khoản/đăng nhập.
- Subscription/thanh toán.
- iOS.
- Cố tình bypass Do Not Disturb hoặc các chính sách an toàn của Android.
- Tự động thay đổi global alarm volume mà không có thao tác/cấu hình rõ ràng của người dùng.

## 5. Định hướng kỹ thuật

- Ngôn ngữ: Kotlin.
- UI: Jetpack Compose.
- Build: Gradle Kotlin DSL.
- Một app module: `:app`.
- Min SDK: 26.
- Compile SDK / Target SDK: 36 cho baseline V1.
- Async: Kotlin Coroutines + Flow.
- Settings: Preferences DataStore.
- Event history: Room.
- Dependency injection: manual constructor injection/simple service locator; không thêm Hilt ở V1 nếu chưa thật sự cần.
- Không cần INTERNET permission trong V1.
- Package mặc định cho project: `com.personal.cameraalarm`.

## 6. Nguyên tắc sản phẩm

1. **Reliability > UI đẹp.** Nếu phải chọn, alarm đúng và dừng đúng quan trọng hơn animation.
2. **Không alarm giả.** Khi thiếu quyền exact alarm hoặc notification listener, UI phải nói rõ app chưa sẵn sàng.
3. **Không spam.** Một thời điểm chỉ có tối đa một alarm pending hoặc ringing.
4. **STOP phải idempotent.** Bấm nhiều lần vẫn chỉ dẫn tới trạng thái đã dừng.
5. **Local-first.** Notification content và history chỉ lưu trên thiết bị.
6. **Cấu hình rõ ràng.** Không bật monitoring nếu chưa chọn source app và chưa có trigger rule hợp lệ.
7. **Không phụ thuộc hãng camera.** V1 hoạt động với bất kỳ app camera nào tạo Android notification mà listener đọc được.

## 7. Definition of Done cho V1

V1 được xem là hoàn tất khi test thực tế chứng minh:

1. Điện thoại đang khóa màn hình, app Camera Alarm không mở.
2. App camera gửi notification phù hợp.
3. Trong điều kiện bình thường, alarm được kích hoạt xấp xỉ delay cấu hình, mặc định 1 giây sau khi pipeline nhận trigger hợp lệ.
4. Alarm phát liên tục và rung liên tục cho tới khi STOP.
5. Notification lặp từ camera khi alarm đang pending/ringing không tạo alarm thứ hai.
6. STOP dừng audio, vibration, foreground service và chuyển sang cooldown.
7. Sau cooldown, trigger mới có thể tạo alarm mới.
8. Khi thiếu quyền bắt buộc, app hiển thị đúng trạng thái và không tuyên bố Monitoring Ready.
9. Unit tests cho matcher/state machine/dedup/cooldown đều pass.
10. `./gradlew test lint assembleDebug` pass.

## 8. Tài liệu liên quan

Đọc theo thứ tự:

1. `01-product-requirements.md`
2. `02-architecture.md`
3. `03-domain-state-machine.md`
4. `04-notification-trigger-pipeline.md`
5. `05-alarm-scheduling-runtime.md`
6. `06-platform-permissions-compatibility.md`
7. `07-data-ui-observability.md`
8. `08-testing-quality.md`
9. `09-delivery-scope.md`
