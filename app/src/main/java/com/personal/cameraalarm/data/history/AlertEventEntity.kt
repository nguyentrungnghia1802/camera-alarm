package com.personal.cameraalarm.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alert_events",
    indices = [
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["alarmToken"])
    ]
)
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtEpochMs: Long,
    val sourcePackage: String?,
    val notificationKey: String?,
    val title: String?,
    val textPreview: String?,
    val normalizedHash: String?,
    val decision: String,
    val ruleId: String?,
    val alarmToken: String?,
    val details: String?
)
