package com.yunx.app.data.network

class PasscodeRequiredException(message: String = "该分享需要提取码") :
    IllegalStateException(message)

class InvalidPasscodeException(message: String = "提取码错误") :
    IllegalStateException(message)

class RateLimitedException(message: String = "请求过于频繁") :
    IllegalStateException(message)
