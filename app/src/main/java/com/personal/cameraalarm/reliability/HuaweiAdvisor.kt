package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository

class HuaweiAdvisor(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : AndroidDefaultAdvisor(readinessRepo, manufacturer, brand) {

    private val isHuaweiDevice = isHuaweiFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isHuaweiDevice
    override val deviceFamilyName: String get() = if (isHuaweiDevice) "Huawei / Honor (EMUI / HarmonyOS)" else "Huawei (Không khớp)"
    override val oemKey: String get() = "huawei"

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        val items = mutableListOf<ReliabilityItem>()

        // 1. App Launch Management (Huawei)
        items.add(
            ReliabilityItem(
                id = "huawei_app_launch",
                title = "Quản lý khởi chạy (App Launch - Quản lý thủ công)",
                description = "Bắt buộc trên EMUI/HarmonyOS để ứng dụng không bị hệ thống tự động tắt khi tắt màn hình.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Khởi chạy ứng dụng",
                actionIntent = getHuaweiAppLaunchIntent(context),
                userInstruction = "1. Mở Cài đặt > Pin > Khởi chạy ứng dụng (App launch).\n2. Tìm 'Camera Alarm' và TẮT 'Quản lý tự động'.\n3. Trong bảng hiện ra, BẬT cả 3 mục: 'Tự động khởi chạy', 'Khởi chạy thứ cấp', 'Chạy dưới nền'."
            )
        )

        // 2. Battery Optimization (Huawei)
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "huawei_battery",
                title = "Bỏ qua tối ưu hóa pin",
                description = "Ngăn EMUI đưa ứng dụng vào danh sách tiết kiệm năng lượng.",
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Cài đặt Pin",
                actionIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                userInstruction = "1. Bấm 'Mở Cài đặt Pin'.\n2. Chuyển bộ lọc sang 'Tất cả ứng dụng'.\n3. Tìm Camera Alarm và chọn 'Không cho phép tối ưu' (Don't allow)."
            )
        )

        // 3. Lock in Recents (Huawei)
        items.add(
            ReliabilityItem(
                id = "huawei_lock_recents",
                title = "Khóa ứng dụng trong Đa nhiệm (Recents)",
                description = "Vuốt nhẹ thẻ Camera Alarm xuống trong màn hình đa nhiệm để khóa.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = "1. Mở màn hình ứng dụng gần đây.\n2. Vuốt nhẹ cửa sổ Camera Alarm xuống để khóa lại (hiện biểu tượng ổ khóa)."
            )
        )

        return items
    }

    private fun getHuaweiAppLaunchIntent(context: Context): Intent {
        val candidates = listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.bootstart.BootStartActivity")
        )
        for (cmp in candidates) {
            val intent = Intent().setComponent(cmp)
            if (resolves(context, intent)) return intent
        }
        return Intent(Settings.ACTION_SETTINGS)
    }

    companion object {
        fun isHuaweiFamily(manufacturer: String, brand: String): Boolean {
            val m = manufacturer.lowercase()
            val b = brand.lowercase()
            val candidates = listOf("huawei", "honor")
            return candidates.any { m.contains(it) || b.contains(it) }
        }

        private fun resolves(context: Context, intent: Intent): Boolean {
            return try {
                intent.resolveActivity(context.packageManager) != null
            } catch (_: Exception) {
                false
            }
        }
    }
}
