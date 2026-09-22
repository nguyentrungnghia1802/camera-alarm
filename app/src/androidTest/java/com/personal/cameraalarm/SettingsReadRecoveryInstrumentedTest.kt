package com.personal.cameraalarm

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cameraalarm.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SettingsReadRecoveryInstrumentedTest {
    @Test fun transientReadErrorDoesNotEmitMonitoringOffDefaults() = runBlocking {
        var subscriptions = 0
        var saved: Preferences = preferencesOf(
            booleanPreferencesKey("monitoring_enabled") to true,
            intPreferencesKey("settings_profile_version") to 3
        )
        val data = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow {
                if (subscriptions++ == 0) throw IOException("transient startup read")
                emit(saved)
            }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                saved = transform(saved)
                return saved
            }
        }
        val repository = SettingsRepository(InstrumentationRegistry.getInstrumentation().targetContext, data)
        assertTrue(repository.current().monitoringEnabled)
        assertEquals(2, subscriptions)
    }
}
