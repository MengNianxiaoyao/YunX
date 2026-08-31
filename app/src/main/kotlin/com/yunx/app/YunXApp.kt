package com.yunx.app

import android.app.Application
import com.yunx.app.crash.CrashHandler
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.download.DownloadManagerHolder
import com.yunx.app.data.download.StartupCleanupPolicy
import com.yunx.app.data.network.QuarkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class YunXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        // 设备标识同步且幂等，必须在任何平台 API 可能被后台构造前完成。
        com.yunx.app.data.network.XunleiDeviceFingerprint.init(this)
        com.yunx.app.data.network.Pan123DeviceId.init(this)
        com.yunx.app.data.network.C139DeviceFingerprint.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            AppDatabase.get(this@YunXApp).downloadTaskDao().markInterruptedAsPaused()
            // 优先消费持久化记录，再做兜底扫描，避免两个清理器并发删除同一目录。
            try {
                withTimeoutOrNull(StartupCleanupPolicy.CLEANUP_TIMEOUT_MILLIS) {
                    DownloadManagerHolder.get(this@YunXApp).retryPendingCleanups()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 失败记录保留，下次启动继续重试。
            }
            try {
                withTimeoutOrNull(StartupCleanupPolicy.CLEANUP_TIMEOUT_MILLIS) {
                    sweepQuarkTempSubdirs()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // 兜底扫描失败不影响应用启动。
            }
        }
    }

    /** 删除夸克「YunX临时转存」下所有 tr_* 遗留子目录（TEMP_SUBDIR_PREFIX 识别，不触碰目录外文件） */
    private suspend fun sweepQuarkTempSubdirs() {
        val deps = DownloadManagerHolder.getDependencies(this)
        val cookie = deps.db.quarkAccountDao().getAccount()?.cookie ?: return
        if (!QuarkConstants.isValidCookie(cookie)) return
        val rootFiles = deps.cleanupQuarkApi.getFileList(QuarkConstants.DEFAULT_PDIR_FID, cookie).orEmpty()
        val tempDir = rootFiles.firstOrNull { it.isdir && it.fname == QuarkConstants.TEMP_DIR_NAME } ?: return
        // 在单次上限内先收集再删除，避免边翻页边删使后续页移位漏项。
        val stale = buildList {
            var page = 1
            while (StartupCleanupPolicy.shouldScanNextPage(page, size)) {
                val files = deps.cleanupQuarkApi.listCloudFiles(tempDir.fid, cookie, page).orEmpty()
                addAll(
                    files.filter { it.isdir && it.fname.startsWith(QuarkConstants.TEMP_SUBDIR_PREFIX) }
                        .take(StartupCleanupPolicy.MAX_SWEEP_DIRECTORIES - size)
                )
                if (files.size < 50) break
                page++
            }
        }
        // 与既有 cleanupTempDir 一致：fire-and-forget，失败不阻断
        stale.forEach { deps.cleanupQuarkApi.deleteFile(it.fid, cookie) }
    }
}
