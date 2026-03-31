/**
 * Copyright (c) 2017 m2049r
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "monfluo.h"
#include "wallet2_api.h"
#include <cinttypes>

#ifdef __cplusplus
extern "C"
{
#endif

static JavaVM *cachedJVM;
static jclass class_Boolean;
static jclass class_Long;
static jclass class_List;
static jclass class_ArrayList;
static jclass class_WalletListener;
static jclass class_TransactionInfo;
static jclass class_Transfer;
static jclass class_WalletStatusWithError;
static jclass class_CoinsInfo;
static jclass class_Contact;
static jclass class_Pair;

std::mutex _listenerMutex;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    cachedJVM = jvm;
    LOGI("JNI_OnLoad");
    JNIEnv *jenv;
    if (jvm->GetEnv(reinterpret_cast<void **>(&jenv), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    LOGI("JNI_OnLoad ok");

    class_Boolean = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("java/lang/Boolean")));
    class_Long = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("java/lang/Long")));
    class_List = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("java/util/List")));
    class_ArrayList = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("java/util/ArrayList")));
    class_Pair = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("kotlin/Pair")));
    class_TransactionInfo = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("org/monfluo/wallet/android/model/TransactionInfo")));
    class_Transfer = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("org/monfluo/wallet/android/model/Transfer")));
    class_WalletListener = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("org/monfluo/wallet/android/model/WalletListener")));
    class_WalletStatusWithError = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("org/monfluo/wallet/android/model/WalletStatusWithError")));
    class_CoinsInfo = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("org/monfluo/wallet/android/model/Enote")));
    class_Contact = reinterpret_cast<jclass>(jenv->NewGlobalRef(jenv->FindClass("org/monfluo/wallet/android/model/Contact")));
    return JNI_VERSION_1_6;
}
#ifdef __cplusplus
}
#endif

int attachJVM(JNIEnv **jenv) {
    int envStat = cachedJVM->GetEnv((void **) jenv, JNI_VERSION_1_6);
    if (envStat == JNI_EDETACHED) {
        if (cachedJVM->AttachCurrentThread(jenv, nullptr) != 0) {
            LOGE("Failed to attach");
            return JNI_ERR;
        }
    } else if (envStat == JNI_EVERSION) {
        LOGE("GetEnv: version not supported");
        return JNI_ERR;
    }
    LOGV("envStat = %i", envStat);
    return envStat;
}

void detachJVM(JNIEnv *jenv, int envStat) {
    LOGV("envStat = %i", envStat);
    if (jenv->ExceptionCheck()) {
        jenv->ExceptionDescribe();
    }

    if (envStat == JNI_EDETACHED) {
        cachedJVM->DetachCurrentThread();
    }
}

struct DelegatingWalletListener final : Monero::WalletListener {
    jobject jlistener;

    DelegatingWalletListener(JNIEnv *env, jobject aListener) {
        LOGD("Created Delegating Wallet Listener");
        jlistener = env->NewGlobalRef(aListener);
    }

    ~DelegatingWalletListener() override {
        LOGD("Destroyed Delegating Listener");
    };

    void deleteGlobalJavaRef(JNIEnv *env) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        env->DeleteGlobalRef(jlistener);
        jlistener = nullptr;
    }

    /**
     * @brief updated  - generic callback, called when any event (sent/received/block received/etc) happened with the wallet;
     */
    void updated() override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        LOGD("updated");
        if (jlistener == nullptr) return;
        JNIEnv *jenv;
        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        jmethodID listenerClass_updated = jenv->GetMethodID(class_WalletListener, "updated", "()V");
        jenv->CallVoidMethod(jlistener, listenerClass_updated);

        detachJVM(jenv, envStat);
    }


    /**
     * @brief moneySpent - called when money spent
     * @param txId       - transaction id
     * @param amount     - amount
     */
    void moneySpent(const std::string &txId, uint64_t amount) override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        LOGD("moneySpent %" PRIu64, amount);
        if (jlistener == nullptr) return;
        JNIEnv *jenv;

        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        jmethodID listenerClass_moneySpent = jenv->GetMethodID(class_WalletListener, "moneySpent","(Ljava/lang/String;J)V");

        jstring jTxId = jenv->NewStringUTF(txId.c_str());
        auto jAmount = static_cast<jlong>(amount);
        jenv->CallVoidMethod(jlistener, listenerClass_moneySpent, jTxId, jAmount);
        jenv->DeleteLocalRef(jTxId);
        detachJVM(jenv, envStat);
    }

    /**
     * @brief moneyReceived - called when money received
     * @param txId          - transaction id
     * @param amount        - amount
     */
    void moneyReceived(const std::string &txId, uint64_t amount) override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        LOGD("moneyReceived %" PRIu64, amount);
        if (jlistener == nullptr) return;
        JNIEnv *jenv;

        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        jmethodID listenerClass_moneyReceived = jenv->GetMethodID(class_WalletListener, "moneyReceived", "(Ljava/lang/String;J)V");

        jstring jTxId = jenv->NewStringUTF(txId.c_str());
        auto jAmount = static_cast<jlong>(amount);
        jenv->CallVoidMethod(jlistener, listenerClass_moneyReceived, jTxId, jAmount);
        jenv->DeleteLocalRef(jTxId);
        detachJVM(jenv, envStat);
    }

    /**
     * @brief unconfirmedMoneyReceived - called when payment arrived in tx pool
     * @param txId          - transaction id
     * @param amount        - amount
     */
    void unconfirmedMoneyReceived(const std::string &txId, uint64_t amount) override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        LOGD("unconfirmedMoneyReceived %" PRIu64, amount);
        if (jlistener == nullptr) return;
        JNIEnv *jenv;

        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        jmethodID listenerClass_unconfirmedMoneyReceived = jenv->GetMethodID(class_WalletListener, "unconfirmedMoneyReceived", "(Ljava/lang/String;J)V");

        jstring jTxId = jenv->NewStringUTF(txId.c_str());
        auto jAmount = static_cast<jlong>(amount);
        jenv->CallVoidMethod(jlistener, listenerClass_unconfirmedMoneyReceived, jTxId, jAmount);
        jenv->DeleteLocalRef(jTxId);
        detachJVM(jenv, envStat);

    }

    /**
     * @brief newBlock      - called when new block received
     * @param height        - block height
     * @param last          - true if the block is the last block in the batch
     */
    void newBlock(uint64_t height, bool last) override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        LOGD("newBlock %" PRIu64, height);
        if (jlistener == nullptr) return;
        JNIEnv *jenv;
        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        auto h = static_cast<jlong>(height);
        auto l = static_cast<jboolean>(last);
        jmethodID listenerClass_newBlock = jenv->GetMethodID(class_WalletListener, "newBlock", "(JZ)V");
        jenv->CallVoidMethod(jlistener, listenerClass_newBlock, h, l);

        detachJVM(jenv, envStat);
    }

    /**
     * @brief refreshed - called when wallet refreshed by background thread or explicitly refreshed by calling "refresh" synchronously
     */
    void refreshed() override {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        LOGD("refreshed");
        if (jlistener == nullptr) return;
        JNIEnv *jenv;

        int envStat = attachJVM(&jenv);
        if (envStat == JNI_ERR) return;

        jmethodID listenerClass_refreshed = jenv->GetMethodID(class_WalletListener, "refreshed", "()V");
        jenv->CallVoidMethod(jlistener, listenerClass_refreshed);
        detachJVM(jenv, envStat);
    }
};


