#![allow(non_snake_case, non_upper_case_globals)]

use std::{borrow::Cow, fmt::Debug, ops::Deref, str::FromStr, sync::OnceLock};

use const_format::concatcp;
use jni::{
    Env, JValueOwned,
    objects::{JClass, JMethodID, JObject, JString, JValue},
    refs::Global,
    signature::{RuntimeFieldSignature, RuntimeMethodSignature},
    strings::{JNIString, MUTF8Chars},
    sys::{jint, jlong},
};

#[rustfmt::skip]
mod constants {
    use const_format::concatcp as concat;

    pub const CLASSJ__java_lang_Object: &'static str = "java/lang/Object";
    pub const CLASSJ__java_lang_String: &'static str = "java/lang/String";
    pub const CLASSJ__java_lang_Number: &'static str = "java/lang/Number";
    pub const CLASS__java_lang_Number__METHOD__intValue0: &'static str = "intValue";
    pub const CLASS__java_lang_Number__METHOD__intValue0__SIGNATURE: &'static str = "()I";
    pub const CLASS__java_lang_Enum: &'static str = "java/lang/Enum";
    pub const CLASS__java_lang_Enum__METHOD__name0: &'static str = "name";
    pub const CLASS__java_lang_Enum__METHOD__name0__SIGNATURE: &'static str = concat!("()L", CLASSJ__java_lang_String, ";");
    
    pub const CLASSJ__java_util_List: &'static str = "java/util/List";
    pub const CLASS__java_util_List__METHOD__add1: &'static str = "add";
    pub const CLASS__java_util_List__METHOD__add1__SIGNATURE: &'static str = concat!("(L", CLASSJ__java_lang_Object, ";)Z");
    pub const CLASS__java_util_ArrayList: &'static str = "java.util.ArrayList";
    pub const CLASS__java_util_ArrayList__METHOD__init0__SIGNATURE: &'static str = "()V";
    
    pub const CLASSJ__org_monfluo_wallet_lib_seed_SeedLanguge: &'static str = "org/monfluo/wallet/lib/seed/SeedLanguage";
    pub const CLASS__org_monfluo_wallet_lib_seed_Seed: &'static str = "org/monfluo/wallet/lib/seed/Seed";
    pub const CLASS__org_monfluo_wallet_lib_seed_Seed__METHOD__getMnemonic0: &'static str = "getMnemonic";
    pub const CLASS__org_monfluo_wallet_lib_seed_Seed__METHOD__getMnemonic0__SIGNATURE: &'static str = concat!("()L", CLASSJ__java_lang_String, ";");
    pub const CLASS__org_monfluo_wallet_lib_seed_Seed__METHOD__getLanguage0: &'static str = "getLanguage";
    pub const CLASS__org_monfluo_wallet_lib_seed_Seed__METHOD__getLanguage0__SIGNATURE: &'static str = concat!("()L", CLASSJ__org_monfluo_wallet_lib_seed_SeedLanguge, ";");
    pub const CLASS__org_monfluo_wallet_lib_seed_MoneroSeedLanguage: &'static str = "org.monfluo.wallet.lib.seed.MoneroSeedLanguage";
    pub const CLASSJ__org_monfluo_wallet_lib_seed_MoneroSeedLanguage: &'static str = "org/monfluo/wallet/lib/seed/MoneroSeedLanguage";
    pub const CLASS__org_monfluo_wallet_lib_seed_MoneroSeed: &'static str = "org.monfluo.wallet.lib.seed.MoneroSeed";
    pub const CLASS__org_monfluo_wallet_lib_seed_MoneroSeed__METHOD__init2__SIGNATURE: &'static str = concat!("(L", CLASSJ__java_lang_String, ";L", CLASSJ__org_monfluo_wallet_lib_seed_MoneroSeedLanguage, ";)V");
    pub const CLASS__org_monfluo_wallet_lib_seed_PolyseedLanguage: &'static str = "org.monfluo.wallet.lib.seed.PolyseedLanguage";
    pub const CLASSJ__org_monfluo_wallet_lib_seed_PolyseedLanguage: &'static str = "org/monfluo/wallet/lib/seed/PolyseedLanguage";
    pub const CLASS__org_monfluo_wallet_lib_seed_Polyseed: &'static str = "org.monfluo.wallet.lib.seed.Polyseed";
    pub const CLASS__org_monfluo_wallet_lib_seed_Polyseed__METHOD__init2__SIGNATURE: &'static str = concat!("(L", CLASSJ__java_lang_String, ";L", CLASSJ__org_monfluo_wallet_lib_seed_PolyseedLanguage, ";)V");
    
    pub const CLASS__org_monfluo_wallet_lib_wallet_Wallet: &'static str = "org.monfluo.wallet.lib.wallet.Wallet";
    pub const CLASS__org_monfluo_wallet_lib_wallet_Wallet__METHOD__init1__SIGNATURE: &'static str = "(J)V";
    pub const CLASS__org_monfluo_wallet_lib_wallet_Daemon: &'static str = "org.monfluo.wallet.lib.wallet.Daemon";
    pub const CLASS__org_monfluo_wallet_lib_wallet_Daemon__METHOD__init6__SIGNATURE: &'static str = concat!("(JL", CLASSJ__java_lang_String, ";L", CLASSJ__java_lang_String, ";L", CLASSJ__java_lang_String, ";L", CLASSJ__java_lang_String, ";L", CLASSJ__java_lang_String, ";)V");

