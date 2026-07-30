use jni::{
    Env, EnvUnowned,
    errors::ThrowRuntimeExAndDefault,
    jni_mangle,
    objects::{JObject, JString},
    sys::jint,
};
use monfluo_oxide::{
    monero_daemon_rpc::MoneroDaemon,
    transport::MonfluoTransport,
    wallet::{Wallet, WalletSyncObserver},
};

struct MonfluoJavaObserver<'a, 'local> {
    env: &'a mut Env<'local>,
    cache: &'static crate::java::JniCache,
    observer: &'a JObject<'local>,
}

impl<'a, 'local> WalletSyncObserver for &mut MonfluoJavaObserver<'a, 'local> {
    async fn observe(&mut self, state: monfluo_oxide::wallet::WalletSyncState) {
        let java_wallet_sync_state = crate::java::new_wallet_sync_state(self.env, state);
        crate::java::wallet_sync_observer_observe(
            self.env,
            self.cache,
            self.observer,
            &java_wallet_sync_state,
        );
    }
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getName")]
pub fn get_name<'local>(mut env: EnvUnowned<'local>, wallet: JObject<'local>) -> JString<'local> {
    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
            let name = wallet.name();
            let java_name = name.map_or(JString::null(), |name| env.new_string(name).unwrap());

            Ok::<JString, jni::errors::Error>(java_name)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getBalance")]
pub fn get_balance<'local>(
    mut env: EnvUnowned<'local>,
    wallet: JObject<'local>,
) -> JObject<'local> {
    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
            let balance = wallet.balance_view();
            let java_balance = crate::java::new_balance(env, balance);
            Ok::<JObject, jni::errors::Error>(java_balance)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getAccountView")]
pub fn get_account_view<'local>(
    mut env: EnvUnowned<'local>,
    wallet: JObject<'local>,
    account_index: jint,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let account_view = wallet.account_view(account_index as u32);
            let java_account_view = crate::java::new_account_view(env, cache, account_view);
            Ok::<JObject, jni::errors::Error>(java_account_view)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getTransactionViews")]
pub fn get_transaction_views<'local>(
    mut env: EnvUnowned<'local>,
    wallet: JObject<'local>,
    _account_index: jint,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let transaction_views = wallet.transaction_views();
            let java_transactions_view =
                crate::java::new_transaction_views(env, cache, &transaction_views);

            Ok::<JObject, jni::errors::Error>(java_transactions_view)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "sync")]
pub fn refresh<'local>(
    mut env: EnvUnowned<'local>,
    wallet: JObject<'local>,
    daemon: JObject<'local>,
    observer: JObject<'local>,
) {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let daemon_handle = crate::java::get_handle(env, &daemon);
            let daemon =
                crate::bridge::from_java_handle::<MoneroDaemon<MonfluoTransport>>(daemon_handle);
            let mut monfluo_java_observer = MonfluoJavaObserver {
                env,
                cache,
                observer: &observer,
            };
            wallet.sync(daemon, &mut monfluo_java_observer).await;
            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "registerNewAddress")]
pub fn register_new_address<'local>(
    mut env: EnvUnowned<'local>,
    wallet: JObject<'local>,
    account_index: u32,
) -> jint {
    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let new_address = wallet.register_new_address(account_index).expect(&format!(
                "failed to register new address for account {}",
                account_index
            ));
            Ok::<jint, jni::errors::Error>(new_address as i32)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "store")]
pub fn store<'local>(mut env: EnvUnowned<'local>, wallet: JObject<'local>) {
    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            wallet.store().await;
            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "close")]
pub fn close<'local>(mut env: EnvUnowned<'local>, wallet: JObject<'local>) {
    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            crate::bridge::drop(wallet);
            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}
