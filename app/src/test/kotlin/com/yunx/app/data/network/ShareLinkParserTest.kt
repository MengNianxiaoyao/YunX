package com.yunx.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkParserTest {

    @Test
    fun parsesAllSupportedPlatforms() {
        val cases = listOf(
            "https://pan.quark.cn/s/Abc123?pwd=a1B2" to (SharePlatform.QUARK to "Abc123"),
            "https://drive.uc.cn/s/Abc123" to (SharePlatform.UC to "Abc123"),
            "https://pan.xunlei.com/s/Abc_123-xy" to (SharePlatform.XUNLEI to "Abc_123-xy"),
            "https://pan.baidu.com/s/1Abc_123-xy?pwd=9xYz" to (SharePlatform.BAIDU to "Abc_123-xy"),
            "https://yun.139.com/shareweb/#/w/i/Abc_123" to (SharePlatform.C139 to "Abc_123"),
            "https://www.123pan.com/s/2785Vv-T4Ded" to (SharePlatform.PAN123 to "2785Vv-T4Ded")
        )

        cases.forEach { (text, expected) ->
            val parsed = ShareLinkParser.parse(text)!!
            assertEquals(expected.first, parsed.platform)
            assertEquals(expected.second, parsed.shareId)
        }
    }

    @Test
    fun explicitTextPasswordIsExtracted() {
        val parsed = ShareLinkParser.parse("链接 https://drive.uc.cn/s/Abc123 提取码：a1B2")!!
        assertEquals("a1B2", parsed.pwd)
    }

    @Test
    fun unrelatedUrlBeforeShareLinkDoesNotHideSupportedLink() {
        val parsed = ShareLinkParser.parse(
            "参考 https://example.com/help，再打开 https://pan.quark.cn/s/Abc123"
        )!!

        assertEquals(SharePlatform.QUARK, parsed.platform)
        assertEquals("Abc123", parsed.shareId)
    }

    @Test
    fun passwordSeparatedByWhitespaceIsExtracted() {
        val parsed = ShareLinkParser.parse("https://drive.uc.cn/s/Abc123 提取码 a1B2")!!

        assertEquals("a1B2", parsed.pwd)
    }

    @Test
    fun alternatePasswordQueryNamesAreExtracted() {
        assertEquals("a1B2", ShareLinkParser.parse("https://drive.uc.cn/s/Abc123?p=a1B2")?.pwd)
        assertEquals("c3D4", ShareLinkParser.parse("https://drive.uc.cn/s/Abc123?passcode=c3D4")?.pwd)
    }

    @Test
    fun rejectsUnrelatedUrl() {
        assertNull(ShareLinkParser.parse("https://example.com/s/Abc123"))
    }
}
