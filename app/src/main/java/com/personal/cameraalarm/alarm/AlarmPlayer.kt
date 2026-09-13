package com.personal.cameraalarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager

interface AlarmPlayer {
    fun start(): Result<Unit>
    fun stop()
    val isPlaying: Boolean
}

class AndroidAlarmPlayer(private val context: Context) : AlarmPlayer {
    private var player: MediaPlayer? = null
    override val isPlaying: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)
    override fun start(): Result<Unit> {
        if (isPlaying) return Result.success(Unit)
        stop()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return Result.failure(IllegalStateException("System alarm URI unavailable"))
        val created = MediaPlayer()
        return try {
            created.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            created.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            created.setDataSource(context, uri)
            created.isLooping = true
            created.prepare()
            created.setVolume(1f, 1f)
            created.start()
            player = created
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { created.release() }
            Result.failure(e)
        }
    }
    override fun stop() {
        val old = player ?: return
        player = null
        try { if (old.isPlaying) old.stop() } catch (_: IllegalStateException) {
            // A failed prepare/start can leave MediaPlayer in an unplayable state.
        } finally { runCatching { old.release() } }
    }
}
