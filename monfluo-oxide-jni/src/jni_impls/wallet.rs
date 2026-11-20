use jni::{
    EnvUnowned,
    errors::ThrowRuntimeExAndDefault,
    jni_mangle,
    objects::{JObject, JString},
    refs::Global,
    sys::jint,
    vm::JavaVM,
};
use monfluo_oxide::{
    monero_daemon_rpc::MoneroDaemon,
    monero_wallet::transaction::NotPruned,
    transport::MonfluoTransport,
    wallet::{TransactionWrapper, Wallet, WalletSyncObserver},
};
use tokio::sync::{Mutex, Notify};
use tracing::{debug, error};

static PENDING_TRANSACTION: Mutex<Option<TransactionWrapper<NotPruned>>> = Mutex::const_new(None);

struct MonfluoJavaObserver {
    vm: JavaVM,
    cache: &'static crate::java::JniCache,
    observer: Global<JObject<'static>>,
}

impl WalletSyncObserver for MonfluoJavaObserver {
    fn observe(&mut self, state: monfluo_oxide::wallet::WalletSyncState) {
        debug!("Notifying java observer about new wallet state: {state:?}");
        let result = self.vm.attach_current_thread_for_scope(|env| {
            debug!("Java VM attached for notifying java observer");
            let java_wallet_sync_state = crate::java::new_wallet_sync_state(env, self.cache, state);
            debug!("Java wallet sync state object created");
            crate::java::wallet_sync_observer_observe(
                env,
                self.cache,
                self.observer.as_obj(),
                &java_wallet_sync_state,
            );
            debug!("Java observer notified");
            Ok::<(), jni::errors::Error>(())
        });
        if let Err(e) = result {
            error!("Failed to notify java observer: {e}");
        }
    }
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getName")]
pub fn get_name<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
) -> JString<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
            let name = wallet.name();
            let java_name = name.map_or(JString::null(), |name| env.new_string(name).unwrap());

            Ok::<JString, jni::errors::Error>(java_name)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getWalletView")]
pub fn get_wallet_view<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
            let wallet_view = wallet.wallet_view().await;
            let java_wallet_view = crate::java::new_wallet_view(env, cache, &wallet_view);
            Ok::<JObject, jni::errors::Error>(java_wallet_view)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getSeedView")]
pub fn get_seed_view<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
            let seed_view = wallet.seed_view();
            let java_seed_view = seed_view.as_ref().map_or(JObject::null(), |seed_view| {
                crate::java::new_wallet_seed_view(env, cache, seed_view)
            });
            Ok::<JObject, jni::errors::Error>(java_seed_view)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getSpendKey")]
pub fn get_spend_key<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        let handle = crate::java::get_handle(env, cache, &java_wallet);
        let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
        let spend_key = wallet.spend_key();
        Ok::<JObject, jni::errors::Error>(crate::java::new_monero_bytes(env, cache, &spend_key))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getViewKey")]
pub fn get_view_key<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        let handle = crate::java::get_handle(env, cache, &java_wallet);
        let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
        let view_key = wallet.view_key();
        Ok::<JObject, jni::errors::Error>(crate::java::new_monero_bytes(env, cache, &view_key))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getPublicSpendKey")]
pub fn get_public_spend_key<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        let handle = crate::java::get_handle(env, cache, &java_wallet);
        let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
        let public_spend_key = wallet.public_spend_key();
        Ok::<JObject, jni::errors::Error>(crate::java::new_monero_bytes(
            env,
            cache,
            &public_spend_key,
        ))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getPublicViewKey")]
pub fn get_public_view_key<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        let handle = crate::java::get_handle(env, cache, &java_wallet);
        let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
        let public_view_key = wallet.public_view_key();
        Ok::<JObject, jni::errors::Error>(crate::java::new_monero_bytes(
            env,
            cache,
            &public_view_key,
        ))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getLegacySeed")]
pub fn get_legacy_seed<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
    java_monero_seed_language: JObject<'local>,
    java_seed_offset_passphrase: JString<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        let handle = crate::java::get_handle(env, cache, &java_wallet);
        let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
        let language =
            crate::java::from_java_monero_seed_language(env, cache, &java_monero_seed_language);
        let seed_offset_passphrase =
            crate::java::JavaStringHelper::from_jstring(env, &java_seed_offset_passphrase);
        let seed = wallet.legacy_seed(language, &seed_offset_passphrase.to_cow_str());
        let seed = seed.to_string();
        let java_mnemonic = env
            .new_string(seed.as_str())
            .expect("Failed to create java string for the legacy seed");
        let java_language = crate::java::to_java_monero_seed_language(env, cache, language);
        Ok::<JObject, jni::errors::Error>(crate::java::new_monero_seed(
            env,
            cache,
            &java_mnemonic,
            &java_language,
        ))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getAddress")]
pub fn get_address<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
) -> JString<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        let handle = crate::java::get_handle(env, cache, &java_wallet);
        let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
        let address = wallet.address(None).to_string();
        Ok::<JString, jni::errors::Error>(
            env.new_string(address)
                .expect("Failed to create java string for the wallet address"),
        )
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "network")]
pub fn network<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        let handle = crate::java::get_handle(env, cache, &java_wallet);
        let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
        let java_network = crate::java::to_java_network(env, cache, wallet.network());
        Ok::<JObject, jni::errors::Error>(java_network)
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "sync")]
pub fn sync<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
    java_daemon: JObject<'local>,
    java_observer: JObject<'local>,
) {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        let vm = env.get_java_vm().expect("Failed to get Java VM");
        let global_observer = env
            .new_global_ref(java_observer)
            .expect("Failed to make a global ref of a java observer");
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let daemon_handle = crate::java::get_handle(env, cache, &java_daemon);
            let daemon =
                crate::bridge::from_java_handle::<MoneroDaemon<MonfluoTransport>>(daemon_handle);

            let monfluo_java_observer = MonfluoJavaObserver {
                vm,
                cache,
                observer: global_observer,
            };
            let cancel = Notify::new();
            wallet.sync(daemon, monfluo_java_observer, &cancel).await;
            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "registerNewAddress")]
