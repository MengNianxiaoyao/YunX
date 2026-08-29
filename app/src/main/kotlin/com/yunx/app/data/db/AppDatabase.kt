package com.yunx.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yunx.app.data.security.AndroidKeystoreCredentialCipher
import com.yunx.app.data.security.CredentialCipher

@Database(
    entities = [QuarkAccountEntity::class, DownloadTaskEntity::class, DownloadCleanupEntity::class, UCAccountEntity::class, XunleiAccountEntity::class, BaiduAccountEntity::class, C139AccountEntity::class, Pan123AccountEntity::class],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rawQuarkAccountDao(): QuarkAccountDao

    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun downloadCleanupDao(): DownloadCleanupDao

    abstract fun rawUcAccountDao(): UCAccountDao

    abstract fun rawXunleiAccountDao(): XunleiAccountDao

    abstract fun rawBaiduAccountDao(): BaiduAccountDao

    abstract fun rawC139AccountDao(): C139AccountDao

    abstract fun rawPan123AccountDao(): Pan123AccountDao

    private lateinit var credentialCipher: CredentialCipher

    fun quarkAccountDao(): QuarkAccountDao = SecureAccountDaos.quark(rawQuarkAccountDao(), credentialCipher)
    fun ucAccountDao(): UCAccountDao = SecureAccountDaos.uc(rawUcAccountDao(), credentialCipher)
    fun xunleiAccountDao(): XunleiAccountDao = SecureAccountDaos.xunlei(rawXunleiAccountDao(), credentialCipher)
    fun baiduAccountDao(): BaiduAccountDao = SecureAccountDaos.baidu(rawBaiduAccountDao(), credentialCipher)
    fun c139AccountDao(): C139AccountDao = SecureAccountDaos.c139(rawC139AccountDao(), credentialCipher)
    fun pan123AccountDao(): Pan123AccountDao = SecureAccountDaos.pan123(rawPan123AccountDao(), credentialCipher)

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yunx.db"
                )
                    .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    // 早期开发版（1-8）无可靠 schema；从 v9 起必须保留凭证和下载任务
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8)
                    .build()
                    .also { database ->
                        database.credentialCipher = AndroidKeystoreCredentialCipher()
                        instance = database
                    }
            }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_task ADD COLUMN requestHeadersJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE download_task ADD COLUMN chunkCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_task ADD COLUMN plannedTotalSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_task ADD COLUMN cleanupId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_task ADD COLUMN expectedSha256 TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v12：6 个账号表统一加登录失效标记 invalidAt（0 = 正常；P1-4 失效检测） */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quark_account ADD COLUMN invalidAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE uc_account ADD COLUMN invalidAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE xunlei_account ADD COLUMN invalidAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE baidu_account ADD COLUMN invalidAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE c139_account ADD COLUMN invalidAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pan123_account ADD COLUMN invalidAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v13：删除从未接线的 cleanupId 死列（P2-6；云端临时目录清扫改为启动时按 tr_ 前缀识别，不依赖该列） */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite 删列需按新 schema 重建表（列定义与 DownloadTaskEntity 逐一对齐，除 cleanupId 外）
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `_new_download_task` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`totalSize` INTEGER NOT NULL, " +
                        "`downloadedSize` INTEGER NOT NULL, " +
                        "`status` INTEGER NOT NULL, " +
                        "`errorMsg` TEXT NOT NULL, " +
                        "`savePath` TEXT NOT NULL, " +
                        "`requestHeadersJson` TEXT NOT NULL DEFAULT '{}', " +
                        "`chunkCount` INTEGER NOT NULL DEFAULT 0, " +
                        "`plannedTotalSize` INTEGER NOT NULL DEFAULT 0, " +
                        "`expectedSha256` TEXT NOT NULL DEFAULT '', " +
                        "`createTime` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `_new_download_task` (`id`, `url`, `fileName`, `totalSize`, `downloadedSize`, `status`, " +
                        "`errorMsg`, `savePath`, `requestHeadersJson`, `chunkCount`, `plannedTotalSize`, `expectedSha256`, `createTime`) " +
                        "SELECT `id`, `url`, `fileName`, `totalSize`, `downloadedSize`, `status`, " +
                        "`errorMsg`, `savePath`, `requestHeadersJson`, `chunkCount`, `plannedTotalSize`, `expectedSha256`, `createTime` FROM `download_task`"
                )
                db.execSQL("DROP TABLE `download_task`")
                db.execSQL("ALTER TABLE `_new_download_task` RENAME TO `download_task`")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `download_cleanup` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `platform` TEXT NOT NULL, `resourceId` TEXT NOT NULL, `credential` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }
    }
}
