use std::panic::catch_unwind;

use jni::{
    EnvUnowned,
    errors::ThrowRuntimeExAndDefault,
    jni_mangle,
    objects::{JObject, JString},
    refs::Global,
    sys::{jint, jlong},
    vm::JavaVM,
};
use monfluo_oxide::{
    monero_daemon_rpc::MoneroDaemon,
    transport::MonfluoTransport,
    wallet::{Wallet, WalletSyncObserver},
};
use tracing::{debug, error, info};

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

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getCurrentHeight")]
pub fn get_height<'local>(mut env: EnvUnowned<'local>, wallet: JObject<'local>) -> jint {
    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
            let height = wallet.state().height();

            Ok::<jint, jni::errors::Error>(height.current.unwrap_or(0) as jint)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.wallet.Wallet", "getBalance")]
pub fn get_balance<'local>(
    mut env: EnvUnowned<'local>,
    wallet: JObject<'local>,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(handle);
            let balance = wallet.balance_view();
            let java_balance = crate::java::new_balance(env, cache, balance);
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
pub fn sync<'local>(
    mut env: EnvUnowned<'local>,
    wallet: JObject<'local>,
    daemon: JObject<'local>,
    observer: JObject<'local>,
) {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        let vm = env.get_java_vm().expect("Failed to get Java VM");
        let global_observer = env
            .new_global_ref(observer)
            .expect("Failed to make a global ref of a java observer");
        crate::RUNTIME.block_on(async {
            let wallet_handle = crate::java::get_handle(env, &wallet);
            let wallet = crate::bridge::from_java_handle::<Wallet>(wallet_handle);
            let daemon_handle = crate::java::get_handle(env, &daemon);
            let daemon =
                crate::bridge::from_java_handle::<MoneroDaemon<MonfluoTransport>>(daemon_handle);

            let monfluo_java_observer = MonfluoJavaObserver {
                vm,
                cache,
                observer: global_observer,
            };
            wallet.sync(&crate::RUNTIME, daemon, monfluo_java_observer);
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
            wallet.close().await;
            crate::bridge::drop(wallet);
            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}
