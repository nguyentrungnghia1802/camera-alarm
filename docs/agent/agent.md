# AI Agent Instructions

## 1. Mục tiêu

Bạn đang phát triển ứng dụng Android cá nhân **Camera Alarm**. Ưu tiên cao nhất là **correctness và reliability của notification -> exact alarm -> continuous alarm**, không phải UI đẹp.

## 2. Source of truth

Trước khi làm task:

1. Đọc file này.
2. Đọc `docs\agent\tasks\phase3-task.md`.
3. Chỉ đọc các file trong `docs/project/` liên quan trực tiếp tới task hiện tại.
4. Đọc source/test liên quan trực tiếp; không quét toàn repository nếu không cần.

Nếu code hiện tại mâu thuẫn với `docs/project/`, ưu tiên docs trừ khi Android SDK thực tế chứng minh docs sai. Nếu phải thay đổi quyết định kiến trúc, cập nhật docs trong cùng task.

## 3. Quy trình đơn giản

Đây là dự án cá nhân, một người làm.

- Chỉ dùng branch `main`.
- Không tạo feature branch/worktree nếu người dùng không yêu cầu.
- Làm từng task theo thứ tự trong `task.md`.
- Không làm trước task Phase 2 khi Phase 1 chưa pass acceptance gates.
- Sau khi hoàn thành task, chạy test phù hợp và tick checklist.
- Commit trực tiếp vào `main` bằng message ngắn, rõ.
- Chỉ push khi code đang ở trạng thái build/test pass phù hợp với milestone.
- Không force-push, reset destructive hoặc xóa thay đổi không phải của task.

## 4. Nguyên tắc coding

- Kotlin + Jetpack Compose.
- Một `:app` module trong V1.
- Giữ file/component nhỏ, một trách nhiệm chính.
- Core logic như matcher/state machine phải test được mà không cần Android framework.
- Android API được bọc qua adapter/interface khi điều đó giúp test correctness.
- Không thêm Hilt, backend, networking hoặc framework lớn nếu docs không yêu cầu.
- Không dùng polling cho notification.
- Không dùng `Thread.sleep()` để tạo delay.
- Không dùng WorkManager cho delay 1 giây.
- Không phát alarm trực tiếp trong `NotificationListenerService`.
- Không bypass exact-alarm permission bằng fallback inexact rồi báo thành công.
- Không để nhiều alarm active cùng lúc.
- STOP phải idempotent.
- Không log full camera notification text trong release build.

## 5. Android/platform correctness

Các khu vực sau phải làm cẩn thận và kiểm tra docs Android chính thức khi cần:

- NotificationListenerService lifecycle/access.
- Exact alarm permission và `canScheduleExactAlarms()`.
- Background foreground-service restrictions.
- Foreground-service type cho target SDK hiện tại.
- POST_NOTIFICATIONS từ API 33+.
- Full-screen intent từ API 34+.
- MediaPlayer lifecycle, `USAGE_ALARM`, wake mode.
- Vibration lifecycle.

Không suy đoán platform behavior nếu có thể kiểm chứng bằng Android official docs hoặc test device.

## 6. Testing

Phase 1 yêu cầu test nghiêm ngặt hơn UI.

Tối thiểu trước khi đánh dấu core task done:

```bash
./gradlew test
```

Trước milestone/release:

```bash
./gradlew test lint assembleDebug
```

Nếu task liên quan Android behavior mà unit test không đủ, thực hiện instrumented/manual test theo `docs/project/08-testing-quality.md`.

Không đánh dấu checklist done khi test liên quan đang fail.

## 7. Scope discipline

- Làm đúng task hiện tại.
- Không refactor unrelated code.
- Không thêm feature "tiện thể".
- Nếu phát hiện bug ngoài scope nhưng ảnh hưởng correctness task hiện tại, sửa tối thiểu và ghi rõ.
- Future extensions trong `09-delivery-scope.md` không được tự triển khai trong V1.

## 8. Khi gặp ambiguity

Ưu tiên theo thứ tự:

1. `docs/project/`.
2. `docs/agent/task.md` acceptance criteria.
3. Android official behavior.
4. Giải pháp đơn giản nhất giữ đúng invariant.

Chỉ hỏi người dùng khi quyết định thực sự thay đổi product behavior lớn. Với chi tiết implementation nhỏ, tự chọn phương án đơn giản, testable và ghi rõ trong commit nếu cần.
