package com.personal.cameraalarm.alarm.sound

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmSoundPreviewController(private val context: Context) {
    private var player: MediaPlayer? = null
    private val mutablePlayingKey = MutableStateFlow<String?>(null)
    val playingSoundKey: StateFlow<String?> = mutablePlayingKey.asStateFlow()

    val isPlaying: Boolean
        get() = player != null && runCatching { player?.isPlaying == true }.getOrDefault(false)

    @Synchronized
    fun play(sound: AlarmSound): Result<Unit> {
        stop()
        val created = MediaPlayer()
        var afd: AssetFileDescriptor? = null
        return try {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            afd = context.resources.openRawResourceFd(sound.rawResourceId)
            created.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            created.isLooping = true
            created.prepare()
            created.setVolume(1f, 1f)
            created.start()
            player = created
            mutablePlayingKey.value = sound.key
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { created.release() }
            Result.failure(e)
        } finally {
            runCatching { afd?.close() }
        }
    }

    @Synchronized
    fun stop() {
        val old = player ?: return
        player = null
        mutablePlayingKey.value = null
        try {
            if (old.isPlaying) old.stop()
        } catch (_: IllegalStateException) {
        } finally {
            runCatching { old.release() }
        }
    }
}
