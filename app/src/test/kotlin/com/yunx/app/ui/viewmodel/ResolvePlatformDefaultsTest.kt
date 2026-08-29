package com.yunx.app.ui.viewmodel

import com.yunx.app.data.network.SharePlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolvePlatformDefaultsTest {
    @Test
    fun mapsEveryPlatformToStableDisplayNameAndRoot() {
        assertEquals("夸克网盘", ResolvePlatformDefaults.displayName(SharePlatform.QUARK))
        assertEquals("UC 网盘", ResolvePlatformDefaults.displayName(SharePlatform.UC))
        assertEquals("迅雷网盘", ResolvePlatformDefaults.displayName(SharePlatform.XUNLEI))
        assertEquals("百度网盘", ResolvePlatformDefaults.displayName(SharePlatform.BAIDU))
        assertEquals("139 网盘", ResolvePlatformDefaults.displayName(SharePlatform.C139))
        assertEquals("123云盘", ResolvePlatformDefaults.displayName(SharePlatform.PAN123))
        assertEquals("0", ResolvePlatformDefaults.defaultDirFid(SharePlatform.PAN123))
        assertEquals("", ResolvePlatformDefaults.defaultDirFid(SharePlatform.BAIDU))
    }
}
