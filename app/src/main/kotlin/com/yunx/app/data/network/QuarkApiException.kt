package com.yunx.app.data.network

/**
 * 夸克 API 业务异常（携带服务端返回的 message 与 code 字段）：
 * 用于把服务端原因（如「提取码错误」「分享已失效」「file not found」）透传给 UI。
 * code 用于识别特定错误（如 21001 提取码错误重定向）。
 * P2-5：继承 AliDriveApiException（与 UC 共享基类），夸克侧既有 catch 全部兼容。
 */
class QuarkApiException(message: String, code: Int = 0) : AliDriveApiException(message, code)