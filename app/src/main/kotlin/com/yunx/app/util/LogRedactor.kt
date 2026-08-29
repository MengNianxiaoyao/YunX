package com.yunx.app.util

import java.net.URI

/** Redacts capability-bearing URL paths, queries, fragments and user info. */
object LogRedactor {
    private val absoluteUrl = Regex("""https?://[^\s\"'<>]+""", RegexOption.IGNORE_CASE)
    private val secretAssignment = Regex(
        """(?i)\b(cookie|authorization|access[_-]?token|refresh[_-]?token|captcha[_-]?token|bduss|stoken|__puus|__pus|rmkey|signature|sign|os_sso_sid|pass_code_token|share_fid_token|fids_token|sekey|randsk|userdata|bdstoken)\b(\s*[=:]\s*)([^\s,;]+)"""
    )
    private val secretJsonAssignment = Regex(
        """(?i)("|')?(cookie|authorization|access[_-]?token|refresh[_-]?token|captcha[_-]?token|bduss|stoken|__puus|__pus|rmkey|signature|sign|os_sso_sid|pass_code_token|share_fid_token|fids_token|sekey|randsk|userdata|bdstoken)\1\s*:\s*("|')[^"']*(\3)"""
    )

    fun url(value: Any?): String {
        val raw = value?.toString()?.takeIf { it.isNotBlank() } ?: return "<none>"
        return runCatching {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase() ?: return@runCatching "<relative-url>"
            val host = uri.host?.lowercase() ?: return@runCatching "<$scheme-url>"
            val defaultPort = (scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)
            val port = if (uri.port >= 0 && !defaultPort) ":${uri.port}" else ""
            "$scheme://$host$port"
        }.getOrDefault("<invalid-url>")
    }

    fun line(value: String): String {
        val withoutUrls = absoluteUrl.replace(value) { match -> url(match.value) }
        val withoutAssignments = secretAssignment.replace(withoutUrls) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}<redacted>"
        }
        return secretJsonAssignment.replace(withoutAssignments) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[1]}:<redacted>"
        }
    }

    /** Exception messages can contain signed URLs or server-returned credential fields. */
    fun error(value: Throwable?): String = line(value?.message.orEmpty()).ifBlank { "${value?.javaClass?.simpleName ?: "UnknownError"}" }
}
