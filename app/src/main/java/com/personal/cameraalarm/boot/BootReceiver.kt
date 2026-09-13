package com.personal.cameraalarm.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val app = context.applicationContext as? CameraAlarmApp ?: return
        val pendingResult = goAsync()
        app.scope.launch {
            try {
                val reconciler = DefaultBootReconciler(context, app.container)
                reconciler.reconcile()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
