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
        assertEquals("0", ResolvePlatformDefaults.capabilities(SharePlatform.QUARK).rootDir)
        assertEquals("0", ResolvePlatformDefaults.capabilities(SharePlatform.UC).rootDir)
        assertEquals("", ResolvePlatformDefaults.capabilities(SharePlatform.XUNLEI).rootDir)
        assertEquals("/", ResolvePlatformDefaults.capabilities(SharePlatform.BAIDU).rootDir)
        assertEquals("/", ResolvePlatformDefaults.capabilities(SharePlatform.C139).rootDir)
        assertEquals("0", ResolvePlatformDefaults.capabilities(SharePlatform.PAN123).rootDir)
    }

    @Test
    fun mapsPlatformDownloadHeaders() {
        val credential = "secret"

        assertEquals(
            credential,
            ResolvePlatformDefaults.downloadHeaders(SharePlatform.QUARK, credential)["Cookie"]
        )
        val ucHeaders = ResolvePlatformDefaults.downloadHeaders(SharePlatform.UC, credential)
        assertEquals(credential, ucHeaders["Cookie"])
        assertTrue(ucHeaders.containsKey("Referer"))
        assertTrue(ucHeaders.containsKey("Origin"))
        assertEquals(
            credential,
            ResolvePlatformDefaults.downloadHeaders(SharePlatform.BAIDU, credential)["Cookie"]
        )

        listOf(SharePlatform.XUNLEI, SharePlatform.C139, SharePlatform.PAN123).forEach { platform ->
            assertFalse(ResolvePlatformDefaults.downloadHeaders(platform, credential).containsKey("Cookie"))
        }
        assertTrue(
            ResolvePlatformDefaults.downloadHeaders(SharePlatform.PAN123, credential)
                .containsKey("Referer")
        )
    }
}
