package com.yunx.app.data.network

import android.content.Context
import kotlin.random.Random

/**
 * 139 设备指纹管理器（X-Deviceinfo / x-yun-client-info 中的 32 位 hex）：
 * - 首次启动生成 32 位 hex 并持久化，此后跨进程重启永久复用；
 * - 未初始化（异常路径）时回退到原抓包共享指纹（保持旧行为，绝不崩）。
 *
 * 目的：原值为全体用户共用的抓包常量，平台看到"一台设备、大量 IP/账号"是强滥用信号，
 * 任一用户触发风控全体连带受损；每设备独立指纹消除该集体风险。
 * 与 XunleiDeviceFingerprint 同一模式。
 *
 * 已验证安全：C139Api.calSign 只对 bodyJson + ts + rand 计算，设备头不参与签名；
 * SHARE_X_DEVICEINFO（分享读取/取链）不含指纹，保持常量不动。
 */
object C139DeviceFingerprint {

    private const val PREFS = "c139_device_fp"
    private const val KEY_HEX = "device_hex"
    private const val HEX = "0123456789abcdef"

    /** 未初始化时的 fallback：官方抓包共享指纹（保持旧行为，绝不崩） */
    private const val FALLBACK_HEX = "2cdaf7ada9e353c70eba99092e177991"

    @Volatile
    private var initialized = false

    @Volatile
    private var hex: String = FALLBACK_HEX

    /** 进程启动时调用一次（Application.onCreate）；幂等，可重复调用 */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_HEX, null)
            if (saved != null) {
                hex = saved
            } else {
                val generated = randomHex(32)
                prefs.edit().putString(KEY_HEX, generated).apply()
                hex = generated
            }
            initialized = true
        }
    }

    /** X-Deviceinfo（个人网盘管理：列目录/重命名/移动/删除/取链） */
    fun xDeviceInfo(): String =
        "||9|7.17.9|chrome|116.0.0.0|$hex||windows 10||zh-CN|||"

    /** x-yun-client-info（个人网盘管理，末段为 base64("undefined")） */
    fun xClientInfo(): String =
        "||9|7.17.9|chrome|116.0.0.0|$hex||windows 10||zh-CN|||dW5kZWZpbmVk||"

    private fun randomHex(len: Int): String = buildString {
        repeat(len) { append(HEX[Random.nextInt(16)]) }
    }
}
