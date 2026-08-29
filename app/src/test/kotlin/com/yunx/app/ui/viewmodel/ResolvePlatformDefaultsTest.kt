package com.yunx.app.ui.viewmodel

import com.yunx.app.data.network.SharePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun modelsShareDownloadCapabilities() {
        val transferPlatforms = setOf(
            SharePlatform.QUARK,
            SharePlatform.XUNLEI,
            SharePlatform.BAIDU
        )

        SharePlatform.values().forEach { platform ->
            val capabilities = ResolvePlatformDefaults.capabilities(platform)
            assertEquals(ResolvePlatformDefaults.displayName(platform), capabilities.name)
            assertEquals(
                platform in transferPlatforms,
                capabilities.requiresTransferForShareDownload
            )
            assertTrue(capabilities.supportsShareSave)
            assertTrue(capabilities.supportsFolderDownload)
        }

        assertTrue(ResolvePlatformDefaults.capabilities(SharePlatform.UC).supportsShareVideoPreview)
        assertFalse(ResolvePlatformDefaults.capabilities(SharePlatform.QUARK).supportsShareVideoPreview)
        assertEquals("", ResolvePlatformDefaults.capabilities(SharePlatform.XUNLEI).rootDir)
        assertEquals("/", ResolvePlatformDefaults.capabilities(SharePlatform.BAIDU).rootDir)
        assertEquals("/", ResolvePlatformDefaults.capabilities(SharePlatform.C139).rootDir)
    }
}
