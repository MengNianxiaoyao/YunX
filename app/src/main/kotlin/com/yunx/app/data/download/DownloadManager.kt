package com.yunx.app.data.download

import android.content.Context
import android.util.Log
import com.yunx.app.R
import com.yunx.app.util.LogRedactor
import com.yunx.app.data.db.DownloadTaskDao
import com.yunx.app.data.db.DownloadTaskEntity
import com.yunx.app.data.db.DownloadCleanupDao
import com.yunx.app.data.db.DownloadCleanupEntity
import com.yunx.app.data.network.model.DownloadCleanup
import com.yunx.app.data.metrics.OperationId
import com.yunx.app.data.metrics.RequestOperationContext
import com.yunx.app.data.metrics.RequestOperationContextHolder
import com.yunx.app.data.metrics.RequestPlatform
import com.yunx.app.data.metrics.RequestStage
import com.yunx.app.data.security.AndroidKeystoreCredentialCipher
import com.yunx.app.data.security.CredentialCipher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/** 实时下载统计（用于 UI 展示速度/剩余时间/线程数） */
data class DownloadStats(
    val speed: Long = 0L,        // 字节/秒
    val remainMillis: Long = -1L, // 剩余时间（毫秒），未知为 -1
    val chunkCount: Int = 1       // 分片（线程）数
)

private const val TAG = "YunX-DL"

/** 单文件 Range 分片的安全并发上限。迅雷等 CDN 对单文件并发 Range 有阈值，
 *  超过约 8 个并发会把多余请求降级为 200 整文件（忽略 Range），
 *  进而触发整任务回退单流、速度暴跌。压在安全上限内，所有分片都能稳定拿到 206。 */
private const val RANGE_WORKERS_CAP = 8

/** 百度直链分片并发上限：直链与账号（BDUSS）绑定，对 baidupcs 的满并发 Range 请求
 *  是账号维度的滥用信号；参照迅雷压在安全上限内（P1-6 平台级节流） */
private const val BAIDU_WORKERS_CAP = 8

/** 错峰建连上限（序号）：第 i 个分片首次请求前延迟 (min(i, STAGGER_CAP) * STAGGER_MS) */
private const val STAGGER_CAP = 8
private const val STAGGER_MS = 25L

/** RANGE_IGNORED 容忍次数：CDN 偶发 200（限流中间态）前 N 次不触发整任务回退，继续领新片；超过才回退单流 */
private const val RANGE_IGNORED_TOLERANCE = 3

/**
 * 下载任务管理器：
 * - 任务持久化（Room），状态流转 PENDING → DOWNLOADING → COMPLETED / PAUSED / FAILED；
 * - 分片多线程下载（每片一个协程，信号量限并发）；
 * - 断点续传：part 文件保留，暂停/重启后从已有大小继续；
 * - 完成后合并分片并保存到公共 Download 目录。
 */
