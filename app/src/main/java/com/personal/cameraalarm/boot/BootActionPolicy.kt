package com.personal.cameraalarm.boot

import android.content.Intent

internal object BootActionPolicy {
    private val supportedActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON"
    )

    fun isSupported(action: String?): Boolean = action != null && action in supportedActions
}
