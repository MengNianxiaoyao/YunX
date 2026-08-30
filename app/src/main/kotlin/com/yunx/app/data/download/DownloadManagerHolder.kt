package com.yunx.app.data.download

import android.content.Context
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.prefs.SettingsRepository

object DownloadManagerHolder {
    @Volatile private var dependencies: Dependencies? = null

    fun get(context: Context): DownloadManager = getDependencies(context).downloadManager

    fun getDependencies(context: Context): Dependencies {
        dependencies?.let { return it }
        synchronized(this) {
            dependencies?.let { return it }
            val appContext = context.applicationContext
            val db = AppDatabase.get(appContext)
            val settings = SettingsRepository(appContext)
            val quarkApi = QuarkApi()
            return Dependencies(
                db = db,
                settings = settings,
                quarkApi = quarkApi,
                ucApi = UCApi(),
                xunleiApi = XunleiApi(),
                baiduApi = BaiduApi(),
                c139Api = C139Api(),
                pan123Api = Pan123Api(),
                downloadManager = DownloadManager(
                    context = appContext,
                    dao = db.downloadTaskDao(),
                    cleanupDao = db.downloadCleanupDao(),
                    cleanupHandler = { cleanup ->
                        when (cleanup.platform) {
                            SharePlatform.QUARK.name -> quarkApi.deleteFile(cleanup.resourceId, cleanup.credential) != null
                            else -> false
                        }
                    },
                    downloader = ChunkDownloader { HttpClients.downloadClient() },
                    threadProvider = settings::downloadThreadsFor,
                    saveDirProvider = { settings.downloadDirUri },
                    concurrencyProvider = { settings.maxConcurrentDownloads },
                    speedLimitProvider = { settings.downloadSpeedLimit },
                    retryCountProvider = { settings.downloadRetryCount },
                    keepWhenLockedProvider = { settings.keepDownloadWhenLocked },
                    showSpeedProvider = { settings.notificationShowSpeed }
                )
            ).also { dependencies = it }
        }
    }

    data class Dependencies(
        val db: AppDatabase,
        val settings: SettingsRepository,
        val quarkApi: QuarkApi,
        val ucApi: UCApi,
        val xunleiApi: XunleiApi,
        val baiduApi: BaiduApi,
        val c139Api: C139Api,
        val pan123Api: Pan123Api,
        val downloadManager: DownloadManager
    )
}