    pub const CLASSJ__org_monfluo_wallet_lib_wallet_sync_WalletSyncObserver: &'static str = "org/monfluo/wallet/lib/wallet/sync/WalletSyncObserver";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_WalletSyncObserver__METHOD__observe1: &'static str = "observe";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_WalletSyncObserver__METHOD__observe1__SIGNATURE: &'static str = concat!("(L", CLASSJ__org_monfluo_wallet_lib_wallet_sync_WalletSyncState, ";)V");
    pub const CLASSJ__org_monfluo_wallet_lib_wallet_sync_WalletSyncError: &'static str = "org/monfluo/wallet/lib/wallet/sync/WalletSyncError";
    pub const CLASSJ__org_monfluo_wallet_lib_wallet_sync_WalletSyncState: &'static str = "org/monfluo/wallet/lib/wallet/sync/WalletSyncState";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Preparing: &'static str = "org.monfluo.wallet.lib.wallet.sync.Preparing";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Preparing__METHOD__init2__SIGNATURE: &'static str = "(JI)V";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Starting: &'static str = "org.monfluo.wallet.lib.wallet.sync.Starting";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Starting__METHOD__init4__SIGNATURE: &'static str = concat!("(JIIL", CLASSJ__org_monfluo_wallet_lib_data_NodeInfo, ";)V");
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Syncing: &'static str = "org.monfluo.wallet.lib.wallet.sync.Syncing";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Syncing__METHOD__init3__SIGNATURE: &'static str = "(JII)V";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Finished: &'static str = "org.monfluo.wallet.lib.wallet.sync.Finished";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Finished__METHOD__init2__SIGNATURE: &'static str = "(JI)V";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Cancelled: &'static str = "org.monfluo.wallet.lib.wallet.sync.Cancelled";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Cancelled__METHOD__init1__SIGNATURE: &'static str = "(J)V";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Failed: &'static str = "org.monfluo.wallet.lib.wallet.sync.Failed";
    pub const CLASS__org_monfluo_wallet_lib_wallet_sync_Failed__METHOD__init2__SIGNATURE: &'static str = concat!("(JL", CLASSJ__org_monfluo_wallet_lib_wallet_sync_WalletSyncError, ";)V");

    pub const CLASS__org_monfluo_wallet_lib_data_Balance: &'static str = "org.monfluo.wallet.lib.data.Balance";
    pub const CLASS__org_monfluo_wallet_lib_data_Balance__METHOD__init4__SIGNATURE: &'static str = "(JJJJ)V";
    pub const CLASS__org_monfluo_wallet_lib_data_AccountView: &'static str = "org.monfluo.wallet.lib.data.AccountView";
    pub const CLASS__org_monfluo_wallet_lib_data_AccountView__METHOD__init2__SIGNATURE: &'static str = concat!("(IL", CLASSJ__java_util_List, ";)V");
    pub const CLASS__org_monfluo_wallet_lib_data_AddressView: &'static str = "org.monfluo.wallet.lib.data.AddressView";
    pub const CLASS__org_monfluo_wallet_lib_data_AddressView__METHOD__init5__SIGNATURE: &'static str = concat!("(IIL", CLASSJ__java_lang_String, ";ZJ)V");
    pub const CLASS__org_monfluo_wallet_lib_data_TransactionHash: &'static str = "org.monfluo.wallet.lib.data.TransactionHash";
    pub const CLASSJ__org_monfluo_wallet_lib_data_TransactionHash: &'static str = "org/monfluo/wallet/lib/data/TransactionHash";
    pub const CLASS__org_monfluo_wallet_lib_data_TransactionHash__METHOD__init1__SIGNATURE: &'static str = concat!("([B)V");
    pub const CLASS__org_monfluo_wallet_lib_data_TransactionViewInput: &'static str = "org.monfluo.wallet.lib.data.TransactionViewInput";
    pub const CLASS__org_monfluo_wallet_lib_data_TransactionViewInput__METHOD__init2__SIGNATURE: &'static str = concat!("(L", CLASSJ__java_lang_String, ";J)V");
    pub const CLASS__org_monfluo_wallet_lib_data_TransactionViewSpend: &'static str = "org.monfluo.wallet.lib.data.TransactionViewSpend";
    pub const CLASS__org_monfluo_wallet_lib_data_TransactionViewSpend__METHOD__init1__SIGNATURE: &'static str = "(J)V";
    pub const CLASS__org_monfluo_wallet_lib_data_TransactionView: &'static str = "org.monfluo.wallet.lib.data.TransactionView";
    pub const CLASS__org_monfluo_wallet_lib_data_TransactionView__METHOD__init6__SIGNATURE: &'static str = concat!("(L", CLASSJ__org_monfluo_wallet_lib_data_TransactionHash, ";JJIL", CLASSJ__java_util_List, ";L", CLASSJ__java_util_List, ";)V");

    pub const CLASS__org_monfluo_wallet_lib_data_NodeInfo: &'static str = "org.monfluo.wallet.lib.data.NodeInfo";
    pub const CLASSJ__org_monfluo_wallet_lib_data_NodeInfo: &'static str = "org/monfluo/wallet/lib/data/NodeInfo";
    pub const CLASS__org_monfluo_wallet_lib_data_NodeInfo__METHOD__init2__SIGNATURE: &'static str = "(II)V";
}