//// helper methods
std::vector<std::string> java2cpp(JNIEnv *env, jobject arrayList) {

    jmethodID java_util_List_size = env->GetMethodID(class_List, "size", "()I");
    jmethodID java_util_List_get = env->GetMethodID(class_List, "get", "(I)Ljava/lang/Object;");

    jint len = env->CallIntMethod(arrayList, java_util_List_size);
    std::vector<std::string> result;
    result.reserve(len);
    for (jint i = 0; i < len; i++) {
        auto element = reinterpret_cast<jstring>(env->CallObjectMethod(arrayList, java_util_List_get, i));
        const char *pchars = env->GetStringUTFChars(element, nullptr);
        result.emplace_back(pchars);
        env->ReleaseStringUTFChars(element, pchars);
        env->DeleteLocalRef(element);
    }
    return result;
}

jlong getElement(JNIEnv *env, jlongArray arr_j, int element) {
    jlong result;
    env->GetLongArrayRegion(arr_j, element, 1, &result);
    return result;
}

std::vector<std::uint64_t> java2cpp_long(JNIEnv *env, jlongArray longArray) {
    jint len = env->GetArrayLength(longArray);
    std::vector<std::uint64_t> result;
    result.reserve(len);
    for (jint i = 0; i < len; i++) {
        jlong amount = getElement(env, longArray, i);
        result.emplace_back(amount);
    }
    return result;
}

std::set<std::string> java2cpp_set(JNIEnv *env, jobject list) {

    jmethodID java_util_List_size = env->GetMethodID(class_List, "size", "()I");
    jmethodID java_util_List_get = env->GetMethodID(class_List, "get", "(I)Ljava/lang/Object;");

    jint len = env->CallIntMethod(list, java_util_List_size);
    std::set<std::string> result;
    for (jint i = 0; i < len; i++) {
        auto element = reinterpret_cast<jstring>(env->CallObjectMethod(list, java_util_List_get, i));
        const char *pchars = env->GetStringUTFChars(element, nullptr);
        result.emplace(pchars);
        env->ReleaseStringUTFChars(element, pchars);
        env->DeleteLocalRef(element);
    }
    return result;
}

std::pair<std::string, uint64_t> extract_pair(JNIEnv *env, jobject p) {
    jfieldID first = env->GetFieldID(class_Pair, "first", "Ljava/lang/Object;");
    jfieldID second = env->GetFieldID(class_Pair, "second", "Ljava/lang/Object;");
    auto string = reinterpret_cast<jstring>(env->GetObjectField(p, first));
    std::string converted_string = env->GetStringUTFChars(string, nullptr);
    auto value = reinterpret_cast<uint64_t>(env->GetObjectField(p, second));
    auto pair = std::make_pair(converted_string, value);
    return pair;
}

std::vector<std::pair<std::string, uint64_t>> java2cpp_pairvector(JNIEnv *env, jobject list) {

    jmethodID java_util_List_size = env->GetMethodID(class_List, "size", "()I");
    jmethodID java_util_List_get = env->GetMethodID(class_List, "get", "(I)Ljava/lang/Object;");

    jint len = env->CallIntMethod(list, java_util_List_size);
    std::vector<std::pair<std::string, uint64_t>> result;

    for (jint i = 0; i < len; i++) {
        jobject element = env->CallObjectMethod(list, java_util_List_get, i);
        auto pair = extract_pair(env, element);
        result.emplace_back(pair);
    }
    return result;
}

