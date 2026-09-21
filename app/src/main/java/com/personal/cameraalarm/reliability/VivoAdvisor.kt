package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.R

class VivoAdvisor(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : AndroidDefaultAdvisor(readinessRepo, manufacturer, brand) {

    private val isVivoDevice = isVivoFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isVivoDevice
    override val deviceFamilyName: String get() = "Vivo / iQOO (Funtouch OS / OriginOS)"
    override val oemKey: String get() = "vivo"

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        val items = mutableListOf<ReliabilityItem>()

        // 1. High background power consumption (Vivo)
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "vivo_high_power",
                title = context.getString(R.string.reliability_vivo_battery_title),
                description = context.getString(R.string.reliability_vivo_battery_desc),
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_open_battery_settings),
                actionIntent = getVivoBatteryIntent(context),
                userInstruction = context.getString(R.string.reliability_vivo_battery_steps)
            )
        )

        // 2. Autostart (Vivo)
        items.add(
            ReliabilityItem(
                id = "vivo_autostart",
                title = context.getString(R.string.reliability_vivo_autostart_title),
                description = context.getString(R.string.reliability_vivo_autostart_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_vivo_autostart_action),
                actionIntent = getVivoAutostartIntent(context),
                userInstruction = context.getString(R.string.reliability_vivo_autostart_steps)
            )
        )

        // 3. Lock in Recents (Vivo)
        items.add(
            ReliabilityItem(
                id = "vivo_lock_recents",
                title = context.getString(R.string.reliability_lock_recents_title),
                description = context.getString(R.string.reliability_vivo_lock_recents_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = context.getString(R.string.reliability_vivo_lock_recents_steps)
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
        return DeviceReliabilityAdvisor.getBatteryOptimizationIntent(context)
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
