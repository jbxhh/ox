package net.mynero.wallet.service

import android.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import net.mynero.wallet.model.CoinsInfo
import net.mynero.wallet.model.PendingTransaction
import net.mynero.wallet.model.Wallet
import net.mynero.wallet.model.WalletManager
import net.mynero.wallet.util.Constants
import org.json.JSONArray
import org.json.JSONException
import java.util.Collections

class UTXOService(thread: MoneroHandlerThread?) : ServiceBase(thread) {
    private val _utxos = MutableLiveData<List<CoinsInfo>>()

    @JvmField
    var utxos: LiveData<List<CoinsInfo>> = _utxos
    private var internalCachedUtxos: List<CoinsInfo> = ArrayList()
    private var frozenCoins = ArrayList<String?>()
    val utxosInternal: List<CoinsInfo>
        get() {
            return WalletManager.instance?.wallet?.coins?.all ?: emptyList()
        }

    init {
        instance = this
        try {
            loadFrozenCoins()
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    fun refreshUtxos() {
        val coinsInfos: List<CoinsInfo> = this.utxosInternal
        _utxos.postValue(coinsInfos)
        internalCachedUtxos = coinsInfos
    }

    fun getUtxos(): List<CoinsInfo> {
        return Collections.unmodifiableList(internalCachedUtxos)
    }

    fun toggleFrozen(selectedCoins: HashMap<String?, CoinsInfo>) {
        val frozenCoinsCopy = ArrayList(frozenCoins)
        for (coin in selectedCoins.values) {
            if (frozenCoinsCopy.contains(coin.pubKey)) {
                frozenCoinsCopy.remove(coin.pubKey)
            } else {
                frozenCoinsCopy.add(coin.pubKey)
            }
        }
        frozenCoins = frozenCoinsCopy
        saveFrozenCoins()
        refreshUtxos()
        BalanceService.instance!!.refreshBalance()
    }

    fun isCoinFrozen(coinsInfo: CoinsInfo): Boolean {
        return frozenCoins.contains(coinsInfo.pubKey)
    }

    @Throws(JSONException::class)
    private fun loadFrozenCoins() {
        val prefService = PrefService.instance
        val frozenCoinsArrayString = prefService!!.getString(Constants.PREF_FROZEN_COINS, "[]")
        val frozenCoinsArray = JSONArray(frozenCoinsArrayString)
        for (i in 0 until frozenCoinsArray.length()) {
            val pubKey = frozenCoinsArray.getString(i)
            frozenCoins.add(pubKey)
        }
        refreshUtxos()
    }

    private fun saveFrozenCoins() {
        val prefService = PrefService.instance
        val jsonArray = JSONArray()
        val frozenCoinsCopy = ArrayList(frozenCoins)
        for (pubKey in frozenCoinsCopy) {
            jsonArray.put(pubKey)
        }
        prefService!!.edit()!!
            .putString(Constants.PREF_FROZEN_COINS, jsonArray.toString()).apply()
    }

    @Throws(Exception::class)
    fun selectUtxos(
        amount: Long,
        sendAll: Boolean,
        feePriority: PendingTransaction.Priority
    ): ArrayList<String> {
        val basicFeeEstimate = calculateBasicFee(amount, feePriority)
        val amountWithBasicFee = amount + basicFeeEstimate
        val selectedUtxos = ArrayList<String>()
        val seenTxs = ArrayList<String>()
        val utxos: List<CoinsInfo> = ArrayList(getUtxos())
        var amountSelected: Long = 0
        utxos.sorted()
        //loop through each utxo
        for (coinsInfo in utxos) {
            if (!coinsInfo.isSpent && coinsInfo.isUnlocked && !coinsInfo.isFrozen && !frozenCoins.contains(
                    coinsInfo.pubKey
                )
            ) { //filter out spent, locked, and frozen outputs
                if (sendAll) {
                    // if send all, add all utxos and set amount to send all
                    coinsInfo.keyImage?.let { selectedUtxos.add(it) }
                    amountSelected = Wallet.SWEEP_ALL
                } else {
                    //if amount selected is still less than amount needed, and the utxos tx hash hasn't already been seen, add utxo
                    if (amountSelected <= amountWithBasicFee && !seenTxs.contains(coinsInfo.hash)) {
                        coinsInfo.keyImage?.let { selectedUtxos.add(it) }
                        // we don't want to spend multiple utxos from the same transaction, so we prevent that from happening here.
                        coinsInfo.hash?.let { seenTxs.add(it) }
                        amountSelected += coinsInfo.amount
                    }
                }
            }
        }
        if (amountSelected < amountWithBasicFee && !sendAll) {
            throw Exception("insufficient wallet balance")
        }
        return selectedUtxos
    }

    private fun calculateBasicFee(amount: Long, feePriority: PendingTransaction.Priority): Long {
        val destinations = ArrayList<Pair<String, Long>>()
        destinations.add(
            Pair(
                "87MRtZPrWUCVUgcFHdsVb5MoZUcLtqfD3FvQVGwftFb8eSdMnE39JhAJcbuSW8X2vRaRsB9RQfuCpFciybJFHaz3QYPhCLw",
                amount
            )
        )
        // destination string doesn't actually matter here, so i'm using the donation address. amount also technically doesn't matter
        return WalletManager.instance!!.wallet!!.estimateTransactionFee(destinations, feePriority)
    }

    companion object {
        @JvmStatic
        var instance: UTXOService? = null
    }
}