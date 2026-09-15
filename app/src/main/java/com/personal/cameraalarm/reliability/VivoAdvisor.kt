package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository

class VivoAdvisor(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : AndroidDefaultAdvisor(readinessRepo, manufacturer, brand) {

    private val isVivoDevice = isVivoFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isVivoDevice
    override val deviceFamilyName: String get() = if (isVivoDevice) "Vivo / iQOO (Funtouch OS / OriginOS)" else "Vivo (Không khớp)"
    override val oemKey: String get() = "vivo"

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        val items = mutableListOf<ReliabilityItem>()

        // 1. High background power consumption (Vivo)
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "vivo_high_power",
                title = "Tiêu thụ pin nền cao (High background power consumption)",
                description = "Cho phép ứng dụng tiếp tục chạy ngầm khi màn hình tắt trên Funtouch OS.",
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Cài đặt Pin",
                actionIntent = getVivoBatteryIntent(context),
                userInstruction = "1. Mở Cài đặt > Pin (Battery).\n2. Chọn 'Mức tiêu thụ pin dưới nền cao' (High background power consumption).\n3. Bật công tắc cho 'Camera Alarm'."
            )
        )

        // 2. Autostart (Vivo)
        items.add(
            ReliabilityItem(
                id = "vivo_autostart",
                title = "Quản lý tự khởi động (Autostart)",
                description = "Cho phép Camera Alarm tự chạy sau khi khởi động máy hoặc khi có cảnh báo.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Quản lý quyền",
                actionIntent = getVivoAutostartIntent(context),
                userInstruction = "1. Mở Cài đặt > Ứng dụng & Quyền > Quản lý quyền (Permission management).\n2. Chọn 'Tự khởi động' (Autostart) và BẬT cho Camera Alarm."
            )
        )

        // 3. Lock in Recents (Vivo)
        items.add(
            ReliabilityItem(
                id = "vivo_lock_recents",
                title = "Khóa ứng dụng trong Đa nhiệm (Recents)",
                description = "Khóa app để tránh bị trình quản lý RAM của Vivo dọn dẹp.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = "1. Mở màn hình ứng dụng gần đây.\n2. Vuốt nhẹ thẻ Camera Alarm xuống để hiển thị biểu tượng ổ khóa.\n3. Bấm khóa app."
            )
        )

        return items
    }

    private fun getVivoAutostartIntent(context: Context): Intent {
        val candidates = listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
        )
        for (cmp in candidates) {
            val intent = Intent().setComponent(cmp)
            if (resolves(context, intent)) return intent
        }
        return Intent(Settings.ACTION_SETTINGS)
    }

    private fun getVivoBatteryIntent(context: Context): Intent {
        val candidates = listOf(
            ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.MainGuideActivity")
        )
        for (cmp in candidates) {
            val intent = Intent().setComponent(cmp)
            if (resolves(context, intent)) return intent
        }
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    companion object {
        fun isVivoFamily(manufacturer: String, brand: String): Boolean {
            val m = manufacturer.lowercase()
            val b = brand.lowercase()
            val candidates = listOf("vivo", "iqoo")
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
