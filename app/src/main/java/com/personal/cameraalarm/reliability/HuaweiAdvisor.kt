package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.R

class HuaweiAdvisor(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : AndroidDefaultAdvisor(readinessRepo, manufacturer, brand) {

    private val isHuaweiDevice = isHuaweiFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isHuaweiDevice
    override val deviceFamilyName: String get() = "Huawei / Honor (EMUI / HarmonyOS)"
    override val oemKey: String get() = "huawei"

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        val items = mutableListOf<ReliabilityItem>()

        // 1. App Launch Management (Huawei)
        items.add(
            ReliabilityItem(
                id = "huawei_app_launch",
                title = context.getString(R.string.reliability_huawei_launch_title),
                description = context.getString(R.string.reliability_huawei_launch_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_huawei_launch_action),
                actionIntent = getHuaweiAppLaunchIntent(context),
                userInstruction = context.getString(R.string.reliability_huawei_launch_steps)
            )
        )

        // 2. Battery Optimization (Huawei)
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "huawei_battery",
                title = context.getString(R.string.reliability_huawei_battery_title),
                description = context.getString(R.string.reliability_huawei_battery_desc),
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_open_battery_settings),
                actionIntent = DeviceReliabilityAdvisor.getBatteryOptimizationIntent(context),
                userInstruction = context.getString(R.string.reliability_huawei_battery_steps)
            )
        )

        // 3. Lock in Recents (Huawei)
        items.add(
            ReliabilityItem(
                id = "huawei_lock_recents",
                title = context.getString(R.string.reliability_lock_recents_title),
                description = context.getString(R.string.reliability_huawei_lock_recents_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = context.getString(R.string.reliability_huawei_lock_recents_steps)
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
