#![forbid(clippy::unwrap_used)]

pub mod java;

use std::str::FromStr;

use jni::objects::JObject;
use jni::sys::{JNI_VERSION_1_6, jint, jobject};
use jni::{JNIEnv, JavaVM};

use jni_macros::jni_mangle;

#[unsafe(no_mangle)]
pub extern "system" fn JNI_OnLoad(jvm: JavaVM, _reserved: *mut std::ffi::c_void) -> jint {
    let mut env = jvm.get_env().expect("Failed to get env during JNI load");
    java::init_cache(&mut env);
    JNI_VERSION_1_6
}

#[jni_mangle("org.monfluo.wallet.lib.seed.MoneroSeedLanguage", "getWords")]
pub fn get_words_monero<'local>(mut env: JNIEnv<'local>, object: JObject<'local>) -> jobject {
    let cache = java::get_cache();

    let list = java::ArrayList::new(&mut env);
    let enum_name = java::Enum(object).name(&mut env, cache);
    let enum_name = env
        .get_string(&enum_name)
        .expect("Failed to get string from MoneroSeedLanguage enum name");
    let enum_name = enum_name
        .to_str()
        .expect("MoneroSeedLanguage enum name was not a UTF-8 string");
    let words = monfluo_oxide::monero_wallet_util::seed::original::Language::from_str(enum_name)
        .unwrap()
        .get_words();

    for word in words {
        let word = env
            .new_string(word)
            .expect("Failed to create java string for a monero language word");
        list.add(&mut env, cache, JObject::from(word));
    }

    list.0.into_raw()
}

#[jni_mangle("org.monfluo.wallet.lib.seed.PolyseedLanguage", "getWords")]
pub fn get_words_polyseed<'local>(mut env: JNIEnv<'local>, object: JObject<'local>) -> jobject {
    let cache = java::get_cache();

    let list = java::ArrayList::new(&mut env);
    let enum_name = java::Enum(object).name(&mut env, cache);
    let enum_name = env
        .get_string(&enum_name)
        .expect("Failed to get string from PolyseedLanguage enum name");
    let enum_name = enum_name
        .to_str()
        .expect("PolyseedLanguage enum name was not a UTF-8 string");
    let words = monfluo_oxide::monero_wallet_util::seed::polyseed::Language::from_str(enum_name)
        .unwrap()
        .get_words();

    for word in words {
        let word = env
            .new_string(word)
            .expect("Failed to create java string for a polyseed language word");
        list.add(&mut env, cache, JObject::from(word));
    }

    list.0.into_raw()
}
