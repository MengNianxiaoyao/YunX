package com.yunx.app.data.download

import java.io.File
import kotlin.math.ceil
import kotlin.math.min

/** 分片计划（runTask 规划阶段的纯数据）：由 total 与线程数唯一确定 */
internal data class ChunkPlan(
    val total: Long,
    val chunkCount: Int,
    /** 单片大小（向上取整覆盖 total） */
    val chunkSize: Long,
    /** 主池片数（70%，均分连续前半段） */
    val mainPoolCount: Int,
    /** 弹性区起始字节（主池之后） */
    val elasticStart: Long
)

/** 重试区间（主池 part_i 或弹性区间 seg_{start}_{end}） */
internal data class RetryRange(val start: Long, val end: Long, val file: File)

/** 断点续传统计结果 */
internal data class ResumeState(
    /** 已下载字节（主池 part + 完整 seg 磁盘真实长度，钳制到 total） */
    val downloadedBytes: Long,
    /** 弹性区续传起点（完整 seg 前缀末尾的下一字节；无弹性区时 = elasticStart） */
    val elasticResumeStart: Long
)

/**
 * 分片规划纯逻辑（P3 从 DownloadManager 挖出，可单测；行为与原内联实现逐字节一致）。
 * 保留此处的注释——它们是分片模型的逆向经验沉淀，丢失后极难重建。
 */
internal object DownloadPlanner {

    /** 分片数规划（任务池模型）：分片数 = 线程数 × 8，远多于并发线程数。
     *  worker 循环领取盈余块，任一分片慢时其他线程继续领新片，根治"尾部并发塌缩"；
     *  保留 1MB 单片下限（避免过多小片）与 512 封顶。 */
    fun chunkCountFor(total: Long, threads: Int): Int {
        if (total <= 0) return 1
        val minChunkBytes = 1 * 1024 * 1024L
        val bySize = when {
            total < 5 * 1024 * 1024 -> 1          // < 5MB 不分片
            total < 50 * 1024 * 1024 -> 8         // < 50MB
            total < 500 * 1024 * 1024 -> 32       // < 500MB
            else -> 64                            // ≥ 500MB 基础值
        }
        // 任务池：每线程平均领 8 片，天然抗慢片拖尾（比 1:1 映射多 8 倍盈余）
        val want = maxOf(bySize, threads * 8)
        return minOf(want, (total / minChunkBytes).toInt().coerceAtLeast(1), 512)
    }

    /** 完整分片计划：片数 → 片大小（向上取整覆盖 total）→ 主池 70% → 弹性区起点 */
    fun planOf(total: Long, threads: Int): ChunkPlan {
        val chunkCount = chunkCountFor(total, threads)
        val chunkSize = ceil(total.toDouble() / chunkCount).toLong()
        val mainPoolCount = (chunkCount * 0.7).toInt().coerceIn(1, chunkCount)
        val elasticStart = mainPoolCount * chunkSize
        return ChunkPlan(total, chunkCount, chunkSize, mainPoolCount, elasticStart)
    }

    /** plan.txt 签名：part_$i 按索引命名，但区间由 chunkCount/total 推导。
     *  跨会话改了线程数或服务器探测大小变化 → 旧 part 区间错位 → 续传膨胀/损坏，
     *  检测到签名不一致时整目录清空重下（旧 part 不可信）。 */
    fun planSignature(plan: ChunkPlan): String =
        "chunks=${plan.chunkCount} total=${plan.total} main=${plan.mainPoolCount}"

