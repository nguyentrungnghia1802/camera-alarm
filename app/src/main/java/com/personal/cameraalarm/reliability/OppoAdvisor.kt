package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository

class OppoAdvisor(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : AndroidDefaultAdvisor(readinessRepo, manufacturer, brand) {

    private val isOppoDevice = isOppoFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isOppoDevice
    override val deviceFamilyName: String get() = if (isOppoDevice) "Oppo / Realme (ColorOS / Realme UI)" else "Oppo (Không khớp)"
    override val oemKey: String get() = "oppo"

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        val items = mutableListOf<ReliabilityItem>()

        // 1. Auto-launch (ColorOS)
        items.add(
            ReliabilityItem(
                id = "oppo_autostart",
                title = "Cho phép Tự khởi động (Auto-launch)",
                description = "Cho phép Camera Alarm tự khởi chạy và duy trì dịch vụ nhận thông báo camera.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Quản lý khởi động",
                actionIntent = getOppoAutostartIntent(context),
                userInstruction = "1. Bấm 'Mở Quản lý khởi động' (hoặc Cài đặt > Quản lý ứng dụng > Tự khởi chạy).\n2. Bật công tắc cho 'Camera Alarm'."
            )
        )

        // 2. Allow background activity (ColorOS)
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "oppo_background_activity",
                title = "Cho phép hoạt động dưới nền & Tắt đóng băng",
                description = "Ngăn ColorOS đóng băng ứng dụng khi màn hình tắt.",
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Cài đặt App",
                actionIntent = getAppDetailsIntent(context),
                userInstruction = "1. Bấm 'Mở Cài đặt App' > chọn 'Pin' (Battery).\n2. Bật 'Cho phép hoạt động dưới nền' (Allow background activity).\n3. Tắt 'Tối ưu hóa thời lượng pin khi ngủ' (Sleep standby optimization)."
            )
        )

        // 3. Lock App in Recents (ColorOS)
        items.add(
            ReliabilityItem(
                id = "oppo_lock_recents",
                title = "Khóa ứng dụng trong Đa nhiệm (Recents)",
                description = "Khóa Camera Alarm trong màn hình gần đây để không bị tính năng dọn dẹp tắt.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = "1. Mở màn hình ứng dụng gần đây.\n2. Bấm vào biểu tượng 2 chấm/3 chấm ở góc thẻ Camera Alarm.\n3. Chọn 'Khóa' (Lock)."
            )
        )

        return items
    }

    private fun getOppoAutostartIntent(context: Context): Intent {
        val candidates = listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
        )
        for (cmp in candidates) {
            val intent = Intent().setComponent(cmp)
            if (resolves(context, intent)) return intent
        }
        return getAppDetailsIntent(context)
    }

    private fun getAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    companion object {
        fun isOppoFamily(manufacturer: String, brand: String): Boolean {
            val m = manufacturer.lowercase()
            val b = brand.lowercase()
            val candidates = listOf("oppo", "realme", "oneplus")
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
