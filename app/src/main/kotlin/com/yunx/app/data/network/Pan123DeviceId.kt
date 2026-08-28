package com.yunx.app.data.network

import android.content.Context
import com.yunx.app.data.network.Pan123Constants.newLoginUuid

/**
 * 123 云盘设备标识管理器（loginuuid 持久化）：
 * - 首次启动生成 32 位 hex 并持久化，此后跨进程重启永久复用；
 * - 未初始化（异常路径）时回退到进程内随机值（不崩，仅退化）。
 *
 * 目的：loginuuid 是设备维度标识（文档 §3.2，不参与签名），若每次启动都变，
 * 平台看到的是"同一账号不断换设备"（设备农场特征）；持久化后每台设备稳定唯一。
 * 与 XunleiDeviceFingerprint 同一模式。
 */
object Pan123DeviceId {

    private const val PREFS = "pan123_device_id"
    private const val KEY_LOGIN_UUID = "loginuuid"

    @Volatile
    private var initialized = false

    /** 未初始化时的 fallback：进程内固定随机值（好于每次请求都变） */
    @Volatile
    private var cached: String = newLoginUuid()

    /** 进程启动时调用一次（Application.onCreate）；幂等，可重复调用 */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getString(KEY_LOGIN_UUID, null)
            if (saved != null) {
                cached = saved
            } else {
                val generated = newLoginUuid()
                prefs.edit().putString(KEY_LOGIN_UUID, generated).apply()
                cached = generated
            }
            initialized = true
        }
    }

    fun value(): String = cached
}
