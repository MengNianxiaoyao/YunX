package com.yunx.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogRedactorTest {
    @Test
    fun removesPathQueryFragmentAndUserInfo() {
        val redacted = LogRedactor.url("https://user:pass@cdn.example:8443/private/a?sign=secret#token")
        assertEquals("https://cdn.example:8443", redacted)
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("private"))
        assertFalse(redacted.contains("user"))
    }

    @Test
    fun handlesInvalidAndRelativeValues() {
        assertEquals("<relative-url>", LogRedactor.url("segment.ts?token=secret"))
        assertEquals("<none>", LogRedactor.url(null))
    }

    @Test
    fun redactsUrlsAndKnownSecretAssignmentsFromExportedLines() {
        val line = LogRedactor.line(
            "download https://cdn.example/private.bin?sign=url-secret Cookie=session-secret access_token=jwt-secret"
        )
        assertFalse(line.contains("url-secret"))
        assertFalse(line.contains("session-secret"))
        assertFalse(line.contains("jwt-secret"))
        assertEquals("download https://cdn.example Cookie=<redacted> access_token=<redacted>", line)
    }

    @Test
    fun redactsAdditionalPlatformCredentials() {
        val line = LogRedactor.line("os_sso_sid=sso pass_code_token=pass bdstoken=bd")
        assertEquals("os_sso_sid=<redacted> pass_code_token=<redacted> bdstoken=<redacted>", line)
    }

    @Test
    fun redactsJsonCredentialsAndExceptionMessages() {
        val line = LogRedactor.line("{\"cookie\":\"session-secret\",\"access_token\":\"jwt-secret\"}")
        assertFalse(line.contains("session-secret"))
        assertFalse(line.contains("jwt-secret"))
        assertEquals("{\"cookie\":<redacted>,\"access_token\":<redacted>}", line)

        assertEquals("request https://cdn.example", LogRedactor.error(
            IllegalStateException("request https://cdn.example/file?sign=secret")
        ))
    }
}
