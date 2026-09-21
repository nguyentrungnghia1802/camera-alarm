package com.personal.cameraalarm.reliability

import android.content.Context
import android.os.Build
import com.personal.cameraalarm.R
import com.personal.cameraalarm.permission.ReadinessRepository

open class AndroidDefaultAdvisor(
    protected val readinessRepo: ReadinessRepository? = null,
    val manufacturer: String = Build.MANUFACTURER,
    val brand: String = Build.BRAND
) : DeviceReliabilityAdvisor {

    override val isApplicable: Boolean get() = true
    override val deviceFamilyName: String get() = "Google Pixel / AOSP"
    override val oemKey: String get() = "generic"

    override fun getStandardItems(context: Context): List<ReliabilityItem> {
        val readiness = readinessRepo?.snapshot()
        val items = mutableListOf<ReliabilityItem>()

        // 1. Notification Access
        val notifGranted = readiness?.notificationAccessGranted ?: false
        items.add(
            ReliabilityItem(
                id = "notification_access",
                title = context.getString(R.string.reliability_notification_access_title),
                description = context.getString(R.string.reliability_notification_access_desc),
                status = if (notifGranted) ReliabilityStatus.READY else ReliabilityStatus.MISSING,
                actionLabel = if (!notifGranted) context.getString(R.string.btn_grant) else null,
                actionIntent = readinessRepo?.notificationAccessSettingsIntent()
            )
        )

        // 2. Exact Alarm
        val exactGranted = readiness?.exactAlarmGranted ?: true
        items.add(
            ReliabilityItem(
                id = "exact_alarm",
                title = context.getString(R.string.reliability_exact_alarm_title),
                description = context.getString(R.string.reliability_exact_alarm_desc),
                status = if (exactGranted) ReliabilityStatus.READY else ReliabilityStatus.MISSING,
                actionLabel = if (!exactGranted) context.getString(R.string.btn_grant) else null,
                actionIntent = readinessRepo?.exactAlarmSettingsIntent()
            )
        )

        // 3. App Notifications
        val postGranted = readiness?.postNotificationsGranted ?: true
        items.add(
            ReliabilityItem(
                id = "post_notifications",
                title = context.getString(R.string.reliability_notifications_title),
                description = context.getString(R.string.reliability_notifications_desc),
                status = if (postGranted) ReliabilityStatus.READY else ReliabilityStatus.MISSING,
                actionLabel = if (!postGranted) context.getString(R.string.btn_open_settings) else null,
                actionIntent = readinessRepo?.appNotificationSettingsIntent()
            )
        )

        // 4. Full-Screen Alarm (Lock Screen)
        val canFullScreen = readinessRepo?.canUseFullScreenIntent() ?: true
        items.add(
            ReliabilityItem(
                id = "full_screen",
                title = context.getString(R.string.reliability_fullscreen_title),
                description = context.getString(R.string.reliability_fullscreen_desc),
                status = if (canFullScreen) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                actionLabel = if (!canFullScreen && readinessRepo?.fullScreenIntentSettingsIntent() != null) context.getString(R.string.btn_open_settings) else null,
                actionIntent = readinessRepo?.fullScreenIntentSettingsIntent()
            )
        )

        // 5. Battery Optimization
        val isIgnoringBattery = DeviceReliabilityAdvisor.isIgnoringBatteryOptimizations(context)
        items.add(
            ReliabilityItem(
                id = "battery_optimization",
                title = context.getString(R.string.reliability_battery_title),
                description = context.getString(R.string.reliability_battery_desc),
                status = if (isIgnoringBattery) ReliabilityStatus.READY else ReliabilityStatus.USER_CONFIRMATION_REQUIRED,
                actionLabel = if (!isIgnoringBattery) context.getString(R.string.reliability_battery_action) else null,
                actionIntent = if (!isIgnoringBattery) DeviceReliabilityAdvisor.getBatteryOptimizationIntent(context) else null
            )
        )

        return items
    }

    override fun getOemItems(context: Context): List<ReliabilityItem> {
        return emptyList()
    }
}
