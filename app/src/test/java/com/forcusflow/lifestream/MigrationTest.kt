package com.forcusflow.lifestream

import androidx.sqlite.db.SupportSQLiteDatabase
import com.forcusflow.lifestream.data.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class MigrationTest {

    @Test
    fun testMigration9To10ExecutesExpectedSql() {
        val executedSqls = mutableListOf<String>()

        val invocationHandler = java.lang.reflect.InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSqls.add(args[0] as String)
            }
            null
        }

        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            invocationHandler
        ) as SupportSQLiteDatabase

        // Execute migration 9 -> 10
        AppDatabase.MIGRATION_9_10.migrate(fakeDb)

        assertTrue(
            "Should alter templates table to add postponedUntilDate",
            executedSqls.any { it.contains("ALTER TABLE templates ADD COLUMN postponedUntilDate TEXT DEFAULT NULL") }
        )
        assertTrue(
            "Should alter templates table to add lastPostponedAt",
            executedSqls.any { it.contains("ALTER TABLE templates ADD COLUMN lastPostponedAt INTEGER DEFAULT NULL") }
        )
        assertTrue(
            "Should fix legacy timeline_items where createdAt = 0",
            executedSqls.any { it.contains("UPDATE timeline_items SET createdAt = COALESCE(completedAt, scheduledAt, 1) WHERE createdAt = 0") }
        )
    }

    @Test
    fun testMigration10To11ExecutesExpectedSql() {
        val executedSqls = mutableListOf<String>()

        val invocationHandler = java.lang.reflect.InvocationHandler { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSqls.add(args[0] as String)
            }
            null
        }

        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            invocationHandler
        ) as SupportSQLiteDatabase

        // Execute migration 10 -> 11
        AppDatabase.MIGRATION_10_11.migrate(fakeDb)

        assertTrue(
            "Should alter timeline_items table to add showOnTimeline",
            executedSqls.any { it.contains("ALTER TABLE timeline_items ADD COLUMN showOnTimeline INTEGER NOT NULL DEFAULT 0") }
        )
    }
}
