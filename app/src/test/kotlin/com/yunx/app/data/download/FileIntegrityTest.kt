package com.yunx.app.data.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileIntegrityTest {
    @Test
    fun acceptsMatchingSha256CaseInsensitively() = withTempFile("hello") { file ->
        assertTrue(
            FileIntegrity.matchesSha256(
                file,
                "2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824"
            )
        )
    }

    @Test
    fun rejectsMismatchedOrMalformedSha256() = withTempFile("hello") { file ->
        assertFalse(FileIntegrity.matchesSha256(file, "0".repeat(64)))
        assertFalse(FileIntegrity.matchesSha256(file, "not-a-sha256"))
    }

    @Test
    fun skipsVerificationWhenHashIsAbsent() = withTempFile("hello") { file ->
        assertTrue(FileIntegrity.matchesSha256(file, ""))
    }

    private fun withTempFile(content: String, block: (File) -> Unit) {
        val file = File.createTempFile("yunx-integrity-", ".tmp")
        try {
            file.writeText(content)
            block(file)
        } finally {
            file.delete()
        }
    }
}