pub fn register_new_address<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
    java_account_index: jint,
) -> jint {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let account_index = java_account_index as u32;
            let new_address = wallet.register_new_address(account_index).await;
            Ok::<jint, jni::errors::Error>(new_address as i32)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "createAndSignTransaction")]
pub fn create_and_sign_transaction<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
    java_daemon: JObject<'local>,
    java_account_index: jint,
    java_payments: JObject<'local>,
    java_fee_priority: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let daemon_handle = crate::java::get_handle(env, cache, &java_daemon);
            let daemon =
                crate::bridge::from_java_handle::<MoneroDaemon<MonfluoTransport>>(daemon_handle);
            let payments = crate::java::from_java_list(env, cache, &java_payments)
                .into_iter()
                .map(|java_payment| crate::java::from_java_payment(env, cache, &java_payment))
                .collect::<Vec<_>>();
            let account_index = java_account_index as u32;
            let fee_priority = crate::java::from_java_fee_priority(env, cache, &java_fee_priority);
            let (transaction, outgoing_transfer) = wallet
                .create_transaction(daemon, account_index, payments, fee_priority)
                .await
                .expect("Failed to create transaction");
            let transaction = wallet
                .sign_transaction(transaction)
                .await
                .expect("Failed to sign transaction");
            let first_seen_timestamp = monfluo_oxide::current_timestamp();
            let pending_transaction = TransactionWrapper {
                inner: transaction.clone(),
                hash: transaction.hash(),
                block_info: None,
                first_seen_timestamp,
                outgoing_transfer: Some(outgoing_transfer.clone()),
            };
            let transaction_view = wallet
                .create_transaction_view_of(&pending_transaction)
                .await;
            *PENDING_TRANSACTION.lock().await = Some(pending_transaction);
            let java_transaction_view =
                crate::java::new_transaction_view(env, cache, &transaction_view);
            Ok::<JObject<'local>, jni::errors::Error>(java_transaction_view)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle(
    "org.monfluo.wallet.lib.wallet.Wallet",
    "createAndSignSweepTransaction"
)]
pub fn create_and_sign_sweep_transaction<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
    java_daemon: JObject<'local>,
    java_account_index: jint,
    java_address: JObject<'local>,
    java_fee_priority: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let daemon_handle = crate::java::get_handle(env, cache, &java_daemon);
            let daemon =
                crate::bridge::from_java_handle::<MoneroDaemon<MonfluoTransport>>(daemon_handle);
            let address = crate::java::from_java_address(env, cache, &java_address);
            let account_index = java_account_index as u32;
            let fee_priority = crate::java::from_java_fee_priority(env, cache, &java_fee_priority);
            let (transaction, outgoing_transfer) = wallet
                .create_sweep_transaction(daemon, account_index, address, fee_priority)
                .await
                .expect("Failed to create sweep transaction");
            let transaction = wallet
                .sign_transaction(transaction)
                .await
                .expect("Failed to sign transaction");
            let first_seen_timestamp = monfluo_oxide::current_timestamp();
            let pending_transaction = TransactionWrapper {
                inner: transaction.clone(),
                hash: transaction.hash(),
                block_info: None,
                first_seen_timestamp,
                outgoing_transfer: Some(outgoing_transfer.clone()),
            };
            let transaction_view = wallet
                .create_transaction_view_of(&pending_transaction)
                .await;
            *PENDING_TRANSACTION.lock().await = Some(pending_transaction);
            let java_transaction_view =
                crate::java::new_transaction_view(env, cache, &transaction_view);
            Ok::<JObject<'local>, jni::errors::Error>(java_transaction_view)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "sendTransaction")]
pub fn send_transaction<'local>(
    mut env: EnvUnowned<'local>,
    java_wallet: JObject<'local>,
    java_daemon: JObject<'local>,
    java_transaction_hash: JObject<'local>,
) {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let daemon_handle = crate::java::get_handle(env, cache, &java_daemon);
            let daemon =
                crate::bridge::from_java_handle::<MoneroDaemon<MonfluoTransport>>(daemon_handle);
            let transaction_hash =
                crate::java::from_monero_bytes(env, cache, &java_transaction_hash);
            let mut pending_transaction_guard = PENDING_TRANSACTION.lock().await;
            match &*pending_transaction_guard {
                Some(pending_tranasction) => {
                    if pending_tranasction.inner.hash() != transaction_hash {
                        panic!("Wrong pending transaction hash");
                    }
                    wallet
                        .send_transaction(daemon, pending_tranasction.clone())
                        .await
                        .expect("Failed to send transaction");
                    *pending_transaction_guard = None;
                }
                None => panic!("There is no pending transaction"),
            }
            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "store")]
pub fn store<'local>(mut env: EnvUnowned<'local>, java_wallet: JObject<'local>) {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            wallet.store().await.expect("Failed to store wallet");
            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "close")]
pub fn close<'local>(mut env: EnvUnowned<'local>, java_wallet: JObject<'local>) {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, cache, &java_wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            wallet.store().await.expect("Failed to store wallet");
            crate::bridge::drop(wallet);
            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}