use constants::*;
use monfluo_oxide::{
    monero_wallet::interface::NodeInfo,
    wallet::{
        WalletSyncState,
        view::{
            account::AccountView,
            address::AddressView,
            balance::BalanceView,
            transaction::{TransactionView, TransactionViewInput, TransactionViewSpend},
        },
    },
};
use zeroize::Zeroizing;

pub const HANDLE_FIELD_NAME: &'static str = "_handle";

#[rustfmt::skip]
pub struct JniCache {
    pub class__java_lang_Number__method__intValue0: JMethodID,
    pub class__java_lang_Enum__method__name0: JMethodID,
    pub class__java_util_List__method__add1: JMethodID,
    pub class__java_util_ArrayList: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_wallet_WalletSyncObserver__method__observe1: JMethodID,
    pub class__org_monfluo_wallet_lib_seed_Seed__method__getMnemonic0: JMethodID,
    pub class__org_monfluo_wallet_lib_seed_Seed__method__getLanguage0: JMethodID,
    pub class__org_monfluo_wallet_lib_data_Balance: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_data_AddressView: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_data_NodeInfo: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_data_TransactionHash: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_data_TransactionViewInput: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_data_AccountView: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_data_TransactionViewSpend: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_data_TransactionView: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_seed_MoneroSeedLanguage: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_seed_MoneroSeed: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_seed_PolyseedLanguage: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_seed_Polyseed: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_wallet_Wallet: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_wallet_Daemon: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_wallet_sync_Preparing: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_wallet_sync_Starting: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_wallet_sync_Syncing: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_wallet_sync_Finished: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_wallet_sync_Cancelled: Global<JClass<'static>>,
    pub class__org_monfluo_wallet_lib_wallet_sync_Failed: Global<JClass<'static>>,
}

impl Debug for JniCache {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("JniCache").finish()
    }
}

static JNI_CACHE: OnceLock<JniCache> = OnceLock::new();

#[rustfmt::skip]
macro_rules! create_java_string {
    ($env:expr, $name:expr) => {{
        $env.new_string($name).expect(const_format::concatcp!("Failed to make a java string from ", stringify!($name)))
    }};
}

#[rustfmt::skip]
macro_rules! find_class {
    ($env:expr, $name:ident) => {{
        tracing::trace!("{}", const_format::concatcp!("Looking for class \"", $name, "\""));
        $env.find_class(JNIString::from($name))
            .expect(const_format::concatcp!(
                "Could not find class with name ",
                $name
            ))
    }};
}

#[rustfmt::skip]
macro_rules! load_class_as_global {
    ($env:expr, $name:ident) => {{
        tracing::trace!("{}", const_format::concatcp!("Loading class \"", $name, "\""));
        let class = $env.load_class(JNIString::from($name))
            .expect(const_format::concatcp!(
                "Could not load class with name ",
                $name
            ));
        tracing::trace!("{}", const_format::concatcp!("Loaded class \"", $name, "\""));
        $env.new_global_ref(class)
            .expect(const_format::concatcp!(
                "Could not create a global reference for class ",
                $name
            ))
    }};
}

#[rustfmt::skip]
macro_rules! create_runtime_method_signature {
    ($env:expr, $name:ident) => {
        RuntimeMethodSignature::from_str($name)
            .expect(const_format::concatcp!(
                "Could not create runtime method signature ",
                $name,
            ))
    };
}

#[rustfmt::skip]
macro_rules! find_method {
    ($env:expr, $class:ident, $name:ident, $signature:ident) => {{
        let class = find_class!($env, $class);
        let name = JNIString::from($name);
        let runtime_method_signature = create_runtime_method_signature!($env, $signature);
        $env.get_method_id(class, name, runtime_method_signature.method_signature())
            .expect(const_format::concatcp!(
                "Could not find method with name ",
                $name,
                " and signature ",
                $signature,
                " in class ",
                $class,
            ))
    }};
}

#[rustfmt::skip]
macro_rules! create_new_object {
    ($env:expr, $cache:expr, $class:ident, $signature:ident, $arguments:expr) => {{
        tracing::trace!("{}", const_format::concatcp!("Creating new object of class ", stringify!($class), " with constructor signature ", stringify!($signature), " (\"", $signature, "\") and arguments ", stringify!($arguments)));
        let class: &JClass = $cache.$class.as_ref();
        let runtime_method_signature = create_runtime_method_signature!($env, $signature);
        $env.new_object(class, runtime_method_signature.method_signature(), $arguments)
            .expect(concat!(
                "Could not create new object for class ",
                stringify!($class),
                " and signature ",
                stringify!($signature)
            ))
    }};
}

