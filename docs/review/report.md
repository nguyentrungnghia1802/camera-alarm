# Project Review Report

**Ngày review:** 2026-09-21  
**Phạm vi:** toàn bộ tài liệu trong `docs/agent`, `docs/project`; source và resource của `app`, `fake-camera`; unit test, instrumentation test và bằng chứng verification hiện có.  
**Commit được review:** `9fbd5fe` (`main`, trùng `origin/main` tại thời điểm review).  
**Nguyên tắc:** chỉ phân tích; không sửa source/test, không commit.

## 1. Tổng quan

### Trạng thái hiện tại

Camera Alarm đã có cấu trúc V1 khá đầy đủ: Notification Listener, pipeline lọc/match, state machine serialize bằng token, exact alarm, foreground service, full-screen UI, audio/vibration, STOP, schedule, cooldown, rules, Room history, DataStore settings/runtime state, boot reconciliation và màn hình hướng dẫn Samsung/Xiaomi.

Các điểm tốt chính:

- Core domain được tách tương đối rõ khỏi Android adapter; matcher, reducer, duplicate guard và coordinator có thể unit test.
- `AlarmToken` được truyền xuyên suốt schedule/receiver/service/STOP; service từ chối START/STOP sai token, giảm race giữa nhiều alarm.
- Manifest không yêu cầu `INTERNET`, `CAMERA`, `RECORD_AUDIO` hay quyền package rộng; listener/service/alarm receivers nội bộ không exported.
- Exact-alarm permission, notification permission, full-screen-intent permission, battery state và OEM guidance đều có đường kiểm tra/hiển thị.
- Unit tests, lint, debug build và release build hiện đều chạy được.

Tuy nhiên, release gate chưa đạt. Bộ instrumentation hiện không biên dịch vì test gọi DAO API đã bị xoá; do đó không thể chạy lại matrix thiết bị trên HEAD hiện tại. Ngoài ra còn các rủi ro đáng kể ở privacy, đường khởi động foreground service, boot listener recovery và dữ liệu history.

### Mức độ hoàn thiện

Đánh giá tổng thể: **feature-complete ở mức V1, nhưng verification và hardening chưa release-complete**.

- **Architecture/core logic:** tốt, có nền tảng mở rộng.
- **Build/unit quality:** tốt; 108 logical tests được chạy trên debug/release/module tương ứng, tổng 216 lượt, không failure/error/skip.
- **Runtime/device quality:** chưa đủ bằng chứng cho mã hiện tại.
- **Privacy/observability:** cần chỉnh trước khi phân phối thực tế.
- **UX/localization/OEM:** dùng được nhưng chưa hoàn thiện và chưa được xác nhận trên thiết bị thật mục tiêu.

### Có phù hợp dùng thực tế chưa

**Chưa nên phát hành rộng hoặc coi là bản production-ready.** Có thể tiếp tục dùng nội bộ/development với người dùng hiểu rõ cách cấp quyền và giới hạn thiết bị. Trước release thực tế cần đóng các issue High, khôi phục instrumentation gate và chạy lại matrix thiết bị trên đúng commit release.

### Bằng chứng build/verification tại thời điểm review

| Gate | Kết quả | Ghi chú |
| --- | --- | --- |
| Git state trước review | PASS | `main` sạch, trùng `origin/main` |
| `test lint assembleDebug` | PASS | 216 lượt test, 0 failure/error/skip; lint 0 error, 93 warning |
| `:app:assembleRelease` | PASS | Sinh `app-release-unsigned.apk`; chưa phải artifact đã ký để phân phối |
| `:app:assembleAndroidTest` | **FAIL** | `DatabaseInstrumentedTest.kt:80`: unresolved reference `pruneOverRetention` |
| Device/emulator hiện tại | NOT RUN | `adb devices -l` không có thiết bị |
| Bằng chứng cũ | PARTIAL | API 31/36 từng pass sau STOP fix; API 33/34 chưa rerun trên final code của vòng đó. HEAD hiện tại còn có thay đổi mới hơn nên không thay thế được current verification |

