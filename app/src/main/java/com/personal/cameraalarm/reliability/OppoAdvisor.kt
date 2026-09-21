package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.R

class OppoAdvisor(
    readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : AndroidDefaultAdvisor(readinessRepo, manufacturer, brand) {

    private val isOppoDevice = isOppoFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isOppoDevice
    override val deviceFamilyName: String get() = "Oppo / Realme (ColorOS / Realme UI)"
    override val oemKey: String get() = "oppo"

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        val items = mutableListOf<ReliabilityItem>()

        // 1. Auto-launch (ColorOS)
        items.add(
            ReliabilityItem(
                id = "oppo_autostart",
                title = context.getString(R.string.reliability_oppo_autostart_title),
                description = context.getString(R.string.reliability_oppo_autostart_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_oppo_autostart_action),
                actionIntent = getOppoAutostartIntent(context),
                userInstruction = context.getString(R.string.reliability_oppo_autostart_steps)
            )
        )

        // 2. Allow background activity (ColorOS)
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "oppo_background_activity",
                title = context.getString(R.string.reliability_oppo_background_title),
                description = context.getString(R.string.reliability_oppo_background_desc),
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_open_app_settings),
                actionIntent = getAppDetailsIntent(context),
                userInstruction = context.getString(R.string.reliability_oppo_background_steps)
            )
        )

        // 3. Lock App in Recents (ColorOS)
        items.add(
            ReliabilityItem(
                id = "oppo_lock_recents",
                title = context.getString(R.string.reliability_lock_recents_title),
                description = context.getString(R.string.reliability_oppo_lock_recents_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = context.getString(R.string.reliability_oppo_lock_recents_steps)
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
