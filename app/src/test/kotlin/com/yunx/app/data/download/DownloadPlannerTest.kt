package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadPlannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ---------- chunkCountFor ----------

    @Test
    fun chunkCountForNonPositiveTotalIsSingleChunk() {
        assertEquals(1, DownloadPlanner.chunkCountFor(0L, 16))
        assertEquals(1, DownloadPlanner.chunkCountFor(-5L, 16))
    }

    @Test
    fun chunkCountForSizeTiers() {
        // < 5MB 基础值 1，但任务池抬升 want=max(1, 1*8)=8，受 1MB 单片下限钳到 4
        assertEquals(4, DownloadPlanner.chunkCountFor(4 * 1024 * 1024L, 1))
        assertEquals(8, DownloadPlanner.chunkCountFor(10 * 1024 * 1024L, 1))           // < 50MB 基础值
        assertEquals(32, DownloadPlanner.chunkCountFor(100 * 1024 * 1024L, 1))         // < 500MB 基础值
        assertEquals(64, DownloadPlanner.chunkCountFor(1024 * 1024 * 1024L, 1))        // ≥ 500MB 基础值
    }

    @Test
    fun chunkCountForThreadMultiplierAndCaps() {
        // 任务池：线程 × 8 盈余（10MB / 2 线程 → want 16，受 1MB 单片下限约束为 10）
        assertEquals(10, DownloadPlanner.chunkCountFor(10 * 1024 * 1024L, 2))
        // 512 封顶（600MB / 64 线程 → want 512）
        assertEquals(512, DownloadPlanner.chunkCountFor(600L * 1024 * 1024, 64))
        // 1MB 单片下限（10MB / 64 线程 → want 512，但最多 10 片）
        assertEquals(10, DownloadPlanner.chunkCountFor(10 * 1024 * 1024L, 64))
    }

    // ---------- planOf / planSignature ----------

    @Test
    fun planCoversTotalExactly() {
        val plan = DownloadPlanner.planOf(10L * 1024 * 1024, 2)
        // 片数 × 片大小恰好覆盖 total，且无冗余整片
        assertTrue(plan.chunkCount * plan.chunkSize >= plan.total)
        assertTrue((plan.chunkCount - 1) * plan.chunkSize < plan.total)
        // 主池 70% + 弹性区起点
        assertEquals(7, plan.mainPoolCount)
        assertEquals(7 * plan.chunkSize, plan.elasticStart)
        assertTrue(plan.elasticStart < plan.total)
    }

    @Test
    fun planSmallFileHasNoElasticRegion() {
        // ≤ 1MB：1MB 单片下限强制单片，无弹性区
        val plan = DownloadPlanner.planOf(512L * 1024, 2)
        assertEquals(1, plan.chunkCount)
        assertEquals(1, plan.mainPoolCount)
        assertEquals(plan.total, plan.elasticStart)
    }

    @Test
    fun planSignatureFormatStable() {
        val plan = ChunkPlan(total = 100, chunkCount = 10, chunkSize = 10, mainPoolCount = 7, elasticStart = 70)
        assertEquals("chunks=10 total=100 main=7", DownloadPlanner.planSignature(plan))
    }

    // ---------- resumeState ----------

    /** 构造测试计划：total=100，10 片 × 10 字节，主池 7 片（0..69），弹性区 70..99 */
    private fun testPlan() =
        ChunkPlan(total = 100, chunkCount = 10, chunkSize = 10, mainPoolCount = 7, elasticStart = 70)

    private fun writeFile(dir: File, name: String, size: Int) {
        File(dir, name).writeBytes(ByteArray(size))
    }

    @Test
    fun resumeSumsMainPoolPartsAndCompleteSegs() {
        val dir = tmp.newFolder("chunks")
        for (i in 0 until 7) writeFile(dir, "part_$i", 10)
        writeFile(dir, "seg_70_79.part", 10)
        writeFile(dir, "seg_80_89.part", 10)

        val state = DownloadPlanner.resumeState(dir, testPlan())

        assertEquals(90L, state.downloadedBytes)
        // 两个完整 seg 是弹性区前缀 → 续传起点推进到 90
        assertEquals(90L, state.elasticResumeStart)
    }

    @Test
    fun resumeDeletesIncompleteSegBeforeSumming() {
        // P1-5 回归：不完整 seg 必须先删再统计，其字节不得计入进度
        val dir = tmp.newFolder("chunks")
        for (i in 0 until 7) writeFile(dir, "part_$i", 10)
        writeFile(dir, "seg_90_99.part", 5) // 不完整（区间 10 字节，仅 5 字节）

        val state = DownloadPlanner.resumeState(dir, testPlan())

        assertEquals(70L, state.downloadedBytes) // 而非 75
        assertFalse(File(dir, "seg_90_99.part").exists()) // 已删除
        assertEquals(70L, state.elasticResumeStart) // 不完整 seg 不在完整前缀内
    }

    @Test
    fun resumeClampsToTotal() {
        // 旧 job 残留导致磁盘累计超过 total：钳制到 total，不得显示"已下载 > 总大小"
        val dir = tmp.newFolder("chunks")
        for (i in 0 until 7) writeFile(dir, "part_$i", 20) // 7 × 20 = 140 > 100

        val state = DownloadPlanner.resumeState(dir, testPlan())

        assertEquals(100L, state.downloadedBytes)
    }

    @Test
    fun elasticResumeStartAdvancesOnlyThroughContiguousPrefix() {
        val dir = tmp.newFolder("chunks")
        for (i in 0 until 7) writeFile(dir, "part_$i", 10)
        // 完整但不连续：跳过 70_79，只有 80_89 —— 前缀断裂，起点不动
        writeFile(dir, "seg_80_89.part", 10)
        writeFile(dir, "seg_90_99.part", 10)

        val state = DownloadPlanner.resumeState(dir, testPlan())

        assertEquals(90L, state.downloadedBytes) // 两个完整 seg 均计入
        assertEquals(70L, state.elasticResumeStart) // 前缀断裂：仍从 70 开始（70_79 将重下）
    }

    // ---------- missingRanges ----------

    @Test
    fun missingRangesCollectsShortPartsAndFailedElasticBlocks() {
        val dir = tmp.newFolder("chunks")
        for (i in 0 until 7) writeFile(dir, "part_$i", 10)
        writeFile(dir, "part_1", 4) // 主池第 1 片不完整
        val elastic = mapOf(
            "70_79" to ChunkResult.OK,
            "80_89" to ChunkResult.FAILED,
            "90_99" to ChunkResult.RANGE_IGNORED
        )

        val missing = DownloadPlanner.missingRanges(dir, testPlan(), elastic)

        assertEquals(3, missing.size)
        assertEquals(10L..19L, missing[0].start..missing[0].end) // 主池缺失片在前
        assertEquals("part_1", missing[0].file.name)
        assertEquals(80L..89L, missing[1].start..missing[1].end)
        assertEquals("seg_80_89.part", missing[1].file.name)
        assertEquals(90L..99L, missing[2].start..missing[2].end) // RANGE_IGNORED 同样需重试
        assertEquals("seg_90_99.part", missing[2].file.name)
    }

    @Test
    fun missingRangesEmptyWhenAllComplete() {
        val dir = tmp.newFolder("chunks")
        for (i in 0 until 7) writeFile(dir, "part_$i", 10)
        val elastic = mapOf("70_79" to ChunkResult.OK)

        assertTrue(DownloadPlanner.missingRanges(dir, testPlan(), elastic).isEmpty())
    }

    // ---------- ElasticAllocator ----------

    @Test
    fun allocatorHandsOutSequentialBlocksUntilExhausted() {
        val alloc = ElasticAllocator(total = 10_000_000L, elasticStart = 0L)
        val b1 = alloc.take()
        assertEquals(0L..4_194_303L, b1)
        val b2 = alloc.take()
        assertEquals(4_194_304L..8_388_607L, b2)
        val b3 = alloc.take() // 尾部不足 4MB 整块领取
        assertEquals(8_388_608L..9_999_999L, b3)
        assertNull(alloc.take())
    }

    @Test
    fun skipToOnlyMovesForward() {
        val alloc = ElasticAllocator(total = 10_000_000L, elasticStart = 0L)
        alloc.skipTo(5_000_000L)
        assertEquals(5_000_000L..9_194_303L, alloc.take())
        alloc.skipTo(1_000_000L) // 只前进：不回退
        assertEquals(9_194_304L..9_999_999L, alloc.take())
    }
}