### Kết quả review theo phạm vi

#### Architecture

- Cấu trúc `app`/`fake-camera`, manual `AppContainer`, repository, ViewModel và core alarm/pipeline nhìn chung dễ theo dõi.
- Core pure Kotlin là điểm mạnh; Android side-effect tập trung ở scheduler, receiver, service, listener và reliability advisor.
- `AppContainer` đang gánh nhiều wiring và một số policy/history side effect; các ViewModel cũng lặp logic Test Alarm. Khi mở rộng, đây sẽ là điểm khó kiểm soát.
- Dependency không có lỗi build hiện tại nhưng lint báo nhiều version mới hơn. Không nên nâng đồng loạt ngay trước release; nên có đợt upgrade riêng kèm regression matrix.
- Room đang ở schema version 1, `exportSchema = false`, chưa có migration strategy. Đây là technical debt rõ ràng cho các bản sau V1.

#### Alarm Reliability

- Notification listener xử lý bất đồng bộ qua app scope; pipeline dùng `Mutex`, kiểm tra monitoring/source/schedule/rule/duplicate và chuyển đến coordinator.
- Exact alarm kiểm tra capability và dùng tokenized immutable `PendingIntent`; receiver kiểm tra persisted pending token trước khi start service.
- FGS `systemExempted`, notification channel, full-screen intent, direct activity fallback, looped alarm audio, vibration và STOP đều đã hiện diện.
- Persisted `Pending` có thể sống qua process recreation; orphan `Ringing` được làm sạch bằng process nonce. Boot reconciliation chủ động reset state và request listener rebind.
- Reliability chưa thể xác nhận trên current HEAD vì instrumentation không compile và không có device. Screen lock, Doze, background start, process death, reboot, permission revoke, full-screen denied và STOP cần được chạy lại cùng một release candidate.

#### Android Compatibility

- Target/compile SDK 36, min SDK 26; đã khai báo exact alarm, FGS type, full-screen, notification, vibration, wake lock và boot permissions phù hợp với kiến trúc hiện tại.
- AOSP readiness được phân loại khá tốt giữa blocking và degraded.
- Samsung/Xiaomi advisor/deep link có defensive fallback, nhưng tài liệu dự án tự ghi nhận chưa có physical Xiaomi verification; vòng review này cũng không có thiết bị thật.
- Luồng xin bỏ battery optimization gọi direct allowlist intent nhưng manifest không khai báo quyền liên quan; đây vừa là rủi ro tương thích vừa cần xem xét chính sách phân phối.

#### UX/UI

- User flow chính đầy đủ: onboarding/readiness, source, rules, schedule, settings, diagnostics, history và test alarm.
- Có resource English/Vietnamese và locale switching, nhưng vẫn còn chuỗi hard-code lẫn hai ngôn ngữ trong reliability advisor, validation và snackbar.
- Không có Compose UI/accessibility test cho font scale lớn, TalkBack, text truncation hoặc layout màn hình nhỏ.
- Cấu hình có nhiều lớp quyền hệ thống/OEM; checklist giúp ích nhưng vẫn cần walkthrough trên thiết bị thật và ngôn ngữ Việt/Anh.

#### Data & Storage

- Room dùng cho rules/history, DataStore dùng cho settings và runtime state; lựa chọn phù hợp với loại dữ liệu.
- History có pagination, filter, clear và retention, nhưng implementation hiện là 100 event/3 ngày/tối đa 10 suppressed, lệch tài liệu V1 yêu cầu tối đa 500.
- Pipeline ghi history cho mọi decision, bao gồm wrong-package, cùng title/text preview. Điều này thu thập notification ngoài nguồn được chọn và tạo I/O không cần thiết.
- History chưa phản ánh đầy đủ lifecycle: không thấy producer ghi `ALARM_FIRED`/`ALARM_RUNTIME_ERROR`; boot event cũng bị UI phân loại như error chung.
- Chưa có schema export/migration; backup/data-transfer rules chưa được định nghĩa.

#### Security, Performance, Testing

