package com.personal.cameraalarm.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.personal.cameraalarm.app.CameraAlarmApp
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!BootActionPolicy.isSupported(intent?.action)) return

        val app = context.applicationContext as? CameraAlarmApp ?: return
        val action = intent?.action ?: return
        // Register a continuation before async work; process death must not require opening the UI.
        app.container.recoveryJobs.boot(action)
        val pendingResult = goAsync()
        val pm = context.getSystemService(PowerManager::class.java)
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CameraAlarm:BootReceiverWakeLock")
        wakeLock?.acquire(15_000)

        app.scope.launch {
            try {
                val reconciler = DefaultBootReconciler(context, app.container, action)
                reconciler.reconcile()
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (_: Exception) {}
                }
                pendingResult.finish()
            }
        }
    }
}
