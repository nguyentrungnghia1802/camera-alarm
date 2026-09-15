package com.personal.cameraalarm.alarm

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager
import android.util.Log
import com.personal.cameraalarm.BuildConfig
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
        val primaryResult = playRawResource(selected.key, selected.rawResourceId)
        if (primaryResult.isSuccess) return primaryResult

        if (BuildConfig.DEBUG) {
            Log.w("CameraAlarm", "Selected sound failed key=${selected.key}, falling back to alarm_default")
        }

        // If selected sound failed and was not default, fallback to default bundled sound
        if (selected.key != AlarmSoundCatalog.DEFAULT_KEY) {
            val defaultSound = AlarmSoundCatalog.defaultSound()
            val fallbackResult = playRawResource(defaultSound.key, defaultSound.rawResourceId)
            if (fallbackResult.isSuccess) return fallbackResult
        }

        // Ultimate fallback to system alarm URI
        if (BuildConfig.DEBUG) {
            Log.w("CameraAlarm", "Default sound failed, falling back to system alarm URI")
        }
        val systemResult = playSystemUri()
        if (systemResult.isSuccess) return systemResult

        if (BuildConfig.DEBUG) {
            Log.e("CameraAlarm", "All alarm sound options failed: primary=${primaryResult.exceptionOrNull()?.message}, system=${systemResult.exceptionOrNull()?.message}")
        }
        return primaryResult
    }

    private fun playRawResource(key: String, rawResId: Int): Result<Unit> {
        val created = MediaPlayer()
        var afd: AssetFileDescriptor? = null
        var prepareResult = "success"
        var playResult = "success"

        return try {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            created.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            afd = context.resources.openRawResourceFd(rawResId)
                ?: return Result.failure(IllegalStateException("Resource $rawResId unavailable"))

            if (afd.declaredLength < 0) {
                created.setDataSource(afd.fileDescriptor)
            } else {
                created.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
            }
            created.isLooping = true
            try {
                created.prepare()
            } catch (e: Exception) {
                prepareResult = "failed: ${e.message}"
                throw e
            }

            created.setVolume(1f, 1f)
            try {
                created.start()
            } catch (e: Exception) {
                playResult = "failed: ${e.message}"
                throw e
            }

            player = created
            if (BuildConfig.DEBUG) {
                Log.d(
                    "CameraAlarm",
                    "Alarm sound:\nselected key: $key\nresolved resource: $rawResId\nprepare result: $prepareResult\nplay result: $playResult"
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(
                    "CameraAlarm",
                    "Alarm sound:\nselected key: $key\nresolved resource: $rawResId\nprepare result: $prepareResult\nplay result: failed: ${e.message}"
                )
            }
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
        var prepareResult = "success"
        var playResult = "success"

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
            try {
                created.prepare()
            } catch (e: Exception) {
                prepareResult = "failed: ${e.message}"
                throw e
            }

            created.setVolume(1f, 1f)
            try {
                created.start()
            } catch (e: Exception) {
                playResult = "failed: ${e.message}"
                throw e
            }

            player = created
            if (BuildConfig.DEBUG) {
                Log.d(
                    "CameraAlarm",
                    "Alarm sound:\nselected key: system_default\nresolved resource: $uri\nprepare result: $prepareResult\nplay result: $playResult"
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(
                    "CameraAlarm",
                    "Alarm sound:\nselected key: system_default\nresolved resource: $uri\nprepare result: $prepareResult\nplay result: failed: ${e.message}"
                )
            }
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
