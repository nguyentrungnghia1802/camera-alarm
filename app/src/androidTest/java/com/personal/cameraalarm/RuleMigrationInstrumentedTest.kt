package com.personal.cameraalarm

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cameraalarm.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleMigrationInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration1To2PreservesRulesAndNormalizesPriorityInOldEvaluationOrder() {
        context.deleteDatabase(TEST_DB)
        helper.createDatabase(TEST_DB, 1).apply {
            insertRule("late", "Late", "camera.three", "[\"motion\"]", 2, 300)
            insertRule("first", "First", "camera.one", "[\"person\",\"motion\"]", 1, 100)
            insertRule("legacy-fourth", "Fourth", "camera.four", "[\"vehicle\"]", 2, 400)
            insertRule("second", "Second", "camera.two", "[\"human\"]", 1, 200)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            val rows = mutableListOf<List<Any>>()
            db.query(
                "SELECT id, priority, sourcePackage, keywordsJson, matchMode FROM trigger_rules ORDER BY priority"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    rows += listOf(
                        cursor.getString(0),
                        cursor.getInt(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4)
                    )
                }
            }

            assertEquals(listOf("first", "second", "late", "legacy-fourth"), rows.map { it[0] })
            assertEquals(listOf(1, 2, 3, 4), rows.map { it[1] })
            assertEquals(listOf("camera.one", "camera.two", "camera.three", "camera.four"), rows.map { it[2] })
            assertEquals("[\"person\",\"motion\"]", rows.first()[3])
            assertTrue(rows.all { it[4] == "CONTAINS_ANY" })
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertRule(
        id: String,
        name: String,
        sourcePackage: String,
        keywordsJson: String,
        priority: Int,
        createdAt: Long
    ) {
        execSQL(
            """
            INSERT INTO trigger_rules(
                id, name, enabled, sourcePackage, matchMode, keywordsJson,
                priority, createdAtEpochMs, updatedAtEpochMs
            ) VALUES (?, ?, 1, ?, 'CONTAINS_ANY', ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(id, name, sourcePackage, keywordsJson, priority, createdAt, createdAt)
        )
    }

    private companion object {
        const val TEST_DB = "rule-migration-test"
    }
}
