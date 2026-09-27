package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** 边写边算摘要的流式拷贝单测（保存阶段单遍 SHA-256 的核心循环，不依赖 Android 框架）。 */
class StreamCopyDigestTest {
    @Test
    fun copiesBytesAndAccumulatesDigest() {
        val payload = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
        val digest = MessageDigest.getInstance("SHA-256")
        val out = ByteArrayOutputStream()
        val total = DownloadSaver.copyToDigest(ByteArrayInputStream(payload), out, digest)

        assertEquals(payload.size.toLong(), total)
        assertTrue(out.toByteArray().contentEquals(payload))
        val expected = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals(expected, FileIntegrity.hexOf(digest))
        assertTrue(FileIntegrity.isValidSha256Hex(expected))
    }

    @Test
    fun copiesWithoutDigestWhenNotRequired() {
        val out = ByteArrayOutputStream()
        val total = DownloadSaver.copyToDigest(ByteArrayInputStream("hello".toByteArray()), out, null)

        assertEquals(5L, total)
        assertEquals("hello", out.toString(Charsets.UTF_8))
    }

    @Test
    fun emptyInputCopiesZeroBytes() {
        val digest = MessageDigest.getInstance("SHA-256")
        val out = ByteArrayOutputStream()
        val total = DownloadSaver.copyToDigest(ByteArrayInputStream(ByteArray(0)), out, digest)

        assertEquals(0L, total)
        assertEquals(0, out.size())
    }

    @Test
    fun rejectsMalformedSha256Hex() {
        assertFalse(FileIntegrity.isValidSha256Hex(""))
        assertFalse(FileIntegrity.isValidSha256Hex("not-a-sha256"))
        assertFalse(FileIntegrity.isValidSha256Hex("0".repeat(63)))
        assertFalse(FileIntegrity.isValidSha256Hex("G".repeat(64)))
    }
}
