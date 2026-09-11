package org.monfluo.wallet.android.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.monfluo.wallet.android.model.MoneroUnit

class TextHelperTest {
    @ParameterizedTest
    @MethodSource("moneroAmounts")
    fun `should correctly convert monero amount to string`(
        amount: Long,
        unit: MoneroUnit,
        expected: String,
    ) {
        val displayAmount = TextHelper.getDisplayAmount(amount, unit, false)
        assertEquals(expected, displayAmount)
        assertEquals(amount, TextHelper.parseAmountToAtomicUnits(displayAmount, unit))
    }

    @ParameterizedTest
    @MethodSource("wordGroups")
    fun `should correctly group words`(
        text: String,
        expected: List<Triple<Boolean, Int, Int>>,
    ) {
        assertEquals(expected, TextHelper.groupWordsBy(text) { word -> word == "good" })
    }

    companion object {
        @JvmStatic
        fun moneroAmounts(): List<Arguments> =
            listOf(
                Arguments.of(
                    123456789123456L,
                    MoneroUnit.MONERO,
                    "123.456789123456",
                ),
                Arguments.of(
                    1000L,
                    MoneroUnit.MONERO,
                    "0.000000001000",
                ),
                Arguments.of(
                    1L,
                    MoneroUnit.MONERO,
                    "0.000000000001",
                ),
                Arguments.of(
                    123456789123456L,
                    MoneroUnit.MILLINERO,
                    "123456.789123456",
                ),
                Arguments.of(
                    1000L,
                    MoneroUnit.MILLINERO,
                    "0.000001000",
                ),
                Arguments.of(
                    1L,
                    MoneroUnit.MILLINERO,
                    "0.000000001",
                ),
                Arguments.of(
                    123456789123456L,
                    MoneroUnit.PICONERO,
                    "123456789123456",
                ),
                Arguments.of(
                    1000L,
                    MoneroUnit.PICONERO,
                    "1000",
                ),
                Arguments.of(
                    1L,
                    MoneroUnit.PICONERO,
                    "1",
                ),
            )

        @JvmStatic
        fun wordGroups(): List<Arguments> =
            listOf(
                Arguments.of(
                    "",
                    emptyList<Triple<Boolean, Int, Int>>(),
                ),
                Arguments.of(
                    "good",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 0, 4)),
                ),
                Arguments.of(
                    "good ",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 0, 4)),
                ),
                Arguments.of(
                    "good  ",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 0, 4)),
                ),
                Arguments.of(
                    " good",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 1, 5)),
                ),
                Arguments.of(
                    "  good",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 2, 6)),
                ),
                Arguments.of(
                    "bad",
                    listOf<Triple<Boolean, Int, Int>>(Triple(false, 0, 3)),
                ),
                Arguments.of(
                    "bad ",
                    listOf<Triple<Boolean, Int, Int>>(Triple(false, 0, 3)),
                ),
                Arguments.of(
                    "bad  ",
                    listOf<Triple<Boolean, Int, Int>>(Triple(false, 0, 3)),
                ),
                Arguments.of(
                    " bad",
                    listOf<Triple<Boolean, Int, Int>>(Triple(false, 1, 4)),
                ),
                Arguments.of(
                    "  bad",
                    listOf<Triple<Boolean, Int, Int>>(Triple(false, 2, 5)),
                ),
                Arguments.of(
                    "good bad",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 0, 4), Triple(false, 5, 8)),
                ),
                Arguments.of(
                    "good bad ",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 0, 4), Triple(false, 5, 8)),
                ),
                Arguments.of(
                    "good bad  ",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 0, 4), Triple(false, 5, 8)),
                ),
                Arguments.of(
                    " good bad",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 1, 5), Triple(false, 6, 9)),
                ),
                Arguments.of(
                    "  good bad",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 2, 6), Triple(false, 7, 10)),
                ),
                Arguments.of(
                    "good  bad",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 0, 4), Triple(false, 6, 9)),
                ),
                Arguments.of(
                    "bad good",
                    listOf<Triple<Boolean, Int, Int>>(Triple(false, 0, 3), Triple(true, 4, 8)),
                ),
                Arguments.of(
                    "bad good good",
                    listOf<Triple<Boolean, Int, Int>>(Triple(false, 0, 3), Triple(true, 4, 13)),
                ),
                Arguments.of(
                    "bad bad good",
                    listOf<Triple<Boolean, Int, Int>>(Triple(false, 0, 7), Triple(true, 8, 12)),
                ),
                Arguments.of(
                    "good bad bad good",
                    listOf<Triple<Boolean, Int, Int>>(Triple(true, 0, 4), Triple(false, 5, 12), Triple(true, 13, 17)),
                ),
                Arguments.of(
                    "bad good good bad",
                    listOf<Triple<Boolean, Int, Int>>(Triple(false, 0, 3), Triple(true, 4, 13), Triple(false, 14, 17)),
                ),
            )
    }
}
