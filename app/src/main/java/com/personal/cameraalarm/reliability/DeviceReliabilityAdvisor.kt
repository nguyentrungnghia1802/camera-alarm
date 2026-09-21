package com.personal.cameraalarm.reliability

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

enum class ReliabilityStatus {
    READY,
    MISSING,
    UNKNOWN,
    USER_CONFIRMATION_REQUIRED,
    NOT_APPLICABLE
}

data class ReliabilityItem(
    val id: String,
    val title: String,
    val description: String,
    val status: ReliabilityStatus,
    val isOemSpecific: Boolean = false,
    val actionLabel: String? = null,
    val actionIntent: Intent? = null,
    val userInstruction: String? = null
)

interface DeviceReliabilityAdvisor {
    val isApplicable: Boolean
    val deviceFamilyName: String
    val oemKey: String
    fun getStandardItems(context: Context): List<ReliabilityItem>
    fun getOemItems(context: Context): List<ReliabilityItem>
    fun getReliabilityItems(context: Context): List<ReliabilityItem> {
        return getStandardItems(context) + getOemItems(context)
    }

    companion object {
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        }

        const val BATTERY_OPTIMIZATION_SETTINGS_ACTION =
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS

        fun getBatteryOptimizationIntent(context: Context): Intent {
            val generalSettings = Intent(BATTERY_OPTIMIZATION_SETTINGS_ACTION)
            val canOpenGeneralSettings = try {
                generalSettings.resolveActivity(context.packageManager) != null
            } catch (_: RuntimeException) {
                false
            }
            if (canOpenGeneralSettings) return generalSettings

            return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        }
    }
}
