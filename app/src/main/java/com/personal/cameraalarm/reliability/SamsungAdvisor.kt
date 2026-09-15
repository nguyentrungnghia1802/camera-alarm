package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository

class SamsungAdvisor(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : AndroidDefaultAdvisor(readinessRepo, manufacturer, brand) {

    private val isSamsungDevice = isSamsungFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isSamsungDevice
    override val deviceFamilyName: String get() = if (isSamsungDevice) "Samsung One UI" else "Samsung (Không khớp)"
    override val oemKey: String get() = "samsung"

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        val items = mutableListOf<ReliabilityItem>()

        // 1. Samsung Battery: Unrestricted
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "samsung_battery_unrestricted",
                title = "Đặt Pin thành 'Không hạn chế'",
                description = "Ngăn Samsung One UI tự động đóng băng app khi tắt màn hình hoặc không mở trong vài ngày.",
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Cài đặt App",
                actionIntent = getAppDetailsIntent(context),
                userInstruction = "1. Bấm 'Mở Cài đặt App'.\n2. Chọn mục 'Pin' (Battery).\n3. Đổi từ 'Tối ưu hóa' (Optimized) sang 'Không hạn chế' (Unrestricted)."
            )
        )

        // 2. Samsung Never Sleeping Apps
        items.add(
            ReliabilityItem(
                id = "samsung_never_sleeping",
                title = "Ứng dụng không bao giờ nghỉ (Never Sleeping Apps)",
                description = "Đưa Camera Alarm vào danh sách miễn trừ ngủ sâu của hệ điều hành Samsung.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Mở Chăm sóc pin",
                actionIntent = getSamsungBatteryCareIntent(),
                userInstruction = "1. Bấm 'Mở Chăm sóc pin' (hoặc Cài đặt > Pin & Chăm sóc thiết bị > Pin).\n2. Chọn 'Giới hạn sử dụng nền' (Background usage limits).\n3. Chọn 'Ứng dụng không bao giờ nghỉ' (Never sleeping apps) và bấm '+' để thêm Camera Alarm."
            )
        )

        // 3. Samsung Lock in Recents
        items.add(
            ReliabilityItem(
                id = "samsung_lock_recents",
                title = "Khóa ứng dụng trong Đa nhiệm (Recents)",
                description = "Ngăn tính năng 'Đóng tất cả' (Close All) của Samsung tắt tiến trình nhận thông báo.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = "1. Vuốt mở màn hình ứng dụng gần đây (Recent Apps).\n2. Nhấn và giữ biểu tượng Camera Alarm phía trên cửa sổ app.\n3. Chọn 'Khóa ứng dụng này' (Lock this app)."
            )
        )

        return items
    }

    private fun getAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    private fun getSamsungBatteryCareIntent(): Intent {
        // Attempt to launch Samsung Device Care Battery settings directly
        return try {
            Intent().apply {
                component = ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    companion object {
        fun isSamsungFamily(manufacturer: String, brand: String): Boolean {
            val m = manufacturer.lowercase()
            val b = brand.lowercase()
            return m.contains("samsung") || b.contains("samsung")
        }
    }
}