jobject create_java_pair(JNIEnv *env, const jobject first, const jobject second) {
    jmethodID java_pair_constructor = env->GetMethodID(class_Pair, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");

    jobject result = env->NewObject(class_Pair, java_pair_constructor, first, second);

    return result;
}

jobject create_java_boolean(JNIEnv *env, jboolean value) {
    jmethodID constructor = env->GetMethodID(class_Boolean, "<init>", "(Z)V");
    return env->NewObject(class_Boolean, constructor, value);
}

jobject create_java_long(JNIEnv *env, jlong value) {
    jmethodID constructor = env->GetMethodID(class_Long, "<init>", "(J)V");
    return env->NewObject(class_Long, constructor, value);
}

/// end helpers

#ifdef __cplusplus
extern "C"
{
#endif


/**********************************/
/********** WalletManager *********/
/**********************************/
JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_createWalletJ(JNIEnv *env, jobject instance,
                                                         jstring path, jstring password,
                                                         jstring language,
                                                         jint networkType) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_language = env->GetStringUTFChars(language, nullptr);
    auto _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWallet(
                    std::string(_path),
                    std::string(_password),
                    std::string(_language),
                    _networkType);

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(language, _language);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_createWalletPolyseedJ(JNIEnv *env, jobject instance,
                                                                 jstring path, jstring password,
                                                                 jstring passpharse,
                                                                 jstring language,
                                                                 jint networkType) {
    std::string seed_words;
    std::string err;
    bool _polyseedCreate = Monero::Wallet::createPolyseed(seed_words, err);
    if (!_polyseedCreate) {
        LOGE("Failed to polyseedCreate");
    }

    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_passpharse = env->GetStringUTFChars(passpharse, nullptr);
    const char *_language = env->GetStringUTFChars(language, nullptr); // NOT USED ANYMORE?????
    auto _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromPolyseed(
                    std::string(_path),
                    std::string(_password),
                    _networkType,
                    seed_words,
                    std::string(_passpharse), true);

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(language, _language);
    env->ReleaseStringUTFChars(passpharse, _passpharse);
    return reinterpret_cast<jlong>(wallet);
}


JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_openWalletJ(JNIEnv *env, jobject instance,
                                                       jstring path, jstring password,
                                                       jint networkType) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    auto _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->openWallet(
                    std::string(_path),
                    std::string(_password),
                    _networkType);

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_recoveryWalletJ(JNIEnv *env, jobject instance,
                                                           jstring path, jstring password,
                                                           jstring mnemonic, jstring offset,
                                                           jint networkType,
                                                           jlong restoreHeight) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_mnemonic = env->GetStringUTFChars(mnemonic, nullptr);
    const char *_offset = env->GetStringUTFChars(offset, nullptr);
    auto _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->recoveryWallet(
                    std::string(_path),
                    std::string(_password),
                    std::string(_mnemonic),
                    _networkType,
                    (uint64_t) restoreHeight,
                    1, // kdf_rounds
                    std::string(_offset));

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(mnemonic, _mnemonic);
    env->ReleaseStringUTFChars(offset, _offset);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_recoveryWalletPolyseedJ(JNIEnv *env, jobject instance,
                                                                   jstring path, jstring password,
                                                                   jstring mnemonic, jstring offset,
                                                                   jint networkType) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_mnemonic = env->GetStringUTFChars(mnemonic, nullptr);
    const char *_offset = env->GetStringUTFChars(offset, nullptr);
    auto _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromPolyseed(
                    std::string(_path),
                    std::string(_password),
                    _networkType,
                    std::string(_mnemonic),
                    std::string(_offset), false);

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(mnemonic, _mnemonic);
    env->ReleaseStringUTFChars(offset, _offset);
    return reinterpret_cast<jlong>(wallet);
}


JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_createWalletFromKeysJ(JNIEnv *env, jobject instance,
                                                                 jstring path, jstring password,
                                                                 jstring language,
                                                                 jint networkType,
                                                                 jlong restoreHeight,
                                                                 jstring addressString,
                                                                 jstring viewKeyString,
                                                                 jstring spendKeyString) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_language = env->GetStringUTFChars(language, nullptr);
    auto _networkType = static_cast<Monero::NetworkType>(networkType);
    const char *_addressString = env->GetStringUTFChars(addressString, nullptr);
    const char *_viewKeyString = env->GetStringUTFChars(viewKeyString, nullptr);
    const char *_spendKeyString = env->GetStringUTFChars(spendKeyString, nullptr);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromKeys(
                    std::string(_path),
                    std::string(_password),
                    std::string(_language),
                    _networkType,
                    (uint64_t) restoreHeight,
                    std::string(_addressString),
                    std::string(_viewKeyString),
                    std::string(_spendKeyString));

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(language, _language);
    env->ReleaseStringUTFChars(addressString, _addressString);
    env->ReleaseStringUTFChars(viewKeyString, _viewKeyString);
    env->ReleaseStringUTFChars(spendKeyString, _spendKeyString);
    return reinterpret_cast<jlong>(wallet);
}


