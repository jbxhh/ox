/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.monfluo.wallet.model

import timber.log.Timber
import java.io.File

class WalletManager {
    val networkType = NetworkType.NetworkType_Mainnet

    fun createWallet(aFile: File, password: String, language: String): Wallet {
        val walletHandle = createWalletJ(aFile.absolutePath, password, language, networkType.value)
        val wallet = Wallet(
            walletHandle,
            WalletInfo(aFile, password)
        )
        return wallet
    }

    private external fun createWalletJ(
        path: String,
        password: String,
        language: String,
        networkType: Int
    ): Long

    fun createWalletPolyseed(
        aFile: File,
        password: String,
        passphrase: String,
        language: String
    ): Wallet {
        val walletHandle = createWalletPolyseedJ(
            aFile.absolutePath,
            password,
            passphrase,
            language,
            networkType.value
        )
        val wallet = Wallet(
            walletHandle,
            WalletInfo(aFile, password)
        )
        if (wallet.status.isOk) {
            wallet.setPassword(password) // this rewrites the keys file (which contains the restore height)
        } else Timber.e("Failed to create polyseed wallet: ${wallet.status}")
        return wallet
    }

    private external fun createWalletPolyseedJ(
        path: String,
        password: String,
        passphrase: String,
        language: String,
        networkType: Int
    ): Long

    fun openWallet(path: String, password: String): Wallet {
        val walletHandle = openWalletJ(path, password, networkType.value)
        val wallet = Wallet(
            walletHandle,
            WalletInfo(File(path), password)
        )
        return wallet
    }

    private external fun openWalletJ(path: String, password: String, networkType: Int): Long
    fun recoveryWallet(
        aFile: File, password: String,
        mnemonic: String, offset: String,
        restoreHeight: Long
    ): Wallet {
        val walletHandle = recoveryWalletJ(
            aFile.absolutePath, password,
            mnemonic, offset,
            networkType.value, restoreHeight
        )
        val wallet = Wallet(
            walletHandle,
            WalletInfo(aFile, password)
        )
        return wallet
    }

    private external fun recoveryWalletJ(
        path: String, password: String,
        mnemonic: String, offset: String,
        networkType: Int, restoreHeight: Long
    ): Long

    fun recoveryWalletPolyseed(
        aFile: File, password: String,
        mnemonic: String, offset: String
    ): Wallet {
        val walletHandle = recoveryWalletPolyseedJ(
            aFile.absolutePath, password,
            mnemonic, offset,
            networkType.value
        )
        val wallet = Wallet(
            walletHandle,
            WalletInfo(aFile, password)
        )
        return wallet
    }

    private external fun recoveryWalletPolyseedJ(
        path: String, password: String,
        mnemonic: String, offset: String,
        networkType: Int
    ): Long

    fun createWalletWithKeys(
        aFile: File, password: String, language: String, restoreHeight: Long,
        addressString: String, viewKeyString: String, spendKeyString: String
    ): Wallet {
        val walletHandle = createWalletFromKeysJ(
            aFile.absolutePath, password,
            language, networkType.value, restoreHeight,
            addressString, viewKeyString, spendKeyString
        )
        val wallet = Wallet(
            walletHandle,
            WalletInfo(aFile, password)
        )
        return wallet
    }

    private external fun createWalletFromKeysJ(
        path: String, password: String,
        language: String,
        networkType: Int,
        restoreHeight: Long,
        addressString: String,
        viewKeyString: String,
        spendKeyString: String
    ): Long

    external fun closeJ(wallet: Wallet?): Boolean

    fun walletExists(aFile: File): Boolean {
        return walletExists(aFile.absolutePath)
    }

    private external fun walletExists(path: String?): Boolean
    external fun verifyWalletPassword(
        keysFileName: String?,
        password: String?,
        noSpendKey: Boolean
    ): Boolean

    external fun setDaemonAddressJ(address: String)
    external fun getDaemonVersion(): Int
    external fun getBlockchainHeight(): Long
    external fun getBlockchainTargetHeight(): Long
    external fun getNetworkDifficulty(): Long
    external fun getBlockTarget(): Long

    external fun resolveOpenAlias(address: String?, dnssec_valid: Boolean): String?

    external fun setProxyJ(address: String?): Boolean

    companion object {

        val instance: WalletManager
            get() = synchronized(WalletManager::class.java) {
                return@synchronized WalletManager()
            }

        fun addressPrefix(networkType: NetworkType): String {
            return when (networkType) {
                NetworkType.NetworkType_Testnet -> "9A-"
                NetworkType.NetworkType_Mainnet -> "4-"
                NetworkType.NetworkType_Stagenet -> "5-"
            }
        }

        @JvmStatic
        external fun initLogger(argv0: String?, defaultLogBaseName: String?)

        @JvmStatic
        external fun setLogLevel(level: Int)

        @JvmStatic
        external fun moneroVersion(): String?
    }
}