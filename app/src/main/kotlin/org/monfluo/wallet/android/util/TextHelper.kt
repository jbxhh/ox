package org.monfluo.wallet.android.util

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.Build.VERSION_CODES
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.core.text.toSpannable
import org.monfluo.wallet.android.R
import org.monfluo.wallet.android.model.MoneroUnit
import java.math.BigDecimal
import java.math.RoundingMode

private const val ADDRESS_CHUNK_LENGTH = 6
private val HIGHLIGHTED_ADDRESS_CHUNKS = setOf(0, 2, -1, -3)

object TextHelper {
    fun parseAmountToAtomicUnits(
        string: String,
        unit: MoneroUnit,
    ): Long? = BigDecimal(string).scaleByPowerOfTen(unit.decimals).longValueExact()

    private fun <S : Spannable> tryMonospaceize(
        spannable: S,
        start: Int = 0,
        end: Int = spannable.length,
        flags: Int = Spannable.SPAN_INCLUSIVE_EXCLUSIVE,
        style: Int = Typeface.NORMAL,
        color: Int? = null,
    ): S {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
            spannable.setSpan(TypefaceSpan(Typeface.MONOSPACE), start, end, flags)
        }
        spannable.setSpan(StyleSpan(style), start, end, flags)
        if (color != null) {
            spannable.setSpan(ForegroundColorSpan(color), start, end, flags)
        }
        return spannable
    }

    fun getDisplayAmount(
        amount: Long,
        unit: MoneroUnit,
        streetMode: Boolean,
    ): String =
        if (streetMode) {
            unit.streetModeText
        } else {
            getDecimalAmount(amount, unit.decimals).setScale(unit.decimals, RoundingMode.HALF_UP).toPlainString()
        }

    private fun getDecimalAmount(
        amount: Long,
        decimals: Int,
    ): BigDecimal = BigDecimal(amount).scaleByPowerOfTen(-decimals)

    fun formatUnlockedBalance(
        context: Context,
        balance: Long,
        unit: MoneroUnit,
        streetMode: Boolean,
        style: Int,
        color: Int? = null,
    ): SpannableStringBuilder =
        SpannableStringBuilder()
            .append(tryMonospaceize(getDisplayAmount(balance, unit, streetMode).toSpannable(), style = style, color = color))
            .append(' ')
            .append(context.getString(unit.unitResourceId))

    fun formatLockedBalance(
        context: Context,
        balance: Long,
        unit: MoneroUnit,
        streetMode: Boolean,
        style: Int,
    ): SpannableStringBuilder =
        formatUnlockedBalance(context, balance, unit, streetMode, style)
            .append(' ')
            .append(context.getString(R.string.balance_locked))

    fun formatFrozenBalance(
        context: Context,
        balance: Long,
        unit: MoneroUnit,
        streetMode: Boolean,
        style: Int,
    ): SpannableStringBuilder =
        formatUnlockedBalance(context, balance, unit, streetMode, style)
            .append(' ')
            .append(context.getString(R.string.balance_frozen))

    fun formatAddress(address: String): SpannableStringBuilder {
        val formattedAddress = SpannableStringBuilder()
        val chunks = address.chunked(ADDRESS_CHUNK_LENGTH)
        val chunksSize = chunks.size
        chunks.forEachIndexed { index, chunk ->
            val spannable = chunk.toSpannable()
            if (HIGHLIGHTED_ADDRESS_CHUNKS.contains(index) || HIGHLIGHTED_ADDRESS_CHUNKS.contains(-chunksSize + index)) {
                spannable.setSpan(StyleSpan(Typeface.BOLD), 0, spannable.length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
            formattedAddress.append(spannable)
            if (index != chunksSize - 1) {
                formattedAddress.append(' ')
            }
        }
        return formattedAddress
    }

    fun <G> groupWordsBy(
        charSequence: CharSequence,
        groupingFunction: (substring: String) -> G,
    ): List<Triple<G, Int, Int>> {
        val result = mutableListOf<Triple<G, Int, Int>>()

        var start = 0

        for (i in 0..charSequence.length) {
            val ch = charSequence.getOrNull(i)
            if (ch == null || ch.isWhitespace()) {
                if (start < i) {
                    val word = charSequence.substring(start, i)
                    val group = groupingFunction(word)

                    val lastIndex = result.size - 1
                    val last = result.getOrNull(lastIndex)
                    if (last?.first == group) {
                        result.set(lastIndex, Triple(group, last.second, i))
                    } else {
                        result.add(Triple(group, start, i))
                    }
                }
                start = i + 1
            }
        }

        return result
    }
}
