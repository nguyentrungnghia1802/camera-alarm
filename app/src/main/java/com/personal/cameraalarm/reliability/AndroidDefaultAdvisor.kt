package com.personal.cameraalarm.reliability

import android.content.Context
import android.os.Build
import com.personal.cameraalarm.permission.ReadinessRepository

open class AndroidDefaultAdvisor(
    protected val readinessRepo: ReadinessRepository? = null,
    val manufacturer: String = Build.MANUFACTURER,
    val brand: String = Build.BRAND
) : DeviceReliabilityAdvisor {

    override val isApplicable: Boolean get() = true
    override val deviceFamilyName: String get() = "Google Pixel / AOSP / Chuẩn"
    override val oemKey: String get() = "generic"

    override fun getStandardItems(context: Context): List<ReliabilityItem> {
        val readiness = readinessRepo?.snapshot()
        val items = mutableListOf<ReliabilityItem>()

        // 1. Notification Access
        val notifGranted = readiness?.notificationAccessGranted ?: false
        items.add(
            ReliabilityItem(
                id = "notification_access",
                title = "Quyền truy cập thông báo",
                description = "Bắt buộc để ứng dụng lắng nghe và phát hiện thông báo từ camera.",
                status = if (notifGranted) ReliabilityStatus.READY else ReliabilityStatus.MISSING,
                actionLabel = if (!notifGranted) "Cấp quyền" else null,
                actionIntent = readinessRepo?.notificationAccessSettingsIntent()
            )
        )

        // 2. Exact Alarm
        val exactGranted = readiness?.exactAlarmGranted ?: true
        items.add(
            ReliabilityItem(
                id = "exact_alarm",
                title = "Quyền báo thức chính xác",
                description = "Bắt buộc để kích hoạt chuông báo khẩn cấp không bị hệ thống trì hoãn.",
                status = if (exactGranted) ReliabilityStatus.READY else ReliabilityStatus.MISSING,
                actionLabel = if (!exactGranted) "Cấp quyền" else null,
                actionIntent = readinessRepo?.exactAlarmSettingsIntent()
            )
        )

        // 3. App Notifications
        val postGranted = readiness?.postNotificationsGranted ?: true
        items.add(
            ReliabilityItem(
                id = "post_notifications",
                title = "Thông báo ứng dụng",
                description = "Cho phép hiển thị thông báo chạy nền và nút DỪNG chuông khi có sự cố.",
                status = if (postGranted) ReliabilityStatus.READY else ReliabilityStatus.MISSING,
                actionLabel = if (!postGranted) "Cài đặt" else null,
                actionIntent = readinessRepo?.appNotificationSettingsIntent()
            )
        )

        // 4. Full-Screen Alarm (Lock Screen)
        val canFullScreen = readinessRepo?.canUseFullScreenIntent() ?: true
        items.add(
            ReliabilityItem(
                id = "full_screen",
                title = "Bật sáng màn hình khóa (Full-Screen Alarm)",
                description = "Cho phép hiển thị màn hình báo động đè lên màn hình khóa để bạn xử lý ngay.",
                status = if (canFullScreen) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                actionLabel = if (!canFullScreen && readinessRepo?.fullScreenIntentSettingsIntent() != null) "Cài đặt" else null,
                actionIntent = readinessRepo?.fullScreenIntentSettingsIntent()
            )
        )

        // 5. Battery Optimization
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "battery_optimization",
                title = "Không tối ưu hóa pin (Unrestricted)",
                description = "Ngăn hệ thống tạm dừng ứng dụng hoặc đóng băng bộ đếm khi màn hình tắt lâu.",
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                actionLabel = if (!isIgnoringBattery) "Bỏ tối ưu pin" else null,
                actionIntent = if (!isIgnoringBattery) DeviceReliabilityAdvisor.getBatteryOptimizationIntent(context) else null
            )
        )

        return items
    }

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        return emptyList()
    }
}
