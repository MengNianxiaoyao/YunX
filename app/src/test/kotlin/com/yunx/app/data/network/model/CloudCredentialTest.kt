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
}
