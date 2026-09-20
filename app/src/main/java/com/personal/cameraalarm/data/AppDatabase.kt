package com.personal.cameraalarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.personal.cameraalarm.data.history.AlertEventDao
import com.personal.cameraalarm.data.history.AlertEventEntity
import com.personal.cameraalarm.data.rule.TriggerRuleDao
import com.personal.cameraalarm.data.rule.TriggerRuleEntity

@Database(
    entities = [TriggerRuleEntity::class, AlertEventEntity::class],
    version = 2,
    exportSchema = true
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
                ).addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val orderedIds = mutableListOf<String>()
                db.query(
                    "SELECT id FROM trigger_rules ORDER BY priority ASC, createdAtEpochMs ASC, id ASC"
                ).use { cursor ->
                    while (cursor.moveToNext()) orderedIds += cursor.getString(0)
                }

                // Use unique temporary priorities before assigning the stable 1..N order.
                orderedIds.forEachIndexed { index, id ->
                    db.execSQL(
                        "UPDATE trigger_rules SET priority = ? WHERE id = ?",
                        arrayOf(-(index + 1), id)
                    )
                }
                orderedIds.forEachIndexed { index, id ->
                    db.execSQL(
                        "UPDATE trigger_rules SET priority = ? WHERE id = ?",
                        arrayOf(index + 1, id)
                    )
                }
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_trigger_rules_priority ON trigger_rules(priority)"
                )
            }
        }
    }
}