- Quyền app tương đối tối thiểu và dữ liệu xử lý local; đây là điểm tốt.
- Privacy vẫn có rủi ro vì lưu preview notification không thuộc selected source và mặc định Android backup đang không được override/exclude.
- `CameraAlarmService` dùng `runBlocking` để đọc DataStore trên service main thread, bao gồm trước lúc hoàn tất `startForeground()`. Cold-start/I/O chậm có thể làm tăng độ trễ hoặc chạm timeout FGS.
- Không thấy leak hiển nhiên: coroutine scope/service runtime được sở hữu tập trung, MediaPlayer/vibration có STOP lifecycle. Tuy vậy chưa có soak/leak test hoặc benchmark dài hạn.
- Test core khá rộng, nhưng một số test ViewModel/boot/integration chỉ kiểm tra logic mô phỏng thay vì gọi production implementation. Thiếu UI/accessibility, migration/backup/privacy, slow-I/O FGS và OEM physical tests.

## 2. Current Status

| Area                 | Status | Notes |
| -------------------- | ------ | ----- |
| Alarm Core           | ĐẠT CÓ ĐIỀU KIỆN | Token/state machine/matcher/coordinator tốt; còn main-thread I/O và chưa có current device run |
| Background           | CHƯA XÁC NHẬN | Có FGS, wake lock, persisted state, boot reconciliation; reboot/Doze/process-death trên HEAD chưa được chạy lại |
| Full-screen Alarm    | MỘT PHẦN | Có permission check, notification full-screen intent và fallback; chưa current-test screen lock/unlocked/API 34+ |
| Schedule             | ĐẠT Ở UNIT | Overnight/day mask/timezone logic có test; cần xác nhận lại end-to-end trên device |
| Cooldown             | ĐẠT Ở UNIT | Persisted state và suppression có test; history detail còn hạn chế |
| Rules                | ĐẠT CÓ LƯU Ý | CRUD/matcher/priority có test; edit reset `createdAt`, có thể đổi tie-break order |
| History              | CẦN CẢI THIỆN | Lệch retention spec, lưu wrong-package preview, lifecycle/error event chưa đầy đủ |
| Localization         | MỘT PHẦN | Có EN/VI resources và locale switch; còn nhiều hard-coded mixed-language strings |
| Device Compatibility | CHƯA XÁC NHẬN | AOSP adapters có; Samsung/Xiaomi chủ yếu là guidance/unit tests, chưa physical validation hiện tại |
| Testing              | **KHÔNG ĐẠT RELEASE GATE** | Unit/lint/build pass nhưng AndroidTest source không compile; không có thiết bị chạy current HEAD |

## 3. Issues Found

### ISSUE-01 — Instrumentation release gate bị hỏng

- **Priority:** High
- **Problem:** `app/src/androidTest/.../DatabaseInstrumentedTest.kt:80` gọi `pruneOverRetention()`, trong khi production DAO hiện chỉ có `pruneRetention(...)` tại `AlertEventDao.kt:56`. Test vẫn assert retention 500 nhưng production đã chuyển thành 100/3 ngày/10 suppressed.
- **Impact:** `:app:assembleAndroidTest` fail ở compile; toàn bộ connected instrumentation và device matrix không thể chạy trên current HEAD. Các PASS lịch sử không chứng minh release candidate hiện tại.
- **Recommended fix:** Đồng bộ instrumentation test với retention contract đã được quyết định; bắt buộc `assembleAndroidTest` và connected tests pass trên đúng commit release.

### ISSUE-02 — Lưu nội dung notification của package không được chọn

- **Priority:** High
- **Problem:** `TriggerPipeline.process()` luôn gọi `history.record(...)` sau mọi decision. `AppContainer` sau đó lưu `sourcePackage`, `title` và `textPreview`, kể cả `IGNORED_WRONG_PACKAGE`. Điều này trái với thiết kế trong `docs/project/04-notification-trigger-pipeline.md` cho phép bỏ qua DB ở wrong-package để tránh noise.
- **Impact:** Room có thể chứa preview notification từ email/chat/app khác mà người dùng không chọn; tăng privacy exposure, dung lượng và battery/I/O. Rủi ro tăng thêm vì app chưa định nghĩa backup exclusion.
- **Recommended fix:** Không persist nội dung wrong-package; nếu cần chẩn đoán chỉ đếm hoặc debug-log metadata tối thiểu, không lưu title/text. Thêm regression test xác nhận notification ngoài nguồn không được ghi DB.

