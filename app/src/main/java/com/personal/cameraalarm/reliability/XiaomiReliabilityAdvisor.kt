package com.personal.cameraalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.personal.cameraalarm.permission.ReadinessRepository
import com.personal.cameraalarm.permission.ReadinessState

class XiaomiReliabilityAdvisor(
    private val readinessRepo: ReadinessRepository? = null,
    manufacturer: String = Build.MANUFACTURER,
    brand: String = Build.BRAND
) : DeviceReliabilityAdvisor {

    private val isXiaomiDevice = isXiaomiFamily(manufacturer, brand)

    override val isApplicable: Boolean get() = isXiaomiDevice
    override val deviceFamilyName: String get() = if (isXiaomiDevice) "Xiaomi / Redmi / POCO" else "Generic Android"

    override fun getReliabilityItems(context: Context): List<ReliabilityItem> {
        val readiness = readinessRepo?.snapshot() ?: ReadinessState(
            notificationAccessGranted = false,
            listenerConnected = false,
            exactAlarmGranted = false,
            postNotificationsGranted = false,
            sourceConfigured = false,
            ruleConfigured = false,
            alarmVolumeNonZero = false
        )
        val items = mutableListOf<ReliabilityItem>()

        // 1. Notification Access
        items.add(
            ReliabilityItem(
                id = "notification_access",
                title = "Notification Access",
                description = "Required to detect camera alert notifications from your selected app.",
                status = if (readiness.notificationAccessGranted) ReliabilityStatus.READY else ReliabilityStatus.MISSING,
                actionLabel = if (!readiness.notificationAccessGranted) "Grant Access" else null,
                actionIntent = readinessRepo?.notificationAccessSettingsIntent()
            )
        )

        // 2. Exact Alarm
        items.add(
            ReliabilityItem(
                id = "exact_alarm",
                title = "Exact Alarm Permission",
                description = "Required to schedule precise countdown alarms.",
                status = if (readiness.exactAlarmGranted) ReliabilityStatus.READY else ReliabilityStatus.MISSING,
                actionLabel = if (!readiness.exactAlarmGranted) "Grant" else null,
                actionIntent = readinessRepo?.exactAlarmSettingsIntent()
            )
        )

        // 3. App Notifications
        items.add(
            ReliabilityItem(
                id = "post_notifications",
                title = "App Notifications",
                description = "Allows Camera Alarm to show foreground notifications and stop actions.",
                status = if (readiness.postNotificationsGranted) ReliabilityStatus.READY else ReliabilityStatus.MISSING,
                actionLabel = if (!readiness.postNotificationsGranted) "Settings" else null,
                actionIntent = readinessRepo?.appNotificationSettingsIntent()
            )
        )

        // 4. Full-screen Alarm
        val canFullScreen = readinessRepo?.canUseFullScreenIntent() ?: false
        items.add(
            ReliabilityItem(
                id = "full_screen",
                title = "Full-Screen Alarm (Lock Screen)",
                description = "Displays ringing activity over lock screen when alarm fires.",
                status = if (canFullScreen) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                actionLabel = if (!canFullScreen && readinessRepo?.fullScreenIntentSettingsIntent() != null) "Settings" else null,
                actionIntent = readinessRepo?.fullScreenIntentSettingsIntent()
            )
        )

        // OEM specific items
        if (!isXiaomiDevice) {
            items.add(
                ReliabilityItem(
                    id = "xiaomi_autostart",
                    title = "Background Autostart",
                    description = "OEM autostart protection.",
                    status = ReliabilityStatus.NOT_APPLICABLE
                )
            )
            items.add(
                ReliabilityItem(
                    id = "xiaomi_battery",
                    title = "Battery: No Restrictions",
                    description = "OEM background battery optimization.",
                    status = ReliabilityStatus.NOT_APPLICABLE
                )
            )
            items.add(
                ReliabilityItem(
                    id = "xiaomi_lock_recents",
                    title = "Lock App in Recents",
                    description = "Prevent task killer from terminating app.",
                    status = ReliabilityStatus.NOT_APPLICABLE
                )
            )
            items.add(
                ReliabilityItem(
                    id = "xiaomi_popup",
                    title = "Lock-screen & Pop-up Windows",
                    description = "Display pop-up windows in background.",
                    status = ReliabilityStatus.NOT_APPLICABLE
                )
            )
            return items
        }

        // 5. Background Autostart (Xiaomi)
        items.add(
            ReliabilityItem(
                id = "xiaomi_autostart",
                title = "Background Autostart",
                description = "Enable Background Autostart to ensure Camera Alarm receives notifications when closed.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Open Autostart",
                actionIntent = getAutostartIntent(context),
                userInstruction = "1. Tap 'Open Autostart'.\n2. Locate 'Camera Alarm'.\n3. Turn ON the switch to allow autostart."
            )
        )

        // 6. Battery: No Restrictions (Xiaomi)
        items.add(
            ReliabilityItem(
                id = "xiaomi_battery",
                title = "Battery: No Restrictions",
                description = "Set MIUI / HyperOS Battery Saver to 'No restrictions' so the system won't kill the listener.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Open Battery Settings",
                actionIntent = getBatterySettingsIntent(context),
                userInstruction = "1. Tap 'Open Battery Settings'.\n2. Select 'No restrictions' for Camera Alarm."
            )
        )

        // 7. Lock App in Recents (Xiaomi)
        items.add(
            ReliabilityItem(
                id = "xiaomi_lock_recents",
                title = "Lock App in Recents",
                description = "Lock Camera Alarm in the Recent Apps screen so clearing apps will not kill it.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                userInstruction = "1. Open Recent Apps (swipe up & hold).\n2. Long-press Camera Alarm card or pull down.\n3. Tap the Padlock icon to lock."
            )
        )

        // 8. Lock-screen & Pop-up Windows (Xiaomi)
        items.add(
            ReliabilityItem(
                id = "xiaomi_popup",
                title = "Lock-screen & Background Pop-up Windows",
                description = "Allow Camera Alarm to 'Show on Lock screen' and 'Display pop-up windows while in background'.",
                status = ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                isOemSpecific = true,
                actionLabel = "Open Permissions",
                actionIntent = getOtherPermissionsIntent(context),
                userInstruction = "1. Tap 'Open Permissions'.\n2. Enable 'Show on Lock screen'.\n3. Enable 'Display pop-up windows while running in the background'."
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

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
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
