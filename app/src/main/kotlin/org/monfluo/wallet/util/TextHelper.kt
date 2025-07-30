package org.monfluo.wallet.util

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Build.VERSION_CODES
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.core.text.toSpannable
import org.monfluo.wallet.R
import org.monfluo.wallet.model.MoneroUnit
import java.math.BigDecimal
import java.math.RoundingMode

object TextHelper {

    private fun <S : Spannable> tryMonospaceize(spannable: S, start: Int = 0, end: Int = spannable.length, flags: Int = Spannable.SPAN_INCLUSIVE_EXCLUSIVE, style: Int = Typeface.NORMAL): S {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
            spannable.setSpan(TypefaceSpan(Typeface.MONOSPACE), start, end, flags)
        }
        spannable.setSpan(StyleSpan(style), start, end, flags)
        return spannable
    }

    fun getDisplayAmount(amount: Long, unit: MoneroUnit, streetMode: Boolean,): String =
        if (streetMode) unit.streetModeText
        else getDecimalAmount(amount, unit.decimals).setScale(unit.decimals, RoundingMode.HALF_UP).toPlainString()

    private fun getDecimalAmount(amount: Long, decimals: Int): BigDecimal {
        return BigDecimal(amount).scaleByPowerOfTen(-decimals)
    }

    fun formatUnlockedBalance(context: Context, balance: Long, unit: MoneroUnit, streetMode: Boolean, style: Int): SpannableStringBuilder = SpannableStringBuilder()
        .append(tryMonospaceize(getDisplayAmount(balance, unit, streetMode).toSpannable(), style = style))
        .append(' ')
        .append(context.getString(unit.unitResourceId))

    fun formatLockedBalance(context: Context, balance: Long, unit: MoneroUnit, streetMode: Boolean, style: Int): SpannableStringBuilder = formatUnlockedBalance(context, balance, unit, streetMode, style)
        .append(context.getString(R.string.balance_locked))

    fun formatFrozenBalance(context: Context, balance: Long, unit: MoneroUnit, streetMode: Boolean, style: Int): SpannableStringBuilder = formatUnlockedBalance(context, balance, unit, streetMode, style)
        .append(' ')
        .append(context.getString(R.string.balance_frozen))
}