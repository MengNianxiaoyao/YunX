package com.yunx.app.data.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 平台级控制面请求节流器（P1-6）：
 * 限制对同一平台的控制面请求（转存/取链/删除/分享等风控敏感操作）的最小起点间隔，
 * 使「转存 → 取链 → 删除」这类链路不会在同一秒内打完。
 *
 * 与 SpeedLimiter（下载数据面的字节级限速）是两回事，不要混用：
 * - 本类管「请求何时发出」（控制面频率）；
 * - SpeedLimiter 管「数据以多快流入」（数据面带宽）。
 *
 * 语义：同一平台的请求在门内排队，相邻两个请求的**起点**间隔不小于配置值；
 * 等待期间协程挂起（delay），不占用线程。只间隔请求起点、不串行整个 HTTP 请求。
 * 未配置间隔的平台直接放行（夸克/UC/迅雷/139/123 风险低于百度，接入只需加一行 map 条目）。
 */
object PlatformRateLimiter {

    /** 平台标识 */
    const val BAIDU = "baidu"

    /** 平台 → 控制面请求最小起点间隔（毫秒） */
    private val intervalsMs = mapOf(
        // 百度：风控最敏感平台；转存→取链→删除三步各间隔 500ms，全链 ≥1s
        BAIDU to 500L
    )

    private val lastStartAt = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** 等待该平台控制面请求的发出轮次（排队 + 最小起点间隔） */
    suspend fun awaitTurn(platform: String) {
        val interval = intervalsMs[platform] ?: return
        val lock = locks.getOrPut(platform) { Mutex() }
        lock.withLock {
            val now = System.nanoTime()
            val last = lastStartAt[platform] ?: 0L
            val waitNanos = last + interval * 1_000_000 - now
            if (waitNanos > 0) delay((waitNanos + 999_999) / 1_000_000)
            lastStartAt[platform] = System.nanoTime()
        }
    }
}
