package com.yunx.app.data.db

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun migratesV13ToV14AndPreservesDownloadTasks() {
        val name = "migration-test-${System.currentTimeMillis()}"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE download_task (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "url TEXT NOT NULL, fileName TEXT NOT NULL, totalSize INTEGER NOT NULL, " +
                                "downloadedSize INTEGER NOT NULL, status INTEGER NOT NULL, errorMsg TEXT NOT NULL, " +
                                "savePath TEXT NOT NULL, requestHeadersJson TEXT NOT NULL DEFAULT '{}', " +
                                "chunkCount INTEGER NOT NULL DEFAULT 0, plannedTotalSize INTEGER NOT NULL DEFAULT 0, " +
                                "expectedSha256 TEXT NOT NULL DEFAULT '', createTime INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO download_task " +
                    "(url, fileName, totalSize, downloadedSize, status, errorMsg, savePath, " +
                    "requestHeadersJson, chunkCount, plannedTotalSize, expectedSha256, createTime) " +
                    "VALUES ('https://example.test/file', 'file.bin', 10, 4, 2, '', '', '{}', 1, 10, '', 1)"
            )
            AppDatabase.MIGRATION_13_14.migrate(db)

            db.query("SELECT COUNT(*) FROM download_task").use { cursor ->
                assertEquals(1, if (cursor.moveToFirst()) cursor.getInt(0) else 0)
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'download_cleanup'"
            ).use { cursor ->
                assertNotNull(cursor.apply { moveToFirst() }.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migratesV14ToV15AndAddsBookmarksAndDownloadMetadata() {
        val name = "migration-test-${System.currentTimeMillis()}"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE download_task (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "url TEXT NOT NULL, fileName TEXT NOT NULL, totalSize INTEGER NOT NULL, " +
                                "downloadedSize INTEGER NOT NULL, status INTEGER NOT NULL, errorMsg TEXT NOT NULL, " +
                                "savePath TEXT NOT NULL, requestHeadersJson TEXT NOT NULL DEFAULT '{}', " +
                                "chunkCount INTEGER NOT NULL DEFAULT 0, plannedTotalSize INTEGER NOT NULL DEFAULT 0, " +
                                "expectedSha256 TEXT NOT NULL DEFAULT '', createTime INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO download_task " +
                    "(url, fileName, totalSize, downloadedSize, status, errorMsg, savePath, " +
                    "requestHeadersJson, chunkCount, plannedTotalSize, expectedSha256, createTime) " +
                    "VALUES ('https://example.test/file', 'file.bin', 10, 4, 2, '', '', '{}', 1, 10, '', 1)"
            )
            AppDatabase.MIGRATION_14_15.migrate(db)

            db.query("SELECT platform, avgSpeed FROM download_task").use { cursor ->
                cursor.moveToFirst()
                assertEquals("", cursor.getString(0))
                assertEquals(0L, cursor.getLong(1))
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'bookmark'"
            ).use { cursor ->
                assertNotNull(cursor.apply { moveToFirst() }.getString(0))
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migratesV15ToV16AndPreservesDownloadTask() {
        val name = "migration-test-${System.currentTimeMillis()}"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE download_task (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "url TEXT NOT NULL, fileName TEXT NOT NULL, totalSize INTEGER NOT NULL, " +
                                "downloadedSize INTEGER NOT NULL, status INTEGER NOT NULL, errorMsg TEXT NOT NULL, " +
                                "savePath TEXT NOT NULL, requestHeadersJson TEXT NOT NULL DEFAULT '{}', " +
                                "chunkCount INTEGER NOT NULL DEFAULT 0, plannedTotalSize INTEGER NOT NULL DEFAULT 0, " +
                                "expectedSha256 TEXT NOT NULL DEFAULT '', platform TEXT NOT NULL DEFAULT '', " +
                                "avgSpeed INTEGER NOT NULL DEFAULT 0, createTime INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        try {
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO download_task " +
                    "(url, fileName, totalSize, downloadedSize, status, errorMsg, savePath, " +
                    "requestHeadersJson, chunkCount, plannedTotalSize, expectedSha256, platform, avgSpeed, createTime) " +
                    "VALUES ('https://example.test/file', 'file.bin', 10, 4, 2, '', '', '{}', 1, 10, '', 'uc', 123, 1)"
            )

            AppDatabase.MIGRATION_15_16.migrate(db)

            db.query("SELECT downloadedSize, status, platform, avgSpeed, operationId FROM download_task").use { cursor ->
                cursor.moveToFirst()
                assertEquals(4L, cursor.getLong(0))
                assertEquals(2, cursor.getInt(1))
                assertEquals("uc", cursor.getString(2))
                assertEquals(123L, cursor.getLong(3))
                assertEquals("", cursor.getString(4))
            }
            db.query("PRAGMA table_info(download_task)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                val notNullIndex = cursor.getColumnIndex("notnull")
                val defaultIndex = cursor.getColumnIndex("dflt_value")
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "operationId") {
                        found = true
                        assertEquals(1, cursor.getInt(notNullIndex))
                        assertEquals("''", cursor.getString(defaultIndex))
                    }
                }
                assertEquals(true, found)
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