// virtual void setSubaddressLookahead(uint32_t major, uint32_t minor) = 0;

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_walletExists(JNIEnv *env, jobject instance,
                                                        jstring path) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    bool exists = Monero::WalletManagerFactory::getWalletManager()->walletExists(std::string(_path));
    env->ReleaseStringUTFChars(path, _path);
    return static_cast<jboolean>(exists);
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_verifyWalletPassword(JNIEnv *env, jobject instance,
                                                                jstring keys_file_name,
                                                                jstring password,
                                                                jboolean no_spend_key) {
    const char *_keys_file_name = env->GetStringUTFChars(keys_file_name, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    bool passwordOk = Monero::WalletManagerFactory::getWalletManager()->verifyWalletPassword(
            std::string(_keys_file_name), std::string(_password), no_spend_key);
    env->ReleaseStringUTFChars(keys_file_name, _keys_file_name);
    env->ReleaseStringUTFChars(password, _password);
    return static_cast<jboolean>(passwordOk);
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_setDaemonAddressJ(JNIEnv *env, jobject instance,
                                                             jstring address) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    Monero::WalletManagerFactory::getWalletManager()->setDaemonAddress(std::string(_address));
    env->ReleaseStringUTFChars(address, _address);
}

// returns whether the daemon can be reached, and its version number
JNIEXPORT jint JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_getDaemonVersion(JNIEnv *env, jobject instance) {
    uint32_t version;
    bool isConnected = Monero::WalletManagerFactory::getWalletManager()->connected(&version);
    if (!isConnected) version = 0;
    return version;
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_getBlockchainHeight(JNIEnv *env, jobject instance) {
    auto wm = Monero::WalletManagerFactory::getWalletManager();
    auto result = wm->blockchainHeight();
    return static_cast<jlong>(result);
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_getBlockchainTargetHeight(JNIEnv *env, jobject instance) {
    return static_cast<jlong>(Monero::WalletManagerFactory::getWalletManager()->blockchainTargetHeight());
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_getBlockchainAndTargetHeight(JNIEnv *env, jobject instance) {
    auto wm = Monero::WalletManagerFactory::getWalletManager();
    auto result = wm->blockchainAndTargetHeight();
    auto height = static_cast<jlong>(result.first);
    auto targetHeight = static_cast<jlong>(result.second);
    return create_java_pair(env, create_java_long(env, height), create_java_long(env, targetHeight));
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_getNetworkDifficulty(JNIEnv *env, jobject instance) {
    return static_cast<jlong>(Monero::WalletManagerFactory::getWalletManager()->networkDifficulty());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_getBlockTarget(JNIEnv *env, jobject instance) {
    return static_cast<jlong>(Monero::WalletManagerFactory::getWalletManager()->blockTarget());
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_resolveOpenAlias(JNIEnv *env, jobject instance, jstring address) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    bool dnssec_valid;
    std::string resolvedAlias =Monero::WalletManagerFactory::getWalletManager()->resolveOpenAlias(std::string(_address), dnssec_valid);
    env->ReleaseStringUTFChars(address, _address);
    return create_java_pair(env, env->NewStringUTF(resolvedAlias.c_str()), create_java_boolean(env, (jboolean) dnssec_valid));
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_setProxyJ(JNIEnv *env, jobject instance,
                                                     jstring address) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    bool rc =
            Monero::WalletManagerFactory::getWalletManager()->setProxy(std::string(_address));
    env->ReleaseStringUTFChars(address, _address);
    return rc;
}


JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_closeJ(JNIEnv *env, jobject instance,
                                                  jobject walletInstance) {
    auto *wallet = getHandle<Monero::Wallet>(env, walletInstance);
    bool closeSuccess = Monero::WalletManagerFactory::getWalletManager()->closeWallet(wallet,
                                                                                      false);
    if (closeSuccess) {
        auto *walletListener = getHandle<DelegatingWalletListener>(env, walletInstance, "listenerHandle");
        if (walletListener != nullptr) {
            walletListener->deleteGlobalJavaRef(env);
            delete walletListener;
        }
    }
    LOGD("wallet closed");
    return static_cast<jboolean>(closeSuccess);
}




/**********************************/
/************ Wallet **************/
/**********************************/

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getSeed(JNIEnv *env, jobject instance, jstring seedOffset) {
    const char *_seedOffset = env->GetStringUTFChars(seedOffset, nullptr);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string _seed;
    std::string seedOffsetStr(_seedOffset);
    bool _getPolyseed = wallet->getPolyseed(_seed, seedOffsetStr);

    if (_getPolyseed) {
        LOGD("_getPolyseed: true");
    } else {
        LOGD("_getPolyseed: false");
        LOGE("wallet->getSeed() %s", wallet->errorString().c_str());
        LOGD("Falling back to normal, boring, seed");
        _seed = wallet->seed(std::string(_seedOffset));
    }

    env->ReleaseStringUTFChars(seedOffset, _seedOffset);

    jstring seed = env->NewStringUTF(_seed.c_str());
    return seed;
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getLegacySeed(JNIEnv *env, jobject instance,
                                                  jstring seedOffset) {
    const char *_seedOffset = env->GetStringUTFChars(seedOffset, nullptr);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string _seed;
    std::string seedOffsetStr(_seedOffset);
    _seed = wallet->seed(std::string(_seedOffset));

    env->ReleaseStringUTFChars(seedOffset, _seedOffset);

    jstring seed = env->NewStringUTF(_seed.c_str());
    return seed;
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_isPolyseedSupported(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string seed;
    std::string passphrase;
    bool isPolyseed = wallet->getPolyseed(seed, passphrase);

    return isPolyseed;
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_isEncryptedPolyseed(JNIEnv *env, jclass clazz, jstring polyseed) {
    const char *_polyseed = env->GetStringUTFChars(polyseed, nullptr);

    bool isEncrypted = Monero::Wallet::isEncryptedPolyseed(std::string(_polyseed));

    env->ReleaseStringUTFChars(polyseed, _polyseed);

    return isEncrypted;
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getPolyseedSeedAndPassphrase(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string seed;
    std::string passphrase;
    bool isPolyseed = wallet->getPolyseed(seed, passphrase);

    if (isPolyseed) {
        jstring java_seed = env->NewStringUTF(seed.c_str());
        jstring java_passphrase = env->NewStringUTF(passphrase.c_str());
        return create_java_pair(env, java_seed, java_passphrase);
    } else {
        return nullptr;
    }
}

jobject newWalletStatusInstance(JNIEnv *env, int status, const std::string &errorString) {
    jmethodID init = env->GetMethodID(class_WalletStatusWithError, "<init>", "(ILjava/lang/String;)V");
    jstring _errorString = env->NewStringUTF(errorString.c_str());
    jobject instance = env->NewObject(class_WalletStatusWithError, init, status, _errorString);
    env->DeleteLocalRef(_errorString);
    return instance;
}


JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getWalletStatusWithError(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    int status;
    std::string errorString;
    wallet->statusWithErrorString(status, errorString);

    return newWalletStatusInstance(env, status, errorString);
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getPassword(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->getPassword().c_str());
}


JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_setPassword(JNIEnv *env, jobject instance,
                                                jstring password) {
    const char *_password = env->GetStringUTFChars(password, nullptr);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    bool success = wallet->setPassword(std::string(_password));
    env->ReleaseStringUTFChars(password, _password);
    return static_cast<jboolean>(success);
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getAddressJ(JNIEnv *env, jobject instance,
                                                jint accountIndex,
                                                jint addressIndex) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(
            wallet->address((uint32_t) accountIndex, (uint32_t) addressIndex).c_str());
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getPath(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->path().c_str());
}

JNIEXPORT jint JNICALL
Java_org_monfluo_wallet_android_model_Wallet_nettype(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->nettype();
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getIntegratedAddress(JNIEnv *env, jobject instance,
                                                         jstring payment_id) {
    const char *_payment_id = env->GetStringUTFChars(payment_id, nullptr);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    std::string address = wallet->integratedAddress(_payment_id);
    env->ReleaseStringUTFChars(payment_id, _payment_id);
    return env->NewStringUTF(address.c_str());
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getSecretViewKey(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->secretViewKey().c_str());
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getPublicViewKey(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->publicViewKey().c_str());
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getSecretSpendKey(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->secretSpendKey().c_str());
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getPublicSpendKey(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->publicSpendKey().c_str());
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_store(JNIEnv *env, jobject instance,
                                          jstring path) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    bool success = wallet->store(std::string(_path));
    if (!success) {
        LOGE("store() %s", wallet->errorString().c_str());
    }
    env->ReleaseStringUTFChars(path, _path);
    return static_cast<jboolean>(success);
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getFilename(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->filename().c_str());
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_initJ(JNIEnv *env, jobject instance,
                                          jstring daemon_address,
                                          jlong upper_transaction_size_limit,
                                          jstring daemon_username, jstring daemon_password,
                                          jstring proxy) {
    const char *_daemon_address = env->GetStringUTFChars(daemon_address, nullptr);
    const char *_daemon_username = env->GetStringUTFChars(daemon_username, nullptr);
    const char *_daemon_password = env->GetStringUTFChars(daemon_password, nullptr);
    const char *_daemon_proxy = env->GetStringUTFChars(proxy, nullptr);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    bool status = wallet->init(_daemon_address, (uint64_t) upper_transaction_size_limit,
                               _daemon_username,
                               _daemon_password, false, false, _daemon_proxy);
    env->ReleaseStringUTFChars(daemon_address, _daemon_address);
    env->ReleaseStringUTFChars(daemon_username, _daemon_username);
    env->ReleaseStringUTFChars(daemon_password, _daemon_password);
    env->ReleaseStringUTFChars(proxy, _daemon_proxy);

    return static_cast<jboolean>(status);
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_setRestoreHeight(JNIEnv *env, jobject instance,
                                                     jlong height) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setRefreshFromBlockHeight((uint64_t) height);
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getRestoreHeight(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jlong>(wallet->getRefreshFromBlockHeight());
}

JNIEXPORT jint JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getConnectionStatusJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->connected();
}
JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_setTrustedDaemonJ(JNIEnv *env, jobject instance,
                                                      jboolean trusted) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    if (trusted) {
        wallet->setTrustedDaemon(true);
    } else {
        wallet->setTrustedDaemon(false);
    }
}
JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_isTrustedDaemonJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    bool td = wallet->trustedDaemon();
    return td;
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_setProxyJ(JNIEnv *env, jobject instance,
                                              jstring address) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    bool rc = wallet->setProxy(std::string(_address));
    env->ReleaseStringUTFChars(address, _address);
    return rc;
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getBalance(JNIEnv *env, jobject instance,
                                               jint accountIndex) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jlong>(wallet->balance((uint32_t) accountIndex));
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getBalanceAll(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jlong>(wallet->balanceAll());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getUnlockedBalance(JNIEnv *env, jobject instance,
                                                       jint accountIndex) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jlong>(wallet->unlockedBalance((uint32_t) accountIndex));
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getUnlockedBalanceAll(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jlong>(wallet->unlockedBalanceAll());
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_isWatchOnly(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet->watchOnly());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getBlockChainHeightJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jlong>(wallet->blockChainHeight());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getApproximateBlockChainHeight(JNIEnv *env,
                                                                   jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jlong>(wallet->approximateBlockChainHeight());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getDaemonBlockChainHeightJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jlong>(wallet->daemonBlockChainHeight());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getDaemonBlockChainTargetHeight(JNIEnv *env,
                                                                    jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jlong>(wallet->daemonBlockChainTargetHeight());
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_isSynchronizedJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet->synchronized());
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getDisplayAmount(JNIEnv *env, jclass clazz,
                                                     jlong amount) {
    return env->NewStringUTF(Monero::Wallet::displayAmount(amount).c_str());
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getAmountFromString(JNIEnv *env, jclass clazz,
                                                        jstring amount) {
    const char *_amount = env->GetStringUTFChars(amount, nullptr);
    jobject jAmount = nullptr;
    try {
        jAmount = create_java_long(env, static_cast<jlong>(Monero::Wallet::amountFromString(_amount)));
    } catch (...) {
        // We do not care if parsing failed, just return null
    }
    env->ReleaseStringUTFChars(amount, _amount);
    return jAmount;
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_generatePaymentId(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(Monero::Wallet::genPaymentId().c_str());
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_isPaymentIdValid(JNIEnv *env, jclass clazz,
                                                     jstring payment_id) {
    const char *_payment_id = env->GetStringUTFChars(payment_id, nullptr);
    bool isValid = Monero::Wallet::paymentIdValid(_payment_id);
    env->ReleaseStringUTFChars(payment_id, _payment_id);
    return static_cast<jboolean>(isValid);
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_isAddressValid(JNIEnv *env, jclass clazz,
                                                   jstring address, jint networkType) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    auto _networkType = static_cast<Monero::NetworkType>(networkType);
    bool isValid = Monero::Wallet::addressValid(_address, _networkType);
    env->ReleaseStringUTFChars(address, _address);
    return static_cast<jboolean>(isValid);
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getPaymentIdFromAddress(JNIEnv *env, jclass clazz,
                                                            jstring address,
                                                            jint networkType) {
    auto _networkType = static_cast<Monero::NetworkType>(networkType);
    const char *_address = env->GetStringUTFChars(address, nullptr);
    std::string payment_id = Monero::Wallet::paymentIdFromAddress(_address, _networkType);
    env->ReleaseStringUTFChars(address, _address);
    return env->NewStringUTF(payment_id.c_str());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getMaximumAllowedAmount(JNIEnv *env, jclass clazz) {
    return static_cast<jlong>(Monero::Wallet::maximumAllowedAmount());
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_startRefreshJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->startRefresh();
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_pauseRefresh(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->pauseRefresh();
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_refresh(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet->refresh());
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_refreshAsync(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->refreshAsync();
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_rescanBlockchainAsyncJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->rescanBlockchainAsync();
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_createTransactionMultDestJ(JNIEnv *env, jobject instance,
                                                               jobject dst_addrs,
                                                               jstring payment_id,
                                                               jlongArray amounts, jint mixin_count,
                                                               jint priority,
                                                               jint accountIndex,
                                                               jobject key_images) {
    const std::set<std::string> _key_images = java2cpp_set(env, key_images);
    const std::vector<std::string> _dst_addrs = java2cpp(env, dst_addrs);
    const std::vector<std::uint64_t> _dst_amounts = java2cpp_long(env, amounts);
    const char *_payment_id = env->GetStringUTFChars(payment_id, nullptr);
    auto _priority = static_cast<Monero::PendingTransaction::Priority>(priority);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    Monero::PendingTransaction *tx = wallet->createTransactionMultDest(_dst_addrs, _payment_id,
                                                                       _dst_amounts,
                                                                       (uint32_t) mixin_count,
                                                                       _priority,
                                                                       (uint32_t) accountIndex, {},
                                                                       _key_images);

    env->ReleaseStringUTFChars(payment_id, _payment_id);
    return reinterpret_cast<jlong>(tx);
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_estimateTransactionFee(JNIEnv *env, jobject instance,
                                                           jobject destinations, jint priority) {
    std::vector<std::pair<std::string, uint64_t>> dest_vector = java2cpp_pairvector(env,
                                                                                    destinations);
    auto _priority = static_cast<Monero::PendingTransaction::Priority>(priority);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    uint64_t fee = wallet->estimateTransactionFee(dest_vector, _priority);
    return static_cast<jlong>(fee);
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_createSweepTransaction(JNIEnv *env, jobject instance,
                                                           jstring dst_addr, jstring payment_id,
                                                           jint mixin_count,
                                                           jint priority,
                                                           jint accountIndex, jobject key_images) {
    const std::set<std::string> _key_images = java2cpp_set(env, key_images);
    const char *_dst_addr = env->GetStringUTFChars(dst_addr, nullptr);
    const char *_payment_id = env->GetStringUTFChars(payment_id, nullptr);
    auto _priority =
            static_cast<Monero::PendingTransaction::Priority>(priority);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    Monero::optional<uint64_t> empty;

    Monero::PendingTransaction *tx = wallet->createTransaction(_dst_addr, _payment_id,
                                                               empty, (uint32_t) mixin_count,
                                                               _priority,
                                                               (uint32_t) accountIndex, {},
                                                               _key_images);

    env->ReleaseStringUTFChars(dst_addr, _dst_addr);
    env->ReleaseStringUTFChars(payment_id, _payment_id);
    return reinterpret_cast<jlong>(tx);
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_createSweepUnmixableTransactionJ(JNIEnv *env,
                                                                     jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    Monero::PendingTransaction *tx = wallet->createSweepUnmixableTransaction();
    return reinterpret_cast<jlong>(tx);
}


JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_disposeTransaction(JNIEnv *env, jobject instance,
                                                       jobject pendingTransaction) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    auto *_pendingTransaction =
            getHandle<Monero::PendingTransaction>(env, pendingTransaction);
    wallet->disposeTransaction(_pendingTransaction);
}


jobject newCoinsInfo(JNIEnv *env, const Monero::Enote& info) {

    jmethodID c = env->GetMethodID(class_CoinsInfo, "<init>", "(JJJZLjava/lang/String;JLjava/lang/String;Ljava/lang/String;ZJZLjava/lang/String;)V");
    jstring _key_image = env->NewStringUTF(info.keyImage.c_str());
    jstring _pub_key = env->NewStringUTF(info.pubKey.c_str());
    jstring _hash = env->NewStringUTF(info.hash.c_str());
    jstring _address = env->NewStringUTF(info.address.c_str());
    jobject result = env->NewObject(class_CoinsInfo, c,
                                    static_cast<jlong> (info.idx),
                                    static_cast<jlong> (info.blockHeight),
                                    static_cast<jlong> (info.globalOutputIndex),
                                    info.spent,
                                    _key_image,
                                    static_cast<jlong> (info.amount),
                                    _hash,
                                    _pub_key,
                                    info.unlocked,
                                    static_cast<jlong> (info.internalOutputIndex),
                                    info.frozen,
                                    _address);

    env->DeleteLocalRef(_key_image);
    env->DeleteLocalRef(_hash);
    env->DeleteLocalRef(_pub_key);
    env->DeleteLocalRef(_address);
    return result;
}

jobject newContact(JNIEnv *env, const Monero::AddressBookRow& contact) {
    jmethodID c = env->GetMethodID(class_Contact, "<init>", "(ILjava/lang/String;Ljava/lang/String;)V");
    jstring _name = env->NewStringUTF(contact.getDescription().c_str());
    jstring _address = env->NewStringUTF(contact.getAddress().c_str());
    jobject result = env->NewObject(class_Contact, c, static_cast<jint>(contact.getRowId()), _name, _address);

    env->DeleteLocalRef(_name);
    env->DeleteLocalRef(_address);
    return result;
}

jobject coins_cpp2java(JNIEnv *env, const std::vector<Monero::Enote>& vector) {

    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add", "(Ljava/lang/Object;)Z");

    jobject arrayList = env->NewObject(class_ArrayList, java_util_ArrayList_,
                                       static_cast<jint> (vector.size()));
    for (const Monero::Enote& s: vector) {
        jobject info = newCoinsInfo(env, s);
        env->CallBooleanMethod(arrayList, java_util_ArrayList_add, info);
        env->DeleteLocalRef(info);
    }

    return arrayList;
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getEnotesJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    std::vector<Monero::Enote> enotes = wallet->enotes();
    return coins_cpp2java(env, enotes);
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_freeze(JNIEnv *env, jobject instance, jlong idx) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->freeze(static_cast<ssize_t>(idx));
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_thaw(JNIEnv *env, jobject instance, jlong idx) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->thaw(static_cast<ssize_t>(idx));
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getHistoryJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return reinterpret_cast<jlong>(wallet->history());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getAddressBookJ(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return reinterpret_cast<jlong>(wallet->addressBook());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_Wallet_setListenerJ(JNIEnv *env, jobject instance,
                                                 jobject javaListener) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setListener(nullptr); // clear old listener
    // delete old listener
    auto *oldListener = getHandle<DelegatingWalletListener>(env, instance, "listenerHandle");
    if (oldListener != nullptr) {
        oldListener->deleteGlobalJavaRef(env);
        delete oldListener;
    }
    if (javaListener == nullptr) {
        LOGD("null listener");
        return 0;
    } else {
        auto *listener = new DelegatingWalletListener(env, javaListener);
        wallet->setListener(listener);
        return reinterpret_cast<jlong>(listener);
    }
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_Wallet_setUserNote(JNIEnv *env, jobject instance,
                                                jstring txid, jstring note) {

    const char *_txid = env->GetStringUTFChars(txid, nullptr);
    const char *_note = env->GetStringUTFChars(note, nullptr);

    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    bool success = wallet->setUserNote(_txid, _note);

    env->ReleaseStringUTFChars(txid, _txid);
    env->ReleaseStringUTFChars(note, _note);

    return static_cast<jboolean>(success);
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getUserNote(JNIEnv *env, jobject instance,
                                                jstring txid) {

    const char *_txid = env->GetStringUTFChars(txid, nullptr);

    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string note = wallet->getUserNote(_txid);

    env->ReleaseStringUTFChars(txid, _txid);
    return env->NewStringUTF(note.c_str());
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getTxKey(JNIEnv *env, jobject instance,
                                             jstring txid) {

    const char *_txid = env->GetStringUTFChars(txid, nullptr);

    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string txKey = wallet->getTxKey(_txid);

    env->ReleaseStringUTFChars(txid, _txid);
    return env->NewStringUTF(txKey.c_str());
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_addAccount(JNIEnv *env, jobject instance,
                                               jstring label) {

    const char *_label = env->GetStringUTFChars(label, nullptr);

    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->addSubaddressAccount(_label);

    env->ReleaseStringUTFChars(label, _label);
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getAddressLabel(JNIEnv *env, jobject instance,
                                                       jint accountIndex, jint addressIndex) {

    auto *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string label = wallet->getSubaddressLabel((uint32_t) accountIndex,
                                                   (uint32_t) addressIndex);

    return env->NewStringUTF(label.c_str());
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_setAddressLabel(JNIEnv *env, jobject instance,
                                                       jint accountIndex, jint addressIndex,
                                                       jstring label) {

    const char *_label = env->GetStringUTFChars(label, nullptr);

    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setSubaddressLabel(accountIndex, addressIndex, _label);

    env->ReleaseStringUTFChars(label, _label);
}

JNIEXPORT jint JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getNumAccounts(JNIEnv *env, jobject instance) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jint>(wallet->numSubaddressAccounts());
}

JNIEXPORT jint JNICALL
Java_org_monfluo_wallet_android_model_Wallet_getNumAddresses(JNIEnv *env, jobject instance,
                                                       jint accountIndex) {
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jint>(wallet->numSubaddresses(accountIndex));
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_Wallet_addAddress(JNIEnv *env, jobject instance,
                                                  jint accountIndex,
                                                  jstring label) {

    const char *_label = env->GetStringUTFChars(label, nullptr);
    auto *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->addSubaddress(accountIndex, _label);
    env->ReleaseStringUTFChars(label, _label);
}


JNIEXPORT jint JNICALL
Java_org_monfluo_wallet_android_model_TransactionHistory_getCount(JNIEnv *env, jobject instance) {
    auto *history = getHandle<Monero::TransactionHistory>(env,
                                                                                instance);
    return history->count();
}

jobject newTransferInstance(JNIEnv *env, uint64_t amount, const std::string &address) {
    jmethodID c = env->GetMethodID(class_Transfer, "<init>", "(JLjava/lang/String;)V");
    jstring _address = env->NewStringUTF(address.c_str());
    jobject transfer = env->NewObject(class_Transfer, c, static_cast<jlong> (amount), _address);
    env->DeleteLocalRef(_address);
    return transfer;
}

jobject newTransferList(JNIEnv *env, Monero::TransactionInfo *info) {
    const std::vector<Monero::TransactionInfo::Transfer> &transfers = info->transfers();
    // make new ArrayList
    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add", "(Ljava/lang/Object;)Z");
    jobject result = env->NewObject(class_ArrayList, java_util_ArrayList_, static_cast<jint> (transfers.size()));
    // create Transfer objects and stick them in the List
    for (const Monero::TransactionInfo::Transfer &s: transfers) {
        jobject element = newTransferInstance(env, s.amount, s.address);
        env->CallBooleanMethod(result, java_util_ArrayList_add, element);
        env->DeleteLocalRef(element);
    }
    return result;
}

jobject newTransactionInfo(JNIEnv *env, Monero::TransactionInfo *info) {
    jmethodID c = env->GetMethodID(class_TransactionInfo, "<init>", "(IZZZJJJLjava/lang/String;JLjava/lang/String;IIJLjava/util/List;)V");
    jobject transfers = newTransferList(env, info);
    jstring _hash = env->NewStringUTF(info->hash().c_str());
    jstring _paymentId = env->NewStringUTF(info->paymentId().c_str());
    uint32_t subaddrIndex = 0;
    if (info->direction() == Monero::TransactionInfo::Direction_In)
        subaddrIndex = *(info->subaddrIndex().begin());
    jobject result = env->NewObject(class_TransactionInfo, c,
                                    info->direction(),
                                    info->isPending(),
                                    info->isFailed(),
                                    info->isCoinbase(),
                                    static_cast<jlong> (info->amount()),
                                    static_cast<jlong> (info->fee()),
                                    static_cast<jlong> (info->blockHeight()),
                                    _hash,
                                    static_cast<jlong> (info->timestamp()),
                                    _paymentId,
                                    static_cast<jint> (info->subaddrAccount()),
                                    static_cast<jint> (subaddrIndex),
                                    static_cast<jlong> (info->confirmations()),
                                    transfers);
    env->DeleteLocalRef(transfers);
    env->DeleteLocalRef(_hash);
    env->DeleteLocalRef(_paymentId);
    return result;
}

jobject stringVecToJavaList(JNIEnv *env, const std::vector<std::string> &vector) {
    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add", "(Ljava/lang/Object;)Z");

    jobject arrayList = env->NewObject(class_ArrayList, java_util_ArrayList_, static_cast<jint> (vector.size()));
    for (std::string s : vector) {
        jstring javaTxid = env->NewStringUTF(s.c_str());
        env->CallBooleanMethod(arrayList, java_util_ArrayList_add, javaTxid);
        env->DeleteLocalRef(javaTxid);
    }
    return arrayList;
}

jobject cpp2java(JNIEnv *env, const std::vector<Monero::TransactionInfo *> &vector) {

    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add", "(Ljava/lang/Object;)Z");

    jobject arrayList = env->NewObject(class_ArrayList, java_util_ArrayList_, static_cast<jint> (vector.size()));
    for (Monero::TransactionInfo *s: vector) {
        jobject info = newTransactionInfo(env, s);
        env->CallBooleanMethod(arrayList, java_util_ArrayList_add, info);
        env->DeleteLocalRef(info);
    }
    return arrayList;
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_TransactionHistory_refreshJ(JNIEnv *env, jobject instance) {
    auto *history = getHandle<Monero::TransactionHistory>(env, instance);
    history->refresh();
    return cpp2java(env, history->getAll());
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_TransactionHistory_getAllJ(JNIEnv *env, jobject instance) {
    auto *history = getHandle<Monero::TransactionHistory>(env, instance);
    return cpp2java(env, history->getAll());
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_AddressBook_getContacts(JNIEnv *env, jobject instance) {
    auto *addressBook = getHandle<Monero::AddressBook>(env, instance);

    std::vector<Monero::AddressBookRow*> all = addressBook->getAll();

    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add", "(Ljava/lang/Object;)Z");

    jobject arrayList = env->NewObject(class_ArrayList, java_util_ArrayList_, static_cast<jint>(all.size()));
    for (const Monero::AddressBookRow* row : all) {
        jobject contact = newContact(env, *row);
        env->CallBooleanMethod(arrayList, java_util_ArrayList_add, contact);
        env->DeleteLocalRef(contact);
    }

    return arrayList;
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_AddressBook_addContact(JNIEnv *env, jobject instance, jobject contact) {
    auto *addressBook = getHandle<Monero::AddressBook>(env, instance);

    jmethodID monfluo_Contact_name = env->GetMethodID(class_Contact, "getName", "()Ljava/lang/String;");
    jmethodID monfluo_Contact_address = env->GetMethodID(class_Contact, "getAddress", "()Ljava/lang/String;");

    jstring java_name = (jstring) env->CallObjectMethod(contact, monfluo_Contact_name);
    const char* _name = env->GetStringUTFChars(java_name, nullptr);
    jstring java_address = (jstring) env->CallObjectMethod(contact, monfluo_Contact_address);
    const char* _address = env->GetStringUTFChars(java_address, nullptr);

    bool result = addressBook->addRow(std::string(_address), std::string(), std::string(_name));

    env->ReleaseStringUTFChars(java_name, _name);
    env->ReleaseStringUTFChars(java_address, _address);

    return static_cast<jboolean>(result);
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_AddressBook_setName(JNIEnv *env, jobject instance, jint index, jstring name) {
    auto *addressBook = getHandle<Monero::AddressBook>(env, instance);

    const char* _name = env->GetStringUTFChars(name, nullptr);

    bool result = addressBook->setDescription(static_cast<std::size_t>(index), std::string(_name));

    env->ReleaseStringUTFChars(name, _name);

    return static_cast<jboolean>(result);
}

JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_AddressBook_delete(JNIEnv *env, jobject instance, jint index) {
    auto *addressBook = getHandle<Monero::AddressBook>(env, instance);

    bool result = addressBook->deleteRow(static_cast<std::size_t>(index));

    return static_cast<jboolean>(result);
}

JNIEXPORT jint JNICALL
Java_org_monfluo_wallet_android_model_PendingTransaction_getStatusJ(JNIEnv *env, jobject instance) {
    auto *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx->status();
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_PendingTransaction_getErrorString(JNIEnv *env, jobject instance) {
    auto *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return env->NewStringUTF(tx->errorString().c_str());
}

JNIEXPORT jobject JNICALL
Java_org_monfluo_wallet_android_model_PendingTransaction_txid(JNIEnv *env, jobject instance) {
    auto *tx = getHandle<Monero::PendingTransaction>(env, instance);
    std::vector<std::string> txids = tx->txid();
    return stringVecToJavaList(env, txids);
}

// commit transaction or save to file if filename is provided.
JNIEXPORT jboolean JNICALL
Java_org_monfluo_wallet_android_model_PendingTransaction_commit(JNIEnv *env, jobject instance,
                                                       jstring filename, jboolean overwrite) {

    const char *_filename = env->GetStringUTFChars(filename, nullptr);

    auto *tx = getHandle<Monero::PendingTransaction>(env, instance);
    bool success = tx->commit(_filename, overwrite);

    env->ReleaseStringUTFChars(filename, _filename);
    return static_cast<jboolean>(success);
}


JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_PendingTransaction_getAmount(JNIEnv *env, jobject instance) {
    auto *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return static_cast<jlong>(tx->amount());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_PendingTransaction_getDust(JNIEnv *env, jobject instance) {
    auto *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return static_cast<jlong>(tx->dust());
}

JNIEXPORT jlong JNICALL
Java_org_monfluo_wallet_android_model_PendingTransaction_getFee(JNIEnv *env, jobject instance) {
    auto *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return static_cast<jlong>(tx->fee());
}


JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_initLogger(JNIEnv *env, jclass clazz,
                                                      jstring argv0,
                                                      jstring default_log_base_name) {

    const char *_argv0 = env->GetStringUTFChars(argv0, nullptr);
    const char *_default_log_base_name = env->GetStringUTFChars(default_log_base_name, nullptr);

    Monero::Wallet::init(_argv0, _default_log_base_name);

    env->ReleaseStringUTFChars(argv0, _argv0);
    env->ReleaseStringUTFChars(default_log_base_name, _default_log_base_name);
}

JNIEXPORT void JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_setLogLevel(JNIEnv *env, jclass clazz, jint level) {
    Monero::WalletManagerFactory::setLogLevel(level);
}

JNIEXPORT jstring JNICALL
Java_org_monfluo_wallet_android_model_WalletManager_moneroVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(MONERO_VERSION);
}

#ifdef __cplusplus
}
#endif
