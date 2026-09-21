package com.personal.cameraalarm

import com.personal.cameraalarm.alarm.AlarmRuntimeConfig
import com.personal.cameraalarm.alarm.AlarmRuntimeConfigCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRuntimeConfigCacheTest {
    @Test
    fun coldStartReadReturnsSafeDefaultsWithoutWaitingForSettingsIo() = runTest {
        val cache = AlarmRuntimeConfigCache()
        val releaseSettings = CompletableDeferred<Unit>()
        val slowSettingsLoad = async {
            releaseSettings.await()
            cache.update(
                AlarmRuntimeConfig(
                    vibrationEnabled = false,
                    fullScreenEnabled = false,
                    soundKey = "alarm_digital"
                )
            )
        }

        val coldSnapshot = cache.current()
        assertTrue(coldSnapshot.vibrationEnabled)
        assertTrue(coldSnapshot.fullScreenEnabled)

        releaseSettings.complete(Unit)
        slowSettingsLoad.await()
        val loadedSnapshot = cache.current()
        assertFalse(loadedSnapshot.vibrationEnabled)
        assertFalse(loadedSnapshot.fullScreenEnabled)
        assertEquals("alarm_digital", loadedSnapshot.soundKey)
    }
}
