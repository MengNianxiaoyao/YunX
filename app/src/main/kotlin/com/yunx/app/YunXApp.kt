package com.yunx.app

import android.app.Application
import com.yunx.app.crash.CrashHandler
import com.yunx.app.data.db.AppDatabase
import com.yunx.app.data.download.DownloadManagerHolder
import com.yunx.app.data.network.QuarkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YunXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            AppDatabase.get(this@YunXApp).downloadTaskDao().markInterruptedAsPaused()
            DownloadManagerHolder.get(this@YunXApp).retryPendingCleanups()
        }
        // 夸克云端「YunX临时转存」清扫：tr_* 唯一子目录在正常流程由下载完成回调删除
        // （见 QuarkResolveRepository.getShareDownloadLink 的延迟删除设计），进程被杀则永久残留。
        // 启动时（无任何活动下载）一次性清理；失败静默，下次启动自然重试。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { sweepQuarkTempSubdirs() }
        }
        // 迅雷动态设备指纹：首次启动生成并持久化（开源分发后每台设备独立指纹）
        com.yunx.app.data.network.XunleiDeviceFingerprint.init(this)
        // 123 设备标识持久化（loginuuid 跨进程稳定，不再每次启动更换）
        com.yunx.app.data.network.Pan123DeviceId.init(this)
        // 139 动态设备指纹（X-Deviceinfo/x-yun-client-info 的 hex 每设备独立，替代共享抓包常量）
        com.yunx.app.data.network.C139DeviceFingerprint.init(this)
    }

    /** 删除夸克「YunX临时转存」下所有 tr_* 遗留子目录（TEMP_SUBDIR_PREFIX 识别，不触碰目录外文件） */
    private suspend fun sweepQuarkTempSubdirs() {
        val deps = DownloadManagerHolder.getDependencies(this)
        val cookie = deps.db.quarkAccountDao().getAccount()?.cookie ?: return
        if (!QuarkConstants.isValidCookie(cookie)) return
        val rootFiles = deps.quarkApi.getFileList(QuarkConstants.DEFAULT_PDIR_FID, cookie).orEmpty()
        val tempDir = rootFiles.firstOrNull { it.isdir && it.fname == QuarkConstants.TEMP_DIR_NAME } ?: return
        // 先收集全部遗留目录再删除（边翻页边删会使后续页移位漏项）
        val stale = buildList {
            var page = 1
            while (true) {
                val files = deps.quarkApi.listCloudFiles(tempDir.fid, cookie, page).orEmpty()
                addAll(files.filter { it.isdir && it.fname.startsWith(QuarkConstants.TEMP_SUBDIR_PREFIX) })
                if (files.size < 50) break
                page++
            }
        }
        // 与既有 cleanupTempDir 一致：fire-and-forget，失败不阻断
        stale.forEach { deps.quarkApi.deleteFile(it.fid, cookie) }
    }
}
