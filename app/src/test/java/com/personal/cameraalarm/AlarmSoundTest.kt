package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.AlarmPlayer
import com.personal.cameraalarm.alarm.AlarmRuntimeController
import com.personal.cameraalarm.alarm.AlarmToken
import com.personal.cameraalarm.alarm.VibrationController
import com.personal.cameraalarm.alarm.sound.AlarmSoundCatalog
import org.junit.Assert.*
import org.junit.Test

class AlarmSoundTest {

    @Test
    fun catalogContainsAllBundledSounds() {
        val sounds = AlarmSoundCatalog.allSounds
        assertEquals(4, sounds.size)
        val keys = sounds.map { it.key }.toSet()
        assertTrue(keys.contains("alarm_default"))
        assertTrue(keys.contains("alarm_siren"))
        assertTrue(keys.contains("alarm_warning"))
        assertTrue(keys.contains("alarm_loud"))
    }

    @Test
    fun defaultSoundResolvesCorrectly() {
        val defaultSound = AlarmSoundCatalog.defaultSound()
        assertEquals(AlarmSoundCatalog.DEFAULT_KEY, defaultSound.key)
        assertEquals("Default Alarm", defaultSound.displayName)
    }

    @Test
    fun validKeyResolvesExpectedSound() {
        val siren = AlarmSoundCatalog.resolve("alarm_siren")
        assertEquals("alarm_siren", siren.key)
        assertEquals("Siren", siren.displayName)

        val loud = AlarmSoundCatalog.resolve("alarm_loud")
        assertEquals("alarm_loud", loud.key)
        assertEquals("Loud Alarm", loud.displayName)
    }

    @Test
    fun unknownKeyFallsBackToDefaultSound() {
        val resolved = AlarmSoundCatalog.resolve("non_existent_key")
        assertEquals(AlarmSoundCatalog.DEFAULT_KEY, resolved.key)
        assertEquals("Default Alarm", resolved.displayName)
    }

    @Test
    fun nullOrBlankKeyFallsBackToDefaultSound() {
        assertEquals(AlarmSoundCatalog.DEFAULT_KEY, AlarmSoundCatalog.resolve(null).key)
        assertEquals(AlarmSoundCatalog.DEFAULT_KEY, AlarmSoundCatalog.resolve("").key)
        assertEquals(AlarmSoundCatalog.DEFAULT_KEY, AlarmSoundCatalog.resolve("   ").key)
    }

    @Test
    fun isValidKeyIdentifiesCorrectKeys() {
        assertTrue(AlarmSoundCatalog.isValidKey("alarm_default"))
        assertTrue(AlarmSoundCatalog.isValidKey("alarm_siren"))
        assertFalse(AlarmSoundCatalog.isValidKey("unknown"))
        assertFalse(AlarmSoundCatalog.isValidKey(""))
        assertFalse(AlarmSoundCatalog.isValidKey(null))
    }

    private class RecordingPlayer : AlarmPlayer {
        var playedSoundKey: String? = null
        var isStarted = false
        override val isPlaying get() = isStarted
        override fun start(soundKey: String?): Result<Unit> {
            isStarted = true
            playedSoundKey = soundKey
            return Result.success(Unit)
        }
        override fun stop() {
            isStarted = false
        }
    }

    private class NoOpVibration : VibrationController {
        override fun startRepeating() {}
        override fun stop() {}
    }

    @Test
    fun productionAlarmUsesSelectedLogicalKey() {
        val player = RecordingPlayer()
        val controller = AlarmRuntimeController(player, NoOpVibration())
        val token = AlarmToken("prod-1")

        controller.start(token, vibrationEnabled = false, soundKey = "alarm_siren")

        assertTrue(player.isStarted)
        assertEquals("alarm_siren", player.playedSoundKey)
    }

    @Test
    fun productionAlarmWithNullSoundKeyPassesNullToPlayer() {
        val player = RecordingPlayer()
        val controller = AlarmRuntimeController(player, NoOpVibration())
        val token = AlarmToken("prod-2")

        controller.start(token, vibrationEnabled = true)

        assertTrue(player.isStarted)
        assertNull(player.playedSoundKey)
    }
}
