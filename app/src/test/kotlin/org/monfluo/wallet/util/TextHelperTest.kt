package org.monfluo.wallet.util

import org.monfluo.wallet.model.MoneroUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class TextHelperTest {

    @ParameterizedTest
    @MethodSource("moneroAmounts")
    fun `should correctly convert monero amount to string`(amount: Long, unit: MoneroUnit, expected: String) {
        assertEquals(expected, TextHelper.getDisplayAmount(amount, unit, false))
    }

    companion object {

        @JvmStatic
        fun moneroAmounts(): List<Arguments> = listOf(
            Arguments.of(
                123456789123456L,
                MoneroUnit.MONERO,
                "123.456789123456"
            ),
            Arguments.of(
                1000L,
                MoneroUnit.MONERO,
                "0.000000001000"
            ),
            Arguments.of(
                1L,
                MoneroUnit.MONERO,
                "0.000000000001"
            ),
            Arguments.of(
                123456789123456L,
                MoneroUnit.MILLINERO,
                "123456.789123456"
            ),
            Arguments.of(
                1000L,
                MoneroUnit.MILLINERO,
                "0.000001000"
            ),
            Arguments.of(
                1L,
                MoneroUnit.MILLINERO,
                "0.000000001"
            ),
            Arguments.of(
                123456789123456L,
                MoneroUnit.PICONERO,
                "123456789123456"
            ),
            Arguments.of(
                1000L,
                MoneroUnit.PICONERO,
                "1000"
            ),
            Arguments.of(
                1L,
                MoneroUnit.PICONERO,
                "1"
            ),
        )
    }
}