#[rustfmt::skip]
macro_rules! call_cached_method {
    ($env:expr, $cache:expr, $object:expr, $method:ident, bool, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Primitive(jni::signature::Primitive::Boolean), $arguments)
                .expect(const_format::concatcp!("Could not call bool method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Bool(b) => b == jni::sys::JNI_TRUE,
            other => panic!("Unexpected return type {:?} from bool method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, i8, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Primitive(jni::signature::Primitive::Byte), $arguments)
                .expect(const_format::concatcp!("Could not call i8 method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Byte(x) => x,
            other => panic!("Unexpected return type {:?} from i8 method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, i16, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Primitive(jni::signature::Primitive::Short), $arguments)
                .expect(const_format::concatcp!("Could not call i16 method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Short(x) => x,
            other => panic!("Unexpected return type {:?} from i16 method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, i32, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Primitive(jni::signature::Primitive::Int), $arguments)
                .expect(const_format::concatcp!("Could not call i32 method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Int(x) => x,
            other => panic!("Unexpected return type {:?} from i32 method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, i64, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Primitive(jni::signature::Primitive::Long), $arguments)
                .expect(const_format::concatcp!("Could not call i64 method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Long(x) => x,
            other => panic!("Unexpected return type {:?} from i64 method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, f32, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Primitive(jni::signature::Primitive::Float), $arguments)
                .expect(const_format::concatcp!("Could not call f32 method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Float(x) => x,
            other => panic!("Unexpected return type {:?} from f32 method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, f64, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Primitive(jni::signature::Primitive::Double), $arguments)
                .expect(const_format::concatcp!("Could not call f64 method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Double(x) => x,
            other => panic!("Unexpected return type {:?} from f64 method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, char, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Primitive(jni::signature::Primitive::Char), $arguments)
                .expect(const_format::concatcp!("Could not call char method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Char(x) => x,
            other => panic!("Unexpected return type {:?} from char method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, jobject, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Object, $arguments)
                .expect(const_format::concatcp!("Could not call jobject method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Object(o) => o,
            other => panic!("Unexpected return type {:?} from jobject method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, jarray, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Array, $arguments)
                .expect(const_format::concatcp!("Could not call jarray method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Object(o) => o,
            other => panic!("Unexpected return type {:?} from jarray method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};

    ($env:expr, $cache:expr, $object:expr, $method:ident, unit, $arguments:expr) => {{
        let raw: jni::objects::JValueOwned = unsafe {
            $env.call_method_unchecked($object, $cache.$method, jni::signature::ReturnType::Primitive(jni::signature::Primitive::Void), $arguments)
                .expect(const_format::concatcp!("Could not call unit method ", stringify!($method), " with arguments ", stringify!($arguments)))
        };
        match raw {
            jni::objects::JValueOwned::Void => (),
            other => panic!("Unexpected return type {:?} from unit method {} with arguments {}", other, stringify!($method), stringify!($arguments)),
        }
    }};
}

#[rustfmt::skip]
pub fn init_cache<'local>(env: &mut Env<'local>) {
    let class__java_lang_Number__method__intValue0 = find_method!(env, CLASSJ__java_lang_Number, CLASS__java_lang_Number__METHOD__intValue0, CLASS__java_lang_Number__METHOD__intValue0__SIGNATURE);
    let class__java_lang_Enum__method__name0 = find_method!(env, CLASS__java_lang_Enum, CLASS__java_lang_Enum__METHOD__name0, CLASS__java_lang_Enum__METHOD__name0__SIGNATURE);
    let class__java_util_List__method__add1 = find_method!(env, CLASSJ__java_util_List, CLASS__java_util_List__METHOD__add1, CLASS__java_util_List__METHOD__add1__SIGNATURE);
    let class__org_monfluo_wallet_lib_wallet_WalletSyncObserver__method__observe1 = find_method!(env, CLASSJ__org_monfluo_wallet_lib_wallet_sync_WalletSyncObserver, CLASS__org_monfluo_wallet_lib_wallet_sync_WalletSyncObserver__METHOD__observe1, CLASS__org_monfluo_wallet_lib_wallet_sync_WalletSyncObserver__METHOD__observe1__SIGNATURE);
    let class__org_monfluo_wallet_lib_seed_Seed__method__getMnemonic0 = find_method!(env, CLASS__org_monfluo_wallet_lib_seed_Seed, CLASS__org_monfluo_wallet_lib_seed_Seed__METHOD__getMnemonic0, CLASS__org_monfluo_wallet_lib_seed_Seed__METHOD__getMnemonic0__SIGNATURE);
    let class__org_monfluo_wallet_lib_seed_Seed__method__getLanguage0 = find_method!(env, CLASS__org_monfluo_wallet_lib_seed_Seed, CLASS__org_monfluo_wallet_lib_seed_Seed__METHOD__getLanguage0, CLASS__org_monfluo_wallet_lib_seed_Seed__METHOD__getLanguage0__SIGNATURE);

    let class__java_util_ArrayList = load_class_as_global!(env, CLASS__java_util_ArrayList);
    let class__org_monfluo_wallet_lib_data_Balance = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_data_Balance);
    let class__org_monfluo_wallet_lib_data_AccountView = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_data_AccountView);
    let class__org_monfluo_wallet_lib_data_AddressView = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_data_AddressView);
    let class__org_monfluo_wallet_lib_data_NodeInfo = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_data_NodeInfo);
    let class__org_monfluo_wallet_lib_data_TransactionHash = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_data_TransactionHash);
    let class__org_monfluo_wallet_lib_data_TransactionViewInput = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_data_TransactionViewInput);
    let class__org_monfluo_wallet_lib_data_TransactionViewSpend = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_data_TransactionViewSpend);
    let class__org_monfluo_wallet_lib_data_TransactionView = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_data_TransactionView);
    let class__org_monfluo_wallet_lib_seed_MoneroSeedLanguage = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_seed_MoneroSeedLanguage);
    let class__org_monfluo_wallet_lib_seed_MoneroSeed = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_seed_MoneroSeed);
    let class__org_monfluo_wallet_lib_seed_PolyseedLanguage = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_seed_PolyseedLanguage);
    let class__org_monfluo_wallet_lib_seed_Polyseed = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_seed_Polyseed);
    let class__org_monfluo_wallet_lib_wallet_Wallet = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_wallet_Wallet);
    let class__org_monfluo_wallet_lib_wallet_Daemon = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_wallet_Daemon);
    let class__org_monfluo_wallet_lib_wallet_sync_Preparing = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_wallet_sync_Preparing);
    let class__org_monfluo_wallet_lib_wallet_sync_Starting = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_wallet_sync_Starting);
    let class__org_monfluo_wallet_lib_wallet_sync_Syncing = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_wallet_sync_Syncing);
    let class__org_monfluo_wallet_lib_wallet_sync_Finished = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_wallet_sync_Finished);
    let class__org_monfluo_wallet_lib_wallet_sync_Cancelled = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_wallet_sync_Cancelled);
    let class__org_monfluo_wallet_lib_wallet_sync_Failed = load_class_as_global!(env, CLASS__org_monfluo_wallet_lib_wallet_sync_Failed);

    JNI_CACHE
        .set(JniCache {
            class__java_lang_Number__method__intValue0,
            class__java_lang_Enum__method__name0,
            class__java_util_List__method__add1,
            class__org_monfluo_wallet_lib_wallet_WalletSyncObserver__method__observe1,
            class__org_monfluo_wallet_lib_seed_Seed__method__getMnemonic0,
            class__org_monfluo_wallet_lib_seed_Seed__method__getLanguage0,
            
            class__java_util_ArrayList,
            
            class__org_monfluo_wallet_lib_data_Balance,
            class__org_monfluo_wallet_lib_data_AccountView,
            class__org_monfluo_wallet_lib_data_AddressView,
            class__org_monfluo_wallet_lib_data_NodeInfo,
            class__org_monfluo_wallet_lib_data_TransactionHash,
            class__org_monfluo_wallet_lib_data_TransactionViewInput,
            class__org_monfluo_wallet_lib_data_TransactionViewSpend,
            class__org_monfluo_wallet_lib_data_TransactionView,
            class__org_monfluo_wallet_lib_seed_MoneroSeedLanguage,
            class__org_monfluo_wallet_lib_seed_MoneroSeed,
            class__org_monfluo_wallet_lib_seed_PolyseedLanguage,
            class__org_monfluo_wallet_lib_seed_Polyseed,
            class__org_monfluo_wallet_lib_wallet_Wallet,
            class__org_monfluo_wallet_lib_wallet_Daemon,
            class__org_monfluo_wallet_lib_wallet_sync_Preparing,
            class__org_monfluo_wallet_lib_wallet_sync_Starting,
            class__org_monfluo_wallet_lib_wallet_sync_Syncing,
            class__org_monfluo_wallet_lib_wallet_sync_Finished,
            class__org_monfluo_wallet_lib_wallet_sync_Cancelled,
            class__org_monfluo_wallet_lib_wallet_sync_Failed,
        })
        .expect("Failed to initialize JNI cache");
}

pub fn get_cache() -> &'static JniCache {
    JNI_CACHE.get().expect("JNI cache was not initialized")
}

pub struct JavaStringHelper<'local> {
    mutf8_chars: MUTF8Chars<'local, &'local JString<'local>>,
}

impl<'local> JavaStringHelper<'local> {
    pub fn try_from_jstring(
        env: &Env<'local>,
        jstring: &'local JString<'local>,
    ) -> Result<Self, jni::errors::Error> {
        let mutf8_chars = jstring.mutf8_chars(env)?;
        Ok(Self { mutf8_chars })
    }

    pub fn try_from_nullable_jstring(
        env: &Env<'local>,
        jstring: &'local JString<'local>,
    ) -> Result<Option<Self>, jni::errors::Error> {
        if jstring.is_null() {
            return Ok(None);
        }
        Self::try_from_jstring(env, jstring).map(Some)
    }

    pub fn from_jstring(env: &Env<'local>, jstring: &'local JString<'local>) -> Self {
        Self::try_from_jstring(env, jstring)
            .expect("Failed to construct JavaStringHelper from JString")
    }

    pub fn from_nullable_jstring(
        env: &Env<'local>,
        jstring: &'local JString<'local>,
    ) -> Option<Self> {
        Self::try_from_nullable_jstring(env, jstring)
            .expect("Failed to construct JavaStringHelper from nullable JString")
    }

    pub fn to_cow_str(&'local self) -> Cow<'local, str> {
        self.mutf8_chars.to_str()
    }
}

pub fn from_java_number_int_value<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    java_number: &JObject<'local>,
) -> Option<i32> {
    if java_number.is_null() {
        return None;
    }
    Some(call_cached_method!(
        env,
        cache,
        java_number,
        class__java_lang_Number__method__intValue0,
        i32,
        &[]
    ))
}

pub fn arraylist_new<'local>(env: &mut Env<'local>, cache: &'static JniCache) -> JObject<'local> {
    create_new_object!(
        env,
        cache,
        class__java_util_ArrayList,
        CLASS__java_util_ArrayList__METHOD__init0__SIGNATURE,
        &[]
    )
}

pub fn list_add<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    list: &JObject<'local>,
    object: &JObject<'local>,
) -> bool {
    call_cached_method!(
        env,
        cache,
        list,
        class__java_util_List__method__add1,
        bool,
        &[JValue::Object(&object).as_jni()]
    )
}

pub fn enum_name<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    r#enum: &JObject<'local>,
) -> JString<'local> {
    let name = call_cached_method!(
        env,
        cache,
        r#enum,
        class__java_lang_Enum__method__name0,
        jobject,
        &[]
    );
    JString::cast_local(env, name).expect("Enum name returned Object that is not a String")
}

pub fn get_handle<'local>(env: &mut Env<'local>, object: &JObject<'local>) -> usize {
    let handle = env
        .get_field(
            object,
            JNIString::new(HANDLE_FIELD_NAME),
            RuntimeFieldSignature::from_str("J")
                .unwrap()
                .field_signature(),
        )
        .unwrap();
    let handle = handle.j().unwrap();
    handle as usize
}

pub fn new_monero_seed<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    java_mnemonic: &JString<'local>,
    java_language: &JObject<'local>,
) -> JObject<'local> {
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_seed_MoneroSeed,
        CLASS__org_monfluo_wallet_lib_seed_MoneroSeed__METHOD__init2__SIGNATURE,
        &[JValue::Object(java_mnemonic), JValue::Object(java_language)]
    )
}

pub fn new_polyseed<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    java_mnemonic: &JString<'local>,
    java_language: &JObject<'local>,
) -> JObject<'local> {
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_seed_Polyseed,
        CLASS__org_monfluo_wallet_lib_seed_Polyseed__METHOD__init2__SIGNATURE,
        &[JValue::Object(java_mnemonic), JValue::Object(java_language)]
    )
}

