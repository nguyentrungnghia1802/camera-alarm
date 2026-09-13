package com.personal.cameraalarm.data.settings

data class AppSettings(
    val monitoringEnabled: Boolean = false,
    val sourcePackage: String? = null,
    val sourceLabel: String? = null,
    val alarmDelayMs: Long = 1000L,
    val cooldownMs: Long = 10000L,
    val vibrationEnabled: Boolean = true,
    val fullScreenEnabled: Boolean = false
)
