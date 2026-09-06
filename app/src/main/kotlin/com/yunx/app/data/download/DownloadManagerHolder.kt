package com.yunx.app.data.download

import android.content.Context
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.network.BaiduApi
import com.yunx.app.data.network.C139Api
import com.yunx.app.data.network.HttpClients
import com.yunx.app.data.network.Pan123Api
import com.yunx.app.data.network.QuarkApi
import com.yunx.app.data.network.model.CloudCredential
import com.yunx.app.data.network.UCApi
import com.yunx.app.data.network.XunleiApi
import com.yunx.app.data.network.SharePlatform
import com.yunx.app.data.prefs.SettingsRepository
import com.yunx.app.data.backup.AuthBackupManager
import com.yunx.app.data.network.adapters.BaiduFileSource
import com.yunx.app.data.network.adapters.C139FileSource
import com.yunx.app.data.network.adapters.Pan123FileSource
import com.yunx.app.data.network.adapters.QuarkFileSource
import com.yunx.app.data.network.adapters.UCFileSource
import com.yunx.app.data.network.adapters.XunleiFileSource
import com.yunx.app.data.repository.BaiduAccountRepository
import com.yunx.app.data.repository.BaiduResolveRepository
import com.yunx.app.data.repository.BookmarkRepository
import com.yunx.app.data.repository.C139AccountRepository
import com.yunx.app.data.repository.C139ResolveRepository
import com.yunx.app.data.repository.Pan123AccountRepository
import com.yunx.app.data.repository.Pan123ResolveRepository
import com.yunx.app.data.repository.QuarkAccountRepository
import com.yunx.app.data.repository.QuarkResolveRepository
import com.yunx.app.data.repository.UCAccountRepository
import com.yunx.app.data.repository.UCResolveRepository
import com.yunx.app.data.repository.XunleiAccountRepository
import com.yunx.app.data.repository.XunleiResolveRepository

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
            val cleanupQuarkApi = QuarkApi { HttpClients.cleanupClient() }
            val ucApi = UCApi()
            val xunleiApi = XunleiApi()
            val baiduApi = BaiduApi()
            val c139Api = C139Api()
            val pan123Api = Pan123Api()
            return Dependencies(
                db = db,
                settings = settings,
                quarkApi = quarkApi,
                cleanupQuarkApi = cleanupQuarkApi,
                ucApi = ucApi,
                xunleiApi = xunleiApi,
                baiduApi = baiduApi,
                c139Api = c139Api,
                pan123Api = pan123Api,
                downloadManager = DownloadManager(
                    context = appContext,
                    dao = db.downloadTaskDao(),
                    cleanupDao = db.downloadCleanupDao(),
                    cleanupHandler = { cleanup ->
                        when (cleanup.platform) {
                            SharePlatform.QUARK.name -> cleanupQuarkApi.deleteFile(
                                cleanup.resourceId,
                                CloudCredential.Cookie(cleanup.credential)
                            ) != null
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
                ),
                quarkRepository = QuarkAccountRepository(db.quarkAccountDao(), quarkApi),
                ucRepository = UCAccountRepository(db.ucAccountDao(), ucApi),
                xunleiRepository = XunleiAccountRepository(db.xunleiAccountDao(), xunleiApi),
                baiduRepository = BaiduAccountRepository(db.baiduAccountDao(), baiduApi),
                c139Repository = C139AccountRepository(db.c139AccountDao()),
                pan123Repository = Pan123AccountRepository(db.pan123AccountDao(), pan123Api),
                bookmarkRepository = BookmarkRepository(db.bookmarkDao()),
                backupManager = AuthBackupManager(
                    db.quarkAccountDao(), db.ucAccountDao(), db.xunleiAccountDao(),
                    db.baiduAccountDao(), db.c139AccountDao(), db.pan123AccountDao()
                )
            ).also { dependencies = it }
        }
    }

    data class Dependencies(
        val db: AppDatabase,
        val settings: SettingsRepository,
        val quarkApi: QuarkApi,
        val cleanupQuarkApi: QuarkApi,
        val ucApi: UCApi,
        val xunleiApi: XunleiApi,
        val baiduApi: BaiduApi,
        val c139Api: C139Api,
        val pan123Api: Pan123Api,
        val downloadManager: DownloadManager,
        val quarkRepository: QuarkAccountRepository,
        val ucRepository: UCAccountRepository,
        val xunleiRepository: XunleiAccountRepository,
        val baiduRepository: BaiduAccountRepository,
        val c139Repository: C139AccountRepository,
        val pan123Repository: Pan123AccountRepository,
        val bookmarkRepository: BookmarkRepository,
        val backupManager: AuthBackupManager
    ) {
        val quarkFileSource: QuarkFileSource by lazy {
            QuarkFileSource(quarkApi) { quarkRepository.getFreshCookie()?.let(CloudCredential::Cookie) }
        }
        val ucFileSource: UCFileSource by lazy {
            UCFileSource(ucApi) { ucRepository.getFreshCookie()?.let(CloudCredential::Cookie) }
        }
        val pan123FileSource: Pan123FileSource by lazy {
            Pan123FileSource(pan123Api) { pan123Repository.getAccount()?.accessToken?.let(CloudCredential::AccessToken) }
        }
        val baiduFileSource: BaiduFileSource by lazy {
            BaiduFileSource(baiduApi) { baiduRepository.getAccount()?.cookie?.let(CloudCredential::Cookie) }
        }
        val xunleiFileSource: XunleiFileSource by lazy {
            XunleiFileSource(xunleiApi) {
                xunleiRepository.getAccount()?.let {
                    CloudCredential.Xunlei(it.accessToken, it.deviceId, it.captchaToken)
                }
            }
        }
        val c139FileSource: C139FileSource by lazy {
            C139FileSource(c139Api) { c139Repository.getAccount()?.cookie?.let(CloudCredential::Cookie) }
        }

        val xunleiResolveRepository: XunleiResolveRepository by lazy {
            XunleiResolveRepository(
                api = xunleiApi,
                credentialProvider = {
                    xunleiRepository.getAccount()?.let {
                        CloudCredential.Xunlei(it.accessToken, it.deviceId, it.captchaToken)
                    }
                },
                refreshProvider = {
                    val account = xunleiRepository.getAccount()
                    if (account == null || account.refreshToken.isBlank()) null
                    else xunleiApi.refreshToken(account.refreshToken, account.deviceId)?.also { (token, refresh) ->
                        xunleiRepository.updateTokens(token, refresh)
                    }
                },
                onAuthExpired = { xunleiRepository.markExpired() }
            )
        }
        val baiduResolveRepository: BaiduResolveRepository by lazy { BaiduResolveRepository(baiduApi) }
        val c139ResolveRepository: C139ResolveRepository by lazy { C139ResolveRepository(c139Api) }
        val pan123ResolveRepository: Pan123ResolveRepository by lazy {
            Pan123ResolveRepository(pan123Api) {
                pan123Repository.getAccount()?.accessToken?.let(CloudCredential::AccessToken)
            }
        }
        val quarkResolveRepository: QuarkResolveRepository by lazy { QuarkResolveRepository(quarkApi) }
        val ucResolveRepository: UCResolveRepository by lazy { UCResolveRepository(ucApi) }
    }
}
