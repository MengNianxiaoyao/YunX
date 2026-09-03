package com.yunx.app.data.network.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudCredentialTest {
    @Test
    fun distinguishesCookieAndAccessToken() {
        val cookie: CloudCredential = CloudCredential.Cookie("BDUSS=secret")
        val token: CloudCredential = CloudCredential.AccessToken("jwt-secret")

        assertTrue(cookie is CloudCredential.Cookie)
        assertTrue(token is CloudCredential.AccessToken)
        assertEquals("BDUSS=secret", cookie.value)
        assertEquals("jwt-secret", token.value)
    }

    @Test
    fun rejectsBlankCredentials() {
        assertFalse(CloudCredential.Cookie("").isUsable())
        assertFalse(CloudCredential.AccessToken(" ").isUsable())
        assertTrue(CloudCredential.AccessToken("token").isUsable())
    }

    @Test
    fun xunleiCarriesTripleWithTokenAsValue() {
        val credential = CloudCredential.Xunlei(
            accessToken = "token",
            deviceId = "device",
            captchaToken = "captcha"
        )

        assertEquals("token", credential.value)
        assertEquals("device", credential.deviceId)
        assertEquals("captcha", credential.captchaToken)
        assertTrue(credential.isUsable())
        assertFalse(credential.copy(accessToken = " ").isUsable())
    }

    @Test
    fun narrowsCredentialToPlatformType() {
        val cookie: CloudCredential = CloudCredential.Cookie("cookie")
        val token: CloudCredential = CloudCredential.AccessToken("token")
        val xunlei: CloudCredential = CloudCredential.Xunlei("token", "device", "captcha")

        assertEquals(cookie, cookie.requireCookie())
        assertEquals(token, token.requireAccessToken())
        assertEquals(xunlei, xunlei.requireXunlei())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCookieWhereTokenRequired() {
        CloudCredential.Cookie("cookie").requireAccessToken()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTokenWhereXunleiRequired() {
        CloudCredential.AccessToken("token").requireXunlei()
    }
}