### ISSUE-03 — Blocking DataStore trên critical FGS start path

- **Priority:** High
- **Problem:** `CameraAlarmService` chạy trên main thread và dùng `runBlocking` tại các đường đọc sound/full-screen (`CameraAlarmService.kt:85`, `:227`, `:282`). Lần đọc ở `createFullScreenPendingIntent()` xảy ra trước `startForeground()` (`:219`).
- **Impact:** Cold process hoặc DataStore I/O chậm có thể trì hoãn foreground promotion và thời điểm phát âm thanh; trên điều kiện xấu có thể gây timeout/crash FGS hoặc alarm đến muộn.
- **Recommended fix:** Đưa cấu hình cần thiết vào cache/snapshot đã load, hoặc truyền immutable extras khi schedule; foreground promotion phải dùng dữ liệu sẵn có và hoàn tất ngay. Thêm test với settings source bị delay.

### ISSUE-04 — Boot listener recovery dùng component toggle chưa được chứng minh an toàn

- **Priority:** High
- **Problem:** Sau `NotificationListenerService.requestRebind()`, code lập tức kiểm tra trạng thái rồi disable/enable listener component nếu chưa CONNECTED. Callback kết nối là bất đồng bộ nên nhánh toggle có thể chạy gần như mọi boot. Test hiện có không gọi production `DefaultBootReconciler` đầy đủ.
- **Impact:** Có thể race với NotificationManager/OEM, làm mất hoặc trì hoãn listener access sau reboot; đây là dependency đầu vào quan trọng nhất của app. Rủi ro đặc biệt cao trên thiết bị OEM mà workaround nhắm tới nhưng chưa được test thật.
- **Recommended fix:** Dùng request-rebind có timeout/retry rõ ràng; chỉ dùng OEM workaround đã được chứng minh và không thay đổi user-granted component state ngoài ý muốn. Viết test production reconciler và physical reboot validation.

### ISSUE-05 — Không có current device matrix cho release candidate

- **Priority:** High
- **Problem:** Không có device/emulator kết nối trong vòng review. Bằng chứng `v1-verification.md` đã ghi API 33/34 chưa rerun trên final code lúc đó; current HEAD còn mới hơn. Xiaomi physical validation cũng được tài liệu đánh dấu NOT VERIFIED.
- **Impact:** Chưa xác nhận các đường quan trọng chỉ biểu hiện trên Android runtime: exact permission, notification denial, FGS API 34+, full-screen locked/unlocked, Doze/background restriction, process death, reboot và STOP notification action.
- **Recommended fix:** Sau khi ISSUE-01 được đóng, chạy release matrix API 31/33/34/36 trên cùng commit; bổ sung ít nhất Samsung mục tiêu và Xiaomi/Redmi/POCO nếu app tuyên bố hỗ trợ OEM đó.

### ISSUE-06 — History contract và observability không đồng nhất

- **Priority:** Medium
- **Problem:** Tài liệu yêu cầu retention tối đa 500, production giữ 100 event/3 ngày/10 suppressed. DAO/UI có filter cho `ALARM_FIRED` và `ALARM_RUNTIME_ERROR` nhưng không thấy producer ghi hai event này; `ruleId`/`normalizedHash` trong trigger history luôn null. `BOOT_RECONCILED` không có mapping riêng và hiển thị như error mặc định.
- **Impact:** Mất bằng chứng chẩn đoán sớm, history không thể hiện chính xác alarm đã thật sự fire hay runtime thất bại, và UI có thể gây hiểu nhầm.
- **Recommended fix:** Chốt một contract history duy nhất rồi đồng bộ docs/schema/test/UI; ghi các lifecycle event ở receiver/service, liên kết rule/token và phân loại boot event rõ ràng.

