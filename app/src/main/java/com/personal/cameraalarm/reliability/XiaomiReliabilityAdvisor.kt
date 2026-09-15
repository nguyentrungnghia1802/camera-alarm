package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository

class XiaomiReliabilityAdvisor(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : AndroidDefaultAdvisor(readinessRepo, manufacturer, brand) {

    private val isXiaomiDevice = isXiaomiFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isXiaomiDevice
    override val deviceFamilyName: String get() = if (isXiaomiDevice) "Xiaomi / Redmi / POCO" else "Generic Android"
    override val oemKey: String get() = "xiaomi"

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        val items = mutableListOf<ReliabilityItem>()

        // 1. Background Autostart (Xiaomi)
        items.add(
            ReliabilityItem(
                id = "xiaomi_autostart",
                title = "Tự khởi động chạy nền (Autostart)",
                description = "Bắt buộc trên HyperOS/MIUI để dịch vụ nhận thông báo camera không bị tắt khi đóng app.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Tự khởi động",
                actionIntent = getAutostartIntent(context),
                userInstruction = "1. Bấm 'Mở Tự khởi động'.\n2. Tìm 'Camera Alarm'.\n3. BẬT công tắc cho phép tự khởi chạy."
            )
        )

        // 2. Battery: No Restrictions (Xiaomi)
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "xiaomi_battery",
                title = "Tiết kiệm pin: Không giới hạn (No restrictions)",
                description = "Đặt cấu hình Tiết kiệm pin của MIUI/HyperOS thành 'Không giới hạn'.",
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Cài đặt Pin",
                actionIntent = getBatterySettingsIntent(context),
                userInstruction = "1. Bấm 'Mở Cài đặt Pin'.\n2. Chọn 'Không giới hạn' (No restrictions) cho Camera Alarm."
            )
        )

        // 3. Lock App in Recents (Xiaomi)
        items.add(
            ReliabilityItem(
                id = "xiaomi_lock_recents",
                title = "Khóa ứng dụng trong Đa nhiệm (Recents)",
                description = "Khóa Camera Alarm trong màn hình ứng dụng gần đây để không bị tính năng dọn dẹp tắt mất.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = "1. Vuốt giữ mở màn hình Recent Apps.\n2. Nhấn và giữ thẻ Camera Alarm hoặc kéo nhẹ xuống.\n3. Bấm biểu tượng Ổ khóa (Padlock) để khóa."
            )
        )

        // 4. Lock-screen & Pop-up Windows (Xiaomi)
        items.add(
            ReliabilityItem(
                id = "xiaomi_popup",
                title = "Hiển thị trên Màn hình khóa & Cửa sổ pop-up",
                description = "Cho phép 'Hiển thị trên màn hình khóa' và 'Hiển thị cửa sổ pop-up khi chạy dưới nền'.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Quyền khác",
                actionIntent = getOtherPermissionsIntent(context),
                userInstruction = "1. Bấm 'Mở Quyền khác'.\n2. Bật 'Hiển thị trên Màn hình khóa'.\n3. Bật 'Hiển thị cửa sổ pop-up khi chạy dưới nền'."
            )
        )

        return items
    }

    fun getAutostartIntent(context: Context): Intent {
        val pkg = context.packageName
        val miuiIntent = Intent().apply {
            component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
        }
        if (resolves(context, miuiIntent)) return miuiIntent

        val altIntent = Intent("miui.intent.action.OP_AUTO_START").apply {
            putExtra("extra_pkgname", pkg)
        }
        if (resolves(context, altIntent)) return altIntent

        return getAppDetailsIntent(context)
    }

    fun getBatterySettingsIntent(context: Context): Intent {
        val pkg = context.packageName
        val miuiBattery = Intent().apply {
            component = ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            putExtra("package_name", pkg)
            putExtra("package_label", "Camera Alarm")
        }
        if (resolves(context, miuiBattery)) return miuiBattery

        val aospBattery = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (resolves(context, aospBattery)) return aospBattery

        return getAppDetailsIntent(context)
    }

    fun getOtherPermissionsIntent(context: Context): Intent {
        val pkg = context.packageName
        val miuiPerm = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            putExtra("extra_pkgname", pkg)
        }
        if (resolves(context, miuiPerm)) return miuiPerm

        val altPerm = Intent().apply {
            component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            putExtra("extra_pkgname", pkg)
        }
        if (resolves(context, altPerm)) return altPerm

        return getAppDetailsIntent(context)
    }

    fun getAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    companion object {
        fun isXiaomiFamily(manufacturer: String, brand: String): Boolean {
            val m = manufacturer.lowercase()
            val b = brand.lowercase()
            val candidates = listOf("xiaomi", "redmi", "poco")
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
