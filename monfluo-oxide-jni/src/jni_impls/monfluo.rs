use std::{path::PathBuf, str::FromStr, sync::Arc};

use jni::{
    EnvUnowned,
    errors::ThrowRuntimeExAndDefault,
    jni_mangle,
    objects::{JClass, JObject, JString},
};
use monfluo_oxide::transport::MonfluoTransport;

#[jni_mangle("org.monfluo.wallet.lib.Monfluo", "monfluoVersion")]
pub fn monfluo_version<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
) -> JObject<'local> {
    env.with_env(|env| {
        let version = env.new_string(monfluo_oxide::VERSION).unwrap();
        Ok::<JObject<'local>, jni::errors::Error>(version.into())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.Monfluo", "listWallets")]
pub fn list_wallets<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    monfluo_directory: JString,
) -> JObject<'local> {
    let cache = crate::java::get_cache();

    env.with_env(|mut env| {
        crate::RUNTIME.block_on(async {
            let monfluo_directory = PathBuf::from_str(
                &crate::java::JavaStringHelper::from_jstring(env, &monfluo_directory).to_str(),
            )
            .unwrap();
            let wallets = monfluo_oxide::wallet::Wallet::list(monfluo_directory)
                .await
                .unwrap();
            let list = crate::java::arraylist_new(&mut env);
            for wallet in wallets {
                let wallet = env
                    .new_string(wallet)
                    .expect("Failed to create java string for a wallet path");
                crate::java::list_add(&mut env, cache, &list, &JObject::from(wallet));
            }
            Ok::<JObject, jni::errors::Error>(list)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.Monfluo", "deleteWallet")]
pub fn delete_wallet<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    monfluo_directory: JString,
    wallet_name: JString,
) {
    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let monfluo_directory = PathBuf::from_str(
                &crate::java::JavaStringHelper::from_jstring(env, &monfluo_directory).to_str(),
            )
            .unwrap();
            let wallet_name = crate::java::JavaStringHelper::from_jstring(env, &wallet_name);
            monfluo_oxide::wallet::Wallet::delete(monfluo_directory, &wallet_name.to_str())
                .await
                .unwrap();
            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.Monfluo", "createWallet")]
pub fn create_wallet<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    monfluo_directory: JString,
    name: JString,
    password: JString,
    seed_language: JObject<'local>,
    _seed_offset: JString,
) {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let monfluo_directory = PathBuf::from_str(
                &crate::java::JavaStringHelper::from_jstring(env, &monfluo_directory).to_str(),
            )
            .unwrap();
            let wallet_name = crate::java::JavaStringHelper::from_jstring(env, &name);
            let password = crate::java::JavaStringHelper::from_jstring(env, &password);

            let seed_type = crate::java::from_java_seed_language(env, cache, &seed_language);
            let seed = match seed_type {
                monfluo_oxide::monero_wallet_util::seed::SeedType::Original(language) => {
                    monfluo_oxide::monero_wallet_util::seed::Seed::Original(
                        monfluo_oxide::generate_seed(language),
                    )
                }
                monfluo_oxide::monero_wallet_util::seed::SeedType::Polyseed(language) => {
                    monfluo_oxide::monero_wallet_util::seed::Seed::Polyseed(
                        monfluo_oxide::generate_polyseed(language),
                    )
                }
            };

            monfluo_oxide::wallet::Wallet::create(
                monfluo_directory,
                &wallet_name.to_str(),
                &password.to_str(),
                seed,
                None,
                None,
            )
            .await
            .unwrap();

            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>();
}

#[jni_mangle("org.monfluo.wallet.lib.Monfluo", "restoreWallet")]
pub fn restore_wallet<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    monfluo_directory: JString,
    name: JString,
    password: JString,
    seed: JObject<'local>,
    _seed_offset: JString,
    height: JObject<'local>,
) {
    let cache = crate::java::get_cache();

    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let monfluo_directory = PathBuf::from_str(
                &crate::java::JavaStringHelper::from_jstring(env, &monfluo_directory).to_str(),
            )
            .unwrap();
            let wallet_name = crate::java::JavaStringHelper::from_jstring(env, &name);
            let password = crate::java::JavaStringHelper::from_jstring(env, &password);

            let seed = crate::java::from_java_seed(env, cache, &seed);
            let height = crate::java::from_java_number_int_value(env, cache, &height)
                .map(|height| height as u32);

            monfluo_oxide::wallet::Wallet::create(
                monfluo_directory,
                &wallet_name.to_str(),
                &password.to_str(),
                seed,
                None,
                height,
            )
            .await
            .unwrap();

            Ok::<(), jni::errors::Error>(())
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>();
}

#[jni_mangle("org.monfluo.wallet.lib.Monfluo", "openWallet")]
pub fn open_wallet<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    monfluo_directory: JString,
    name: JString,
    password: JString,
) -> JObject<'local> {
    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let monfluo_directory = PathBuf::from_str(
                &crate::java::JavaStringHelper::from_jstring(env, &monfluo_directory).to_str(),
            )
            .unwrap();
            let wallet_name = crate::java::JavaStringHelper::from_jstring(env, &name);
            let password = crate::java::JavaStringHelper::from_jstring(env, &password);

            let wallet = monfluo_oxide::wallet::Wallet::open(
                monfluo_directory,
                &wallet_name.to_str(),
                &password.to_str(),
            )
            .await
            .unwrap();
            let wallet_handle = crate::bridge::into_java_handle(Arc::new(wallet));
            let java_wallet = crate::java::new_wallet(env, wallet_handle);

            Ok::<JObject, jni::errors::Error>(java_wallet)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.Monfluo", "createDaemon")]
pub fn create_daemon<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    url: JString,
    proxy: JString,
) {
    env.with_env(|env| {
        crate::RUNTIME.block_on(async {
            let url = crate::java::JavaStringHelper::from_jstring(env, &url);
            let _proxy = if proxy.is_null() {
                None
            } else {
                Some(crate::java::JavaStringHelper::from_jstring(env, &proxy))
            };

            let daemon = MonfluoTransport::new(url.to_str().into_owned(), None)
                .await
                .unwrap();
            let daemon_handle = crate::bridge::into_java_handle(Arc::new(daemon));
            let java_daemon = crate::java::new_daemon(env, daemon_handle);

            Ok::<JObject, jni::errors::Error>(java_daemon)
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>();
}
