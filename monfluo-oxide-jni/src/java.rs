#![allow(non_snake_case, non_upper_case_globals)]

use std::{fmt::Debug, sync::OnceLock};

use jni::{
    JNIEnv,
    objects::{JMethodID, JObject, JString, JValue},
};

#[rustfmt::skip]
mod constants {
    use const_format::concatcp as concat;

    pub const CLASS__java_lang_Object: &'static str = "java/lang/Object";
    pub const CLASS__java_lang_String: &'static str = "java/lang/String";
    pub const CLASS__java_lang_Enum: &'static str = "java/lang/Enum";
    pub const CLASS__java_lang_Enum__METHOD__name0: &'static str = "name";
    pub const CLASS__java_lang_Enum__METHOD__name0__SIGNATURE: &'static str = concat!("()L", CLASS__java_lang_String, ";");
    pub const CLASS__java_util_List: &'static str = "java/util/List";
    pub const CLASS__java_util_List__METHOD__add1: &'static str = "add";
    pub const CLASS__java_util_List__METHOD__add1__SIGNATURE: &'static str = concat!("(L", CLASS__java_lang_Object, ";)Z");
    pub const CLASS__java_util_ArrayList: &'static str = "java/util/ArrayList";
    pub const CLASS__java_util_ArrayList__METHOD__init0__SIGNATURE: &'static str = "()V";
}

use constants::*;

#[rustfmt::skip]
pub struct JniCache {
    pub class__java_lang_Enum__method__name0: JMethodID,
    pub class__java_util_List__method__add1: JMethodID,
}

impl Debug for JniCache {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("JniCache").finish()
    }
}

static JNI_CACHE: OnceLock<JniCache> = OnceLock::new();

#[rustfmt::skip]
macro_rules! find_method {
    ($env:expr, $class:expr, $name:expr, $signature:expr) => {
        $env.get_method_id($class, $name, $signature)
            .expect(const_format::concatcp!(
                "Could not find method with name ",
                $name,
                " and signature ",
                $signature,
                " in class ",
                $class
            ))
    };
}

#[rustfmt::skip]
macro_rules! create_new_object {
    ($env:expr, $class:expr, $signature:expr, $arguments:expr) => {
        $env.new_object($class, $signature, $arguments)
            .expect(const_format::concatcp!(
                "Could not create new object for class ",
                $class,
                " and signature ",
                $signature
            ))
    };
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
pub fn init_cache<'local>(env: &mut JNIEnv<'local>) {
    let class__java_lang_Enum__method__name0 = find_method!(env, CLASS__java_lang_Enum, CLASS__java_lang_Enum__METHOD__name0, CLASS__java_lang_Enum__METHOD__name0__SIGNATURE);
    let class__java_util_List__method__add1 = find_method!(env, CLASS__java_util_List, CLASS__java_util_List__METHOD__add1, CLASS__java_util_List__METHOD__add1__SIGNATURE);

    JNI_CACHE
        .set(JniCache {
            class__java_lang_Enum__method__name0,
            class__java_util_List__method__add1,
        })
        .expect("Failed to initialize JNI cache");
}

pub fn get_cache() -> &'static JniCache {
    JNI_CACHE.get().expect("JNI cache was not initialized")
}

#[repr(transparent)]
pub struct ArrayList<'local>(pub JObject<'local>);

impl<'local> ArrayList<'local> {
    pub fn new(env: &mut JNIEnv<'local>) -> Self {
        let object = create_new_object!(
            env,
            CLASS__java_util_ArrayList,
            CLASS__java_util_ArrayList__METHOD__init0__SIGNATURE,
            &[]
        );
        ArrayList(object)
    }

    pub fn add(
        &self,
        env: &mut JNIEnv<'local>,
        cache: &'static JniCache,
        object: JObject<'local>,
    ) -> bool {
        call_cached_method!(
            env,
            cache,
            &self.0,
            class__java_util_List__method__add1,
            bool,
            &[JValue::Object(&object).as_jni()]
        )
    }
}

#[repr(transparent)]
pub struct Enum<'local>(pub JObject<'local>);

impl<'local> Enum<'local> {
    pub fn name(&self, env: &mut JNIEnv<'local>, cache: &'static JniCache) -> JString<'local> {
        let name = call_cached_method!(
            env,
            cache,
            &self.0,
            class__java_lang_Enum__method__name0,
            jobject,
            &[]
        );
        JString::from(name)
    }
}
