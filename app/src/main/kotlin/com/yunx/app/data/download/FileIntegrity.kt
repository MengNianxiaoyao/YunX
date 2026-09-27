package com.yunx.app.data.download

import java.io.File
import java.security.MessageDigest

object FileIntegrity {
    /** SHA-256 十六进制摘要的合法形状（64 位小写 hex），调用方用它决定是否启用校验。 */
    fun isValidSha256Hex(value: String): Boolean =
        value.matches(Regex("[0-9a-f]{64}"))

    /** 取 MessageDigest 当前累计值的十六进制摘要（不重置 digest，可在流式写入后一次性比对）。 */
    fun hexOf(digest: MessageDigest): String =
        digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    fun matchesSha256(file: File, expectedSha256: String): Boolean {
        return matchesSha256(listOf(file), expectedSha256)
    }

    fun matchesSha256(files: List<File>, expectedSha256: String): Boolean {
        val expected = expectedSha256.trim().lowercase()
        if (expected.isEmpty()) return true
        if (!expected.matches(Regex("[0-9a-f]{64}"))) return false

        val digest = MessageDigest.getInstance("SHA-256")
        files.forEach { file ->
            file.inputStream().buffered().use { input ->
                updateDigest(digest, input)
            }
        }
        val actual = digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return actual == expected
    }

    private fun updateDigest(digest: MessageDigest, input: java.io.InputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
}