    /**
     * 断点续传统计（P3 提为纯函数；P1-5 的"先删不完整 seg 再统计"顺序在此固化，可写回归测试）：
     * 1. 删除不完整弹性分片（长度 < 区间大小的 seg 是残留，其字节不可信）；
     * 2. 统计主池 part_i 与剩余 seg 的磁盘真实长度，钳制到 total（防旧 job 残留累加超总大小）；
     * 3. 弹性区续传起点推进到完整 seg 前缀末尾（弹性区按序分配，完成块天然是字节前缀）。
     */
    fun resumeState(chunkDir: File, plan: ChunkPlan): ResumeState {
        // 恢复时先删除不完整弹性分片，再统计磁盘大小，避免把即将删除的字节计入进度
        if (plan.elasticStart < plan.total) {
            chunkDir.listFiles { f -> f.name.startsWith("seg_") && f.name.endsWith(".part") }
                ?.forEach { f ->
                    val range = parseSegName(f.name) ?: return@forEach
                    if (f.length() < (range.last - range.first + 1)) f.delete()
                }
        }
        var downloaded = 0L
        for (i in 0 until plan.mainPoolCount) {
            downloaded += File(chunkDir, "part_$i").length()
        }
        chunkDir.listFiles { f -> f.name.startsWith("seg_") && f.name.endsWith(".part") }
            ?.forEach { downloaded += it.length() }
        val init = minOf(downloaded, plan.total)
        // 弹性区续传：推进到已完整前缀末尾（只前进，跳过已下载弹性块）
        var resumeNext = plan.elasticStart
        if (plan.elasticStart < plan.total) {
            val doneSegs = chunkDir.listFiles { f -> f.name.startsWith("seg_") && f.name.endsWith(".part") }
                ?.mapNotNull { f ->
                    val range = parseSegName(f.name) ?: return@mapNotNull null
                    if (f.length() >= (range.last - range.first + 1)) range else null
                }?.sortedBy { it.first } ?: emptyList()
            for (seg in doneSegs) {
                if (seg.first == resumeNext) resumeNext = seg.last + 1 else break
            }
        }
        return ResumeState(downloadedBytes = init, elasticResumeStart = resumeNext)
    }

    /** 失败区间收集（并行重试用）：主池缺失片（磁盘长度不足）+ 弹性区失败区间 */
    fun missingRanges(
        chunkDir: File,
        plan: ChunkPlan,
        elasticResults: Map<String, ChunkResult>
    ): List<RetryRange> = buildList {
        for (i in 0 until plan.mainPoolCount) {
            val f = File(chunkDir, "part_$i")
            val s = i * plan.chunkSize
            val e = min(s + plan.chunkSize - 1, plan.total - 1)
            if (f.length() < (e - s + 1)) add(RetryRange(s, e, f))
        }
        elasticResults.forEach { (key, res) ->
            if (res != ChunkResult.OK) {
                val s = key.substringBefore('_').toLong()
                val e = key.substringAfter('_').toLong()
                add(RetryRange(s, e, File(chunkDir, "seg_$key.part")))
            }
        }
    }

    /** 解析 seg 文件名（seg_{start}_{end}.part，闭区间） */
    private fun parseSegName(name: String): LongRange? {
        val core = name.removePrefix("seg_").removeSuffix(".part")
        val s = core.substringBefore('_').toLongOrNull() ?: return null
        val e = core.substringAfter('_').toLongOrNull() ?: return null
        return s..e
    }
}

/**
 * 弹性区分配器：按字节顺序领取固定大小块（默认 4MB），保证线程拿到的区间**物理相邻**。
 * 替代"中点劈分"——劈分（先大后小）导致主池耗尽瞬间全部线程涌入弹性区、区间跨度翻倍、
 * 连接复用率崩塌（中后段掉速根因）；按序分配则线程逐个平滑转入弹性区，并发形态不突变。
 */
internal class ElasticAllocator(
    private val total: Long,
    private val elasticStart: Long
) {
    private val lock = Any()
    private var nextStart = elasticStart

    /** 领取下一个弹性块（按字节顺序，块大小 DEFAULT_ELASTIC_BLOCK；不足 4MB 的尾部整块领取） */
    fun take(): LongRange? = synchronized(lock) {
        if (nextStart >= total) return null
        val s = nextStart
        val e = minOf(s + DEFAULT_ELASTIC_BLOCK - 1, total - 1)
        nextStart = e + 1
        s..e
    }

    /** 断点续传：跳过已下载前缀（nextStart 只前进） */
    fun skipTo(start: Long) = synchronized(lock) {
        if (start > nextStart) nextStart = start
    }

    companion object {
        /** 弹性块大小：4MB（可调；CDN 对同区间并发敏感可降 2MB，单连接限速严重可升 8MB） */
        const val DEFAULT_ELASTIC_BLOCK = 4 * 1024 * 1024L
    }
}
