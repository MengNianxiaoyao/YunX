package com.yunx.app

import android.app.Application
import com.yunx.app.crash.CrashHandler
import com.yunx.app.data.db.AppDatabase
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
        }
        // 迅雷动态设备指纹：首次启动生成并持久化（开源分发后每台设备独立指纹）
        com.yunx.app.data.network.XunleiDeviceFingerprint.init(this)
        // 123 设备标识持久化（loginuuid 跨进程稳定，不再每次启动更换）
        com.yunx.app.data.network.Pan123DeviceId.init(this)
        // 139 动态设备指纹（X-Deviceinfo/x-yun-client-info 的 hex 每设备独立，替代共享抓包常量）
        com.yunx.app.data.network.C139DeviceFingerprint.init(this)
    }
}