### ISSUE-07 — Test Alarm có thể xung đột với production alarm đang chạy

- **Priority:** Medium
- **Problem:** Diagnostics có thể set `testAlarmToken` và mở Test Alarm khi production service đã có active token. Service từ chối START token mới nhưng test token vẫn tồn tại; `MainViewModel.stopAlarm()` ưu tiên test token trước production ringing token.
- **Impact:** Nút STOP trên main có thể gửi token test bị service từ chối, trong khi production audio vẫn tiếp tục; UI/test activity cũng có thể gây nhầm trạng thái.
- **Recommended fix:** Vô hiệu Test Alarm khi production state là Pending/Ringing, hoặc chỉ publish test token sau khi service chấp nhận; STOP luôn ưu tiên active production token. Thêm regression test cho hai luồng giao nhau.

### ISSUE-08 — Backup mặc định có thể mang theo dữ liệu notification

- **Priority:** Medium
- **Problem:** `<application>` không đặt `android:allowBackup`, `android:dataExtractionRules` hay `android:fullBackupContent`. Android mặc định bật Auto Backup và bao gồm phần lớn app data; Room history chứa notification preview và DataStore chứa runtime/settings.
- **Impact:** Dữ liệu nhạy cảm có thể được cloud backup hoặc device-to-device transfer ngoài kỳ vọng “local-only”; runtime Pending/Ringing cũ có thể được restore không phù hợp với alarm thực tế.
- **Recommended fix:** Định nghĩa backup policy rõ ràng. Tối thiểu exclude Room history và runtime alarm state; cân nhắc chỉ backup các preference không nhạy cảm. Kiểm tra cả Android <=11 và Android 12+ rules.

### ISSUE-09 — Battery-optimization request chưa hoàn chỉnh

- **Priority:** Medium
- **Problem:** Reliability action gọi `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, nhưng manifest không khai báo `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Lint cũng cảnh báo policy cho direct allowlist request.
- **Impact:** Action có thể fail/không hiện trên một số build/OEM và bị catch im lặng; cấu hình reliability trở nên khó hiểu. Nếu phát hành qua store, cách xin exemption còn có yếu tố policy.
- **Recommended fix:** Quyết định distribution policy trước. Nếu use case đủ điều kiện thì khai báo/xử lý quyền đúng; nếu không, mở trang battery optimization chung và hướng dẫn người dùng thủ công, với feedback khi intent không khả dụng.

### ISSUE-10 — Localization chưa hoàn chỉnh

- **Priority:** Medium
- **Problem:** Reliability advisor có nhiều title/description/instruction hard-code tiếng Việt; một số validation, snackbar và diagnostics hard-code tiếng Anh. Không phải toàn bộ text đi qua resources.
- **Impact:** English UI xuất hiện tiếng Việt và ngược lại; khó bảo trì, khó test locale, giảm chất lượng onboarding quyền vốn đã phức tạp.
- **Recommended fix:** Chuyển toàn bộ user-facing text sang resource EN/VI; thêm locale smoke test và review hai ngôn ngữ trên các màn hình chính.

### ISSUE-11 — Rule edit có thể thay đổi tie-break order

- **Priority:** Medium
- **Problem:** Khi save rule, `createdAtEpochMs` được gán lại bằng thời gian hiện tại thay vì giữ giá trị ban đầu. Rule ordering dùng priority và thời gian tạo để deterministic tie-break.
- **Impact:** Chỉ sửa nội dung một rule có thể âm thầm đổi thứ tự giữa các rule cùng priority, dẫn đến rule match khác.
- **Recommended fix:** Giữ `createdAtEpochMs` khi edit; nếu cần thêm `updatedAtEpochMs` riêng. Thêm test cho equal-priority ordering sau edit.

### ISSUE-12 — Exported boot receiver nhận vendor action không được bảo vệ rõ ràng

