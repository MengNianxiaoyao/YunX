package com.yunx.app.util

import android.webkit.CookieManager

object WebViewCookieCleaner {
    fun clearDomains(cookieManager: CookieManager, vararg domains: String) {
        domains.forEach { domain ->
            cookieManager.getCookie(domain)?.split(';')?.forEach { part ->
                val name = part.substringBefore('=').trim()
                if (name.isNotBlank()) cookieManager.setCookie(domain, "$name=; Max-Age=0; Path=/")
            }
        }
        cookieManager.flush()
    }
}
