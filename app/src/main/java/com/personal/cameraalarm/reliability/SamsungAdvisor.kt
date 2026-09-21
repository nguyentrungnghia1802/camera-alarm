package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.R

class SamsungAdvisor(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : AndroidDefaultAdvisor(readinessRepo, manufacturer, brand) {

    private val isSamsungDevice = isSamsungFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isSamsungDevice
    override val deviceFamilyName: String get() = "Samsung One UI"
    override val oemKey: String get() = "samsung"

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        val items = mutableListOf<ReliabilityItem>()

        // 1. Samsung Battery: Unrestricted
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "samsung_battery_unrestricted",
                title = context.getString(R.string.reliability_samsung_unrestricted_title),
                description = context.getString(R.string.reliability_samsung_unrestricted_desc),
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_open_app_settings),
                actionIntent = getAppDetailsIntent(context),
                userInstruction = context.getString(R.string.reliability_samsung_unrestricted_steps)
            )
        )

        // 2. Samsung Never Sleeping Apps
        items.add(
            ReliabilityItem(
                id = "samsung_never_sleeping",
                title = context.getString(R.string.reliability_samsung_never_sleep_title),
                description = context.getString(R.string.reliability_samsung_never_sleep_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_samsung_battery_care_action),
                actionIntent = getSamsungBatteryCareIntent(context),
                userInstruction = context.getString(R.string.reliability_samsung_never_sleep_steps)
            )
        )

        // 3. Samsung Lock in Recents
        items.add(
            ReliabilityItem(
                id = "samsung_lock_recents",
                title = context.getString(R.string.reliability_lock_recents_title),
                description = context.getString(R.string.reliability_samsung_lock_recents_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = context.getString(R.string.reliability_samsung_lock_recents_steps)
            )
        )

        return items
    }

    private fun getAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    private fun getSamsungBatteryCareIntent(context: Context): Intent {
        val direct = Intent().apply {
            component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            )
        }
        val resolves = try {
            direct.resolveActivity(context.packageManager) != null
        } catch (_: RuntimeException) {
            false
        }
        return if (resolves) direct else getAppDetailsIntent(context)
    }

    companion object {
        fun isSamsungFamily(manufacturer: String, brand: String): Boolean {
            val m = manufacturer.lowercase()
            val b = brand.lowercase()
            return m.contains("samsung") || b.contains("samsung")
        }
    }
}