- **Priority:** Medium
- **Problem:** `BootReceiver` exported và nhận hai QUICKBOOT action dạng string ngoài các system action chuẩn; receiver không kiểm tra sender/permission.
- **Impact:** Trên platform cho phép app khác gửi explicit/custom broadcast, một broadcast giả có thể kích hoạt reconciliation, xoá runtime state/test token và nudge listener component.
- **Recommended fix:** Tách system-protected actions khỏi vendor compatibility path; giới hạn exposure/permission nếu có thể và xác minh behavior trên OEM đích. Thêm test action/source policy.

### ISSUE-13 — Full-screen launch có nhiều đường trùng nhau và API deprecated

- **Priority:** Low
- **Problem:** Receiver/service vừa dùng full-screen notification/PendingIntent vừa thử direct `startActivity`; wake path còn dùng API wake-lock cũ bị lint/deprecation warning.
- **Impact:** Có thể tạo launch attempt dư thừa, behavior khác nhau theo background-activity restrictions và tăng maintenance cost ở SDK sau.
- **Recommended fix:** Sau device matrix, chọn một primary path có fallback đo lường được; migrate khỏi API deprecated theo hướng dẫn Android mà không làm suy yếu locked-screen behavior.

### ISSUE-14 — Test coverage có “logic replica” thay vì production-path coverage

- **Priority:** Low
- **Problem:** Một số test boot/ViewModel/source/integration dựng lại nhánh logic hoặc chỉ assert enum/list thay vì instantiate production class. Không có Compose UI/font-scale/accessibility test, migration/backup test, long-run resource test.
- **Impact:** Test count cao nhưng có thể không bắt wiring regression như lỗi DAO instrumentation hiện tại hoặc component-toggle behavior.
- **Recommended fix:** Ưu tiên contract/integration test gọi production entry point; thêm test ở boundary Android quan trọng thay vì tăng test logic mô phỏng.

### ISSUE-15 — Lint/dependency/release packaging còn nợ

- **Priority:** Low
- **Problem:** Lint pass với 93 warning; gồm deprecated API, dependency updates, locale/plural/icon-density và battery-policy warnings. Release build hiện tạo APK unsigned; chưa thấy signing/distribution/reproducible release gate trong repo.
- **Impact:** Không chặn compile nhưng làm tăng drift SDK, giảm polish và chưa tạo artifact có thể phát hành chính thức.
- **Recommended fix:** Triage lint theo nhóm; xử lý warning liên quan behavior/policy trước. Thiết lập signing ngoài repo, release checklist, checksum/versioning và artifact smoke test.

## 4. Technical Debt

- `AppContainer` đang là composition root đồng thời chứa policy/history callback; nên tách use-case/factory để wiring dễ test hơn.
- Logic Start/Stop Test Alarm lặp ở nhiều ViewModel; nên có một controller dùng chung với lifecycle/acknowledgement rõ ràng.
- Room schema không export và chưa có migration framework; cần thiết lập trước schema version 2.
- DataStore/runtime state và backup/restore chưa có contract rõ cho boot, app update và device transfer.
- History decision hiện dùng raw string thay vì enum/versioned event schema; khó migrate/filter an toàn.
- UI strings chưa tập trung hoàn toàn; không có automated accessibility/font-scale coverage.
- OEM reliability advisor phụ thuộc deep link best-effort; cần telemetry cục bộ/diagnostics rõ hơn để biết action nào mở được mà không thu thập dữ liệu người dùng.
- Alarm launch đang phối hợp nhiều side effect trong service main thread; cần tách “promote immediately” khỏi settings I/O và UI launch.
- Deprecated wake/full-screen API cần kế hoạch thay thế theo target SDK tương lai.
- Dependency versions nên được nâng theo batch có matrix test riêng, không trộn vào hotfix release.

## 5. Recommended Roadmap

### Phase 1: Khôi phục release gate và đóng rủi ro High