pub fn from_java_seed<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    java_seed: &JObject<'local>,
) -> monfluo_oxide::monero_wallet_util::seed::Seed {
    let java_mnemonic = call_cached_method!(
        env,
        cache,
        java_seed,
        class__org_monfluo_wallet_lib_seed_Seed__method__getMnemonic0,
        jobject,
        &[]
    );
    let java_mnemonic = JString::cast_local(env, java_mnemonic).unwrap();
    let mnemonic = JavaStringHelper::from_jstring(env, &java_mnemonic);
    let java_language = call_cached_method!(
        env,
        cache,
        java_seed,
        class__org_monfluo_wallet_lib_seed_Seed__method__getLanguage0,
        jobject,
        &[]
    );
    let language = from_java_seed_language(env, cache, &java_language);
    match language {
        monfluo_oxide::monero_wallet_util::seed::SeedType::Original(language) => {
            let seed = monfluo_oxide::monero_wallet_util::seed::original::Seed::from_string(
                language,
                Zeroizing::new(mnemonic.to_cow_str().deref().to_owned()),
            )
            .unwrap();
            monfluo_oxide::monero_wallet_util::seed::Seed::Original(seed)
        }
        monfluo_oxide::monero_wallet_util::seed::SeedType::Polyseed(language) => {
            let seed = monfluo_oxide::monero_wallet_util::seed::polyseed::Polyseed::from_string(
                language,
                Zeroizing::new(mnemonic.to_cow_str().deref().to_owned()),
            )
            .unwrap();
            monfluo_oxide::monero_wallet_util::seed::Seed::Polyseed(seed)
        }
    }
}

