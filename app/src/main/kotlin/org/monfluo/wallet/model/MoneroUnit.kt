package org.monfluo.wallet.model

import org.monfluo.wallet.R

private const val XMR_DECIMALS = 12

enum class MoneroUnit(
    val decimals: Int,
    val unitResourceId: Int,
    val streetModeText: String,
) {
    MONERO(XMR_DECIMALS, R.string.unit_xmr, "#.############"),
    MILLINERO(XMR_DECIMALS - 3, R.string.unit_milli_xmr, "####.#########"),
    MICRONERO(XMR_DECIMALS - 6, R.string.unit_micro_xmr, "#######.######"),
    NANONERO(XMR_DECIMALS - 9, R.string.unit_nano_xmr, "##########.###"),
    PICONERO(XMR_DECIMALS - 12, R.string.unit_pico_xmr, "#############"),
}
