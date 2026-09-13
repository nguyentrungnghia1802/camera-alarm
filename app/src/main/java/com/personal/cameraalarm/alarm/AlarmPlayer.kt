package com.personal.cameraalarm.alarm

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog

interface AlarmPlayer {
    fun start(soundKey: String? = null): Result<Unit>
    fun stop()
    val isPlaying: Boolean
}

class AndroidAlarmPlayer(private val context: Context) : AlarmPlayer {
    private var player: MediaPlayer? = null
    override val isPlaying: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    override fun start(soundKey: String?): Result<Unit> {
        if (isPlaying) return Result.success(Unit)
        stop()

        val selected = AlarmSoundCatalog.resolve(soundKey)
        val primaryResult = playRawResource(selected.rawResourceId)
        if (primaryResult.isSuccess) return primaryResult

        // If selected sound failed and was not default, fallback to default bundled sound
        if (selected.key != AlarmSoundCatalog.DEFAULT_KEY) {
            val defaultSound = AlarmSoundCatalog.defaultSound()
            val fallbackResult = playRawResource(defaultSound.rawResourceId)
            if (fallbackResult.isSuccess) return fallbackResult
        }

        // Ultimate fallback to system alarm URI
        val systemResult = playSystemUri()
        if (systemResult.isSuccess) return systemResult

        return primaryResult
    }

    private fun playRawResource(rawResId: Int): Result<Unit> {
        val created = MediaPlayer()
        var afd: AssetFileDescriptor? = null
        return try {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            created.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            afd = context.resources.openRawResourceFd(rawResId)
            created.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            created.isLooping = true
            created.prepare()
            created.setVolume(1f, 1f)
            created.start()
            player = created
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { created.release() }
            Result.failure(e)
        } finally {
            runCatching { afd?.close() }
        }
    }

    private fun playSystemUri(): Result<Unit> {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return Result.failure(IllegalStateException("System alarm URI unavailable"))
        val created = MediaPlayer()
        return try {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
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
        try {
            if (old.isPlaying) old.stop()
        } catch (_: IllegalStateException) {
            // A failed prepare/start can leave MediaPlayer in an unplayable state.
        } finally {
            runCatching { old.release() }
        }
    }
}