pub fn new_wallet<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    handle: usize,
) -> JObject<'local> {
    let handle: jlong = handle as jlong;
    let jhandle = JValue::Long(handle);
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_wallet_Wallet,
        CLASS__org_monfluo_wallet_lib_wallet_Wallet__METHOD__init1__SIGNATURE,
        &[jhandle]
    )
}

pub fn new_daemon<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    handle: usize,
    node_name: &str,
    node_address: &str,
    node_username: Option<&str>,
    node_password: Option<&str>,
    node_proxy: Option<&str>,
) -> JObject<'local> {
    let java_node_name = create_java_string!(env, node_name);
    let java_node_address = create_java_string!(env, node_address);
    let java_node_username = node_username
        .map(|node_username| create_java_string!(env, node_username).into())
        .unwrap_or(JObject::null());
    let java_node_password = node_password
        .map(|node_password| create_java_string!(env, node_password).into())
        .unwrap_or(JObject::null());
    let java_node_proxy = node_proxy
        .map(|node_proxy| create_java_string!(env, node_proxy).into())
        .unwrap_or(JObject::null());
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_wallet_Daemon,
        CLASS__org_monfluo_wallet_lib_wallet_Daemon__METHOD__init6__SIGNATURE,
        &[
            JValue::Long(handle as jlong),
            JValue::Object(&java_node_name),
            JValue::Object(&java_node_address),
            JValue::Object(&java_node_username),
            JValue::Object(&java_node_password),
            JValue::Object(&java_node_proxy),
        ]
    )
}

