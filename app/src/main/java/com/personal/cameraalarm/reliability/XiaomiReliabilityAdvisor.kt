package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.R

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
                title = context.getString(R.string.reliability_xiaomi_autostart_title),
                description = context.getString(R.string.reliability_xiaomi_autostart_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_xiaomi_autostart_action),
                actionIntent = getAutostartIntent(context),
                userInstruction = context.getString(R.string.reliability_xiaomi_autostart_steps)
            )
        )

        // 2. Battery: No Restrictions (Xiaomi)
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "xiaomi_battery",
                title = context.getString(R.string.reliability_xiaomi_battery_title),
                description = context.getString(R.string.reliability_xiaomi_battery_desc),
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_open_battery_settings),
                actionIntent = getBatterySettingsIntent(context),
                userInstruction = context.getString(R.string.reliability_xiaomi_battery_steps)
            )
        )

        // 3. Lock App in Recents (Xiaomi)
        items.add(
            ReliabilityItem(
                id = "xiaomi_lock_recents",
                title = context.getString(R.string.reliability_lock_recents_title),
                description = context.getString(R.string.reliability_xiaomi_lock_recents_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = context.getString(R.string.reliability_xiaomi_lock_recents_steps)
            )
        )

        // 4. Lock-screen & Pop-up Windows (Xiaomi)
        items.add(
            ReliabilityItem(
                id = "xiaomi_popup",
                title = context.getString(R.string.reliability_xiaomi_popup_title),
                description = context.getString(R.string.reliability_xiaomi_popup_desc),
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = context.getString(R.string.reliability_xiaomi_other_permissions_action),
                actionIntent = getOtherPermissionsIntent(context),
                userInstruction = context.getString(R.string.reliability_xiaomi_popup_steps)
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
