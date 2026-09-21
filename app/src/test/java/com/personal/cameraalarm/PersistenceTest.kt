package com.personal.cameraalarm

import com.personal.cameraalarm.data.history.AlertEventEntity
import com.personal.cameraalarm.data.rule.TriggerRuleEntity
import com.personal.cameraalarm.data.settings.AppSettings
import com.personal.cameraalarm.data.settings.SettingsDefaults
import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.trigger.TriggerRule
import org.junit.Assert.*
import org.junit.Test

class PersistenceTest {
    @Test
    fun defaultAppSettingsMatchesSpecification() {
        val settings = AppSettings()
        assertFalse("Monitoring should be disabled by default", settings.monitoringEnabled)
        assertNull("Source package should be null by default", settings.sourcePackage)
        assertNull("Source label should be null by default", settings.sourceLabel)
        assertEquals(1000L, settings.alarmDelayMs)
        assertEquals(600000L, settings.cooldownMs)
        assertTrue("Vibration should be enabled by default", settings.vibrationEnabled)
        assertTrue("Full-screen should be enabled by default", settings.fullScreenEnabled)
        assertEquals("alarm_warning_aloud", settings.alarmSoundKey)
        assertEquals(com.personal.cameraalarm.schedule.ScheduleMode.CUSTOM, settings.scheduleMode)
        assertEquals(SettingsDefaults.OVERNIGHT_START_MINUTES, settings.scheduleRanges.single().startMinutes)
        assertEquals(SettingsDefaults.OVERNIGHT_END_MINUTES, settings.scheduleRanges.single().endMinutes)
    }

    @Test
    fun triggerRuleEntityMappingRoundtrip() {
        val domain = TriggerRule(
            id = "rule-1",
            name = "Human Alert",
            enabled = true,
            sourcePackage = "com.camera.app",
            matchMode = MatchMode.CONTAINS_ALL,
            keywords = listOf("human", "doorway"),
            priority = 1,
            createdAtEpochMs = 123456789L
        )
        val entity = TriggerRuleEntity.fromDomain(domain, updatedAtEpochMs = 987654321L)
        assertEquals("rule-1", entity.id)
        assertEquals("Human Alert", entity.name)
        assertTrue(entity.enabled)
        assertEquals("com.camera.app", entity.sourcePackage)
        assertEquals("CONTAINS_ALL", entity.matchMode)
        assertEquals(1, entity.priority)
        assertEquals(123456789L, entity.createdAtEpochMs)
        assertEquals(987654321L, entity.updatedAtEpochMs)

        val restored = entity.toDomain()
        assertEquals(domain.id, restored.id)
        assertEquals(domain.name, restored.name)
        assertEquals(domain.enabled, restored.enabled)
        assertEquals(domain.sourcePackage, restored.sourcePackage)
        assertEquals(domain.matchMode, restored.matchMode)
        assertEquals(domain.keywords, restored.keywords)
        assertEquals(domain.priority, restored.priority)
        assertEquals(domain.createdAtEpochMs, restored.createdAtEpochMs)
        assertEquals(987654321L, restored.updatedAtEpochMs)
    }

    @Test
    fun triggerRuleEntityHandlesInvalidJsonGracefully() {
        val corrupted = TriggerRuleEntity(
            id = "bad-json",
            name = "Bad",
            enabled = true,
            sourcePackage = "com.camera",
            matchMode = "UNKNOWN_MODE",
            keywordsJson = "invalid-json-string",
            priority = 0,
            createdAtEpochMs = 100L,
            updatedAtEpochMs = 100L
        )
        val restored = corrupted.toDomain()
        assertEquals(emptyList<String>(), restored.keywords)
        assertEquals(MatchMode.CONTAINS_ANY, restored.matchMode)
    }

    @Test
    fun alertEventEntityCreationAndSanitization() {
        val longPreview = "a".repeat(400)
        val entity = AlertEventEntity(
            createdAtEpochMs = 1000L,
            sourcePackage = "com.camera",
            notificationKey = "key-1",
            title = "Alert",
            textPreview = longPreview.take(300),
            normalizedHash = "hash123",
            decision = "SCHEDULED",
            ruleId = "rule-1",
            alarmToken = "token-1",
            details = "scheduled successfully"
        )
        assertEquals(300, entity.textPreview?.length)
        assertEquals("SCHEDULED", entity.decision)
    }
}
