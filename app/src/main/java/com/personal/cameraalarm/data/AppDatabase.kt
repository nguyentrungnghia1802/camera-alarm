package com.personal.cameraalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.personal.cameraalarm.data.history.AlertEventDao
import com.personal.cameraalarm.data.history.AlertEventEntity
import com.personal.cameraalarm.data.rule.TriggerRuleDao
import com.personal.cameraalarm.data.rule.TriggerRuleEntity

@Database(
    entities = [TriggerRuleEntity::class, AlertEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun triggerRuleDao(): TriggerRuleDao
    abstract fun alertEventDao(): AlertEventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "camera_alarm_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