pub fn new_wallet_sync_state<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    wallet_sync_state: WalletSyncState,
) -> JObject<'local> {
    match wallet_sync_state {
        WalletSyncState::Preparing {
            timestamp,
            current_height,
        } => {
            create_new_object!(
                env,
                cache,
                class__org_monfluo_wallet_lib_wallet_sync_Preparing,
                CLASS__org_monfluo_wallet_lib_wallet_sync_Preparing__METHOD__init2__SIGNATURE,
                &[
                    JValue::Long(timestamp as jlong),
                    JValue::Int(current_height as jint)
                ]
            )
        }
        WalletSyncState::Starting {
            timestamp,
            current_height,
            target_height,
            node_info,
        } => {
            let java_node_info = new_node_info(env, cache, node_info);
            create_new_object!(
                env,
                cache,
                class__org_monfluo_wallet_lib_wallet_sync_Starting,
                CLASS__org_monfluo_wallet_lib_wallet_sync_Starting__METHOD__init4__SIGNATURE,
                &[
                    JValue::Long(timestamp as jlong),
                    JValue::Int(current_height as jint),
                    JValue::Int(target_height as jint),
                    JValue::Object(&java_node_info),
                ]
            )
        }
        WalletSyncState::Syncing {
            timestamp,
            current_height,
            target_height,
        } => {
            create_new_object!(
                env,
                cache,
                class__org_monfluo_wallet_lib_wallet_sync_Syncing,
                CLASS__org_monfluo_wallet_lib_wallet_sync_Syncing__METHOD__init3__SIGNATURE,
                &[
                    JValue::Long(timestamp as jlong),
                    JValue::Int(current_height as jint),
                    JValue::Int(target_height as jint)
                ]
            )
        }
        WalletSyncState::Finished { timestamp, height } => {
            create_new_object!(
                env,
                cache,
                class__org_monfluo_wallet_lib_wallet_sync_Finished,
                CLASS__org_monfluo_wallet_lib_wallet_sync_Finished__METHOD__init2__SIGNATURE,
                &[
                    JValue::Long(timestamp as jlong),
                    JValue::Int(height as jint)
                ]
            )
        }
        WalletSyncState::Cancelled { timestamp } => {
            create_new_object!(
                env,
                cache,
                class__org_monfluo_wallet_lib_wallet_sync_Cancelled,
                CLASS__org_monfluo_wallet_lib_wallet_sync_Cancelled__METHOD__init1__SIGNATURE,
                &[JValue::Long(timestamp as jlong),]
            )
        }
        WalletSyncState::Failed { timestamp, error } => todo!(),
    }
}

pub fn wallet_sync_observer_observe<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    observer: &JObject<'local>,
    state: &JObject<'local>,
) {
    call_cached_method!(
        env,
        cache,
        observer,
        class__org_monfluo_wallet_lib_wallet_WalletSyncObserver__method__observe1,
        unit,
        &[JValue::Object(&state).as_jni()]
    );
}

pub fn new_balance<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    balance_view: BalanceView,
) -> JObject<'local> {
    let BalanceView {
        total,
        unlocked,
        pending,
        frozen,
    } = balance_view;
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_data_Balance,
        CLASS__org_monfluo_wallet_lib_data_Balance__METHOD__init4__SIGNATURE,
        &[
            JValue::Long(total as i64),
            JValue::Long(unlocked as i64),
            JValue::Long(pending as i64),
            JValue::Long(frozen as i64)
        ]
    )
}

pub fn new_account_view<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    account_view: AccountView,
) -> JObject<'local> {
    let AccountView {
        account_index,
        addresses,
    } = account_view;
    let java_addresses = arraylist_new(env, cache);
    for address in addresses {
        let java_address = new_address_view(env, cache, address);
        list_add(env, cache, &java_addresses, &java_address);
    }
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_data_AccountView,
        CLASS__org_monfluo_wallet_lib_data_AccountView__METHOD__init2__SIGNATURE,
        &[
            JValue::Int(account_index as jint),
            JValue::Object(&java_addresses)
        ]
    )
}

pub fn new_address_view<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    address_view: AddressView,
) -> JObject<'local> {
    let AddressView {
        account_index,
        address_index,
        value,
        used,
        amount,
    } = address_view;
    let java_value = env.new_string(value).unwrap();
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_data_AddressView,
        CLASS__org_monfluo_wallet_lib_data_AddressView__METHOD__init5__SIGNATURE,
        &[
            JValue::Int(account_index as jint),
            JValue::Int(address_index as jint),
            JValue::Object(&java_value),
            JValue::Bool(used),
            JValue::Long(amount as jlong)
        ]
    )
}

pub fn new_node_info<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    node_info: NodeInfo,
) -> JObject<'local> {
    let NodeInfo {
        height,
        target_height,
        nettype: _,
        status: _,
    } = node_info;
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_data_NodeInfo,
        CLASS__org_monfluo_wallet_lib_data_NodeInfo__METHOD__init2__SIGNATURE,
        &[
            JValue::Int(height as jint),
            JValue::Int(target_height as jint)
        ]
    )
}

pub fn new_transaction_hash<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    transaction_hash: &[u8; 32],
) -> JObject<'local> {
    // SAFETY: u8 and i8 have the same size and alignment
    let transaction_hash: &[i8; 32] = unsafe { std::mem::transmute(transaction_hash) };
    let java_array = env.new_byte_array(32).unwrap();
    java_array.set_region(env, 0, transaction_hash).unwrap();
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_data_TransactionHash,
        CLASS__org_monfluo_wallet_lib_data_TransactionHash__METHOD__init1__SIGNATURE,
        &[JValue::Object(&java_array)]
    )
}

