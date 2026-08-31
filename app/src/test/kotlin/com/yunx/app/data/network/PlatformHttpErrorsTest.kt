package com.yunx.app.data.network

import org.junit.Assert.assertThrows
import org.junit.Test

class PlatformHttpErrorsTest {
    @Test
    fun throwsTypedRateLimitOnlyForHttp429() {
        assertThrows(RateLimitedException::class.java) {
            PlatformHttpErrors.throwIfRateLimited(429)
        }

        PlatformHttpErrors.throwIfRateLimited(200)
        PlatformHttpErrors.throwIfRateLimited(401)
        PlatformHttpErrors.throwIfRateLimited(500)
    }
}
