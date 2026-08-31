package com.yunx.app.data.network

class PasscodeRequiredException(message: String = "该分享需要提取码") :
    IllegalStateException(message)

class InvalidPasscodeException(message: String = "提取码错误") :
    IllegalStateException(message)

class RateLimitedException(message: String = "请求过于频繁") :
    IllegalStateException(message)

class LinkExpiredException(message: String = "分享链接已失效") :
    IllegalStateException(message)

class ProtocolChangedException(val platform: String) :
    IllegalStateException("$platform 接口响应结构已变化")

internal object PlatformHttpErrors {
    fun throwIfRateLimited(statusCode: Int) {
        if (statusCode == 429) throw RateLimitedException()
    }
}
