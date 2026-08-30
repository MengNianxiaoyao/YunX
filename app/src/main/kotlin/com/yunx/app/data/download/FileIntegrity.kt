package com.yunx.app.data.download

import java.io.File
import java.security.MessageDigest

object FileIntegrity {
    fun matchesSha256(file: File, expectedSha256: String): Boolean {
        val expected = expectedSha256.trim().lowercase()
        if (expected.isEmpty()) return true
        if (!expected.matches(Regex("[0-9a-f]{64}"))) return false

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        return actual == expected
    }
}