class DownloadManager(
    private val context: Context,
    private val dao: DownloadTaskDao,
    private val cleanupDao: DownloadCleanupDao,
    private val cleanupHandler: suspend (DownloadCleanup) -> Boolean = { false },
    private val downloader: ChunkDownloader,
    /** 下载线程数提供者（按平台，可在设置中修改，动态生效），默认 16。 */
    private val threadProvider: (String) -> Int = { 16 },
    /** 自定义下载保存目录提供者（SAF tree Uri，可空）；null 时保存到系统默认 Download */
    private val saveDirProvider: () -> String? = { null },
    /** 最大同时下载任务数提供者（默认 3）：限制后台并发任务，避免占满带宽/耗尽路由器连接 */
    private val concurrencyProvider: () -> Int = { 1 },
    /** 全局下载速度限制提供者（字节/秒；0 = 不限速） */
    private val speedLimitProvider: () -> Long = { 0L },
    /** 下载失败后自动重试次数提供者（默认 3，上限 10） */
    private val retryCountProvider: () -> Int = { 3 },
    /** 锁屏后保持下载开关（开启时获取 WakeLock 维持 Wi-Fi/CPU） */
    private val keepWhenLockedProvider: () -> Boolean = { true },
    /** 通知栏显示下载速度开关（false 时仅显示通知，隐藏速度） */
    private val showSpeedProvider: () -> Boolean = { true }
) {
    private val credentialCipher: CredentialCipher = AndroidKeystoreCredentialCipher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 当前实际下载中的任务数（用于最大同时下载任务数限制） */
    private val activeDownloads = java.util.concurrent.atomic.AtomicInteger(0)

    /** 全局限速器（令牌桶）：所有任务合计不超过 speedLimitProvider 的字节/秒 */
    private val speedLimiter = SpeedLimiter()

    /**
     * 保存前存储权限检查（Android 9- 写公共 Download 需 WRITE_EXTERNAL_STORAGE 运行时授权）。
     * UI 层注入：无权限时动态申请并等待授权结果；已授权/Android 10+ 直接返回 true。
     * 授权后会自动继续保存（同一协程 await 授权结果再往下走）。
     */
    var storagePermissionProvider: suspend () -> Boolean = { true }

    /**
     * 运行中的任务 Job：value 为 CompletableDeferred，注册/移除全程由 jobsLock 保护，
     * 保证 start/pause/remove 之间无 TOCTOU 竞态（防止"暂停/删除瞬间任务继续跑"）。
     */
    private data class ActiveRun(
        val job: CompletableDeferred<Job> = CompletableDeferred(),
        val stopReason: AtomicReference<DownloadStopReason?> = AtomicReference(null),
        val terminalLogged: AtomicBoolean = AtomicBoolean(false)
    )

    private val activeJobs = ConcurrentHashMap<Long, ActiveRun>()
    private val pausingIds = mutableSetOf<Long>()
    private val removingIds = mutableSetOf<Long>()
    private val jobsLock = Any()

    /** 前台服务计数：有任务在下载时保持前台（避免切后台限速/进程被杀） */
    private val activeTaskCount = java.util.concurrent.atomic.AtomicInteger(0)

    /** 前台通知进度节流（毫秒）：2 秒更新一次，避免频繁刷新系统通知 */
    private val notifyThrottleMs = 2000L
    private val lastNotifyTs = AtomicLong(0)

    /** 更新前台通知进度（2 秒节流；total<=0 时不确定进度，只更新标题；可显示下载速度） */
    private fun notifyProgress(id: Long, fileName: String, new: Long, total: Long) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyTs.get() >= notifyThrottleMs) {
            lastNotifyTs.set(now)
            val percent = if (total > 0) ((new * 100 / total).toInt().coerceIn(0, 100)) else -1
            val speed = _stats.value[id]?.speed ?: 0L
            val speedText = if (speed > 0) formatSpeed(speed) else ""
            DownloadService.update(context, fileName, percent, speedText, showSpeedProvider())
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return ""
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSec.toDouble()
        var i = 0
        while (value >= 1024 && i < units.size - 1) {
            value /= 1024
            i++
        }
        return context.getString(R.string.download_speed_format, value, units[i])
    }

    /**
     * 进度落盘节流：多 worker 并发回调下，每 progressPersistIntervalMs 最多写一次 DB。
     * - force / (total>0 且 new>=total)：完成时强制写，确保最终进度准确；
     * - total<=0（大小未知）时仅按时间节流；
     * - 用 lastAt 的 CAS 保证并发下同一任务只有一个回调写库（避免多线程重复 UPDATE）。
     */
    private suspend fun persistProgressIfDue(
        id: Long,
        new: Long,
        total: Long,
        force: Boolean,
        lastAt: AtomicLong
    ) {
        val now = System.currentTimeMillis()
        val last = lastAt.get()
        if (force || (total > 0 && new >= total) || now - last >= progressPersistIntervalMs) {
            if (lastAt.compareAndSet(last, now)) {
                dao.updateProgressIfStatus(
                    id, DownloadTaskEntity.STATUS_DOWNLOADING, new, total,
                    DownloadTaskEntity.STATUS_DOWNLOADING
                )
            }
        }
    }

    /** 每个任务一把互斥锁：暂停后立即恢复时避免新旧协程并发写分片 */
    private val taskLocks = ConcurrentHashMap<Long, Mutex>()

    /** 任务请求头（Cookie/UA），暂停后恢复仍需使用 */
    private val taskHeaders = ConcurrentHashMap<Long, Map<String, String>>()

    /** 已知文件大小（API 返回，避免探测失败）；-1 表示未知 */
    private val taskSizes = ConcurrentHashMap<Long, Long>()

    /** 任务开始时间（毫秒）：完成时计算平均速度用（暂停/恢复会重置，表示最近一次运行段均值） */
    private val taskStartTimes = ConcurrentHashMap<Long, Long>()

    /** 任务下载完成后的清理回调（如删除网盘临时转存文件；下载成功后才触发） */
    private val taskCallbacks = ConcurrentHashMap<Long, suspend () -> Unit>()

    /** 实时下载统计（速度/剩余时间/线程数） */
    private val _stats = MutableStateFlow<Map<Long, DownloadStats>>(emptyMap())
    val stats: StateFlow<Map<Long, DownloadStats>> = _stats.asStateFlow()

    /** 进度落盘节流（毫秒）：updateProgress 写库会触发全表 Flow 重发 → 主线程全列表重组；
     *  按字节（256KB）节流时高速下载每秒写库几十次，主线程重组洪峰 → ANR。
     *  改为按时间节流落盘，UI 进度由内存 _stats 高频展示、DB 低频持久化（断点续传最多丢几百 ms 进度）。 */
    private val progressPersistIntervalMs = 500L

    val tasks: Flow<List<DownloadTaskEntity>> = dao.observeAll()

    /** 入队并立即开始下载 */
    suspend fun enqueue(
        url: String,
        fileName: String,
        headers: Map<String, String> = emptyMap(),
        /** 已知文件大小（字节）；-1 表示未知，需探测 */
        size: Long = -1L,
        expectedSha256: String = "",
        cleanup: com.yunx.app.data.network.model.DownloadCleanup? = null,
        /** 下载来源平台标识（按平台应用下载线程数设置）；通用/手动添加传空串 */
        platform: String = "",
        /** 下载成功完成后的清理回调（如删除网盘临时转存文件）；失败/取消不触发 */
        onComplete: suspend () -> Unit = {}
    ): Long {
        // 文件名兜底：空白时从 URL 推导，避免保存时变成时间戳
        val safeName = fileName.ifBlank {
            url.substringAfterLast('/').substringBefore('?')
                .ifBlank { "download_${System.currentTimeMillis()}" }
        }
        Log.d(TAG, "enqueue: platform=${DownloadTaskMetric.safePlatform(platform)} size=$size")
        val operationId = OperationId.download()
        val id = dao.insert(
            DownloadTaskEntity(
                url = url,
                fileName = safeName,
                requestHeadersJson = encodeHeaders(headers),
                expectedSha256 = expectedSha256.lowercase(),
                platform = platform,
                operationId = operationId
            )
        )
        cleanup?.let {
            cleanupDao.insert(
                DownloadCleanupEntity(
                    taskId = id,
                    platform = it.platform,
                    resourceId = it.resourceId,
                    credential = credentialCipher.encrypt(it.credential, "download.cleanupCredential")
                )
            )
        }
        // 保存请求头（Cookie/UA），暂停后恢复仍需携带
        if (headers.isNotEmpty()) taskHeaders[id] = headers
        if (size > 0) taskSizes[id] = size
        taskCallbacks[id] = onComplete
        start(id, headers)
        return id
    }

    /**
     * 重新下载：用原直链新建任务（任务卡长按菜单「重新下载」）。
     * 先做 Range 探测校验直链有效性：403/404/网络错误视为直链已过期，返回 false 由 UI 提示。
     */
    suspend fun redownload(id: Long): Boolean {
        val task = dao.get(id) ?: return false
        val headers = loadPersistedHeaders(id)
        val valid = runCatching { downloader.getTotalSize(task.url, headers) != null }.getOrDefault(false)
        if (!valid) return false
        enqueue(
            url = task.url,
            fileName = task.fileName,
            headers = headers,
            size = task.totalSize,
            expectedSha256 = task.expectedSha256,
            platform = task.platform
        )
        return true
    }

    /** 开始/恢复下载（断点续传）。
     *  getCompleted() 属 ExperimentalCoroutinesApi（读取已完成 Deferred 的值，锁内已确保完成） */
    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start(id: Long, headers: Map<String, String> = emptyMap()) {
        // 恢复时未传 headers：沿用入队时保存的（Cookie/UA 对直链下载是必需的）
        val effectiveHeaders = headers.ifEmpty { taskHeaders[id] ?: emptyMap() }
        Log.d(TAG, "start: id=$id headerCount=${effectiveHeaders.size}")
        synchronized(jobsLock) {
            if (id in pausingIds || id in removingIds) return
            // 原子注册：检查 + 占位 + launch + complete 在同一锁内完成，
            // pause/remove 要么拿到已注册的 job，要么拿不到（视为未运行）
            val existing = activeJobs[id]
            if (existing != null) {
                // job 仍活跃（正在下载/收尾）：忽略本次 start，避免重复启动
                if (!existing.job.isCompleted || existing.job.getCompleted().isActive ||
                    existing.stopReason.get() != null
                ) return
                // job 已结束但 finally 尚未清理（暂停后立即恢复的残留）：
                // 移除旧引用，继续注册新 job，保证"点开始"立即生效
                activeJobs.remove(id)
            }
            val run = ActiveRun()
            activeJobs[id] = run
            val job = scope.launch {
                try {
                    // 任务开始：有任务在下载时保持前台服务（避免切后台限速/进程被杀）
                    onTaskStarted(id)
                    // 任务级互斥：同一任务串行执行，暂停后立刻恢复不会并发写分片
                    taskLocks.getOrPut(id) { Mutex() }.withLock {
                        val restoredHeaders = if (effectiveHeaders.isNotEmpty()) {
                            effectiveHeaders
                        } else {
                            loadPersistedHeaders(id)
                        }
                        if (restoredHeaders.isNotEmpty()) taskHeaders[id] = restoredHeaders
                        runTaskWithRetry(id, restoredHeaders, run)
                    }
                } catch (e: CancellationException) {
                    // 主动暂停/删除：part 文件保留（或由 remove 清理）；状态已由调用方设置
                    _stats.update { it - id }
                } catch (e: Exception) {
                    _stats.update { it - id }
                    // 协程已被取消（暂停/删除）：不标记失败，避免覆盖 PAUSED 状态
                    if (isTaskActive() && run.stopReason.get() == null) {
                        val failure = DownloadFailureClassifier.classify(e)
                        Log.e(TAG, "task $id failed kind=${failure.kind.code}")
                        updateStatus(id, DownloadTaskEntity.STATUS_FAILED)
                        dao.updateError(id, failure.message)
                    } else {
                        Log.w(TAG, "task $id cancelled")
                    }
                } finally {
                    // 任务结束（成功/失败/暂停/删除）：无任务时停止前台服务
                    onTaskFinished()
                    // 只移除自己注册的 deferred：
                    // 若暂停后立即恢复（新 job 已注册到同一 id），不能误删新任务的注册，
                    // 否则新任务将无法再被暂停/删除（后台继续下载）
                    synchronized(jobsLock) {
                        if (run.stopReason.get() == null && activeJobs[id] === run) activeJobs.remove(id)
                    }
                    // 注意：taskLocks 不在此清理 —— 若新任务已 getOrPut 拿到锁，
                    // 旧任务 finally 的 remove 会误删新任务的锁导致并发写分片
                }
            }
            // launch 是同步返回 Job 的，锁内 complete，pause/remove 的 await 立即返回
            run.job.complete(job)
        }
    }

    /** 任务开始/结束计数：控制前台服务生命周期（有任务在下载即保持前台） */
    private suspend fun onTaskStarted(id: Long) {
        if (activeTaskCount.getAndIncrement() == 0) {
            val name = runCatching { dao.get(id)?.fileName }.getOrNull()
                ?: context.getString(R.string.download_notification_task_title)
            DownloadService.start(context, name)
        }
        // 锁屏保持下载：开启时获取 PARTIAL_WAKE_LOCK（息屏维持 CPU/网络）
        acquireWakeLockIfNeeded()
    }

    private fun onTaskFinished() {
        if (activeTaskCount.decrementAndGet() <= 0) {
            activeTaskCount.set(0)
            DownloadService.stop(context)
            releaseWakeLock()
        }
    }

    // ---------- 锁屏保持下载（WakeLock） ----------

    @Volatile
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLockIfNeeded() {
        if (!keepWhenLockedProvider()) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK, "yunx:download"
            ).apply { setReferenceCounted(false) }
        }
        wakeLock?.let { if (!it.isHeld) it.acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    /** 暂停下载（保留 part 文件与请求头） */
    fun pause(id: Long) {
        Log.d(TAG, "pause: id=$id")
        val run = synchronized(jobsLock) {
            pausingIds.add(id)
            activeJobs[id]?.also { it.stopReason.compareAndSet(null, DownloadStopReason.PAUSE) }
        }
        // 立即中断该任务所有分片网络请求（不依赖协程取消传播，阻塞 IO 马上停止）
        downloader.cancelCalls(id)
        _stats.update { it - id }
        scope.launch {
            try {
                // 等协程真正退出（确保没有半截写入）后，以磁盘 part/seg 真实大小为准回写进度。
                run?.let { runCatching { it.job.await().cancelAndJoin() } }
                val real = withContext(Dispatchers.IO) {
                    chunkDirOf(id).listFiles()
                        ?.filter {
                            it.name.startsWith("part_") ||
                                (it.name.startsWith("seg_") && it.name.endsWith(".part"))
                        }
                        ?.sumOf { it.length() } ?: 0L
                }
                val task = dao.get(id)
                if (task != null && DownloadTaskStateMachine.canTransition(
                        task.status,
                        DownloadTaskEntity.STATUS_PAUSED
                    )
                ) {
                    if (real > task.downloadedSize) {
                        dao.updateProgressIfStatus(
                            id, DownloadTaskEntity.STATUS_PAUSED, real, task.totalSize, task.status
                        )
                    } else {
                        dao.updateStatusIfStatus(id, DownloadTaskEntity.STATUS_PAUSED, task.status)
                    }
                }
            } finally {
                synchronized(jobsLock) {
                    if (activeJobs[id] === run) activeJobs.remove(id)
                    pausingIds.remove(id)
                }
            }
        }
    }

    /**
     * 删除任务：取消下载 + 清 DB + 清 part 文件；持久化临时资源清理失败时保留记录供下次启动重试。
     * @param deleteLocal 同时删除已保存到本地的文件（savePath）
     */
    fun remove(id: Long, deleteLocal: Boolean = false) {
        Log.d(TAG, "remove: id=$id deleteLocal=$deleteLocal")
        val run = synchronized(jobsLock) {
            removingIds.add(id)
            activeJobs[id]?.also { it.stopReason.set(DownloadStopReason.REMOVE) }
        }
        // 立即中断该任务所有分片网络请求
        downloader.cancelCalls(id)
        _stats.update { it - id }
        taskHeaders.remove(id)
        // 删除任务同样触发清理回调（如删除网盘临时转存文件）：
        // 用户放弃下载时云盘里已转存的临时文件也应一并清理
        val cleanup = taskCallbacks.remove(id)
        scope.launch {
            try {
                // 若任务正在下载：取消并等待协程真正退出，确保没有后台残留写入。
                if (run != null) run.job.await().cancelAndJoin()
                val task = dao.get(id)
                if (task != null && run?.terminalLogged?.get() != true) {
                    logRemovalCancellation(task, run)
                }
                if (deleteLocal) {
                    task?.savePath?.let {
                        val deleted = withContext(Dispatchers.IO) { DownloadSaver.delete(context, it) }
                        Log.d(TAG, "remove: id=$id localDelete=${if (deleted) "success" else "failed"}")
                    }
                }
                val persistentCleanup = cleanupDao.getByTaskId(id) != null
                cleanupPersisted(id)
                dao.delete(id)
                withContext(Dispatchers.IO) { chunkDirOf(id).deleteRecursively() }
                if (!persistentCleanup) cleanup?.let { runCatching { it() } }
            } finally {
                taskLocks.remove(id)
                synchronized(jobsLock) {
                    if (activeJobs[id] === run) activeJobs.remove(id)
                    removingIds.remove(id)
                }
            }
        }
    }

    // ---------- 内部实现 ----------

    private fun encodeHeaders(headers: Map<String, String>): String {
        val json = JSONObject().apply { headers.forEach { (name, value) -> put(name, value) } }.toString()
        return credentialCipher.encrypt(json, "download.requestHeaders")
    }

    private suspend fun loadPersistedHeaders(id: Long): Map<String, String> {
        val stored = dao.get(id)?.requestHeadersJson.orEmpty()
        if (stored.isBlank()) return emptyMap()
        return runCatching {
            val jsonText = credentialCipher.decrypt(stored, "download.requestHeaders")
            val json = JSONObject(jsonText)
            buildMap {
                json.keys().forEach { name -> put(name, json.getString(name)) }
            }.also {
                if (!credentialCipher.isEncrypted(stored)) {
                    dao.updateRequestHeaders(id, encodeHeaders(it))
                }
            }
        }.getOrElse {
            dao.updateRequestHeaders(id, encodeHeaders(emptyMap()))
            emptyMap()
        }
    }

    private suspend fun updateStatus(id: Long, status: Int) {
        val current = dao.get(id) ?: return
        if (!DownloadTaskStateMachine.canTransition(current.status, status)) return
        dao.updateStatusIfStatus(id, status, current.status)
    }

    private suspend fun updateProgress(id: Long, status: Int, downloadedSize: Long, totalSize: Long) {
        // Progress writes are guarded by the current DB status to prevent a late worker
        // callback from overwriting PAUSED or COMPLETED after a concurrent transition.
        dao.updateProgressIfStatus(
            id, status, downloadedSize, totalSize,
            DownloadTaskEntity.STATUS_DOWNLOADING
        )
    }

    private suspend fun completeTask(id: Long, savePath: String, total: Long = 0L) {
        val current = dao.get(id) ?: return
        if (!DownloadTaskStateMachine.canTransition(current.status, DownloadTaskEntity.STATUS_COMPLETED)) return
        val start = taskStartTimes.remove(id)
        val elapsedMs = start?.let { System.currentTimeMillis() - it } ?: 0L
        val avgSpeed = if (total > 0 && elapsedMs > 0) total * 1000 / elapsedMs else 0L
        dao.complete(
            id = id,
            status = DownloadTaskEntity.STATUS_COMPLETED,
            savePath = savePath,
            avgSpeed = avgSpeed,
            expectedStatus = current.status
        )
    }

    /** 启动时重试进程被杀后遗留的云端清理记录。 */
    suspend fun retryPendingCleanups(
        limit: Int = StartupCleanupPolicy.MAX_PENDING_CLEANUPS
    ) {
        cleanupDao.getOldest(StartupCleanupPolicy.pendingLimit(limit)).forEach { record ->
            val credential = runCatching {
                credentialCipher.decrypt(record.credential, "download.cleanupCredential")
            }.getOrNull() ?: return@forEach
            val cleaned = withTimeoutOrNull(StartupCleanupPolicy.SINGLE_CLEANUP_TIMEOUT_MILLIS) {
                try {
                    cleanupHandler(DownloadCleanup(record.platform, record.resourceId, credential))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
            } ?: false
            if (cleaned) {
                cleanupDao.delete(record.id)
            }
        }
    }

    private suspend fun cleanupPersisted(taskId: Long) {
        val record = cleanupDao.getByTaskId(taskId) ?: return
        val credential = runCatching {
            credentialCipher.decrypt(record.credential, "download.cleanupCredential")
        }.getOrNull() ?: return
        val cleanup = DownloadCleanup(record.platform, record.resourceId, credential)
        if (runCatching { cleanupHandler(cleanup) }.getOrDefault(false)) {
            cleanupDao.delete(record.id)
        }
    }

    /** 当前协程是否仍活跃（暂停/删除触发取消后为 false） */
    private suspend fun isTaskActive(): Boolean = coroutineContext[Job]?.isActive == true

    /** 等待并发许可：当前下载任务数 >= 上限时轮询等待（暂停/取消可退出等待） */
    private suspend fun awaitConcurrencySlot() {
        val max = concurrencyProvider().coerceAtLeast(1)
        while (isTaskActive() && activeDownloads.get() >= max) {
            delay(300)
        }
    }

    /**
     * 执行任务并支持失败自动重试（断点续传，part 文件保留）。
     * 同时负责「最大同时下载任务数」并发许可的获取/释放。
     */
    private suspend fun runTaskWithRetry(id: Long, headers: Map<String, String>, run: ActiveRun) {
        val startedAtNanos = System.nanoTime()
        val task = dao.get(id) ?: return
        val platform = task.platform
        val operationId = getOrCreateOperationId(task) ?: return
        var retries = 0
        val maxRetries = retryCountProvider().coerceIn(0, 10)
        try {
            while (true) {
                // 并发许可：排队等待，直到有空闲下载槽位（或任务被暂停/取消）
                awaitConcurrencySlot()
                if (!isTaskActive()) throw CancellationException("下载任务已取消")
                activeDownloads.incrementAndGet()
                try {
                    try {
                        RequestOperationContextHolder.withContext(
                            RequestOperationContext(
                                operationId = operationId,
                                platform = RequestPlatform.from(platform),
                                stage = RequestStage.DOWNLOAD,
                                retry = retries,
                                logSuccessfulRequests = false
                            )
                        ) {
                            runTask(id, headers)
                        }
                        if (!isTaskActive() || dao.get(id)?.status != DownloadTaskEntity.STATUS_COMPLETED) {
                            throw CancellationException("下载任务未完成")
                        }
                        Log.i(
                            TAG,
                            DownloadTaskMetric.terminal(
                                operationId = operationId,
                                taskId = id,
                                platform = platform,
                                outcome = DownloadMetricOutcome.SUCCESS,
                                retries = retries,
                                elapsedMillis = DownloadTaskMetric.elapsedMillis(startedAtNanos, System.nanoTime())
                            )
                        )
                        run.terminalLogged.set(true)
                        return
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (run.stopReason.get() != null) throw CancellationException("下载任务已停止", e)
                        val failure = DownloadFailureClassifier.classify(e)
                        if (isTaskActive() && failure.kind.retryable && retries < maxRetries) {
                            val nextRetry = retries + 1
                            // 逐次递增延迟，避免失败风暴；等待期间取消不计入实际重试次数。
                            delay(1200L * nextRetry)
                            retries = nextRetry
                            Log.i(
                                TAG,
                                DownloadTaskMetric.retry(
                                    operationId = operationId,
                                    taskId = id,
                                    platform = platform,
                                    retry = retries,
                                    maxRetries = maxRetries,
                                    failureKind = failure.kind,
                                    elapsedMillis = DownloadTaskMetric.elapsedMillis(
                                        startedAtNanos,
                                        System.nanoTime()
                                    )
                                )
                            )
                        } else {
                            Log.i(
                                TAG,
                                DownloadTaskMetric.terminal(
                                    operationId = operationId,
                                    taskId = id,
                                    platform = platform,
                                    outcome = DownloadMetricOutcome.FAILURE,
                                    retries = retries,
                                    elapsedMillis = DownloadTaskMetric.elapsedMillis(
                                        startedAtNanos,
                                        System.nanoTime()
                                    ),
                                    failureKind = failure.kind
                                )
                            )
                            run.terminalLogged.set(true)
                            throw DownloadFailureException(failure, e)
                        }
                    }
                } finally {
                    activeDownloads.decrementAndGet()
                }
            }
        } catch (error: CancellationException) {
            val status = dao.get(id)?.status
            if (status == DownloadTaskEntity.STATUS_COMPLETED &&
                run.terminalLogged.compareAndSet(false, true)
            ) {
                Log.i(
                    TAG,
                    DownloadTaskMetric.terminal(
                        operationId = operationId,
                        taskId = id,
                        platform = platform,
                        outcome = DownloadMetricOutcome.SUCCESS,
                        retries = retries,
                        elapsedMillis = DownloadTaskMetric.elapsedMillis(startedAtNanos, System.nanoTime())
                    )
                )
            } else if (status != null && DownloadTerminalPolicy.cancellationOutcome(
                    run.stopReason.get() ?: DownloadStopReason.PAUSE,
                    status
                ) == DownloadMetricOutcome.CANCELLED &&
                run.terminalLogged.compareAndSet(false, true)
            ) {
                Log.i(
                    TAG,
                    DownloadTaskMetric.terminal(
                        operationId = operationId,
                        taskId = id,
                        platform = platform,
                        outcome = DownloadMetricOutcome.CANCELLED,
                        retries = retries,
                        elapsedMillis = DownloadTaskMetric.elapsedMillis(startedAtNanos, System.nanoTime())
                    )
                )
            }
            throw error
        }
    }

    private suspend fun getOrCreateOperationId(task: DownloadTaskEntity): String? {
        if (OperationId.isDownload(task.operationId)) return task.operationId
        if (task.operationId.isNotBlank()) return null
        val candidate = OperationId.download()
        if (dao.initializeOperationIdIfBlank(task.id, candidate) == 1) return candidate
        return dao.get(task.id)?.operationId?.takeIf(OperationId::isDownload)
    }

    private suspend fun logRemovalCancellation(task: DownloadTaskEntity, run: ActiveRun?) {
        if (DownloadTerminalPolicy.cancellationOutcome(
                DownloadStopReason.REMOVE,
                task.status
            ) != DownloadMetricOutcome.CANCELLED
        ) return
        val operationId = getOrCreateOperationId(task) ?: return
        if (run != null && !run.terminalLogged.compareAndSet(false, true)) return
        Log.i(
            TAG,
            DownloadTaskMetric.terminal(
                operationId = operationId,
                taskId = task.id,
                platform = task.platform,
                outcome = DownloadMetricOutcome.CANCELLED,
                retries = 0,
                elapsedMillis = 0
            )
        )
    }

    private suspend fun runTask(id: Long, headers: Map<String, String>) {
        // 协程已被取消（暂停/删除）：直接退出，不写状态
        if (!isTaskActive()) return
        val task = dao.get(id) ?: return
        updateStatus(id, DownloadTaskEntity.STATUS_DOWNLOADING)
        dao.updateError(id, "")
        taskStartTimes[id] = System.currentTimeMillis()
        Log.d(TAG, "runTask: id=$id platform=${DownloadTaskMetric.safePlatform(task.platform)}")

        // HLS（m3u8 转码流，如 UC play）：不走 Range 分片，直接拉分片合并
        if (task.url.contains(".m3u8", true) || task.url.contains(".m3u", true)) {
            Log.d(TAG, "runTask: id=$id mode=hls")
            hlsDownload(id, task, headers)
            return
        }

        // 总大小以服务器探测为准（Range0-0 的 Content-Range 是真实总大小），
        // 避免各平台传入的 size 与实际不符导致分片区间错误 → 文件截断/膨胀损坏
        val total = downloader.getTotalSize(task.url, headers)
            ?: taskSizes[id]?.takeIf { it > 0 }
        if (total == null) {
            // 服务器不返回文件大小（Range/Content-Length 均缺失）：降级为流式下载（开放区间 Range）
            Log.w(TAG, "runTask: id=$id sizeProbe=unknown fallback=stream")
            streamDownload(id, task, headers)
            return
        }
        Log.d(TAG, "getTotalSize: id=$id total=$total")
        updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, task.downloadedSize, total)
        // 取到大小后再次检查取消（暂停可能发生在 getTotalSize 期间）
        if (!isTaskActive()) return

        val threadCount = threadProvider(task.platform).coerceAtLeast(1)
        val chunkDir = chunkDirOf(id).apply { mkdirs() }
        // 分片计划（规划纯逻辑见 DownloadPlanner，可单测）：片数 / 主池 70% / 弹性区起点一并推导
        val plan = DownloadPlanner.planOf(total, threadCount)
        // ★ 分片计划签名：part_$i 按索引命名，但区间由 chunkCount/total 推导。
        //   若跨会话改了线程数或服务器探测大小变化 → 旧 part 区间错位 → 续传膨胀/损坏。
        //   检测到计划不一致时整目录清空重下（旧 part 不可信）。
        val planFile = File(chunkDir, "plan.txt")
        val signature = DownloadPlanner.planSignature(plan)
        if (planFile.exists() && planFile.readText() != signature) {
            Log.w(TAG, "runTask: id=$id 分片计划变化（$signature），清空旧 part 重下")
            chunkDir.deleteRecursively()
            chunkDir.mkdirs()
        } else {
            // 计划一致（断点续传）：主池 part_i 与弹性区 seg_{start}_{end} 均按文件已有长度续传
            // （seg 文件名携带区间信息，downloadChunk 按长度续传，不再删除重下）
        }
        planFile.writeText(signature)
        // 有效并发：迅雷（CDN 对单文件并发 Range 有阈值，约 8 个，超过会降级 200 整文件）与
        // 百度（直链绑定账号，密集 Range 是账号维度滥用信号，P1-6）封顶安全上限；
        // 其他平台保持用户设置的线程数（满并发）
        val isXunlei = headers["User-Agent"]?.contains("xunlei", ignoreCase = true) == true ||
            task.url.contains("xunlei", ignoreCase = true)
        val isBaidu = headers["User-Agent"]?.contains("netdisk", ignoreCase = true) == true ||
            task.url.contains("baidu", ignoreCase = true)
        val effectiveWorkers = when {
            isXunlei -> min(threadCount, RANGE_WORKERS_CAP).coerceAtLeast(1)
            isBaidu -> min(threadCount, BAIDU_WORKERS_CAP).coerceAtLeast(1)
            else -> threadCount.coerceAtLeast(1)
        }
        Log.d(TAG, "分片规划: id=$id chunks=${plan.chunkCount} main=${plan.mainPoolCount} elasticStart=${plan.elasticStart} size=${plan.chunkSize} threads=$threadCount effectiveWorkers=$effectiveWorkers isXunlei=$isXunlei isBaidu=$isBaidu")

        // 注册实时统计：线程数 = 有效并发（受安全上限约束）
        _stats.update { it + (id to DownloadStats(0L, -1L, effectiveWorkers)) }

        // 断点续传统计（纯逻辑见 DownloadPlanner.resumeState，可单测）：
        // 先删不完整 seg 再统计磁盘大小（P1-5 顺序在此固化），并推导弹性区续传起点
        val resume = DownloadPlanner.resumeState(chunkDir, plan)
        val downloaded = AtomicLong(resume.downloadedBytes)
        // ★ 恢复时 DB 旧值可能滞后于磁盘（暂停瞬间未上报的字节）：以磁盘真实大小为准回写，避免进度回跳
        if (resume.downloadedBytes > task.downloadedSize) {
            updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, resume.downloadedBytes, total)
        }
        val lastPersistAt = AtomicLong(0L)
        val speedRecorder = SpeedRecorder()

        // ---------- 任务池（主池 70% 等分）+ 弹性区（30%，空闲线程中点劈分） ----------
        val results = arrayOfNulls<ChunkResult?>(plan.mainPoolCount)
        val nextIdx = AtomicInteger(0)
        val fallback = AtomicBoolean(false)              // 任一分片检测到「服务器忽略 Range」→ 整任务回退单流
        val failReason = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val rangeIgnoredCount = AtomicInteger(0)         // RANGE_IGNORED 累计次数（偶发 200 容忍）

        // ★ 弹性区分配器：按字节顺序领取 4MB 块，区间物理相邻（替代中点劈分，根治中后段掉速）。
        //   续传：不完整 seg 删除重下；完整 seg 前缀推进 nextStart（弹性区按序分配，完成块天然是字节前缀）。
        val elasticAllocator = ElasticAllocator(total, plan.elasticStart)
        if (plan.elasticStart < total) {
            // 推进到已完整前缀末尾（只前进，跳过已下载弹性块）
            elasticAllocator.skipTo(resume.elasticResumeStart)
        }
        val elasticResults = ConcurrentHashMap<String, ChunkResult>()

        val allOk = coroutineScope {
            val workers = List(effectiveWorkers) {
                async(Dispatchers.IO) {
                    // 阶段 1：主池循环领取
                    while (true) {
                        if (fallback.get()) break
                        val i = nextIdx.getAndIncrement()
                        if (i >= plan.mainPoolCount) break
                        // 错峰建连：首请求前按序号微延迟，平摊 TCP/TLS 突发（仅影响首请求，不影响稳态并发）
                        if (i in 1 until effectiveWorkers) delay(min(i.toLong(), STAGGER_CAP.toLong()) * STAGGER_MS)
                        run {
                            if (fallback.get()) return@run
                            val start = i * plan.chunkSize
                            val end = min(start + plan.chunkSize - 1, total - 1)
                            val res = try {
                                downloader.downloadChunk(
                                    taskId = id, url = task.url, start = start, end = end,
                                    partFile = File(chunkDir, "part_$i"), headers = headers
                                ) { bytes ->
                                    speedLimiter.awaitAllow(bytes)
                                    // ★ 钳制到 total：任何竞态都不可能让显示超过总大小
                                    val new = minOf(downloaded.addAndGet(bytes), total)
                                    if (!isTaskActive()) return@downloadChunk
                                    speedRecorder.onBytes(new)?.let { speed ->
                                        val remain = if (speed > 0) (total - new) * 1000 / speed else -1L
                                        _stats.update { it + (id to DownloadStats(speed, remain, effectiveWorkers)) }
                                    }
                                    notifyProgress(id, task.fileName, new, total)
                                    persistProgressIfDue(id, new, total, force = false, lastAt = lastPersistAt)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                failReason.compareAndSet(null, "分片 ${i + 1}/${plan.mainPoolCount}：${LogRedactor.error(e)}")
                                ChunkResult.FAILED
                            }
                            results[i] = res
                            when (res) {
                                ChunkResult.RANGE_IGNORED -> {
                                    // 偶发 200（CDN 限流中间态）不算真降级：前 N 次不触发回退，继续领新片；
                                    // 持续 RANGE_IGNORED 才回退单流
                                    val n = rangeIgnoredCount.incrementAndGet()
                                    Log.w(TAG, "runTask: id=$id 分片${i + 1} 检测到服务器忽略Range（累计 $n/$RANGE_IGNORED_TOLERANCE）")
                                    if (n >= RANGE_IGNORED_TOLERANCE) fallback.compareAndSet(false, true)
                                }
                                ChunkResult.FAILED -> failReason.compareAndSet(null, "分片 ${i + 1}/${plan.mainPoolCount} 下载失败")
                                else -> {}
                            }
                        }
                    }
                    // 阶段 2：主池取空 → 弹性区按字节顺序领取 4MB 块（空闲线程逐个平滑转入，并发形态不突变）
                    while (!fallback.get()) {
                        val range = elasticAllocator.take() ?: break
                        val s = range.first
                        val e = range.last
                        val key = "${s}_${e}"
                        val res = try {
                            run {
                                if (fallback.get()) return@run ChunkResult.FAILED
                                downloader.downloadChunk(
                                    taskId = id, url = task.url, start = s, end = e,
                                    partFile = File(chunkDir, "seg_$key.part"), headers = headers
                                ) { bytes ->
                                    speedLimiter.awaitAllow(bytes)
                                    // ★ 钳制到 total：任何竞态都不可能让显示超过总大小
                                    val new = minOf(downloaded.addAndGet(bytes), total)
                                    if (!isTaskActive()) return@downloadChunk
                                    speedRecorder.onBytes(new)?.let { speed ->
                                        val remain = if (speed > 0) (total - new) * 1000 / speed else -1L
                                        _stats.update { it + (id to DownloadStats(speed, remain, effectiveWorkers)) }
                                    }
                                    notifyProgress(id, task.fileName, new, total)
                                    persistProgressIfDue(id, new, total, force = false, lastAt = lastPersistAt)
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ChunkResult.FAILED
                        }
                        elasticResults[key] = res
                        when (res) {
                            ChunkResult.RANGE_IGNORED -> {
                                val n = rangeIgnoredCount.incrementAndGet()
                                Log.w(TAG, "runTask: id=$id 弹性区间 $key 检测到服务器忽略Range（累计 $n/$RANGE_IGNORED_TOLERANCE）")
                                if (n >= RANGE_IGNORED_TOLERANCE) fallback.compareAndSet(false, true)
                            }
                            ChunkResult.FAILED -> failReason.compareAndSet(null, "弹性区间 ${s}-${e} 下载失败")
                            else -> {}
                        }
                    }
                }
            }
            workers.awaitAll()
            !fallback.get() && results.all { it == ChunkResult.OK } &&
                elasticResults.values.all { it == ChunkResult.OK }
        }

        // ---------- 三种结局 ----------
        if (fallback.get()) {
            // 服务器忽略 Range：回退单条整文件流（只下一次，不按分片重复下载整文件）
            Log.w(TAG, "runTask: id=$id 回退单流整文件下载（避免重复下载整文件）")
            singleStreamFallback(id, task, headers, total, chunkDir, failReason)
            return
        }
        if (!allOk) {
            // 失败区间并行重试：收集主池缺失片 + 弹性区失败区间，复用 worker 池并发补下
            val missing = DownloadPlanner.missingRanges(chunkDir, plan, elasticResults)
            Log.e(TAG, "runTask: id=$id missingRanges=${missing.size} retry=parallel")
            val retryOk = if (missing.isEmpty()) true else coroutineScope {
                val retryIdx = AtomicInteger(0)
                val retryResults = arrayOfNulls<ChunkResult?>(missing.size)
                val retryLastAt = AtomicLong(0L)
                val retryWorkers = List(min(effectiveWorkers, missing.size)) {
                    async(Dispatchers.IO) {
                        while (true) {
                            if (!isTaskActive()) break
                            val pos = retryIdx.getAndIncrement()
                            if (pos >= missing.size) break
                            val m = missing[pos]
                            val res = try {
                                downloader.downloadChunk(
                                    taskId = id, url = task.url, start = m.start, end = m.end,
                                    partFile = m.file, headers = headers
                                ) { bytes ->
                                    speedLimiter.awaitAllow(bytes)
                                    // ★ 钳制到 total：任何竞态都不可能让显示超过总大小
                                    val new = minOf(downloaded.addAndGet(bytes), total)
                                    if (!isTaskActive()) return@downloadChunk
                                    persistProgressIfDue(id, new, total, force = false, lastAt = retryLastAt)
                                    notifyProgress(id, task.fileName, new, total)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                ChunkResult.FAILED
                            }
                            retryResults[pos] = res
                            if (res != ChunkResult.OK) {
                                failReason.compareAndSet(null, "区间 ${m.start}-${m.end} 重试仍失败")
                            }
                        }
                    }
                }
                retryWorkers.awaitAll()
                retryResults.all { it == ChunkResult.OK }
            }
            if (retryOk) {
                Log.d(TAG, "runTask: id=$id 重试补齐所有区间，开始合并")
                finishDownload(id, chunkDir, finalChunkFiles(chunkDir, plan.mainPoolCount), task.fileName, total)
                return
            }
            // 重试仍失败：回退单流
            Log.w(TAG, "runTask: id=$id 分片重试失败，回退单流整文件下载")
            singleStreamFallback(id, task, headers, total, chunkDir, failReason)
            return
        }
        Log.d(TAG, "runTask: id=$id 所有区间完成，开始合并")
        finishDownload(id, chunkDir, finalChunkFiles(chunkDir, plan.mainPoolCount), task.fileName, total)
    }

    /** 最终合并文件列表：主池 part_0..part_{n-1}（连续前半段）+ 弹性区 seg_{start}_{end} 按 start 排序（后半段） */
    private fun finalChunkFiles(chunkDir: File, mainPoolCount: Int): List<File> {
        val mainFiles = (0 until mainPoolCount).map { File(chunkDir, "part_$it") }
        val elasticFiles = chunkDir.listFiles { f ->
            f.name.startsWith("seg_") && f.name.endsWith(".part")
        }?.sortedBy { it.name.removePrefix("seg_").substringBefore('_').toLong() }
            ?: emptyList()
        return mainFiles + elasticFiles
    }

    /**
     * 回退：单条整文件流下载（服务器忽略 Range 时）。
     * 写入**独立**的 full_single.bin（从 0 开始），不复用 part_0，避免与已下分片错位/重复。
     */
    private suspend fun singleStreamFallback(
        id: Long,
        task: DownloadTaskEntity,
        headers: Map<String, String>,
        total: Long,
        chunkDir: File,
        failReason: java.util.concurrent.atomic.AtomicReference<String?>
    ) {
        val fullFile = File(chunkDir, "full_single.bin").apply { delete() } // 全新整文件，从 0 开始
        val fullDownloaded = AtomicLong(0)
        val fullLastAt = AtomicLong(0L)
        val ok = downloader.downloadFull(id, task.url, fullFile, headers, total) { bytes ->
            speedLimiter.awaitAllow(bytes)
            // ★ 钳制到 total：任何竞态都不可能让显示超过总大小
            val new = minOf(fullDownloaded.addAndGet(bytes), total)
            if (!isTaskActive()) return@downloadFull
            persistProgressIfDue(id, new, total, force = false, lastAt = fullLastAt)
            notifyProgress(id, task.fileName, new, total)
        }
        if (!ok) throw IllegalStateException(failReason.get() ?: "分片与单流下载均失败")
        finishDownload(id, chunkDir, listOf(fullFile), task.fileName, total)
    }

    /** 流式降级下载：总大小未知时单分片开放区间下载（Range: bytes=from-），读到 EOF */
    private suspend fun streamDownload(id: Long, task: DownloadTaskEntity, headers: Map<String, String>) {
        if (!isTaskActive()) return
        updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, task.downloadedSize, 0)
        if (!isTaskActive()) return
        _stats.update { it + (id to DownloadStats(0L, -1L, 1)) }
        val chunkDir = chunkDirOf(id).apply { mkdirs() }
        val partFile = File(chunkDir, "part_0")
        val downloaded = AtomicLong(partFile.length())
        val streamLastAt = AtomicLong(0L)
        val ok = downloader.downloadChunk(
            taskId = id,
            url = task.url,
            start = 0,
            end = Long.MAX_VALUE,
            partFile = partFile,
            headers = headers
        ) { bytes ->
            speedLimiter.awaitAllow(bytes)
            val new = downloaded.addAndGet(bytes)
            if (!isTaskActive()) return@downloadChunk
            // 大小未知：只更新已下载量（total=0 表示未知）
            persistProgressIfDue(id, new, 0, force = false, lastAt = streamLastAt)
            // 前台通知进度（2 秒节流，total 未知时仅更新标题）
            notifyProgress(id, task.fileName, new, 0)
        }
        if (ok != ChunkResult.OK) {
            // Range 被 CDN 拒绝（416/403）或忽略（200 整文件）：回退为无 Range 完整 GET
            Log.w(TAG, "streamDownload: id=$id Range 失败，回退完整 GET 下载")
            downloaded.set(0)
            streamLastAt.set(0L)
            updateProgress(id, DownloadTaskEntity.STATUS_DOWNLOADING, 0, 0)
            val ok2 = downloader.downloadFull(
                taskId = id,
                url = task.url,
                partFile = partFile,
                headers = headers
            ) { bytes ->
                speedLimiter.awaitAllow(bytes)
                val new = downloaded.addAndGet(bytes)
                if (!isTaskActive()) return@downloadFull
                persistProgressIfDue(id, new, 0, force = false, lastAt = streamLastAt)
            }
            if (!ok2) throw IllegalStateException("下载失败（Range 与完整下载均失败）")
        }
        if (!isTaskActive()) return
        finishDownload(id, chunkDir, listOf(partFile), task.fileName, 0)
    }

    /** HLS（m3u8 转码流，如 UC play）下载：拉取分片合并 → 保存 → 完成回调 */
    private suspend fun hlsDownload(id: Long, task: DownloadTaskEntity, headers: Map<String, String>) {
        if (!isTaskActive()) return
        _stats.update { it + (id to DownloadStats(0L, -1L, 1)) }
        val hlsFile = File(context.cacheDir, "hls_$id")
        hlsFile.delete()
        val downloaded = AtomicLong(0)
        val hlsLastAt = AtomicLong(0L)
        try {
            val result = HlsDownloader.download(task.url, headers, hlsFile) { bytes ->
                speedLimiter.awaitAllow(bytes)
                val new = downloaded.addAndGet(bytes)
                persistProgressIfDue(id, new, 0, force = false, lastAt = hlsLastAt)
                notifyProgress(id, task.fileName, new, 0)
            }
            if (!isTaskActive()) return
            when (result) {
                HlsDownloader.Result.UNSUPPORTED_ENCRYPTION ->
                    throw IllegalStateException("该视频为加密 HLS 流，暂不支持下载")
                HlsDownloader.Result.FAILED -> throw IllegalStateException("HLS 转码流下载失败")
                HlsDownloader.Result.SUCCESS -> Unit
            }
            if (!storagePermissionProvider()) {
                throw IllegalStateException("未授予存储权限，无法保存到下载目录")
            }
            val savedPath = withContext(Dispatchers.IO) {
                DownloadSaver.save(context, task.fileName, hlsFile, saveDirProvider())
            } ?: throw IllegalStateException("保存到下载目录失败")
            completeTask(id, savedPath, hlsFile.length())
            Log.d(TAG, "hlsDownload: id=$id completed size=${hlsFile.length()}")
            val hadPersistentCleanup = cleanupDao.getByTaskId(id) != null
            cleanupPersisted(id)
            if (!hadPersistentCleanup) {
                taskCallbacks.remove(id)?.let { cb -> runCatching { cb() } }
            } else {
                taskCallbacks.remove(id)
            }
            _stats.update { it - id }
        } finally {
            hlsFile.delete()
        }
    }

    /**
     * 合并分片 → 保存到公共 Download 目录 → 触发完成回调 → 清理。
     * ★ 增加完整性校验：分片非空 + 合并后总大小 == total，任一不符直接抛错，绝不保存损坏文件。
     */
    private suspend fun finishDownload(
        id: Long,
        chunkDir: File,
        chunkFiles: List<File>,
        fileName: String,
        total: Long
    ) {
        if (!isTaskActive()) return
        // 1) 分片完整性
        for (part in chunkFiles) {
            if (!part.exists() || part.length() <= 0) {
                Log.e(TAG, "finishDownload: id=$id 分片缺失/为空 $part")
                throw IllegalStateException("分片文件缺失或为空，拒绝合并（防止文件损坏）")
            }
        }
        // 2) 合并
        // ★ 合并产物放内部缓存（data 分区，非 FUSE 挂载）：大文件 IO 快得多；保存完成即删
        val merged = File(context.cacheDir, "merged_$id")
        try {
            if (!downloader.mergeChunks(chunkFiles, merged)) {
                Log.e(TAG, "finishDownload: id=$id 合并分片失败")
                throw IllegalStateException("合并分片失败")
            }
            // 3) 整体大小校验（total>0 时）
            if (total > 0 && merged.length() != total) {
                Log.e(TAG, "finishDownload: id=$id 文件大小校验失败 期望=$total 实际=${merged.length()}")
                throw IllegalStateException("文件大小校验失败：期望 $total 字节，实际 ${merged.length()} 字节（已拒绝保存损坏文件）")
            }
            // 4) API 提供 SHA-256 时，保存前流式校验内容；无哈希时保持长度校验策略。
            val expectedSha256 = dao.get(id)?.expectedSha256.orEmpty()
            if (expectedSha256.isNotBlank()) {
                val matches = withContext(Dispatchers.IO) {
                    FileIntegrity.matchesSha256(merged, expectedSha256)
                }
                if (!matches) {
                    Log.e(TAG, "finishDownload: id=$id SHA-256 校验失败")
                    throw DownloadFailureException(
                        DownloadFailure(DownloadFailureKind.INTEGRITY, "SHA-256 mismatch")
                    )
                }
            }
            // 5) Android 9- 保存前检查存储权限（动态申请，授权后继续；无权限则报错提示）
            if (!storagePermissionProvider()) {
                throw IllegalStateException("未授予存储权限，无法保存到下载目录")
            }
            // 6) 保存（自定义目录经 SAF 写入；默认目录走 MediaStore/传统路径）
            // ★ 同步阻塞拷贝必须切 IO 线程：任务跑在 Dispatchers.Default（CPU 池），
            //   大文件保存若占满 Default 线程会让整个下载器协程饿死（"100% 卡死保存不了"）
            val savedPath = withContext(Dispatchers.IO) {
                DownloadSaver.save(context, fileName, merged, saveDirProvider())
            }
                ?: throw IllegalStateException("保存到下载目录失败")
            completeTask(id, savedPath, total)
            Log.d(TAG, "finishDownload: id=$id completed size=${merged.length()}")
            val hadPersistentCleanup = cleanupDao.getByTaskId(id) != null
            cleanupPersisted(id)
            if (!hadPersistentCleanup) {
                taskCallbacks.remove(id)?.let { cb -> runCatching { cb() } }
            } else {
                taskCallbacks.remove(id)
            }
            _stats.update { it - id }
        } finally {
            merged.delete()
        }
        chunkDir.deleteRecursively()
    }

    /**
     * 速度采样器：取近 [WINDOW_MS] 秒滑动窗口的平均速度，平滑多线程下载的速度波动。
     * 多线程并发下瞬时速率波动大，短窗口估算剩余时长会剧烈跳动；
     * 改用 5 秒窗口均值后，剩余时长更稳定可靠。
     */
    private class SpeedRecorder {
        private data class Sample(val timeMs: Long, val bytes: Long)

        private val samples = ArrayDeque<Sample>()
        private var lastEmit = 0L

        @Synchronized
        fun onBytes(total: Long): Long? {
            val now = System.currentTimeMillis()
            samples.addLast(Sample(now, total))
            // 剔除窗口外的旧样本，但始终保留至少 2 个（下载起步阶段窗口尚短）
            while (samples.size > 2 && now - samples.first().timeMs > WINDOW_MS) {
                samples.removeFirst()
            }
            // 250ms 发射一次，避免高频刷新 UI/通知
            if (now - lastEmit < 250) return null
            val first = samples.first()
            val elapsed = now - first.timeMs
            val speed = if (elapsed > 0) {
                ((total - first.bytes) * 1000 / elapsed).coerceAtLeast(0)
            } else 0L
            lastEmit = now
            return speed
        }

        private companion object {
            const val WINDOW_MS = 5000L
        }
    }

    /** 全局限速器（令牌桶）：所有任务合计不超过 speedLimitProvider 的字节/秒；0 = 不限速 */
    private inner class SpeedLimiter {
        @Volatile
        private var tokens = 0L
        @Volatile
        private var lastRefillNanos = System.nanoTime()

        @Synchronized
        private fun refill(limit: Long) {
            val now = System.nanoTime()
            val elapsedSec = ((now - lastRefillNanos).coerceAtLeast(0) / 1_000_000_000.0)
            lastRefillNanos = now
            tokens = minOf(limit, tokens + (elapsedSec * limit).toLong())
        }

        /** 消耗 bytes 字节额度；不足则挂起等待（限速生效） */
        suspend fun awaitAllow(bytes: Long) {
            val limit = speedLimitProvider().coerceAtLeast(0L)
            if (limit <= 0L) return
            while (true) {
                val waitMs = synchronized(this) {
                    refill(limit)
                    if (bytes <= tokens) {
                        tokens -= bytes
                        return
                    }
                    ((bytes - tokens) * 1000 / limit).coerceIn(1L, 200L)
                }
                // 锁外挂起等待，避免持锁阻塞其他任务
                delay(waitMs)
            }
        }
    }

    /** 下载临时文件缓存根目录：外部缓存（/storage/emulated/0/Android/data/com.yunx.app/cache），
     *  与最终保存目录解耦，系统可自动清理；外部存储不可用时回退内部缓存目录。 */
    private fun cacheBase(): File = context.externalCacheDir ?: context.cacheDir

    /** 分片临时文件目录：cacheBase()/download_tmp/$id */
    private fun chunkDirOf(id: Long): File = File(cacheBase(), "download_tmp/$id")
}