- **Mục tiêu:** Có một release candidate build/test được, không lưu dữ liệu ngoài phạm vi và không block FGS startup.
- **Task:**
  - Chốt retention contract và sửa instrumentation test/DAO expectation tương ứng.
  - Loại bỏ persistence của wrong-package notification content.
  - Loại bỏ `runBlocking`/DataStore I/O khỏi service main-thread foreground path.
  - Hardening boot listener rebind, bỏ hoặc giới hạn component toggle chưa được chứng minh.
  - Chặn Test Alarm giao nhau với production alarm.
  - Thêm regression tests cho từng lỗi trên; chạy `test`, `lint`, `assembleDebug`, `assembleRelease`, `assembleAndroidTest`.
- **Priority:** Critical path trước release.

### Phase 2: Runtime/device certification

- **Mục tiêu:** Chứng minh alarm đáng tin cậy trên đúng commit release và thiết bị mục tiêu.
- **Task:**
  - Chạy API 31/33/34/36: exact permission granted/denied/revoked, POST_NOTIFICATIONS denied, full-screen allowed/denied.
  - Test foreground/background, screen locked/unlocked, Doze, process kill/recreation, repeated notifications, STOP từ activity/notification/main screen.
  - Test cold boot/reboot/app update và listener reconnect.
  - Test thiết bị Samsung mục tiêu; test Xiaomi/Redmi/POCO thật nếu công bố hỗ trợ Xiaomi/HyperOS.
  - Lưu command, device fingerprint, OS/API, kết quả và commit SHA trong verification doc.
- **Priority:** Bắt buộc trước public/production release.

### Phase 3: Data, UX và maintainability hardening

- **Mục tiêu:** Hoàn thiện privacy, observability, localization và khả năng nâng cấp sau V1.
- **Task:**
  - Định nghĩa backup/data-extraction rules; exclude history/runtime state nhạy cảm.
  - Hoàn thiện history lifecycle (`SCHEDULED`, fired, stopped, runtime error, boot), retention và UI mapping.
  - Chuyển toàn bộ user-facing text sang EN/VI resources; test font scale lớn, TalkBack, màn hình nhỏ.
  - Export Room schema, thêm migration tests và release upgrade test.
  - Triage 93 lint warnings, cập nhật dependency theo batch, thay API deprecated.
  - Thiết lập signed release pipeline/checklist và smoke test artifact đã ký.
- **Priority:** High cho privacy/migration; Medium cho polish/dependency cleanup.

## 6. Release Recommendation

# NEEDS IMPROVEMENT

Lý do quyết định:

1. Instrumentation source hiện không compile, nên release candidate không vượt qua device-test gate.
2. Không có current device matrix cho HEAD; API 33/34 và physical Samsung/Xiaomi chưa được xác nhận đầy đủ trên mã hiện tại.
3. Pipeline đang lưu nội dung notification từ package ngoài nguồn được chọn, tạo rủi ro privacy không cần thiết.
4. FGS critical path có blocking DataStore I/O trước `startForeground()`, ảnh hưởng trực tiếp đến độ tin cậy alarm khi cold start.
5. Boot listener recovery và history/backup contract còn các khoảng trống cần hardening.

Core architecture và phần lớn chức năng đã ở trạng thái tốt, nên đây là **release hold có phạm vi rõ**, không phải yêu cầu viết lại dự án. Sau Phase 1 và Phase 2, nếu mọi gate pass trên cùng release commit và không phát hiện regression High/Critical, dự án có thể được review lại để chuyển sang **READY FOR RELEASE**.

### Tài liệu Android đối chiếu

- Foreground service types và `systemExempted`: <https://developer.android.com/develop/background-work/services/fgs/service-types>
- Exact alarms: <https://developer.android.com/develop/background-work/services/alarms>
- Android 14 full-screen intent behavior: <https://developer.android.com/about/versions/14/behavior-changes-14>
- NotificationListenerService/rebind contract: <https://developer.android.com/reference/android/service/notification/NotificationListenerService>
- Doze/App Standby: <https://developer.android.com/training/monitoring-device-state/doze-standby>
- Auto Backup và data extraction rules: <https://developer.android.com/identity/data/autobackup>
- Manifest permission reference: <https://developer.android.com/reference/android/Manifest.permission>