pub fn new_transaction_view_input<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    transaction_view_input: &TransactionViewInput,
) -> JObject<'local> {
    let TransactionViewInput { address, amount } = transaction_view_input;
    let java_address = env.new_string(address).unwrap();
    let java_amount = *amount as jlong;
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_data_TransactionViewInput,
        CLASS__org_monfluo_wallet_lib_data_TransactionViewInput__METHOD__init2__SIGNATURE,
        &[JValue::Object(&java_address), JValue::Long(java_amount)]
    )
}

pub fn new_transaction_view_spend<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    transaction_view_spend: &TransactionViewSpend,
) -> JObject<'local> {
    let TransactionViewSpend { amount } = transaction_view_spend;
    let java_amount = *amount as jlong;
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_data_TransactionViewSpend,
        CLASS__org_monfluo_wallet_lib_data_TransactionViewSpend__METHOD__init1__SIGNATURE,
        &[JValue::Long(java_amount)]
    )
}

pub fn new_transaction_view<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    transaction_view: &TransactionView,
) -> JObject<'local> {
    let TransactionView {
        transaction_hash,
        timestamp,
        fee,
        inputs,
        confirmations,
        spends,
    } = transaction_view;
    let java_transaction_hash = new_transaction_hash(env, cache, transaction_hash);
    let java_timestamp = *timestamp as jlong;
    let java_fee = *fee as jlong;
    let java_confirmations = *confirmations as jint;
    let java_inputs = arraylist_new(env, cache);
    for input in inputs {
        let java_input = new_transaction_view_input(env, cache, input);
        list_add(env, cache, &java_inputs, &java_input);
    }
    let java_spends = arraylist_new(env, cache);
    for spend in spends {
        let java_spend = new_transaction_view_spend(env, cache, spend);
        list_add(env, cache, &java_spends, &java_spend);
    }
    create_new_object!(
        env,
        cache,
        class__org_monfluo_wallet_lib_data_TransactionView,
        CLASS__org_monfluo_wallet_lib_data_TransactionView__METHOD__init6__SIGNATURE,
        &[
            JValue::Object(&java_transaction_hash),
            JValue::Long(java_timestamp),
            JValue::Long(java_fee),
            JValue::Int(java_confirmations),
            JValue::Object(&java_inputs),
            JValue::Object(&java_spends),
        ]
    )
}

pub fn new_transaction_views<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    transaction_views: &[TransactionView],
) -> JObject<'local> {
    let java_transaction_views = arraylist_new(env, cache);
    for transaction_view in transaction_views {
        let java_transaction_view = new_transaction_view(env, cache, transaction_view);
        list_add(env, cache, &java_transaction_views, &java_transaction_view);
    }
    java_transaction_views
}

pub fn to_java_monero_seed_language<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    monero_seed_language: monfluo_oxide::monero_wallet_util::seed::original::Language,
) -> JObject<'local> {
    let class: &JClass = cache
        .class__org_monfluo_wallet_lib_seed_MoneroSeedLanguage
        .as_ref();
    let JValueOwned::Object(java_monero_seed_language) = env
        .get_static_field(
            class,
            JNIString::new(monero_seed_language.to_string()),
            RuntimeFieldSignature::from_str(concatcp!(
                "L",
                CLASSJ__org_monfluo_wallet_lib_seed_MoneroSeedLanguage,
                ";"
            ))
            .unwrap()
            .field_signature(),
        )
        .unwrap()
    else {
        panic!()
    };
    java_monero_seed_language
}

pub fn to_java_polyseed_language<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    polyseed_language: monfluo_oxide::monero_wallet_util::seed::polyseed::Language,
) -> JObject<'local> {
    let class: &JClass = cache
        .class__org_monfluo_wallet_lib_seed_PolyseedLanguage
        .as_ref();
    let JValueOwned::Object(java_monero_seed_language) = env
        .get_static_field(
            class,
            JNIString::new(polyseed_language.to_string()),
            RuntimeFieldSignature::from_str(concatcp!(
                "L",
                CLASSJ__org_monfluo_wallet_lib_seed_PolyseedLanguage,
                ";"
            ))
            .unwrap()
            .field_signature(),
        )
        .unwrap()
    else {
        panic!()
    };
    java_monero_seed_language
}

pub fn from_java_seed_language<'local>(
    env: &mut Env<'local>,
    cache: &'static JniCache,
    seed_language: &JObject<'local>,
) -> monfluo_oxide::monero_wallet_util::seed::SeedType {
    let seed_language_class = env.get_object_class(seed_language).unwrap();
    let seed_language_class_name = seed_language_class.get_name(env).unwrap();
    let enum_name = enum_name(env, cache, seed_language);
    let enum_name = JavaStringHelper::from_jstring(env, &enum_name);
    match &*JavaStringHelper::from_jstring(env, &seed_language_class_name).to_cow_str() {
        CLASS__org_monfluo_wallet_lib_seed_MoneroSeedLanguage => {
            monfluo_oxide::monero_wallet_util::seed::SeedType::Original(
                monfluo_oxide::monero_wallet_util::seed::original::Language::from_str(
                    &enum_name.to_cow_str(),
                )
                .unwrap(),
            )
        }
        CLASS__org_monfluo_wallet_lib_seed_PolyseedLanguage => {
            monfluo_oxide::monero_wallet_util::seed::SeedType::Polyseed(
                monfluo_oxide::monero_wallet_util::seed::polyseed::Language::from_str(
                    &enum_name.to_cow_str(),
                )
                .unwrap(),
            )
        }
        _ => unreachable!(),
    }
}
