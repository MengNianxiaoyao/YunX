package com.yunx.app.ui.text

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.yunx.app.R
import com.yunx.app.data.error.YunxError

sealed interface UiText {
    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class Plural(
        @PluralsRes val resId: Int,
        val quantity: Int,
        val args: List<Any> = emptyList()
    ) : UiText

    data class Raw(val value: String) : UiText

}

fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Resource -> context.getString(resId, *resolveArgs(context, args))
    is UiText.Plural -> context.resources.getQuantityString(
        resId,
        quantity,
        *resolveArgs(context, args)
    )
    is UiText.Raw -> value
}

private fun resolveArgs(context: Context, args: List<Any>): Array<Any> =
    args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray()

fun YunxError.toUiText(fallback: UiText): UiText = when (this) {
    YunxError.AuthExpired -> UiText.Resource(R.string.error_auth_expired)
    YunxError.PasscodeRequired -> UiText.Resource(R.string.error_passcode_required)
    YunxError.InvalidPasscode -> UiText.Resource(R.string.error_invalid_passcode)
    YunxError.RateLimited -> UiText.Resource(R.string.error_rate_limited)
    YunxError.NetworkUnavailable -> UiText.Resource(R.string.error_network_unavailable)
    YunxError.LinkExpired -> UiText.Resource(R.string.error_link_expired)
    YunxError.RangeUnsupported -> UiText.Resource(R.string.error_range_unsupported)
    YunxError.StorageDenied -> UiText.Resource(R.string.error_storage_denied)
    YunxError.IntegrityCheckFailed -> UiText.Resource(R.string.error_integrity_check_failed)
    is YunxError.ProtocolChanged -> UiText.Resource(
        R.string.error_protocol_changed,
        listOf(platform)
    )
    is YunxError.Unknown -> fallback
}
