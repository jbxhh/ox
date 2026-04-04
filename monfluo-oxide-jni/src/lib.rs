#![forbid(clippy::unwrap_used)]

pub mod java;

use std::str::FromStr;

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::JObject;
use jni::sys::{JNI_VERSION_1_6, jint, jobject};
use jni::{EnvUnowned, JavaVM};

use jni_macros::jni_mangle;

#[unsafe(no_mangle)]
pub extern "system" fn JNI_OnLoad(
    sys_jvm: *mut jni::sys::JavaVM,
    _reserved: *mut std::ffi::c_void,
) -> jint {
    let jvm = unsafe { JavaVM::from_raw(sys_jvm) };
    jvm.with_top_local_frame(|mut env| {
        java::init_cache(&mut env);
        Ok::<(), jni::errors::Error>(())
    })
    .expect("Failed to init java cache");
    JNI_VERSION_1_6
}

#[jni_mangle("org.monfluo.wallet.lib.seed.MoneroSeedLanguage", "getWords")]
pub fn get_words_monero<'local>(mut env: EnvUnowned<'local>, object: JObject<'local>) -> jobject {
    let cache = java::get_cache();

    env.with_env(|mut env| {
        let list = java::ArrayList::new(&mut env);
        let enum_name = java::Enum(object).name(&mut env, cache);
        let enum_name = enum_name
            .mutf8_chars(&env)
            .expect("Failed to get string from MoneroSeedLanguage enum name");
        let enum_name = enum_name.to_str();
        let words =
            monfluo_oxide::monero_wallet_util::seed::original::Language::from_str(&enum_name)
                .unwrap()
                .get_words();

        for word in words {
            let word = env
                .new_string(word)
                .expect("Failed to create java string for a monero language word");
            list.add(&mut env, cache, JObject::from(word));
        }

        Ok::<jobject, jni::errors::Error>(list.0.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

#[jni_mangle("org.monfluo.wallet.lib.seed.PolyseedLanguage", "getWords")]
pub fn get_words_polyseed<'local>(mut env: EnvUnowned<'local>, object: JObject<'local>) -> jobject {
    let cache = java::get_cache();

    env.with_env(|mut env| {
        let list = java::ArrayList::new(&mut env);
        let enum_name = java::Enum(object).name(&mut env, cache);
        let enum_name = enum_name
            .mutf8_chars(&env)
            .expect("Failed to get string from PolyseedLanguage enum name");
        let enum_name = enum_name.to_str();
        let words =
            monfluo_oxide::monero_wallet_util::seed::polyseed::Language::from_str(&enum_name)
                .unwrap()
                .get_words();

        for word in words {
            let word = env
                .new_string(word)
                .expect("Failed to create java string for a polyseed language word");
            list.add(&mut env, cache, JObject::from(word));
        }

        Ok::<jobject, jni::errors::Error>(list.0.into_raw())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}
