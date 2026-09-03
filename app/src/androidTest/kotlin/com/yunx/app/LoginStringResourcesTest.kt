package com.yunx.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginStringResourcesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun formatsLoginTitles() {
        assertEquals(
            "夸克网盘登录",
            context.getString(
                R.string.login_title_format,
                context.getString(R.string.platform_quark)
            )
        )
        assertEquals(
            "139网盘登录",
            context.getString(
                R.string.login_title_format,
                context.getString(R.string.platform_c139)
            )
        )
    }

    @Test
    fun exposesCommonLoginStrings() {
        assertEquals("手动输入 Cookie", context.getString(R.string.login_manual_cookie_description))
        assertEquals("保存", context.getString(R.string.login_save_action))
        assertEquals("登录成功", context.getString(R.string.login_success))
        assertEquals("未检测到登录态，请先完成登录", context.getString(R.string.login_not_detected))
        assertEquals("登录教程", context.getString(R.string.login_tutorial_title))
        assertEquals("知道了", context.getString(R.string.login_tutorial_got_it))
        assertEquals("粘贴 Cookie…", context.getString(R.string.login_cookie_placeholder))
    }

    @Test
    fun exposesPlatformLoginHints() {
        assertEquals(
            "Cookie 无效，请检查是否包含 __pus= 与 __puus=",
            context.getString(R.string.login_cookie_invalid_pus)
        )
        assertEquals(
            "Cookie 无效，请检查是否包含 BDUSS=",
            context.getString(R.string.login_cookie_invalid_bduss)
        )
        assertEquals(
            "Cookie 无效，请检查是否包含 Os_SSo_Sid= 与 RMKEY=",
            context.getString(R.string.login_cookie_invalid_c139)
        )
        assertEquals("页面加载异常，正在重试…", context.getString(R.string.login_page_retrying))
    }

    @Test
    fun exposesBaiduRiskStrings() {
        assertEquals("风险提示", context.getString(R.string.login_risk_title))
        assertEquals("我已了解，继续", context.getString(R.string.login_risk_continue))
        assertEquals("暂不使用", context.getString(R.string.login_risk_dismiss))
    }

    @Test
    fun exposesXunleiFormStrings() {
        assertEquals("登录迅雷网盘", context.getString(R.string.login_xunlei_heading))
        assertEquals("短信验证", context.getString(R.string.login_xunlei_heading_sms))
        assertEquals(
            "账号密码登录触发安全验证，验证码已发送至 13800000000",
            context.getString(R.string.login_xunlei_sms_sent, "13800000000")
        )
        assertEquals("手机号 / 邮箱", context.getString(R.string.login_label_phone_email))
        assertEquals("验证并登录", context.getString(R.string.login_action_verify))
        assertEquals("发送验证码", context.getString(R.string.login_action_send_code))
        assertEquals("验证码已发送", context.getString(R.string.login_code_sent))
        assertEquals("迅雷安全验证", context.getString(R.string.login_xunlei_verify_title))
    }

    @Test
    fun exposesPan123Strings() {
        assertEquals("登录123云盘", context.getString(R.string.login_pan123_heading))
        assertEquals("手机号 / 账号", context.getString(R.string.login_label_phone_account))
        assertEquals("登录中…", context.getString(R.string.login_logging_in))
    }
}
