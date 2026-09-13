package com.personal.cameraalarm.alarm

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

interface VibrationController {
    fun startRepeating()
    fun stop()
}

class AndroidVibrationController(context: Context) : VibrationController {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31)
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)

    override fun startRepeating() {
        if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 300), 0))
    }
    override fun stop() { vibrator.cancel() }
}
